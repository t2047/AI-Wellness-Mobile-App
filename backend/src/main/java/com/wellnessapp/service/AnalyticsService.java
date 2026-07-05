package com.wellnessapp.service;

import com.wellnessapp.dto.AnalyticsDTOs.*;
import com.wellnessapp.entity.User;
import com.wellnessapp.entity.WellnessRecord;
import com.wellnessapp.repository.ChatMessageRepository;
import com.wellnessapp.repository.RecommendationRepository;
import com.wellnessapp.repository.WellnessRecordRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds user-scoped analytics for dashboard views.
 *
 * @author Xuhan Zhang
 */
@Service
public class AnalyticsService {

    private static final int DEFAULT_DAYS = 30;
    private static final int MAX_DAYS = 365;

    private final WellnessRecordRepository wellnessRecordRepository;
    private final RecommendationRepository recommendationRepository;
    private final ChatMessageRepository chatMessageRepository;

    public AnalyticsService(
            WellnessRecordRepository wellnessRecordRepository,
            RecommendationRepository recommendationRepository,
            ChatMessageRepository chatMessageRepository) {
        this.wellnessRecordRepository = wellnessRecordRepository;
        this.recommendationRepository = recommendationRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    /**
     * Build a dashboard snapshot for the requested trailing date range.
     */
    public DashboardResponse getDashboard(User user, Integer requestedDays) {
        int days = normalizeDays(requestedDays);
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1L);

        List<WellnessRecord> records = wellnessRecordRepository
                .findByUserIdAndRecordDateBetweenOrderByRecordDateAsc(
                        user.getId(), startDate, endDate);

        DashboardSummary summary = buildSummary(user, records, startDate, endDate, days);
        List<DailyMetric> dailyMetrics = buildDailyMetrics(records, startDate, endDate);
        List<ActivityBreakdown> activityBreakdown = buildActivityBreakdown(records);

        return DashboardResponse.builder()
                .summary(summary)
                .dailyMetrics(dailyMetrics)
                .activityBreakdown(activityBreakdown)
                .build();
    }

    private DashboardSummary buildSummary(
            User user,
            List<WellnessRecord> records,
            LocalDate startDate,
            LocalDate endDate,
            int days) {
        List<Double> sleepValues = records.stream()
                .map(WellnessRecord::getSleepHours)
                .filter(value -> value != null)
                .toList();

        int totalActivityMinutes = records.stream()
                .map(WellnessRecord::getActivityDurationMinutes)
                .filter(value -> value != null)
                .mapToInt(Integer::intValue)
                .sum();
        long activityRecordCount = records.stream()
                .map(WellnessRecord::getActivityDurationMinutes)
                .filter(value -> value != null)
                .count();

        Set<LocalDate> recordedDates = records.stream()
                .map(WellnessRecord::getRecordDate)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        ActivityBreakdown topActivity = buildActivityBreakdown(records).stream()
                .max(Comparator.comparing(ActivityBreakdown::getTotalMinutes))
                .orElse(null);

        long chatCount = chatMessageRepository.countByUserIdAndCreatedAtBetween(
                user.getId(), startDate.atStartOfDay(), endDate.atTime(LocalTime.MAX));

        return DashboardSummary.builder()
                .startDate(startDate.toString())
                .endDate(endDate.toString())
                .days(days)
                .totalRecords(records.size())
                .recordedDays(recordedDates.size())
                .recordCompletionRate(round((recordedDates.size() * 100.0) / days, 1))
                .currentStreakDays(calculateCurrentStreak(user))
                .latestRecordDate(getLatestRecordDate(user))
                .averageSleepHours(averageOrNull(sleepValues))
                .minSleepHours(sleepValues.stream().min(Double::compareTo).map(value -> round(value, 1)).orElse(null))
                .maxSleepHours(sleepValues.stream().max(Double::compareTo).map(value -> round(value, 1)).orElse(null))
                .totalActivityMinutes(totalActivityMinutes)
                .averageActivityMinutes(activityRecordCount == 0
                        ? null
                        : round(totalActivityMinutes / (double) activityRecordCount, 1))
                .topActivityName(topActivity != null ? topActivity.getActivityName() : null)
                .unreadRecommendations(recommendationRepository.countUnreadByUserId(user.getId()))
                .totalRecommendations(recommendationRepository.countByUserId(user.getId()))
                .chatMessageCount(chatCount)
                .build();
    }

