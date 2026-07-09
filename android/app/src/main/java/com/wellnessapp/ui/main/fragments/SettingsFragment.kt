/**
 * @author Tao Yuchen
 */
package com.wellnessapp.ui.main.fragments

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.wellnessapp.R
import com.wellnessapp.data.api.RetrofitClient
import com.wellnessapp.data.model.ModelConfigRequest
import com.wellnessapp.data.model.ModelConfigResponse
import com.wellnessapp.databinding.FragmentSettingsBinding
import com.wellnessapp.ui.login.LoginActivity
import com.wellnessapp.util.TokenManager
import kotlinx.coroutines.launch

/**
 * Settings tab for managing per-user AI model configurations.
 * Users can save their own model base_url, api_key, and model name.
 *
 * @author WellnessApp Team
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        loadConfigs()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupListeners() {
        binding.btnSave.setOnClickListener { saveConfig() }
        binding.btnRefresh.setOnClickListener { loadConfigs() }
    }

    /**
     * Load all saved configs for the current user.
     */
    private fun loadConfigs() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getModelConfigs()
                if (response.isSuccessful && response.body()?.success == true) {
                    val configs = response.body()!!.data ?: emptyList()
                    renderConfigList(configs)
                } else if (response.code() == 401 || response.code() == 403) {
                    handleSessionExpired()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to load: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Save / update the current model configuration.
     */
    private fun saveConfig() {
        val provider = binding.etProviderName.text.toString().trim()
        val baseUrl = binding.etBaseUrl.text.toString().trim()
        val apiKey = binding.etApiKey.text.toString().trim()
        val modelName = binding.etModelName.text.toString().trim()

        if (provider.isEmpty() || baseUrl.isEmpty() || apiKey.isEmpty() || modelName.isEmpty()) {
            Toast.makeText(requireContext(), "All fields are required", Toast.LENGTH_SHORT).show()
            return
        }

        val request = ModelConfigRequest(
            providerName = provider,
            baseUrl = baseUrl,
            apiKey = apiKey,
            modelName = modelName,
            isActive = true
        )

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.saveModelConfig(request)
                if (response.isSuccessful && response.body()?.success == true) {
                    Toast.makeText(requireContext(), getString(R.string.config_saved), Toast.LENGTH_SHORT).show()
                    binding.etApiKey.text?.clear()
                    loadConfigs()
                } else if (response.code() == 401 || response.code() == 403) {
                    handleSessionExpired()
                } else {
                    Toast.makeText(requireContext(), "Save failed: ${response.body()?.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Render the list of saved configs as cards.
     */
    private fun renderConfigList(configs: List<ModelConfigResponse>) {
        val container = binding.llConfigList
        container.removeAllViews()

        if (configs.isEmpty()) {
            binding.tvNoConfigs.visibility = View.VISIBLE
            return
        }
        binding.tvNoConfigs.visibility = View.GONE

        val primaryColor = ContextCompat.getColor(requireContext(), R.color.primary)
        val surfaceColor = ContextCompat.getColor(requireContext(), R.color.surface)
        val activeGreen = ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark)

        for (config in configs) {
            val card = MaterialCardView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 12 }
                radius = 8 * resources.displayMetrics.density
                cardElevation = 1f
                setContentPadding(12, 12, 12, 12)
                setCardBackgroundColor(surfaceColor)
            }

            val content = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
            }

            // Title row: provider + active badge
            val titleRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            val titleText = TextView(requireContext()).apply {
                text = "${config.providerName} · ${config.modelName}"
                setTextColor(primaryColor)
                setTypeface(null, Typeface.BOLD)
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            titleRow.addView(titleText)

            if (config.isActive) {
                val badge = TextView(requireContext()).apply {
                    text = "● ACTIVE"
                    setTextColor(activeGreen)
                    setTypeface(null, Typeface.BOLD)
                    textSize = 12f
                }
                titleRow.addView(badge)
            }
            content.addView(titleRow)

            // Details
            val detailText = TextView(requireContext()).apply {
                text = "URL: ${config.baseUrl}\nKey: ${config.apiKeyMasked}"
                setTextColor(0xFF666666.toInt())
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 6 }
            }
            content.addView(detailText)

            // Action buttons
            val actionRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 8 }
            }

            if (!config.isActive) {
                val activateBtn = MaterialButton(requireContext()).apply {
                    text = getString(R.string.activate)
                    setTextColor(primaryColor)
                    isClickable = true
                    setOnClickListener {
                        activateConfig(config.id!!)
                    }
                }
                actionRow.addView(activateBtn)
            }

            val deleteBtn = MaterialButton(requireContext()).apply {
                text = getString(R.string.delete_config)
                setTextColor(0xFFCC0000.toInt())
                isClickable = true
                setOnClickListener {
                    deleteConfig(config.id!!)
                }
            }
            actionRow.addView(deleteBtn)

            content.addView(actionRow)
            card.addView(content)
            container.addView(card)
        }
    }

    private fun activateConfig(id: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.activateModelConfig(id)
                if (response.isSuccessful && response.body()?.success == true) {
                    Toast.makeText(requireContext(), getString(R.string.config_activated), Toast.LENGTH_SHORT).show()
                    loadConfigs()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteConfig(id: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.deleteModelConfig(id)
                if (response.isSuccessful && response.body()?.success == true) {
                    Toast.makeText(requireContext(), getString(R.string.config_deleted), Toast.LENGTH_SHORT).show()
                    loadConfigs()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleSessionExpired() {
        TokenManager.logout()
        startActivity(android.content.Intent(requireContext(), LoginActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }
}
