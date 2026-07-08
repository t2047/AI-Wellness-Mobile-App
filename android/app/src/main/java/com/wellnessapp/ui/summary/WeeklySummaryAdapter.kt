package com.wellnessapp.ui.summary

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.wellnessapp.data.model.WeeklySummary
import com.wellnessapp.databinding.ItemWeeklySummaryBinding
import java.util.Locale

/**
 * RecyclerView adapter for weekly health summaries.
 *
 * @author Liu Zhuocheng
 */
class WeeklySummaryAdapter :
    ListAdapter<WeeklySummary, WeeklySummaryAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWeeklySummaryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemWeeklySummaryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(summary: WeeklySummary) {
            binding.tvWeekRange.text = "${summary.weekStartDate} to ${summary.weekEndDate}"
            binding.tvGeneratedAt.text = summary.generatedAt ?: "Generated just now"
            binding.tvAverageSleep.text = summary.averageSleepHours?.let {
                String.format(Locale.US, "%.1f h", it)
            } ?: "No sleep data"
            binding.tvActivityMinutes.text = "${summary.totalActivityMinutes} min"
            binding.tvActiveDays.text = "${summary.activeDays} days"
            binding.tvRecordCount.text = "${summary.recordCount} records"
            binding.tvSummary.text = summary.summaryText
            binding.tvRecommendation.text = summary.recommendationText
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<WeeklySummary>() {
        override fun areItemsTheSame(old: WeeklySummary, new: WeeklySummary): Boolean {
            return old.id == new.id
        }

        override fun areContentsTheSame(old: WeeklySummary, new: WeeklySummary): Boolean {
            return old == new
        }
    }
}
