package com.wellnessapp.ui.chat

import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.wellnessapp.R
import com.wellnessapp.databinding.ItemChatMessageBinding
import io.noties.markwon.Markwon

/**
 * RecyclerView adapter for chat messages.
 *
 * @author WellnessApp Team
 */
class ChatMessageAdapter :
    RecyclerView.Adapter<ChatMessageAdapter.ViewHolder>() {

    private val messages = mutableListOf<ChatMessageItem>()
    private var markwon: Markwon? = null

    fun addMessage(item: ChatMessageItem) {
        messages.add(item)
        notifyItemInserted(messages.size - 1)
    }

    fun clearMessages() {
        val size = messages.size
        messages.clear()
        notifyItemRangeRemoved(0, size)
    }

    override fun getItemViewType(position: Int): Int {
        return when (messages[position]) {
            is ChatMessageItem.User -> VIEW_TYPE_USER
            is ChatMessageItem.Bot -> VIEW_TYPE_BOT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemChatMessageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount(): Int = messages.size

    inner class ViewHolder(
        private val binding: ItemChatMessageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ChatMessageItem) {
            val context = binding.root.context
            if (markwon == null) {
                markwon = Markwon.create(context)
            }
            val markwonInstance = markwon!!

            when (item) {
                is ChatMessageItem.User -> {
                    binding.tvSender.text = "You"
                    binding.tvSender.setTextColor(
                        ContextCompat.getColor(context, R.color.primary))
                    binding.tvMessage.text = item.message
                    binding.tvTime.visibility = android.view.View.GONE

                    // Align user messages to the right
                    (binding.root as android.widget.LinearLayout).gravity = Gravity.END
                    binding.messageContainer.setBackgroundColor(
                        ContextCompat.getColor(context, R.color.primary))
                    binding.tvSender.setTextColor(
                        ContextCompat.getColor(context, R.color.onPrimary))
                    binding.tvMessage.setTextColor(
                        ContextCompat.getColor(context, R.color.onPrimary))
                    binding.tvTime.setTextColor(
                        ContextCompat.getColor(context, R.color.onPrimary))
                }
                is ChatMessageItem.Bot -> {
                    binding.tvSender.text = "WellBot"
                    binding.tvSender.setTextColor(
                        ContextCompat.getColor(context, R.color.accent))
                    markwonInstance.setMarkdown(binding.tvMessage, item.message)
                    binding.tvTime.visibility = android.view.View.GONE

                    // Align bot messages to the left
                    (binding.root as android.widget.LinearLayout).gravity = Gravity.START
                    binding.messageContainer.setBackgroundColor(
                        ContextCompat.getColor(context, R.color.surface))
                    binding.tvSender.setTextColor(
                        ContextCompat.getColor(context, R.color.accent))
                    binding.tvMessage.setTextColor(
                        ContextCompat.getColor(context, R.color.textPrimary))
                }
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_USER = 0
        private const val VIEW_TYPE_BOT = 1
    }
}
