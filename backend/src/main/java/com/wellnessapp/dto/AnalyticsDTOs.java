package com.wellnessapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTOs for analytics dashboard responses.
 *
 * @author WellnessApp Team
 */
public class AnalyticsDTOs {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardResponse {
        private DashboardSummary summary;
        private List<DailyMetric> dailyMetrics;
        private List<ActivityBreakdown> activityBreakdown;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardSummary {
        private String startDate;
        private String endDate;
        private int days;
        private long totalRecords;
        private long recordedDays;
        private double recordCompletionRate;
        private int currentStreakDays;
        private String latestRecordDate;
        private Double averageSleepHours;
        private Double minSleepHours;
        private Double maxSleepHours;
        private Integer totalActivityMinutes;
        private Double averageActivityMinutes;
        private String topActivityName;
        private long unreadRecommendations;
        private long totalRecommendations;
        private long chatMessageCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyMetric {
        private String date;
        private Double averageSleepHours;
        private Integer totalActivityMinutes;
        private long recordCount;
        private boolean hasRecord;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityBreakdown {
        private String activityName;
        private Integer totalMinutes;
        private long recordCount;
    }
}
