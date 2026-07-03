package com.wellnessapp.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Client service for calling DeepSeek Chat Completions API directly.
 *
 * <p>Acts as the second-tier fallback (the first tier is the Python agent's
 * RAG-powered /chat endpoint). Uses {@link RestClient} (Spring 6.x).</p>
 *
 * @author WellnessApp Team
 */
@Service
public class AIClientService {

    private static final Logger log = LoggerFactory.getLogger(AIClientService.class);
    private static final String DEEPSEEK_CHAT_URL = "https://api.deepseek.com/chat/completions";
    private static final String DEEPSEEK_MODEL = "deepseek-v4-flash";

    private final RestClient restClient;
    private final String apiKey;

    /**
     * Fallback system prompt (used when Python agent RAG is unavailable).
     * The RAG pipeline has its own richer prompt with medical knowledge-base access.
     */
    static final String SYSTEM_PROMPT = """
            You are a friendly and knowledgeable wellness assistant named "WellBot".
            Your role is to provide helpful, evidence-based advice on health and wellness topics.

            Current mode: FALLBACK (medical knowledge base is temporarily unavailable).
            Your answers are based on your own training knowledge, not a curated medical corpus.
            If you're unsure about a medical fact, say so clearly.

            Guidelines:
            - ONLY answer questions related to health, wellness, fitness, nutrition, sleep, mental health, and lifestyle.
            - If asked about topics outside wellness, politely redirect the user back to wellbeing.
            - Use the same language as the user.
            - Keep responses concise (2-4 sentences) and actionable.
            - Be encouraging and supportive.
            - Do NOT provide medical diagnoses. Always recommend consulting a healthcare professional for medical concerns.
            - If you don't know something or are unsure, be honest about it.
            """;

    public AIClientService(@Value("${deepseek.api-key:}") String apiKey) {
        this.apiKey = apiKey;
        this.restClient = RestClient.builder()
                .baseUrl(DEEPSEEK_CHAT_URL)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    // ── Public API ─────────────────────────────────────────────────

    /** Single-turn convenience wrapper. */
    public String getChatResponse(String userMessage) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        messages.add(Map.of("role", "user", "content", userMessage));
        return chat(messages, 300);
    }

    /** Multi-turn chat with conversation history. */
    public String chat(List<Map<String, String>> messages) {
        return chat(messages, 300);
    }

    /** Multi-turn chat with configurable max tokens. */
    public String chat(List<Map<String, String>> messages, int maxTokens) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiServiceException("DeepSeek API key is not configured");
        }

        Map<String, Object> body = Map.of(
                "model", DEEPSEEK_MODEL,
                "messages", messages,
                "max_tokens", maxTokens,
                "temperature", 0.7,
                "stream", false
        );

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .header("Authorization", "Bearer " + apiKey)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            return extractReply(response);
        } catch (Exception e) {
            log.error("DeepSeek API call failed: {}", e.getMessage());
            throw new AiServiceException("DeepSeek API unavailable: " + e.getMessage(), e);
        }
    }

    // ── Internal helpers ───────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private String extractReply(Map<String, Object> response) {
        if (response == null) {
            throw new AiServiceException("DeepSeek returned null response");
        }

        List<Map<String, Object>> choices =
                (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new AiServiceException("DeepSeek response did not contain choices");
        }

        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        if (message == null || message.get("content") == null) {
            throw new AiServiceException("DeepSeek response did not contain message content");
        }

        return message.get("content").toString().trim();
    }

    // ── Exception type ─────────────────────────────────────────────

    /** Exception thrown when AI service is unavailable. */
    public static class AiServiceException extends RuntimeException {
        public AiServiceException(String message) { super(message); }
        public AiServiceException(String message, Throwable cause) { super(message, cause); }
    }
}
