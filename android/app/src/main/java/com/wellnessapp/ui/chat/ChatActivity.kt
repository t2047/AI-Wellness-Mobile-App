/**
 * @author Jia Qianrui
 * @author Zhao Lei
 * @author Cai Hanbo
 * @author Tao Yuchen
 */
package com.wellnessapp.ui.chat

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.inputmethod.EditorInfo
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.wellnessapp.R
import com.wellnessapp.data.api.RetrofitClient
import com.wellnessapp.data.model.ChatRequest
import com.wellnessapp.databinding.ActivityChatBinding
import com.wellnessapp.ui.login.LoginActivity
import com.wellnessapp.util.TokenManager
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Chat screen — users can chat with WellBot, the wellness AI assistant.
 *
 * @author WellnessApp Team
 * @author ZHAO LEI
 */
class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
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
                    Toast.makeText(this, R.string.voice_not_recognized, Toast.LENGTH_SHORT).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.inflateMenu(R.menu.menu_chat)
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_new_chat) {
                startNewConversation()
                true
            } else {
                false
            }
        }

        adapter = ChatMessageAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.btnSend.setOnClickListener { sendMessage() }
        binding.btnVoice.setOnClickListener { startVoiceInput() }
        binding.etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }

        loadHistory()
    }

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
            Toast.makeText(this, R.string.voice_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun startNewConversation() {
        adapter.clearMessages()
        binding.tvEmpty.visibility = View.VISIBLE
        Toast.makeText(this, R.string.new_conversation_started, Toast.LENGTH_SHORT).show()
    }

    private fun sendMessage() {
        val message = binding.etMessage.text.toString().trim()
        if (message.isEmpty()) return

        // Show user message immediately
        adapter.addMessage(ChatMessageItem.User(message))
        binding.etMessage.text?.clear()
        binding.recyclerView.scrollToPosition(adapter.itemCount - 1)

        showLoading(true)

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.sendChatMessage(
                    ChatRequest(message)
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val chatResponse = response.body()!!.data!!
                    adapter.addMessage(ChatMessageItem.Bot(formatReply(chatResponse)))
                    binding.recyclerView.scrollToPosition(adapter.itemCount - 1)
                    binding.tvEmpty.visibility = View.GONE
                } else if (response.code() == 401 || response.code() == 403) {
                    handleSessionExpired()
                } else {
                    adapter.addMessage(
                        ChatMessageItem.Bot("Sorry, I couldn't process that. Please try again.")
                    )
                }
            } catch (e: Exception) {
                adapter.addMessage(
                    ChatMessageItem.Bot("Connection error. Please check your network and try again.")
                )
            } finally {
                showLoading(false)
            }
        }
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getChatHistory()
                if (response.isSuccessful && response.body()?.success == true) {
                    val history = response.body()!!.data ?: emptyList()
                    history.asReversed().forEach { msg ->
                        adapter.addMessage(ChatMessageItem.User(msg.userMessage))
                        adapter.addMessage(ChatMessageItem.Bot(msg.botResponse))
                    }
                    if (history.isEmpty()) {
                        binding.tvEmpty.visibility = View.VISIBLE
                    } else {
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

    private fun showLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSend.isEnabled = !loading
        binding.btnVoice.isEnabled = !loading
    }

    /**
     * Appends RAG citations returned by the backend to the visible answer.
     *
     * @author ZHAO LEI
     */
    private fun formatReply(response: com.wellnessapp.data.model.ChatResponse): String {
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
     * Clears an expired local session and returns to login.
     *
     * @author ZHAO LEI
     */
    private fun handleSessionExpired() {
        Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_SHORT).show()
        TokenManager.logout()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}

/**
 * Sealed class for chat message types in the RecyclerView.
 */
sealed class ChatMessageItem {
    data class User(val message: String) : ChatMessageItem()
    data class Bot(val message: String) : ChatMessageItem()
}
