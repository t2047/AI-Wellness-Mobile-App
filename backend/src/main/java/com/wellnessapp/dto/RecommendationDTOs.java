/**
 * @author Jia Qianrui
 */
package com.wellnessapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Recommendation DTOs for agentic AI output.
 *
 * @author WellnessApp Team
 */
public class RecommendationDTOs {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendationResponse {
        private Long id;
        private Long userId;
        private String recommendationText;
        private String analysisSummary;
        private String generatedAt;
        private boolean isRead;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendationTriggerRequest {
        private Long userId;  // Optional — defaults to current user
    }
}
