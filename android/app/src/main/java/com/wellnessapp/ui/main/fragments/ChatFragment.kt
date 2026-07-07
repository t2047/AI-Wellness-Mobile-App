package com.wellnessapp.ui.main.fragments

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.wellnessapp.R
import com.wellnessapp.data.api.RetrofitClient
import com.wellnessapp.data.model.ChatRequest
import com.wellnessapp.data.model.ChatResponse
import com.wellnessapp.databinding.FragmentChatBinding
import com.wellnessapp.ui.chat.ChatMessageAdapter
import com.wellnessapp.ui.chat.ChatMessageItem
import com.wellnessapp.ui.main.MainActivity
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Chat tab content hosted inside MainActivity.
 *
 * @author Xuhan Zhang
 * @author ZHAO LEI
 */
class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: ChatMessageAdapter
    private val speechLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val spokenText = result.data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                if (spokenText.isNotBlank()) {
                    binding.etMessage.setText(spokenText)
                    binding.etMessage.setSelection(spokenText.length)
                    sendMessage()
                } else {
                    Toast.makeText(requireContext(), R.string.voice_not_recognized, Toast.LENGTH_SHORT).show()
                }
            }
        }

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
     * Wires text, voice, and IME send actions.
     *
     * @author Tao Yuchen
     * @author Xuhan Zhang
     * @author ZHAO LEI
     */
    private fun setupListeners() {
        binding.btnSend.setOnClickListener { sendMessage() }
        binding.btnVoice.setOnClickListener { startVoiceInput() }
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
     * Opens Android speech recognition and sends the recognized message.
     *
     * @author ZHAO LEI
     */
    private fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_prompt))
        }

        try {
            speechLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.voice_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Sends one chat message and appends the bot reply.
     *
     * @author Xuhan Zhang
     * @author ZHAO LEI
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
                    val chatResponse = response.body()?.data
                    if (chatResponse != null) {
                        adapter.addMessage(ChatMessageItem.Bot(formatReply(chatResponse)))
                    } else {
                        adapter.addMessage(ChatMessageItem.Bot("The server returned an empty reply."))
                    }
                } else if (response.code() == 401 || response.code() == 403) {
                    handleSessionExpired()
                    return@launch
                } else {
                    adapter.addMessage(
                        ChatMessageItem.Bot("Sorry, It seems there are some issues with the chat service. Please wait a bit and try again, or contact the administrator.")
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
     * @author ZHAO LEI
     */
    private fun loadHistory() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getChatHistory()
                if (response.isSuccessful && response.body()?.success == true) {
                    val history = response.body()!!.data ?: emptyList()
                    history.asReversed().forEach { msg ->
                        adapter.addMessage(ChatMessageItem.User(msg.userMessage))
                        adapter.addMessage(ChatMessageItem.Bot(msg.botResponse))
                    }
                    binding.tvEmpty.visibility =
                        if (history.isEmpty()) View.VISIBLE else View.GONE
                    if (adapter.itemCount > 0) {
                        binding.recyclerView.scrollToPosition(adapter.itemCount - 1)
                    }
                } else if (response.code() == 401 || response.code() == 403) {
                    handleSessionExpired()
                }
            } catch (_: Exception) {
                binding.tvEmpty.visibility = View.VISIBLE
            }
        }
    }

    /**
     * Appends RAG source citations returned by the backend.
     *
     * @author ZHAO LEI
     */
    private fun formatReply(response: ChatResponse): String {
        if (response.sources.isEmpty()) {
            return response.reply
        }
        return buildString {
            append(response.reply)
            append("\n\nSources:")
            response.sources.forEach { source ->
                append("\n[${source.rank}] ${source.title} — ${source.section}")
                if (source.sourceUrl.isNotBlank()) {
                    append("\n${source.sourceUrl}")
                }
            }
        }
    }

    /**
     * Clears an expired session through the main navigation host.
     *
     * @author ZHAO LEI
     */
    private fun handleSessionExpired() {
        Toast.makeText(
            requireContext(),
            "Session expired. Please log in again.",
            Toast.LENGTH_SHORT
        ).show()
        (activity as? MainActivity)?.logout()
    }

    private fun showLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSend.isEnabled = !loading
        binding.btnVoice.isEnabled = !loading
    }
}
