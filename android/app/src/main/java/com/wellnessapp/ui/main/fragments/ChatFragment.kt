package com.wellnessapp.ui.main.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.wellnessapp.data.api.RetrofitClient
import com.wellnessapp.data.model.ChatRequest
import com.wellnessapp.databinding.FragmentChatBinding
import com.wellnessapp.ui.chat.ChatMessageAdapter
import com.wellnessapp.ui.chat.ChatMessageItem
import kotlinx.coroutines.launch

/**
 * Chat tab content hosted inside MainActivity.
 *
 * @author Xuhan Zhang
 */
class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: ChatMessageAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupChatList()
        setupListeners()
        loadHistory()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Configures the chat message list.
     *
     * @author Xuhan Zhang
     */
    private fun setupChatList() {
        adapter = ChatMessageAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    /**
     * Wires send button and IME send action.
     *
     * @author Xuhan Zhang
     */
    private fun setupListeners() {
        binding.btnSend.setOnClickListener { sendMessage() }
        binding.btnNewChat.setOnClickListener { startNewConversation() }
        binding.etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }
    }

    private fun startNewConversation() {
        adapter.clearMessages()
        binding.tvEmpty.visibility = View.VISIBLE
        binding.etMessage.text?.clear()
    }

    /**
     * Sends one chat message and appends the bot reply.
     *
     * @author Xuhan Zhang
     */
    private fun sendMessage() {
        val message = binding.etMessage.text.toString().trim()
        if (message.isEmpty()) return

        adapter.addMessage(ChatMessageItem.User(message))
        binding.etMessage.text?.clear()
        binding.tvEmpty.visibility = View.GONE
        binding.recyclerView.scrollToPosition(adapter.itemCount - 1)
        showLoading(true)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.sendChatMessage(ChatRequest(message))
                if (response.isSuccessful && response.body()?.success == true) {
                    val reply = response.body()!!.data!!.reply
                    adapter.addMessage(ChatMessageItem.Bot(reply))
                } else {
                    adapter.addMessage(
                        ChatMessageItem.Bot("Sorry, I couldn't process that. Please try again.")
                    )
                }
                binding.recyclerView.scrollToPosition(adapter.itemCount - 1)
            } catch (e: Exception) {
                adapter.addMessage(
                    ChatMessageItem.Bot("Connection error. Please check your network and try again.")
                )
            } finally {
                if (_binding != null) {
                    showLoading(false)
                }
            }
        }
    }

    /**
     * Loads existing chat history.
     *
     * @author Xuhan Zhang
     */
    private fun loadHistory() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getChatHistory()
                if (response.isSuccessful && response.body()?.success == true) {
                    val history = response.body()!!.data ?: emptyList()
                    history.forEach { msg ->
                        adapter.addMessage(ChatMessageItem.User(msg.userMessage))
                        adapter.addMessage(ChatMessageItem.Bot(msg.botResponse))
                    }
                    binding.tvEmpty.visibility =
                        if (history.isEmpty()) View.VISIBLE else View.GONE
                    if (adapter.itemCount > 0) {
                        binding.recyclerView.scrollToPosition(adapter.itemCount - 1)
                    }
                }
            } catch (_: Exception) {
                binding.tvEmpty.visibility = View.VISIBLE
            }
        }
    }

    private fun showLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSend.isEnabled = !loading
    }
}
