package com.wellnessapp.ui.health

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wellnessapp.data.api.RetrofitClient
import com.wellnessapp.data.model.WellnessRecord
import com.wellnessapp.databinding.ActivityHealthRecordBinding
import com.wellnessapp.ui.chat.ChatActivity
import com.wellnessapp.ui.login.LoginActivity
import com.wellnessapp.ui.rag.RagActivity
import com.wellnessapp.ui.recommendation.RecommendationActivity
import com.wellnessapp.util.TokenManager
import kotlinx.coroutines.launch

/**
 * Main screen after login — displays list of wellness records
 * with options to add, edit, delete, and navigate to chat/recommendations.
 *
 * @author WellnessApp Team
 */
class HealthRecordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHealthRecordBinding
    private lateinit var adapter: WellnessRecordAdapter
    private var allRecords: List<WellnessRecord> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHealthRecordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        loadRecords()
    }

    private fun setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                com.wellnessapp.R.id.action_chat -> {
                    startActivity(Intent(this, ChatActivity::class.java))
                    true
                }
                com.wellnessapp.R.id.action_recommendations -> {
                    startActivity(Intent(this, RecommendationActivity::class.java))
                    true
                }
                com.wellnessapp.R.id.action_rag -> {
                    startActivity(Intent(this, RagActivity::class.java))
                    true
                }
                com.wellnessapp.R.id.action_logout -> {
                    logout()
                    true
                }
                else -> false
            }
        }
        binding.tvWelcome.text = "Welcome, ${TokenManager.getUsername() ?: "User"}"
    }

    private fun setupRecyclerView() {
        adapter = WellnessRecordAdapter(
            onEditClick = { record -> openEditForm(record) },
            onDeleteClick = { record -> confirmDelete(record) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupListeners() {
        binding.swipeRefresh.setOnRefreshListener { loadRecords() }
        binding.etSearchRecords.doAfterTextChanged { applyRecordFilter() }
        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, RecordFormActivity::class.java))
        }
    }

    private fun loadRecords() {
        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getWellnessRecords()
                if (response.isSuccessful && response.body()?.success == true) {
                    allRecords = response.body()!!.data ?: emptyList()
                    applyRecordFilter()
                } else if (response.code() == 401 || response.code() == 403) {
                    handleSessionExpired()
                } else {
                    val errorMessage = response.body()?.message
                        ?: response.errorBody()?.string()
                        ?: "Failed to load records"
                    showError("Failed to load records (${response.code()}): $errorMessage")
                }
            } catch (e: Exception) {
                showError("Connection error: ${e.localizedMessage}")
            } finally {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun applyRecordFilter() {
        val query = binding.etSearchRecords.text?.toString()?.trim()?.lowercase().orEmpty()
        val filteredRecords = if (query.isEmpty()) {
            allRecords
        } else {
            allRecords.filter { record ->
                record.recordDate.lowercase().contains(query) ||
                        record.activityName.orEmpty().lowercase().contains(query) ||
                        record.sleepHours?.toString().orEmpty().contains(query) ||
                        record.activityDurationMinutes?.toString().orEmpty().contains(query) ||
                        record.notes.orEmpty().lowercase().contains(query)
            }
        }

        adapter.submitList(filteredRecords)
        binding.recyclerView.visibility =
            if (filteredRecords.isEmpty()) View.GONE else View.VISIBLE
        binding.tvEmpty.text = when {
            allRecords.isEmpty() -> getString(com.wellnessapp.R.string.no_data)
            filteredRecords.isEmpty() -> getString(com.wellnessapp.R.string.no_matching_records)
            else -> getString(com.wellnessapp.R.string.no_data)
        }
        binding.tvEmpty.visibility =
            if (filteredRecords.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openEditForm(record: WellnessRecord) {
        val intent = Intent(this, RecordFormActivity::class.java).apply {
            putExtra(RecordFormActivity.EXTRA_RECORD_ID, record.id)
            putExtra(RecordFormActivity.EXTRA_SLEEP_HOURS, record.sleepHours)
            putExtra(RecordFormActivity.EXTRA_ACTIVITY_NAME, record.activityName)
            putExtra(RecordFormActivity.EXTRA_DURATION, record.activityDurationMinutes)
            putExtra(RecordFormActivity.EXTRA_DATE, record.recordDate)
            putExtra(RecordFormActivity.EXTRA_NOTES, record.notes)
        }
        startActivity(intent)
    }

    private fun confirmDelete(record: WellnessRecord) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Record")
            .setMessage("Are you sure you want to delete this record?")
            .setPositiveButton("Delete") { _, _ -> deleteRecord(record) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteRecord(record: WellnessRecord) {
        lifecycleScope.launch {
            try {
                record.id?.let { id ->
                    val response = RetrofitClient.apiService.deleteWellnessRecord(id)
                    if (response.isSuccessful && response.body()?.success == true) {
                        loadRecords()
                    }
                }
            } catch (e: Exception) {
                showError("Failed to delete: ${e.localizedMessage}")
            }
        }
    }

    private fun handleSessionExpired() {
        Toast.makeText(
            this,
            "Session expired. Please log in again.",
            Toast.LENGTH_SHORT
        ).show()
        logout()
    }

    private fun logout() {
        TokenManager.logout()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun showError(message: String) {
        binding.tvEmpty.text = message
        binding.tvEmpty.visibility = View.VISIBLE
    }
}
