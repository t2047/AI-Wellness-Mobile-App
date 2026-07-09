/**
 * @author Liu Zhuocheng
 */
package com.wellnessapp.service;

import com.wellnessapp.entity.User;
import com.wellnessapp.entity.WellnessRecord;
import com.wellnessapp.repository.WellnessRecordRepository;
import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds compact wellness statistics from a user's own records.
 *
 * @author Liu Zhuocheng
 */
@Service
public class WellnessInsightsService {

    private final WellnessRecordRepository recordRepository;

    public WellnessInsightsService(WellnessRecordRepository recordRepository) {
        this.recordRepository = recordRepository;
    }

    /**
     * Summarize a user's recent records without exposing raw entries to prompts.
     *
     * @author WellnessApp Team
     */
    public WellnessStats summarizeRecentDays(User user, int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(Math.max(days, 1) - 1L);
        List<WellnessRecord> records = recordRepository
                .findByUserIdAndRecordDateBetweenOrderByRecordDateAsc(
                        user.getId(), startDate, endDate);
        return summarizeRecords(records, startDate, endDate);
    }

    /**
     * Convert statistics into a short prompt context for the chatbot.
     *
     * @author WellnessApp Team
     */
    public String buildChatContext(User user, int days) {
        WellnessStats stats = summarizeRecentDays(user, days);
        if (stats.getRecordCount() == 0) {
            return "Personal wellness context: The current user has no wellness records "
                    + "in the last " + days + " days. If they ask about personal trends, "
                    + "clearly say they need to log sleep and activity records first.";
        }

        String avgSleep = stats.getAverageSleepHours() == null
                ? "not enough sleep data"
                : String.format("%.1f hours", stats.getAverageSleepHours());

        return "Personal wellness context for the current JWT user only, summarized from "
                + stats.getRecordCount() + " records between " + stats.getStartDate()
                + " and " + stats.getEndDate() + ": average sleep = " + avgSleep
                + ", total activity = " + stats.getTotalActivityMinutes() + " minutes"
                + ", active days = " + stats.getActiveDays()
                + ", recent trend = " + stats.getTrendSummary()
                + ". Use this summary for personal questions, do not claim access to raw records, "
                + "and recommend logging more data when coverage is low.";
    }

    /**
     * Create a student-friendly generated summary from recent stats.
     *
     * @author WellnessApp Team
     */
    public String buildSummaryText(WellnessStats stats) {
        if (stats.getRecordCount() == 0) {
            return "No wellness records were found for this 7-day period. "
                    + "Log daily sleep and activity first so the app can summarize your week.";
        }

        String sleepPart = stats.getAverageSleepHours() == null
                ? "Sleep data was not recorded."
                : String.format("Average sleep was %.1f hours.", stats.getAverageSleepHours());

        return "From " + stats.getStartDate() + " to " + stats.getEndDate()
                + ", you logged " + stats.getRecordCount() + " wellness records. "
                + sleepPart + " Total activity was " + stats.getTotalActivityMinutes()
                + " minutes across " + stats.getActiveDays() + " active days. "
                + "Trend: " + stats.getTrendSummary() + ".";
    }

    /**
     * Create a practical recommendation from recent stats.
     *
     * @author WellnessApp Team
     */
    public String buildRecommendationText(WellnessStats stats) {
        if (stats.getRecordCount() == 0) {
            return "Next week, start by recording sleep hours and any activity each day. "
                    + "After a few entries, the app can give more personalized guidance.";
        }

        if (stats.getAverageSleepHours() != null && stats.getAverageSleepHours() < 7.0) {
            return "Improve sleep first: aim for 7-9 hours by moving bedtime 20-30 minutes earlier "
                    + "and keeping a consistent wake-up time.";
        }

        if (stats.getTotalActivityMinutes() < 150) {
            return "Build activity consistency next week: target at least 150 total minutes, "
                    + "for example five 30-minute walks or workouts.";
        }

        if (stats.getActiveDays() < 3) {
            return "Keep your sleep routine steady and add one more active day next week "
                    + "to make exercise more consistent.";
        }

        return "Maintain your current routine next week and keep logging daily records. "
                + "A small stretch goal is adding 10-15 minutes to one activity session.";
    }

    private WellnessStats summarizeRecords(List<WellnessRecord> records, LocalDate startDate, LocalDate endDate) {
        int recordCount = records.size();
        double sleepTotal = 0.0;
        int sleepCount = 0;
        int totalActivity = 0;
        Set<LocalDate> activeDates = new HashSet<>();

        for (WellnessRecord record : records) {
            if (record.getSleepHours() != null) {
                sleepTotal += record.getSleepHours();
                sleepCount++;
            }
            Integer duration = record.getActivityDurationMinutes();
            if (duration != null && duration > 0) {
                totalActivity += duration;
                activeDates.add(record.getRecordDate());
            }
        }

        Double averageSleep = sleepCount == 0 ? null : Math.round((sleepTotal / sleepCount) * 10.0) / 10.0;

        return WellnessStats.builder()
                .startDate(startDate)
                .endDate(endDate)
                .averageSleepHours(averageSleep)
                .totalActivityMinutes(totalActivity)
                .activeDays(activeDates.size())
                .recordCount(recordCount)
                .trendSummary(buildTrendSummary(records))
                .build();
    }

    private String buildTrendSummary(List<WellnessRecord> records) {
        if (records.size() < 4) {
            return "not enough data for a reliable trend";
        }

        int mid = records.size() / 2;
        List<WellnessRecord> firstHalf = records.subList(0, mid);
        List<WellnessRecord> secondHalf = records.subList(mid, records.size());

        String sleepTrend = compareSleep(firstHalf, secondHalf);
        String activityTrend = compareActivity(firstHalf, secondHalf);
        return "sleep is " + sleepTrend + "; activity is " + activityTrend;
    }

    private String compareSleep(List<WellnessRecord> firstHalf, List<WellnessRecord> secondHalf) {
        Double first = averageSleep(firstHalf);
        Double second = averageSleep(secondHalf);
        if (first == null || second == null) {
            return "unclear";
        }
        double diff = second - first;
        if (diff > 0.5) {
            return "improving";
        }
        if (diff < -0.5) {
            return "declining";
        }
        return "stable";
    }

    private String compareActivity(List<WellnessRecord> firstHalf, List<WellnessRecord> secondHalf) {
        double first = averageActivityMinutes(firstHalf);
        double second = averageActivityMinutes(secondHalf);
        double diff = second - first;
        if (diff > 10.0) {
            return "increasing";
        }
        if (diff < -10.0) {
            return "decreasing";
        }
        return "stable";
    }

    private Double averageSleep(List<WellnessRecord> records) {
        double total = 0.0;
        int count = 0;
        for (WellnessRecord record : records) {
            if (record.getSleepHours() != null) {
                total += record.getSleepHours();
                count++;
            }
        }
        return count == 0 ? null : total / count;
    }

    private double averageActivityMinutes(List<WellnessRecord> records) {
        if (records.isEmpty()) {
            return 0.0;
        }
        int total = 0;
        for (WellnessRecord record : records) {
            total += record.getActivityDurationMinutes() == null
                    ? 0 : record.getActivityDurationMinutes();
        }
        return (double) total / records.size();
    }

    /**
     * Compact statistics for recent wellness records.
     *
     * @author WellnessApp Team
     */
    @Data
    @Builder
    public static class WellnessStats {
        private LocalDate startDate;
        private LocalDate endDate;
        private Double averageSleepHours;
        private Integer totalActivityMinutes;
        private Integer activeDays;
        private Integer recordCount;
        private String trendSummary;
    }
}
