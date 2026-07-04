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
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Chat screen — users can chat with WellBot, the wellness AI assistant.
 *
 * @author WellnessApp Team
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
                    val reply = response.body()!!.data!!.reply
                    adapter.addMessage(ChatMessageItem.Bot(reply))
                    binding.recyclerView.scrollToPosition(adapter.itemCount - 1)
                    binding.tvEmpty.visibility = View.GONE
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
                    history.forEach { msg ->
                        adapter.addMessage(ChatMessageItem.User(msg.userMessage))
                        adapter.addMessage(ChatMessageItem.Bot(msg.botResponse))
                    }
                    if (history.isEmpty()) {
                        binding.tvEmpty.visibility = View.VISIBLE
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
        binding.btnVoice.isEnabled = !loading
    }
}

/**
 * Sealed class for chat message types in the RecyclerView.
 */
sealed class ChatMessageItem {
    data class User(val message: String) : ChatMessageItem()
    data class Bot(val message: String) : ChatMessageItem()
}