    private List<DailyMetric> buildDailyMetrics(
            List<WellnessRecord> records, LocalDate startDate, LocalDate endDate) {
        Map<LocalDate, List<WellnessRecord>> byDate = records.stream()
                .collect(Collectors.groupingBy(
                        WellnessRecord::getRecordDate,
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<DailyMetric> metrics = new ArrayList<>();
        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            List<WellnessRecord> recordsForDate = byDate.getOrDefault(cursor, List.of());
            List<Double> sleepValues = recordsForDate.stream()
                    .map(WellnessRecord::getSleepHours)
                    .filter(value -> value != null)
                    .toList();
            int totalActivityMinutes = recordsForDate.stream()
                    .map(WellnessRecord::getActivityDurationMinutes)
                    .filter(value -> value != null)
                    .mapToInt(Integer::intValue)
                    .sum();

            metrics.add(DailyMetric.builder()
                    .date(cursor.toString())
                    .averageSleepHours(averageOrNull(sleepValues))
                    .totalActivityMinutes(totalActivityMinutes)
                    .recordCount(recordsForDate.size())
                    .hasRecord(!recordsForDate.isEmpty())
                    .build());

            cursor = cursor.plusDays(1);
        }

        return metrics;
    }

    private List<ActivityBreakdown> buildActivityBreakdown(List<WellnessRecord> records) {
        Map<String, List<WellnessRecord>> byActivity = records.stream()
                .filter(record -> record.getActivityName() != null && !record.getActivityName().isBlank())
                .collect(Collectors.groupingBy(
                        record -> normalizeActivityName(record.getActivityName()),
                        Collectors.toList()));

        return byActivity.entrySet().stream()
                .map(entry -> {
                    int totalMinutes = entry.getValue().stream()
                            .map(WellnessRecord::getActivityDurationMinutes)
                            .filter(value -> value != null)
                            .mapToInt(Integer::intValue)
                            .sum();
                    return ActivityBreakdown.builder()
                            .activityName(entry.getKey())
                            .totalMinutes(totalMinutes)
                            .recordCount(entry.getValue().size())
                            .build();
                })
                .sorted(Comparator.comparing(ActivityBreakdown::getTotalMinutes).reversed()
                        .thenComparing(ActivityBreakdown::getActivityName))
                .toList();
    }

    private int calculateCurrentStreak(User user) {
        List<LocalDate> dates = wellnessRecordRepository.findByUserIdOrderByRecordDateAsc(user.getId())
                .stream()
                .map(WellnessRecord::getRecordDate)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();

        if (dates.isEmpty()) {
            return 0;
        }

        int streak = 1;
        LocalDate expectedPreviousDate = dates.get(0).minusDays(1);
        for (int i = 1; i < dates.size(); i++) {
            LocalDate date = dates.get(i);
            if (!date.equals(expectedPreviousDate)) {
                break;
            }
            streak++;
            expectedPreviousDate = expectedPreviousDate.minusDays(1);
        }

        return streak;
    }

    private String getLatestRecordDate(User user) {
        return wellnessRecordRepository.findByUserIdOrderByRecordDateDesc(user.getId())
                .stream()
                .map(WellnessRecord::getRecordDate)
                .findFirst()
                .map(LocalDate::toString)
                .orElse(null);
    }

    private int normalizeDays(Integer requestedDays) {
        if (requestedDays == null) {
            return DEFAULT_DAYS;
        }
        if (requestedDays < 1 || requestedDays > MAX_DAYS) {
            throw new IllegalArgumentException("days must be between 1 and " + MAX_DAYS);
        }
        return requestedDays;
    }

    private Double averageOrNull(List<Double> values) {
        if (values.isEmpty()) {
            return null;
        }
        double average = values.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
        return round(average, 1);
    }

    private double round(double value, int places) {
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }

    private String normalizeActivityName(String activityName) {
        String trimmed = activityName.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        return trimmed.substring(0, 1).toUpperCase(Locale.ROOT)
                + trimmed.substring(1).toLowerCase(Locale.ROOT);
    }
}
