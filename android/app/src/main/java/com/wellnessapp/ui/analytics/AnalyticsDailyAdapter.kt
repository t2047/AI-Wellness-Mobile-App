/**
 * @author Zhang Xuhan
 */
package com.wellnessapp.ui.analytics

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.wellnessapp.data.model.AnalyticsDailyMetric
import com.wellnessapp.databinding.ItemAnalyticsDailyBinding

/**
 * RecyclerView adapter for daily analytics detail rows.
 *
 * @author Xuhan Zhang
 */
class AnalyticsDailyAdapter :
    RecyclerView.Adapter<AnalyticsDailyAdapter.AnalyticsDailyViewHolder>() {

    private val items = mutableListOf<AnalyticsDailyMetric>()
    private var maxExerciseMinutes = 1

    /**
     * Replaces the daily detail rows shown by the dashboard.
     *
     * @author Xuhan Zhang
     */
    fun submitList(metrics: List<AnalyticsDailyMetric>) {
        items.clear()
        items.addAll(metrics)
        maxExerciseMinutes = metrics.maxOfOrNull { it.totalActivityMinutes }?.coerceAtLeast(1) ?: 1
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AnalyticsDailyViewHolder {
        val binding = ItemAnalyticsDailyBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AnalyticsDailyViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AnalyticsDailyViewHolder, position: Int) {
        holder.bind(items[position], maxExerciseMinutes)
    }

    override fun getItemCount(): Int = items.size

    /**
     * ViewHolder for one daily sleep/exercise trend row.
     *
     * @author Xuhan Zhang
     */
    class AnalyticsDailyViewHolder(
        private val binding: ItemAnalyticsDailyBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(metric: AnalyticsDailyMetric, maxExerciseMinutes: Int) {
            val sleepHours = metric.averageSleepHours
            val sleepProgress = ((sleepHours ?: 0.0) / 10.0 * 100).toInt().coerceIn(0, 100)
            val exerciseProgress = if (maxExerciseMinutes > 0) {
                (metric.totalActivityMinutes * 100 / maxExerciseMinutes).coerceIn(0, 100)
            } else {
                0
            }

            binding.tvTrendDate.text = metric.date
            binding.tvTrendSleep.text = sleepHours?.let { String.format("%.1f h", it) } ?: "--"
            binding.tvTrendExercise.text = "${metric.totalActivityMinutes} min"
            binding.progressSleep.progress = sleepProgress
            binding.progressExercise.progress = exerciseProgress
        }
    }
}
