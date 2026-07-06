package com.wellnessapp.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Client service for calling the configured direct AI chat provider.
 *
 * <p>Acts as the second-tier fallback (the first tier is the Python agent's
 * RAG-powered /chat endpoint). Supports OpenAI GPT and DeepSeek through
 * Chat Completions-compatible APIs. Uses {@link RestClient} (Spring 6.x).</p>
 *
 * @author WellnessApp Team
 */
@Service
public class AIClientService {

    private static final Logger log = LoggerFactory.getLogger(AIClientService.class);

    private final RestClient restClient;
    private final String provider;
    private final String apiKey;
    private final String chatUrl;
    private final String model;

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

    public AIClientService(
            @Value("${ai.provider:openai}") String provider,
            @Value("${openai.api-key:}") String openAiApiKey,
            @Value("${openai.chat-url:https://api.openai.com/v1/chat/completions}") String openAiChatUrl,
            @Value("${openai.model:gpt-4o-mini}") String openAiModel,
            @Value("${deepseek.api-key:}") String deepSeekApiKey,
            @Value("${deepseek.chat-url:https://api.deepseek.com/chat/completions}") String deepSeekChatUrl,
            @Value("${deepseek.model:deepseek-chat}") String deepSeekModel) {
        this.provider = normalizeProvider(provider);
        this.apiKey = "deepseek".equals(this.provider) ? deepSeekApiKey : openAiApiKey;
        this.chatUrl = "deepseek".equals(this.provider) ? deepSeekChatUrl : openAiChatUrl;
        this.model = "deepseek".equals(this.provider) ? deepSeekModel : openAiModel;
        this.restClient = RestClient.builder()
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
        return chatWithConfig(apiKey, chatUrl, model, provider, messages, maxTokens);
    }

    /**
     * Chat using user-specific model configuration.
     * Allows users to override the global AI provider with their own API key and endpoint.
     */
    public String chatWithUserConfig(
            String userApiKey, String userBaseUrl, String userModel,
            List<Map<String, String>> messages, int maxTokens) {
        if (userApiKey == null || userApiKey.isBlank()) {
            throw new AiServiceException("User API key is not configured");
        }
        if (userBaseUrl == null || userBaseUrl.isBlank()) {
            throw new AiServiceException("User base URL is not configured");
        }
        String effectiveModel = (userModel != null && !userModel.isBlank()) ? userModel : "gpt-4o-mini";
        return chatWithConfig(userApiKey, userBaseUrl, effectiveModel, "custom", messages, maxTokens);
    }

    /**
     * Core chat implementation — works with any OpenAI-compatible API.
     */
    private String chatWithConfig(
            String apiKey, String chatUrl, String model, String providerLabel,
            List<Map<String, String>> messages, int maxTokens) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiServiceException(providerLabel + " API key is not configured");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("max_tokens", maxTokens);
        body.put("stream", false);
        body.put("temperature", 0.7);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(chatUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            return extractReply(response, providerLabel);
        } catch (Exception e) {
            log.error("{} API call failed: {}", providerLabel, e.getMessage());
            throw new AiServiceException(providerLabel + " API unavailable: " + e.getMessage(), e);
        }
    }

    // ── Internal helpers ───────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private String extractReply(Map<String, Object> response, String providerLabel) {
        if (response == null) {
            throw new AiServiceException(providerLabel + " returned null response");
        }

        List<Map<String, Object>> choices =
                (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new AiServiceException(providerLabel + " response did not contain choices");
        }

        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        if (message == null || message.get("content") == null) {
            throw new AiServiceException(providerLabel + " response did not contain message content");
        }

        return message.get("content").toString().trim();
    }

    private String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return "openai";
        }
        String normalized = provider.trim().toLowerCase();
        if (!"openai".equals(normalized) && !"deepseek".equals(normalized)) {
            throw new AiServiceException("Unsupported AI provider: " + provider);
        }
        return normalized;
    }

    // ── Exception type ─────────────────────────────────────────────

    /** Exception thrown when AI service is unavailable. */
    public static class AiServiceException extends RuntimeException {
        public AiServiceException(String message) { super(message); }
        public AiServiceException(String message, Throwable cause) { super(message, cause); }
    }
}
