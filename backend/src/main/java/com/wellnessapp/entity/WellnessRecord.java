/**
 * @author Jia Qianrui
 */
package com.wellnessapp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Wellness record entity storing user health data.
 *
 * @author WellnessApp Team
 */
@Entity
@Table(name = "wellness_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WellnessRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "sleep_hours")
    private Double sleepHours;

    @Column(name = "activity_name", length = 100)
    private String activityName;

    @Column(name = "activity_duration_minutes")
    private Integer activityDurationMinutes;

    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
