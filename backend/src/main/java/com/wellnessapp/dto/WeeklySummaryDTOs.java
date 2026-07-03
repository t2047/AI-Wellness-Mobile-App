package com.wellnessapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Weekly summary DTOs.
 *
 * @author WellnessApp Team
 */
public class WeeklySummaryDTOs {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeeklySummaryResponse {
        private Long id;
        private String weekStartDate;
        private String weekEndDate;
        private Double averageSleepHours;
        private Integer totalActivityMinutes;
        private Integer activeDays;
        private Integer recordCount;
        private String summaryText;
        private String recommendationText;
        private String generatedAt;
    }
}
