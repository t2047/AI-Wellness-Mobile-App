package com.wellnessapp.ui.main.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.wellnessapp.data.api.RetrofitClient
import com.wellnessapp.databinding.FragmentCoachBinding
import com.wellnessapp.ui.recommendation.RecommendationAdapter
import kotlinx.coroutines.launch

/**
 * Coach tab content hosted inside MainActivity.
 *
 * @author Xuhan Zhang
 */
class CoachFragment : Fragment() {

    private var _binding: FragmentCoachBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: RecommendationAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCoachBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupList()
        setupListeners()
        loadRecommendations()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Configures the recommendation list.
     *
     * @author Xuhan Zhang
     */
    private fun setupList() {
        adapter = RecommendationAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    /**
     * Wires refresh and generate actions.
     *
     * @author Xuhan Zhang
     */
    private fun setupListeners() {
        binding.swipeRefresh.setOnRefreshListener { loadRecommendations() }
        binding.btnGenerate.setOnClickListener { triggerRecommendation() }
    }

    /**
     * Loads existing AI recommendations.
     *
     * @author Xuhan Zhang
     */
    private fun loadRecommendations() {
        binding.swipeRefresh.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
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
                showMessage("Failed to load: ${e.localizedMessage}")
            } finally {
                if (_binding != null) {
                    binding.swipeRefresh.isRefreshing = false
                }
            }
        }
    }

    /**
     * Triggers backend recommendation generation.
     *
     * @author Xuhan Zhang
     */
    private fun triggerRecommendation() {
        showLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.triggerRecommendation()
                if (response.isSuccessful && response.body()?.success == true) {
                    showMessage("Recommendation generated!")
                    loadRecommendations()
                } else {
                    showMessage("Failed to generate: ${response.body()?.message}")
                }
            } catch (e: Exception) {
                showMessage("Error: ${e.localizedMessage}")
            } finally {
                if (_binding != null) {
                    showLoading(false)
                }
            }
        }
    }

    private fun showLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnGenerate.isEnabled = !loading
    }

    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }
}
