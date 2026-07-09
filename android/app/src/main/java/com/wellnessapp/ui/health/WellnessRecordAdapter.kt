/**
 * @author Jia Qianrui
 * @author Cai Hanbo
 */
package com.wellnessapp.ui.health

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.wellnessapp.data.model.WellnessRecord
import com.wellnessapp.databinding.ItemWellnessRecordBinding

/**
 * RecyclerView adapter for displaying wellness records.
 *
 * @author WellnessApp Team
 */
class WellnessRecordAdapter(
    private val onEditClick: (WellnessRecord) -> Unit,
    private val onDeleteClick: (WellnessRecord) -> Unit
) : ListAdapter<WellnessRecord, WellnessRecordAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWellnessRecordBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemWellnessRecordBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(record: WellnessRecord) {
            binding.tvDate.text = record.recordDate

            val sleepText = record.sleepHours?.let {
                "Sleep: ${it}h"
            } ?: "Sleep: Not logged"
            binding.tvSleep.text = sleepText

            val activityText = record.activityName?.let { name ->
                val duration = record.activityDurationMinutes?.let { " (${it}min)" } ?: ""
                "$name$duration"
            } ?: "Activity: Not logged"
            binding.tvActivity.text = activityText

            binding.tvNotes.text = record.notes ?: ""
            binding.tvNotes.visibility =
                if (record.notes.isNullOrBlank()) android.view.View.GONE
                else android.view.View.VISIBLE

            binding.btnEdit.setOnClickListener { onEditClick(record) }
            binding.btnDelete.setOnClickListener { onDeleteClick(record) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<WellnessRecord>() {
        override fun areItemsTheSame(oldItem: WellnessRecord, newItem: WellnessRecord): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: WellnessRecord, newItem: WellnessRecord): Boolean {
            return oldItem == newItem
        }
    }
}
