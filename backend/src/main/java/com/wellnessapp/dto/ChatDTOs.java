/**
 * @author Jia Qianrui
 * @author Tao Yuchen
 */
package com.wellnessapp.dto;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Chat-related DTOs.
 *
 * @author WellnessApp Team
 */
public class ChatDTOs {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatRequest {
        @NotBlank(message = "Message cannot be empty")
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatResponse {
        private String reply;
        private String timestamp;

        /** Source citations from RAG (populated when Tier-1 responds). */
        @Builder.Default
        private List<SourceInfo> sources = List.of();

        /** Tool call trace (populated when Tier-1 responds). */
        @Builder.Default
        private List<Map<String, Object>> toolCalls = List.of();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceInfo {
        private int rank;
        private String title;
        private String section;
        private String sourceUrl;
        private double score;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatHistoryResponse {
        private Long id;
        private String userMessage;
        private String botResponse;
        private String createdAt;
    }
}
