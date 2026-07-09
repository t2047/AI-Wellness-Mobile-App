/**
 * @author Jia Qianrui
 */
package com.wellnessapp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Recommendation entity for AI-generated wellness suggestions.
 *
 * @author WellnessApp Team
 */
@Entity
@Table(name = "recommendations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Recommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "recommendation_text", nullable = false, columnDefinition = "TEXT")
    private String recommendationText;

    @Column(name = "analysis_summary", columnDefinition = "TEXT")
    private String analysisSummary;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    @PrePersist
    protected void onCreate() {
        generatedAt = LocalDateTime.now();
    }
}
