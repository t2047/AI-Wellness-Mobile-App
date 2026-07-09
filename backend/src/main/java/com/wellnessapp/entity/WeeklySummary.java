/**
 * @author Liu Zhuocheng
 */
package com.wellnessapp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Weekly health summary generated for a user's own wellness records.
 *
 * @author Liu Zhuocheng
 */
@Entity
@Table(name = "weekly_summaries")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklySummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "week_start_date", nullable = false)
    private LocalDate weekStartDate;

    @Column(name = "week_end_date", nullable = false)
    private LocalDate weekEndDate;

    @Column(name = "average_sleep_hours")
    private Double averageSleepHours;

    @Column(name = "total_activity_minutes")
    private Integer totalActivityMinutes;

    @Column(name = "active_days")
    private Integer activeDays;

    @Column(name = "record_count")
    private Integer recordCount;

    @Column(name = "summary_text", columnDefinition = "TEXT")
    private String summaryText;

    @Column(name = "recommendation_text", columnDefinition = "TEXT")
    private String recommendationText;

    @Column(name = "generated_at", updatable = false)
    private LocalDateTime generatedAt;

    @PrePersist
    protected void onCreate() {
        generatedAt = LocalDateTime.now();
    }
}
