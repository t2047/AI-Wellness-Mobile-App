package com.wellnessapp.ui.summary

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.wellnessapp.data.api.RetrofitClient
import com.wellnessapp.databinding.ActivityWeeklySummaryBinding
import kotlinx.coroutines.launch

/**
 * Screen for manually generating and viewing weekly health summaries.
 *
 * @author WellnessApp Team
 */
class WeeklySummaryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWeeklySummaryBinding
    private lateinit var adapter: WeeklySummaryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWeeklySummaryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = WeeklySummaryAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { loadSummaries() }
        binding.btnGenerateWeeklySummary.setOnClickListener { generateSummary() }

        loadSummaries()
    }

    private fun loadSummaries() {
        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getWeeklySummaries()
                if (response.isSuccessful && response.body()?.success == true) {
                    val summaries = response.body()!!.data ?: emptyList()
                    adapter.submitList(summaries)
                    binding.tvEmpty.visibility = if (summaries.isEmpty()) View.VISIBLE else View.GONE
                    binding.recyclerView.visibility = if (summaries.isEmpty()) View.GONE else View.VISIBLE
                } else {
                    Snackbar.make(
                        binding.root,
                        "Failed to load summaries: ${response.body()?.message ?: response.code()}",
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Snackbar.make(
                    binding.root,
                    "Connection error: ${e.localizedMessage}",
                    Snackbar.LENGTH_SHORT
                ).show()
            } finally {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun generateSummary() {
        showLoading(true)
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.generateWeeklySummary()
                if (response.isSuccessful && response.body()?.success == true) {
                    Snackbar.make(
                        binding.root,
                        "Weekly summary generated",
                        Snackbar.LENGTH_SHORT
                    ).show()
                    loadSummaries()
                } else {
                    Snackbar.make(
                        binding.root,
                        "Failed to generate: ${response.body()?.message ?: response.code()}",
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Snackbar.make(
                    binding.root,
                    "Error: ${e.localizedMessage}",
                    Snackbar.LENGTH_SHORT
                ).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnGenerateWeeklySummary.isEnabled = !loading
    }
}
