package com.wellnessapp.service;

import com.wellnessapp.dto.WeeklySummaryDTOs.WeeklySummaryResponse;
import com.wellnessapp.entity.User;
import com.wellnessapp.entity.WeeklySummary;
import com.wellnessapp.repository.WeeklySummaryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Business logic for manually generated weekly health summaries.
 *
 * @author WellnessApp Team
 */
@Service
public class WeeklySummaryService {

    private final WeeklySummaryRepository weeklySummaryRepository;
    private final WellnessInsightsService wellnessInsightsService;

    public WeeklySummaryService(
            WeeklySummaryRepository weeklySummaryRepository,
            WellnessInsightsService wellnessInsightsService) {
        this.weeklySummaryRepository = weeklySummaryRepository;
        this.wellnessInsightsService = wellnessInsightsService;
    }

    /**
     * Generate and save a summary for the current user's most recent 7 days.
     *
     * @author WellnessApp Team
     */
    public WeeklySummaryResponse generate(User user) {
        WellnessInsightsService.WellnessStats stats =
                wellnessInsightsService.summarizeRecentDays(user, 7);

        WeeklySummary summary = WeeklySummary.builder()
                .user(user)
                .weekStartDate(stats.getStartDate())
                .weekEndDate(stats.getEndDate())
                .averageSleepHours(stats.getAverageSleepHours())
                .totalActivityMinutes(stats.getTotalActivityMinutes())
                .activeDays(stats.getActiveDays())
                .recordCount(stats.getRecordCount())
                .summaryText(wellnessInsightsService.buildSummaryText(stats))
                .recommendationText(wellnessInsightsService.buildRecommendationText(stats))
                .build();

        return toResponse(weeklySummaryRepository.save(summary));
    }

    /**
     * Get saved summaries for the current user, newest first.
     *
     * @author WellnessApp Team
     */
    public List<WeeklySummaryResponse> getSummaries(User user) {
        return weeklySummaryRepository.findByUserIdOrderByGeneratedAtDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private WeeklySummaryResponse toResponse(WeeklySummary summary) {
        return WeeklySummaryResponse.builder()
                .id(summary.getId())
                .weekStartDate(summary.getWeekStartDate().toString())
                .weekEndDate(summary.getWeekEndDate().toString())
                .averageSleepHours(summary.getAverageSleepHours())
                .totalActivityMinutes(summary.getTotalActivityMinutes())
                .activeDays(summary.getActiveDays())
                .recordCount(summary.getRecordCount())
                .summaryText(summary.getSummaryText())
                .recommendationText(summary.getRecommendationText())
                .generatedAt(summary.getGeneratedAt() != null
                        ? summary.getGeneratedAt().toString() : null)
                .build();
    }
}
