/**
 * @author Tao Yuchen
 */
package com.wellnessapp.ui.rag

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.wellnessapp.data.api.RetrofitClient
import com.wellnessapp.data.model.RagAskRequest
import com.wellnessapp.databinding.ActivityRagBinding
import com.wellnessapp.util.TokenManager
import io.noties.markwon.Markwon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * RAG Drug Knowledge activity — allows users to query an AI-powered
 * drug knowledge base (MSD corpus) via the backend.
 *
 * @author WellnessApp Team
 */
class RagActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRagBinding
    private lateinit var markwon: Markwon

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRagBinding.inflate(layoutInflater)
        setContentView(binding.root)

        markwon = Markwon.create(this)

        setupToolbar()
        setupListeners()
        loadStatus()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupListeners() {
        binding.btnAsk.setOnClickListener {
            val question = binding.etQuestion.text.toString().trim()
            if (question.isNotEmpty()) {
                askQuestion(question)
            }
        }
        binding.btnInfo.setOnClickListener { loadStatus() }
        binding.btnReindex.setOnClickListener { startReindex() }
    }

    private fun loadStatus() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getRagStatus()
                if (response.isSuccessful && response.body()?.success == true) {
                    val status = response.body()!!.data
                    if (status != null) {
                        binding.tvStatus.visibility = View.VISIBLE
                        binding.tvStatus.text = buildString {
                            append("Index: ${status.documentCount} docs, ${status.sourceCount} sources")
                            status.builtAt?.let { append(" | Built: ${it.take(10)}") }
                            status.deepseekModel?.let { append(" | LLM: ${it}") }
                        }
                    }
                } else if (response.code() == 401 || response.code() == 403) {
                    handleSessionExpired()
                }
            } catch (e: Exception) {
                binding.tvStatus.visibility = View.VISIBLE
                binding.tvStatus.text = "RAG index unavailable: ${e.localizedMessage}"
            }
        }
    }

    private fun startReindex() {
        Toast.makeText(this, "Starting reindex...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.startRagReindex(
                    mapOf("force" to true)
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()!!.data
                    if (data != null) {
                        pollReindexStatus(data.taskId)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@RagActivity,
                    "Reindex failed: ${e.localizedMessage}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private suspend fun pollReindexStatus(taskId: String) {
        var completed = false
        var attempts = 0
        while (!completed && attempts < 60) {
            delay(2000)
            attempts++
            try {
                val response = RetrofitClient.apiService.getRagReindexStatus(taskId)
                if (response.isSuccessful && response.body()?.success == true) {
                    val status = response.body()!!.data
                    if (status != null) {
                        binding.tvStatus.visibility = View.VISIBLE
                        binding.tvStatus.text =
                            "Reindex: ${status.progress}% — ${status.phase}: ${status.message}"
                        if (status.status == "completed") {
                            completed = true
                            Toast.makeText(
                                this@RagActivity,
                                "Reindex completed!",
                                Toast.LENGTH_SHORT
                            ).show()
                            loadStatus()
                        } else if (status.status == "failed") {
                            completed = true
                            Toast.makeText(
                                this@RagActivity,
                                "Reindex failed: ${status.error ?: status.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            } catch (_: Exception) {
                // continue polling
            }
        }
    }

    private fun askQuestion(question: String) {
        binding.btnAsk.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE
        binding.etQuestion.text?.clear()
        binding.tvEmpty.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.askRag(
                    RagAskRequest(question = question, topK = 5)
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()!!.data
                    if (data != null) {
                        showAnswer(data.answer ?: "No answer returned.", data.sources ?: emptyList())
                    }
                } else if (response.code() == 401 || response.code() == 403) {
                    handleSessionExpired()
                } else {
                    val errorMsg = response.body()?.message
                        ?: response.errorBody()?.string()
                        ?: "Query failed"
                    showAnswer("Error: $errorMsg", emptyList())
                }
            } catch (e: Exception) {
                showAnswer("Connection error: ${e.localizedMessage}", emptyList())
            } finally {
                binding.btnAsk.isEnabled = true
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun showAnswer(answer: String, sources: List<com.wellnessapp.data.model.RagSource>) {
        binding.cardAnswer.visibility = View.VISIBLE
        markwon.setMarkdown(binding.tvAnswer, answer)

        // Clear old sources
        binding.sourcesContainer.removeAllViews()

        if (sources.isNotEmpty()) {
            binding.tvSourcesHeader.visibility = View.VISIBLE
            for (source in sources) {
                val sourceCard = MaterialCardView(this).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = 8 }
                    cardElevation = 1f
                    radius = 8f
                    setContentPadding(12, 10, 12, 10)
                }

                val sourceText = TextView(this).apply {
                    text = "[${source.rank}] ${source.title} — ${source.sectionTitle}\n" +
                            "Score: ${String.format("%.2f", source.score)} | " +
                            "Source: ${source.sourceUrl}\n" +
                            "\"${source.snippet.take(200)}...\""
                    textSize = 12f
                    setTextColor(getColor(com.wellnessapp.R.color.textSecondary))
                    setLineSpacing(2f, 1f)
                }

                sourceCard.addView(sourceText)
                binding.sourcesContainer.addView(sourceCard)
            }
        } else {
            binding.tvSourcesHeader.visibility = View.GONE
        }

        // Scroll to top
        binding.scrollView.post {
            binding.scrollView.fullScroll(View.FOCUS_UP)
        }
    }

    private fun handleSessionExpired() {
        Toast.makeText(
            this,
            "Session expired. Please log in again.",
            Toast.LENGTH_SHORT
        ).show()
        TokenManager.logout()
        val intent = android.content.Intent(
            this,
            com.wellnessapp.ui.login.LoginActivity::class.java
        )
        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
