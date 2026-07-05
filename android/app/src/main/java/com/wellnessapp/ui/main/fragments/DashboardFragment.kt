package com.wellnessapp.ui.main.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
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
import com.wellnessapp.R
import com.wellnessapp.data.api.RetrofitClient
import com.wellnessapp.data.model.AnalyticsDailyMetric
import com.wellnessapp.data.model.AnalyticsResponse
import com.wellnessapp.data.model.AnalyticsSummary
import com.wellnessapp.databinding.FragmentDashboardBinding
import com.wellnessapp.ui.analytics.AnalyticsDailyAdapter
import com.wellnessapp.util.TokenManager
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Full analytics dashboard tab content hosted inside MainActivity.
 *
 * @author Xuhan Zhang
 */
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private lateinit var dailyAdapter: AnalyticsDailyAdapter
    private var selectedDays = DEFAULT_DAYS

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindWelcomeHeader()
        setupTrendList()
        setupCharts()
        setupRangeSelector()
        loadDashboard(selectedDays)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Shows the dashboard welcome header for the logged-in user.
     *
     * @author Xuhan Zhang
     */
    private fun bindWelcomeHeader() {
        val username = TokenManager.getUsername()
        binding.tvDashboardSubtitle.text = if (username.isNullOrBlank()) {
            "Your wellness overview"
        } else {
            "Welcome back, $username - Your wellness overview"
        }
    }

    /**
     * Configures the daily details list.
     *
     * @author Xuhan Zhang
     */
    private fun setupTrendList() {
        dailyAdapter = AnalyticsDailyAdapter()
        binding.recyclerDailyTrend.layoutManager = LinearLayoutManager(requireContext())
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
        viewLifecycleOwner.lifecycleScope.launch {
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
                if (_binding != null) {
                    showLoading(false)
                }
            }
        }
    }

    /**
     * Renders score, insight, status, metrics, charts, and daily details.
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
        updateStatusCards(summary, sleepStatus, exerciseStatus)
        binding.tvHealthInsight.text = buildHealthInsight(summary, wellnessScore)
        binding.tvInsightTagline.text = buildInsightTagline(wellnessScore)
        binding.tvSleepTrendAverage.text = "Avg: ${formatHours(summary.averageSleepHours)}"
        renderTrendCharts(dashboard.dailyMetrics)
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
            wellnessScore >= 80 -> "Keep this consistent wellness rhythm."
            wellnessScore >= 60 -> "Small daily habits create long-term progress."
            else -> "Keep tracking to improve your wellness pattern."
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
        val sleepEntries = metrics.mapIndexedNotNull { index, metric ->
            metric.averageSleepHours?.let { Entry(index.toFloat(), it.toFloat()) }
        }
        val exerciseEntries = metrics.mapIndexed { index, metric ->
            BarEntry(index.toFloat(), metric.totalActivityMinutes.toFloat())
        }
        val labels = metrics.map { formatChartDate(it.date) }

        renderLineChart(
            chart = binding.chartSleepTrend,
            emptyView = binding.tvNoSleepChart,
            entries = sleepEntries,
            labels = labels,
            label = "Sleep hours",
            lineColor = color(R.color.sleepPurple),
            fillColor = color(R.color.sleepPurpleLight),
            valueSuffix = " h"
        )
        renderBarChart(
            chart = binding.chartExerciseTrend,
            emptyView = binding.tvNoExerciseChart,
            entries = exerciseEntries,
            labels = labels,
            label = "Exercise minutes",
            lineColor = color(R.color.accent),
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
        chart.description.textColor = color(R.color.textSecondary)
        chart.axisRight.isEnabled = false
        chart.axisLeft.textColor = color(R.color.textSecondary)
        chart.axisLeft.axisMinimum = 0f
        chart.axisLeft.axisMaximum = 12f
        chart.axisLeft.granularity = 1f
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.granularity = 1f
        chart.xAxis.textColor = color(R.color.textSecondary)
        chart.xAxis.setDrawGridLines(false)
        chart.legend.form = Legend.LegendForm.LINE
        chart.legend.textColor = color(R.color.textPrimary)
        chart.setTouchEnabled(true)
        chart.setPinchZoom(false)
        chart.setScaleEnabled(false)
        chart.setNoDataText("No trend data available")
        chart.setNoDataTextColor(color(R.color.textSecondary))
    }

    /**
     * Configures the exercise bar chart with clear axes and legend.
     *
     * @author Xuhan Zhang
     */
    private fun configureBarChart(chart: BarChart, descriptionText: String) {
        chart.description.text = descriptionText
        chart.description.textColor = color(R.color.textSecondary)
        chart.axisRight.isEnabled = false
        chart.axisLeft.textColor = color(R.color.textSecondary)
        chart.axisLeft.axisMinimum = 0f
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.granularity = 1f
        chart.xAxis.textColor = color(R.color.textSecondary)
        chart.xAxis.setDrawGridLines(false)
        chart.legend.form = Legend.LegendForm.SQUARE
        chart.legend.textColor = color(R.color.textPrimary)
        chart.setTouchEnabled(true)
        chart.setPinchZoom(false)
        chart.setScaleEnabled(false)
        chart.setNoDataText("No trend data available")
        chart.setNoDataTextColor(color(R.color.textSecondary))
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
            valueTextColor = color(R.color.textSecondary)
            valueTextSize = 9f
            valueFormatter = object : ValueFormatter() {
                override fun getBarLabel(barEntry: BarEntry): String {
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
     * Updates sleep and exercise status cards with badge colors.
     *
     * @author Xuhan Zhang
     */
    private fun updateStatusCards(
        summary: AnalyticsSummary,
        sleepStatus: String,
        exerciseStatus: String
    ) {
        val sleepStyle = getSleepStatusStyle(summary.averageSleepHours, sleepStatus)
        val exerciseStyle = getExerciseStatusStyle(summary.totalActivityMinutes, exerciseStatus)

        bindStatus(
            iconView = binding.tvSleepStatusIcon,
            textView = binding.tvSleepStatus,
            badgeView = binding.tvSleepStatusBadge,
            value = sleepStatus,
            style = sleepStyle
        )
        bindStatus(
            iconView = binding.tvExerciseStatusIcon,
            textView = binding.tvExerciseStatus,
            badgeView = binding.tvExerciseStatusBadge,
            value = exerciseStatus,
            style = exerciseStyle
        )
    }

    private fun bindStatus(
        iconView: TextView,
        textView: TextView,
        badgeView: TextView,
        value: String,
        style: StatusStyle
    ) {
        iconView.text = style.icon
        iconView.setTextColor(color(style.textColor))
        textView.text = value
        textView.setTextColor(color(style.textColor))
        badgeView.text = style.badge
        badgeView.setTextColor(color(style.textColor))
        badgeView.setBackgroundResource(style.background)
    }

    private fun getSleepStatusStyle(averageSleepHours: Double?, status: String): StatusStyle {
        val normalized = status.lowercase()
        return when {
            averageSleepHours != null && averageSleepHours > 9.0 ->
                StatusStyle("i", "Review", R.color.infoText, R.drawable.bg_status_info_pill)
            averageSleepHours != null && averageSleepHours < 7.0 ||
                normalized.contains("below") || normalized.contains("attention") ->
                StatusStyle("!", "Needs Sleep", R.color.warningText, R.drawable.bg_status_warning_pill)
            else -> StatusStyle("OK", "Good", R.color.primary, R.drawable.bg_analytics_pill)
        }
    }

    private fun getExerciseStatusStyle(totalMinutes: Int, status: String): StatusStyle {
        val normalized = status.lowercase()
        return when {
            normalized.contains("track") || normalized.contains("momentum") ->
                StatusStyle("OK", if (normalized.contains("track")) "On Track" else "Active", R.color.primary, R.drawable.bg_analytics_pill)
            totalMinutes <= 0 || normalized.contains("needs") || normalized.contains("no exercise") ->
                StatusStyle("!", "Needs Activity", R.color.warningText, R.drawable.bg_status_warning_pill)
            else -> StatusStyle("OK", "Active", R.color.primary, R.drawable.bg_analytics_pill)
        }
    }

    private fun formatChartDate(date: String): String {
        return if (date.length >= 10) date.substring(5, 10) else date
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
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun color(colorRes: Int): Int {
        return ContextCompat.getColor(requireContext(), colorRes)
    }

    private data class StatusStyle(
        val icon: String,
        val badge: String,
        val textColor: Int,
        val background: Int
    )

    companion object {
        private const val DEFAULT_DAYS = 30
        private const val DAILY_DETAILS_LIMIT = 7
        private const val MAX_SHORT_RANGE_LABELS = 7
        private const val MAX_LONG_RANGE_LABELS = 5
        private const val VALUE_LABEL_LIMIT = 10
    }
}
