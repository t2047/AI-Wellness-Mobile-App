/**
 * @author Zhang Xuhan
 * @author Liu Zhuocheng
 */
package com.wellnessapp.ui.analytics

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.wellnessapp.data.api.RetrofitClient
import com.wellnessapp.data.model.AnalyticsDailyMetric
import com.wellnessapp.data.model.AnalyticsResponse
import com.wellnessapp.data.model.AnalyticsSummary
import com.wellnessapp.databinding.ActivityAnalyticsBinding
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Analytics dashboard screen for wellness summary metrics.
 *
 * @author Xuhan Zhang
 */
class AnalyticsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAnalyticsBinding
    private lateinit var dailyAdapter: AnalyticsDailyAdapter
    private var selectedDays = DEFAULT_DAYS
    private var currentDailyMetrics: List<AnalyticsDailyMetric> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnalyticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        setupTrendList()
        setupCharts()
        setupRangeSelector()
        loadDashboard(selectedDays)
    }

    /**
     * Configures the simple daily trend list.
     *
     * @author Xuhan Zhang
     */
    private fun setupTrendList() {
        dailyAdapter = AnalyticsDailyAdapter()
        binding.recyclerDailyTrend.layoutManager = LinearLayoutManager(this)
        binding.recyclerDailyTrend.adapter = dailyAdapter
        binding.recyclerDailyTrend.isNestedScrollingEnabled = false
    }

    /**
     * Applies shared visual settings to both trend charts.
     *
     * @author Xuhan Zhang
     */
    private fun setupCharts() {
        configureLineChart(binding.chartSleepTrend, "Sleep hours by day")
        configureBarChart(binding.chartExerciseTrend, "Exercise minutes by day")
    }

    /**
     * Configures the 7/30/90 day dashboard selector.
     *
     * @author Xuhan Zhang
     */
    private fun setupRangeSelector() {
        binding.chipGroupRange.setOnCheckedStateChangeListener { _, checkedIds ->
            val days = when (checkedIds.firstOrNull()) {
                binding.chip7Days.id -> 7
                binding.chip90Days.id -> 90
                else -> DEFAULT_DAYS
            }
            if (days != selectedDays) {
                selectedDays = days
                loadDashboard(selectedDays)
            }
        }
        binding.chip30Days.isChecked = true
    }

    /**
     * Loads analytics data for the selected range.
     *
     * @author Xuhan Zhang
     */
    private fun loadDashboard(days: Int) {
        showLoading(true)
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getAnalyticsDashboard(days = days)
                if (response.isSuccessful && response.body()?.success == true) {
                    val dashboard = response.body()?.data
                    if (dashboard?.summary != null) {
                        renderDashboard(dashboard)
                    } else {
                        showToast("No analytics data available")
                    }
                } else {
                    val message = response.body()?.message ?: "Failed to load analytics"
                    showToast(message)
                }
            } catch (e: Exception) {
                showToast("Connection error: ${e.localizedMessage}")
            } finally {
                showLoading(false)
            }
        }
    }

    /**
     * Renders summary cards, insight text, and daily trends.
     *
     * @author Xuhan Zhang
     */
    private fun renderDashboard(dashboard: AnalyticsResponse) {
        val summary = dashboard.summary ?: return
        val wellnessScore = calculateWellnessScore(summary)
        val sleepStatus = getSleepStatus(summary.averageSleepHours)
        val exerciseStatus = getExerciseStatus(summary.totalActivityMinutes, summary.days)

        binding.scoreView.setScore(wellnessScore)
        binding.tvScoreStatus.text = getScoreStatus(wellnessScore)
        binding.tvAverageSleep.text = formatHours(summary.averageSleepHours)
        binding.tvTotalExercise.text = "${summary.totalActivityMinutes} min"
        binding.tvActiveDays.text = "${summary.recordedDays} days"
        binding.tvCompletionRate.text = "${summary.recordCompletionRate.formatOneDecimal()}%"
        binding.tvCurrentStreak.text = "${summary.currentStreakDays} days"
//        updateStatusCards(summary, sleepStatus, exerciseStatus)
        binding.tvHealthInsight.text = buildHealthInsight(summary, wellnessScore)
        binding.tvInsightTagline.text = buildInsightTagline(wellnessScore)
        binding.tvSleepTrendAverage.text = "Avg: ${formatHours(summary.averageSleepHours)}"
        currentDailyMetrics = dashboard.dailyMetrics
        renderTrendCharts(currentDailyMetrics)
        renderDailyDetails(dashboard)
    }

    /**
     * Builds the main health insight sentence from dashboard metrics.
     *
     * @author Xuhan Zhang
     */
    private fun buildHealthInsight(summary: AnalyticsSummary, wellnessScore: Int): String {
        val sleepHours = summary.averageSleepHours
        return when {
            wellnessScore >= 80 ->
                "Your overall wellness pattern is strong. Keep your current sleep and activity routine consistent."
            (sleepHours ?: Double.MAX_VALUE) < 7.0 ->
                "Your sleep is slightly below the recommended range. Try to maintain 7-9 hours of sleep."
            summary.totalActivityMinutes == 0 ->
                "No exercise data was recorded in this range. Try logging light daily activity to build a clearer pattern."
            summary.totalActivityMinutes > 0 && sleepHours != null && sleepHours in 7.0..9.0 ->
                "Your sleep is in a healthy range and activity is being recorded. Keep tracking to maintain the pattern."
            else ->
                "Keep tracking your wellness data to improve your long-term health pattern."
        }
    }

    /**
     * Builds the short score status shown below the score ring.
     *
     * @author Xuhan Zhang
     */
    private fun getScoreStatus(wellnessScore: Int): String {
        return when {
            wellnessScore >= 80 -> "Great Job!"
            wellnessScore >= 60 -> "Keep Going"
            wellnessScore >= 40 -> "Needs Attention"
            else -> "Improve Habits"
        }
    }

    /**
     * Builds the compact tagline shown at the bottom of the insight card.
     *
     * @author Xuhan Zhang
     */
    private fun buildInsightTagline(wellnessScore: Int): String {
        return when {
            wellnessScore >= 80 -> "🌿 Consistency is the key to long-term health."
            wellnessScore >= 60 -> "🌿 Small daily habits create long-term progress."
            else -> "🌿 Keep tracking to improve your wellness pattern."
        }
    }

    /**
     * Shows the latest daily sleep and exercise details, or an empty state.
     *
     * @author Xuhan Zhang
     */
    private fun renderDailyDetails(dashboard: AnalyticsResponse) {
        val metrics = dashboard.dailyMetrics.takeLast(DAILY_DETAILS_LIMIT)
        dailyAdapter.submitList(metrics)
        binding.tvNoTrend.visibility = if (metrics.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerDailyTrend.visibility = if (metrics.isEmpty()) View.GONE else View.VISIBLE
    }

    /**
     * Renders sleep and exercise charts from daily metrics.
     *
     * @author Xuhan Zhang
     */
    private fun renderTrendCharts(metrics: List<AnalyticsDailyMetric>) {
        val points = if (selectedDays <= 7) {
            metrics.map {
                ChartPoint(
                    label = formatChartDate(it.date),
                    averageSleepHours = it.averageSleepHours,
                    totalActivityMinutes = it.totalActivityMinutes
                )
            }
        } else {
            buildWeeklyChartPoints(metrics)
        }
        val sleepEntries = points.mapIndexedNotNull { index, point ->
            point.averageSleepHours?.let { Entry(index.toFloat(), it.toFloat()) }
        }
        val exerciseEntries = points.mapIndexed { index, point ->
            BarEntry(index.toFloat(), point.totalActivityMinutes.toFloat())
        }
        val labels = points.map { it.label }

        renderLineChart(
            chart = binding.chartSleepTrend,
            emptyView = binding.tvNoSleepChart,
            entries = sleepEntries,
            labels = labels,
            label = "Sleep hours",
            lineColor = getColor(com.wellnessapp.R.color.sleepPurple),
            fillColor = getColor(com.wellnessapp.R.color.sleepPurpleLight),
            valueSuffix = " h"
        )
        renderBarChart(
            chart = binding.chartExerciseTrend,
            emptyView = binding.tvNoExerciseChart,
            entries = exerciseEntries,
            labels = labels,
            label = "Exercise minutes",
            lineColor = getColor(com.wellnessapp.R.color.accent),
            valueSuffix = " min"
        )
    }

    /**
     * Configures the sleep line chart with clear axes and legend.
     *
     * @author Xuhan Zhang
     */
    private fun configureLineChart(chart: LineChart, descriptionText: String) {
        chart.description.text = descriptionText
        chart.description.textColor = getColor(com.wellnessapp.R.color.textSecondary)
        chart.axisRight.isEnabled = false
        chart.axisLeft.textColor = getColor(com.wellnessapp.R.color.textSecondary)
        chart.axisLeft.axisMinimum = 0f
        chart.axisLeft.axisMaximum = 12f
        chart.axisLeft.granularity = 1f
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.granularity = 1f
        chart.xAxis.textColor = getColor(com.wellnessapp.R.color.textSecondary)
        chart.xAxis.setDrawGridLines(false)
        chart.legend.form = Legend.LegendForm.LINE
        chart.legend.textColor = getColor(com.wellnessapp.R.color.textPrimary)
        chart.setTouchEnabled(true)
        chart.setPinchZoom(false)
        chart.setScaleEnabled(false)
        chart.setNoDataText("No trend data available")
        chart.setNoDataTextColor(getColor(com.wellnessapp.R.color.textSecondary))
    }

    /**
     * Configures the exercise bar chart with clear axes and legend.
     *
     * @author Xuhan Zhang
     */
    private fun configureBarChart(chart: BarChart, descriptionText: String) {
        chart.description.text = descriptionText
        chart.description.textColor = getColor(com.wellnessapp.R.color.textSecondary)
        chart.axisRight.isEnabled = false
        chart.axisLeft.textColor = getColor(com.wellnessapp.R.color.textSecondary)
        chart.axisLeft.axisMinimum = 0f
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.granularity = 1f
        chart.xAxis.textColor = getColor(com.wellnessapp.R.color.textSecondary)
        chart.xAxis.setDrawGridLines(false)
        chart.legend.form = Legend.LegendForm.SQUARE
        chart.legend.textColor = getColor(com.wellnessapp.R.color.textPrimary)
        chart.setTouchEnabled(true)
        chart.setPinchZoom(false)
        chart.setScaleEnabled(false)
        chart.setNoDataText("No trend data available")
        chart.setNoDataTextColor(getColor(com.wellnessapp.R.color.textSecondary))
    }

    /**
     * Draws one line chart or shows its empty state.
     *
     * @author Xuhan Zhang
     */
    private fun renderLineChart(
        chart: LineChart,
        emptyView: View,
        entries: List<Entry>,
        labels: List<String>,
        label: String,
        lineColor: Int,
        fillColor: Int,
        valueSuffix: String
    ) {
        if (entries.isEmpty()) {
            chart.clear()
            chart.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
            return
        }

        val dataSet = LineDataSet(entries, label).apply {
            color = lineColor
            setCircleColor(lineColor)
            lineWidth = 2.5f
            circleRadius = 3.5f
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            this.fillColor = fillColor
            fillAlpha = 120
            setDrawValues(entries.size <= VALUE_LABEL_LIMIT)
            valueTextColor = lineColor
            valueTextSize = 9f
            valueFormatter = object : ValueFormatter() {
                override fun getPointLabel(entry: Entry): String {
                    return "${entry.y.formatChartValue()}$valueSuffix"
                }
            }
        }

        chart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val index = value.toInt()
                return labels.getOrNull(index) ?: ""
            }
        }
        chart.xAxis.labelCount = getChartLabelCount(labels.size)
        chart.data = LineData(dataSet)
        chart.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
        chart.invalidate()
    }

    /**
     * Draws the exercise bar chart or shows its empty state.
     *
     * @author Xuhan Zhang
     */
    private fun renderBarChart(
        chart: BarChart,
        emptyView: View,
        entries: List<BarEntry>,
        labels: List<String>,
        label: String,
        lineColor: Int,
        valueSuffix: String
    ) {
        if (entries.isEmpty()) {
            chart.clear()
            chart.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
            return
        }

        val dataSet = BarDataSet(entries, label).apply {
            color = lineColor
            setDrawValues(entries.count { it.y > 0f } <= VALUE_LABEL_LIMIT)
            valueTextColor = getColor(com.wellnessapp.R.color.textSecondary)
            valueTextSize = 9f
            valueFormatter = object : ValueFormatter() {
                override fun getBarLabel(barEntry: BarEntry): String {
                    if (barEntry.y <= 0f) return ""
                    return "${barEntry.y.formatChartValue()}$valueSuffix"
                }
            }
        }

        chart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val index = value.toInt()
                return labels.getOrNull(index) ?: ""
            }
        }
        chart.xAxis.labelCount = getChartLabelCount(labels.size)
        chart.data = BarData(dataSet).apply {
            barWidth = 0.55f
        }
        chart.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
        chart.invalidate()
    }

    /**
     * Converts backend ISO dates into compact chart labels.
     *
     * @author Xuhan Zhang
     */
    private fun formatChartDate(date: String): String {
        return if (date.length >= 10) date.substring(5, 10) else date
    }

    private fun buildWeeklyChartPoints(metrics: List<AnalyticsDailyMetric>): List<ChartPoint> {
        return metrics
            .mapNotNull { metric ->
                parseDate(metric.date)?.let { date -> date to metric }
            }
            .groupBy { (date, _) -> naturalWeekKey(date) }
            .toSortedMap()
            .map { (_, datedMetrics) ->
                val weekMetrics = datedMetrics.map { it.second }
                val firstDate = datedMetrics.first().first
                val lastDate = datedMetrics.last().first
                val sleepValues = weekMetrics.mapNotNull { it.averageSleepHours }
                ChartPoint(
                    label = "${firstDate.format(CHART_DATE_FORMATTER)}-${lastDate.format(CHART_DATE_FORMATTER)}",
                    averageSleepHours = sleepValues.takeIf { it.isNotEmpty() }?.average(),
                    totalActivityMinutes = weekMetrics.sumOf { it.totalActivityMinutes }
                )
            }
    }

    private fun parseDate(date: String): LocalDate? {
        return runCatching { LocalDate.parse(date.take(10)) }.getOrNull()
    }

    private fun naturalWeekKey(date: LocalDate): String {
        val weekFields = WeekFields.ISO
        val weekBasedYear = date.get(weekFields.weekBasedYear())
        val weekOfYear = date.get(weekFields.weekOfWeekBasedYear())
        return "%04d-%02d".format(Locale.US, weekBasedYear, weekOfYear)
    }

    private fun getChartLabelCount(labelSize: Int): Int {
        val maxLabels = if (selectedDays <= 7) MAX_SHORT_RANGE_LABELS else MAX_LONG_RANGE_LABELS
        return labelSize.coerceAtMost(maxLabels).coerceAtLeast(1)
    }

    private fun calculateWellnessScore(summary: AnalyticsSummary): Int {
        val sleepScore = when (val sleep = summary.averageSleepHours) {
            null -> 0.0
            in 7.0..9.0 -> 100.0
            in 6.0..<7.0 -> 75.0
            in 5.0..<6.0 -> 50.0
            in 9.0..10.0 -> 75.0
            else -> 35.0
        }

        val weeklyExerciseMinutes = if (summary.days > 0) {
            summary.totalActivityMinutes / summary.days.toDouble() * 7.0
        } else {
            0.0
        }
        val exerciseScore = ((weeklyExerciseMinutes / 150.0) * 100.0).coerceIn(0.0, 100.0)
        val completionScore = summary.recordCompletionRate.coerceIn(0.0, 100.0)
        val streakScore = ((summary.currentStreakDays / 7.0) * 100.0).coerceIn(0.0, 100.0)

        return (sleepScore * 0.35 +
            exerciseScore * 0.25 +
            completionScore * 0.30 +
            streakScore * 0.10).roundToInt().coerceIn(0, 100)
    }

    private fun getSleepStatus(averageSleepHours: Double?): String {
        return when (averageSleepHours) {
            null -> "No sleep data yet"
            in 7.0..9.0 -> "Healthy sleep range"
            in 6.0..<7.0 -> "Slightly below target"
            in 9.0..10.0 -> "Slightly above target"
            else -> "Needs attention"
        }
    }

    private fun getExerciseStatus(totalMinutes: Int, days: Int): String {
        val weeklyMinutes = if (days > 0) totalMinutes / days.toDouble() * 7.0 else 0.0
        return when {
            weeklyMinutes >= 150.0 -> "On track"
            weeklyMinutes >= 75.0 -> "Building momentum"
            totalMinutes > 0 -> "Needs more activity"
            else -> "No exercise data yet"
        }
    }

    private fun formatHours(value: Double?): String {
        return value?.let { "${it.formatOneDecimal()} h" } ?: "--"
    }

    private fun Double.formatOneDecimal(): String {
        return String.format("%.1f", this)
    }

    private fun Float.formatChartValue(): String {
        return if (this % 1f == 0f) {
            this.toInt().toString()
        } else {
            String.format("%.1f", this)
        }
    }

    private fun showLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.contentScroll.visibility = if (loading) View.GONE else View.VISIBLE
        binding.chip7Days.isEnabled = !loading
        binding.chip30Days.isEnabled = !loading
        binding.chip90Days.isEnabled = !loading
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private data class ChartPoint(
        val label: String,
        val averageSleepHours: Double?,
        val totalActivityMinutes: Int
    )

    companion object {
        private const val DEFAULT_DAYS = 30
        private const val DAILY_DETAILS_LIMIT = 7
        private const val MAX_SHORT_RANGE_LABELS = 7
        private const val MAX_LONG_RANGE_LABELS = 5
        private const val VALUE_LABEL_LIMIT = 10
        private val CHART_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd")
    }
}
