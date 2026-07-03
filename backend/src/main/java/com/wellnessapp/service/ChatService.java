package com.wellnessapp.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.wellnessapp.dto.ChatDTOs.*;
import com.wellnessapp.dto.ChatDTOs.ChatHistoryResponse;
import com.wellnessapp.dto.ChatDTOs.ChatRequest;
import com.wellnessapp.dto.ChatDTOs.ChatResponse;
import com.wellnessapp.entity.ChatMessage;
import com.wellnessapp.entity.User;
import com.wellnessapp.repository.ChatMessageRepository;

/**
 * Chatbot service with <strong>3-tier fallback</strong>:
 *
 * <ol>
 *   <li><b>Python Agent RAG</b> — calls {@code POST /chat} on the Python
 *       agent, which has DeepSeek + RAG tool calling over the MSD medical
 *       knowledge base.</li>
 *   <li><b>Direct DeepSeek</b> — if the Python agent is unreachable, calls
 *       the DeepSeek Chat API directly via {@link AIClientService}.</li>
 *   <li><b>Static fallback</b> — if both are unavailable, returns a simple
 *       offline message.</li>
 * </ol>
 *
 * @author WellnessApp Team
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatMessageRepository chatMessageRepository;
    private final AIClientService aiClientService;
    private final WellnessInsightsService wellnessInsightsService;
    private final RestClient restClient;
    private final String agentBaseUrl;

    /** Maximum conversation history depth sent to the AI. */
    private static final int MAX_HISTORY_TURNS = 10;

    public ChatService(
            ChatMessageRepository chatMessageRepository,
            AIClientService aiClientService,
            WellnessInsightsService wellnessInsightsService,
            @Value("${agent.python.base-url:http://localhost:5001}") String agentBaseUrl) {
        this.chatMessageRepository = chatMessageRepository;
        this.aiClientService = aiClientService;
        this.wellnessInsightsService = wellnessInsightsService;
        this.agentBaseUrl = agentBaseUrl;
        this.restClient = RestClient.builder()
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    // ── Public API ─────────────────────────────────────────────────

    /**
     * Process a user message through the 3-tier fallback pipeline.
     */
    public ChatResponse processMessage(User user, ChatRequest request) {
        String userMessage = request.getMessage().trim();
        log.debug("Chat message from user {}: \"{}\"", user.getId(), truncate(userMessage, 80));

        // 1. Build conversation history from DB
        List<Map<String, String>> history = buildHistory(user);
        String wellnessContext = wellnessInsightsService.buildChatContext(user, 14);

        // 2. Try 3 tiers
        String reply = tryPythonAgent(userMessage, history, wellnessContext);
        if (reply != null) {
            log.info("User {} → Tier-1 (Python RAG) responded", user.getId());
        }

        if (reply == null) {
            reply = tryDirectDeepSeek(userMessage, history, wellnessContext);
            if (reply != null) {
                log.info("User {} → Tier-2 (Direct DeepSeek) responded", user.getId());
            }
        }

        if (reply == null) {
            reply = buildFallbackReply(wellnessContext);
            log.warn("User {} → Tier-3 (static fallback) used", user.getId());
        }

        // 3. Persist
        ChatMessage chatMessage = ChatMessage.builder()
                .user(user)
                .userMessage(userMessage)
                .botResponse(reply)
                .build();
        chatMessageRepository.save(chatMessage);

        return ChatResponse.builder()
                .reply(reply)
                .timestamp(LocalDateTime.now().toString())
                .build();
    }

    /**
     * Get chat history for the user.
     */
    public List<ChatHistoryResponse> getHistory(User user) {
        return chatMessageRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(msg -> ChatHistoryResponse.builder()
                        .id(msg.getId())
                        .userMessage(msg.getUserMessage())
                        .botResponse(msg.getBotResponse())
                        .createdAt(msg.getCreatedAt() != null
                                ? msg.getCreatedAt().toString() : null)
                        .build())
                .toList();
    }

    // ── 3-Tier fallback ────────────────────────────────────────────

    /** Tier 1: Python Agent with RAG tool calling. */
    private String tryPythonAgent(
            String userMessage,
            List<Map<String, String>> history,
            String wellnessContext) {
        try {
            List<Map<String, String>> contextualHistory = new ArrayList<>();
            contextualHistory.add(Map.of("role", "system", "content", wellnessContext));
            contextualHistory.addAll(history);

            Map<String, Object> body = Map.of(
                    "message", userMessage,
                    "history", contextualHistory
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(agentBaseUrl + "/chat")
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                return (String) response.get("answer");
            }
            log.warn("Python agent returned unsuccessful response: {}", response);
            return null;
        } catch (Exception e) {
            log.warn("Python agent unavailable: {}", e.getMessage());
            return null;
        }
    }

    /** Tier 2: Direct DeepSeek call (no RAG). */
    private String tryDirectDeepSeek(
            String userMessage,
            List<Map<String, String>> history,
            String wellnessContext) {
        try {
            // Build message list: system prompt + history + new user message
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of(
                    "role", "system",
                    "content", AIClientService.SYSTEM_PROMPT + "\n\n" + wellnessContext));
            messages.addAll(history);
            messages.add(Map.of("role", "user", "content", userMessage));

            return aiClientService.chat(messages);
        } catch (AIClientService.AiServiceException e) {
            log.warn("Direct DeepSeek unavailable: {}", e.getMessage());
            return null;
        }
    }

    /** Tier 3: Static fallback when both AI services are down. */
    private String buildFallbackReply(String wellnessContext) {
        if (wellnessContext.contains("has no wellness records")) {
            return "I cannot see any wellness records from the last 14 days yet. "
                    + "Please log your sleep and activity first, then I can answer questions "
                    + "about your weekly sleep, exercise, and improvement priorities.";
        }
        return "\uD83D\uDC4B Hi! I'm WellBot. I'm currently offline, but I'll be back soon. "
                + "Please try again in a moment. "
                + "In the meantime, you can check your wellness records in the app!";
    }

    // ── History helpers ────────────────────────────────────────────

    /**
     * Build a conversation history from the database.
     * Returns the most recent {@link #MAX_HISTORY_TURNS} user/assistant pairs.
     */
    private List<Map<String, String>> buildHistory(User user) {
        List<ChatMessage> recent = chatMessageRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId());

        if (recent.isEmpty()) {
            return Collections.emptyList();
        }

        // DB returns newest-first; reverse to chronological order and limit
        Collections.reverse(recent);
        if (recent.size() > MAX_HISTORY_TURNS) {
            recent = recent.subList(recent.size() - MAX_HISTORY_TURNS, recent.size());
        }

        List<Map<String, String>> history = new ArrayList<>();
        for (ChatMessage msg : recent) {
            history.add(Map.of("role", "user", "content", msg.getUserMessage()));
            history.add(Map.of("role", "assistant", "content", msg.getBotResponse()));
        }
        return history;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
