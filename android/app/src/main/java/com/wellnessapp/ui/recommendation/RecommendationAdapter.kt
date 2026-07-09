/**
 * @author Jia Qianrui
 */
package com.wellnessapp.ui.recommendation

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.wellnessapp.data.model.Recommendation
import com.wellnessapp.databinding.ItemRecommendationBinding

/**
 * RecyclerView adapter for displaying AI-generated recommendations.
 *
 * @author WellnessApp Team
 */
class RecommendationAdapter :
    ListAdapter<Recommendation, RecommendationAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecommendationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemRecommendationBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(rec: Recommendation) {
            binding.tvGeneratedDate.text = rec.generatedAt ?: "Just now"
            binding.tvRecommendation.text = rec.recommendationText

            // Show analysis summary if available
            if (!rec.analysisSummary.isNullOrBlank()) {
                binding.tvAnalysis.text = rec.analysisSummary
                binding.tvAnalysis.visibility = View.VISIBLE
            } else {
                binding.tvAnalysis.visibility = View.GONE
            }

            // Show "NEW" badge for unread recommendations
            binding.tvNewBadge.visibility =
                if (!rec.isRead) View.VISIBLE else View.GONE
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Recommendation>() {
        override fun areItemsTheSame(old: Recommendation, new: Recommendation): Boolean {
            return old.id == new.id
        }

        override fun areContentsTheSame(old: Recommendation, new: Recommendation): Boolean {
            return old == new
        }
    }
}
