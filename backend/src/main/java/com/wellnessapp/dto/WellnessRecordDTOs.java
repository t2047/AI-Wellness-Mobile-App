package com.wellnessapp.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Wellness Record DTOs for creating, updating, and returning records.
 *
 * @author WellnessApp Team
 */
public class WellnessRecordDTOs {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WellnessRecordRequest {
        @DecimalMin(value = "0.0", message = "Sleep hours must be >= 0")
        @DecimalMax(value = "24.0", message = "Sleep hours must be <= 24")
        private Double sleepHours;

        @Size(max = 100, message = "Activity name must be at most 100 characters")
        private String activityName;

        @Min(value = 0, message = "Activity duration must be >= 0")
        private Integer activityDurationMinutes;

        @NotBlank(message = "Record date is required")
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}",
                message = "Record date must use YYYY-MM-DD format")
        private String recordDate;  // Format: YYYY-MM-DD

        @Size(max = 500, message = "Notes must be at most 500 characters")
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WellnessRecordResponse {
        private Long id;
        private Long userId;
        private Double sleepHours;
        private String activityName;
        private Integer activityDurationMinutes;
        private String recordDate;
        private String notes;
        private String createdAt;
        private String updatedAt;
    }
}
