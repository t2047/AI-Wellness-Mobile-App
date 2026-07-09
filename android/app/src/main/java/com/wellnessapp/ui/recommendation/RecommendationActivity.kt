/**
 * @author Jia Qianrui
 * @author Liu Zhuocheng
 */
package com.wellnessapp.ui.recommendation

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.wellnessapp.data.api.RetrofitClient
import com.wellnessapp.databinding.ActivityRecommendationBinding
import com.wellnessapp.ui.summary.WeeklySummaryActivity
import kotlinx.coroutines.launch

/**
 * Recommendation screen — displays AI-generated wellness recommendations
 * and allows triggering new analysis.
 *
 * @author WellnessApp Team
 * @author Liu Zhuocheng
 */
class RecommendationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecommendationBinding
    private lateinit var adapter: RecommendationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecommendationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = RecommendationAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { loadRecommendations() }
        binding.btnGenerate.setOnClickListener { triggerRecommendation() }
        binding.btnWeeklySummaries.setOnClickListener {
            startActivity(Intent(this, WeeklySummaryActivity::class.java))
        }

        loadRecommendations()
    }

    private fun loadRecommendations() {
        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getRecommendations()
                if (response.isSuccessful && response.body()?.success == true) {
                    val recommendations = response.body()!!.data ?: emptyList()
                    adapter.submitList(recommendations)
                    binding.tvEmpty.visibility =
                        if (recommendations.isEmpty()) View.VISIBLE else View.GONE
                    binding.recyclerView.visibility =
                        if (recommendations.isEmpty()) View.GONE else View.VISIBLE
                }
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Failed to load: ${e.localizedMessage}",
                    Snackbar.LENGTH_SHORT).show()
            } finally {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun triggerRecommendation() {
        showLoading(true)
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.triggerRecommendation()
                if (response.isSuccessful && response.body()?.success == true) {
                    val rec = response.body()!!.data
                    Snackbar.make(binding.root,
                        "Recommendation generated!",
                        Snackbar.LENGTH_SHORT).show()
                    loadRecommendations()
                } else {
                    Snackbar.make(binding.root,
                        "Failed to generate: ${response.body()?.message}",
                        Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Snackbar.make(binding.root,
                    "Error: ${e.localizedMessage}",
                    Snackbar.LENGTH_SHORT).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnGenerate.isEnabled = !loading
    }
}
