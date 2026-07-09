package com.wellnessapp.ui.main.fragments

import android.content.Intent
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.wellnessapp.R
import com.wellnessapp.data.api.RetrofitClient
import com.wellnessapp.data.model.RagAskRequest
import com.wellnessapp.data.model.RagSource
import com.wellnessapp.databinding.FragmentKnowledgeBinding
import com.wellnessapp.ui.login.LoginActivity
import com.wellnessapp.util.TokenManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Knowledge tab content hosted inside MainActivity.
 *
 * @author Xuhan Zhang
 */
class KnowledgeFragment : Fragment() {

    private var _binding: FragmentKnowledgeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentKnowledgeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        loadStatus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Wires RAG ask, status, and reindex actions.
     *
     * @author Xuhan Zhang
     */
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

    /**
     * Loads current RAG index status.
     *
     * @author Xuhan Zhang
     */
    private fun loadStatus() {
        viewLifecycleOwner.lifecycleScope.launch {
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

    /**
     * Starts a backend RAG reindex task.
     *
     * @author Xuhan Zhang
     */
    private fun startReindex() {
        Toast.makeText(requireContext(), "Starting reindex...", Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.startRagReindex(mapOf("force" to true))
                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()!!.data
                    if (data != null) {
                        pollReindexStatus(data.taskId)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Reindex failed: ${e.localizedMessage}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private suspend fun pollReindexStatus(taskId: String) {
        var completed = false
        var attempts = 0
        while (!completed && attempts < 60 && _binding != null) {
            delay(2000)
            attempts++
            try {
                val response = RetrofitClient.apiService.getRagReindexStatus(taskId)
                if (response.isSuccessful && response.body()?.success == true) {
                    val status = response.body()!!.data
                    if (status != null) {
                        binding.tvStatus.visibility = View.VISIBLE
                        binding.tvStatus.text =
                            "Reindex: ${status.progress}% - ${status.phase}: ${status.message}"
                        if (status.status == "completed") {
                            completed = true
                            Toast.makeText(
                                requireContext(),
                                "Reindex completed!",
                                Toast.LENGTH_SHORT
                            ).show()
                            loadStatus()
                        } else if (status.status == "failed") {
                            completed = true
                            Toast.makeText(
                                requireContext(),
                                "Reindex failed: ${status.error ?: status.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            } catch (_: Exception) {
                // Continue polling until the backend reports completion or timeout.
            }
        }
    }

    /**
     * Sends one RAG question to the backend and displays the answer.
     *
     * @author Xuhan Zhang
     */
    private fun askQuestion(question: String) {
        binding.btnAsk.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE
        binding.etQuestion.text?.clear()
        binding.tvEmpty.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.askRag(
                    RagAskRequest(question = question, topK = 3)
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
                if (_binding != null) {
                    binding.btnAsk.isEnabled = true
                    binding.progressBar.visibility = View.GONE
                }
            }
        }
    }

    /**
     * Renders the RAG answer and source cards.
     *
     * @author Xuhan Zhang
     */
    private fun showAnswer(answer: String, sources: List<RagSource>) {
        binding.cardAnswer.visibility = View.VISIBLE
        binding.tvAnswer.text = Html.fromHtml(
            answer.replace("\n", "<br>"),
            Html.FROM_HTML_MODE_COMPACT
        )
        binding.sourcesContainer.removeAllViews()

        if (sources.isNotEmpty()) {
            binding.tvSourcesHeader.visibility = View.VISIBLE
            for (source in sources) {
                val sourceCard = MaterialCardView(requireContext()).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = 8 }
                    cardElevation = 1f
                    radius = 8f
                    setContentPadding(12, 10, 12, 10)
                }

                val sourceText = TextView(requireContext()).apply {
                    text = "[${source.rank}] ${source.title} - ${source.sectionTitle}\n" +
                        "Score: ${String.format("%.2f", source.score)} | " +
                        "Source: ${source.sourceUrl}\n" +
                        "\"${source.snippet.take(200)}...\""
                    textSize = 12f
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.textSecondary))
                    setLineSpacing(2f, 1f)
                }

                sourceCard.addView(sourceText)
                binding.sourcesContainer.addView(sourceCard)
            }
        } else {
            binding.tvSourcesHeader.visibility = View.GONE
        }

        binding.scrollView.post {
            binding.scrollView.fullScroll(View.FOCUS_UP)
        }
    }

    private fun handleSessionExpired() {
        Toast.makeText(
            requireContext(),
            "Session expired. Please log in again.",
            Toast.LENGTH_SHORT
        ).show()
        TokenManager.logout()
        val intent = Intent(requireContext(), LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
    }
}
