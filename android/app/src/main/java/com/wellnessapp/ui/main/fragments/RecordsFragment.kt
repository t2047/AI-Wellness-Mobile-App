package com.wellnessapp.ui.main.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wellnessapp.R
import com.wellnessapp.data.api.RetrofitClient
import com.wellnessapp.data.model.WellnessRecord
import com.wellnessapp.databinding.FragmentRecordsBinding
import com.wellnessapp.ui.health.RecordFormActivity
import com.wellnessapp.ui.health.WellnessRecordAdapter
import com.wellnessapp.ui.main.MainActivity
import kotlinx.coroutines.launch

/**
 * Records tab content hosted inside MainActivity.
 *
 * @author Xuhan Zhang
 */
class RecordsFragment : Fragment() {

    private var _binding: FragmentRecordsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: WellnessRecordAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecordsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupListeners()
        loadRecords()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            loadRecords()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Configures the health records list.
     *
     * @author Xuhan Zhang
     */
    private fun setupRecyclerView() {
        adapter = WellnessRecordAdapter(
            onEditClick = { record -> openEditForm(record) },
            onDeleteClick = { record -> confirmDelete(record) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    /**
     * Connects refresh and add actions.
     *
     * @author Xuhan Zhang
     */
    private fun setupListeners() {
        binding.swipeRefresh.setOnRefreshListener { loadRecords() }
        binding.fabAdd.setOnClickListener {
            startActivity(Intent(requireContext(), RecordFormActivity::class.java))
        }
    }

    /**
     * Loads records for the current user.
     *
     * @author Xuhan Zhang
     */
    private fun loadRecords() {
        binding.swipeRefresh.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getWellnessRecords()
                if (response.isSuccessful && response.body()?.success == true) {
                    val records = response.body()!!.data ?: emptyList()
                    adapter.submitList(records)
                    binding.tvEmpty.text = getString(R.string.no_data)
                    binding.tvEmpty.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
                    binding.recyclerView.visibility =
                        if (records.isEmpty()) View.GONE else View.VISIBLE
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
                if (_binding != null) {
                    binding.swipeRefresh.isRefreshing = false
                }
            }
        }
    }

    private fun openEditForm(record: WellnessRecord) {
        val intent = Intent(requireContext(), RecordFormActivity::class.java).apply {
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
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Record")
            .setMessage("Are you sure you want to delete this record?")
            .setPositiveButton("Delete") { _, _ -> deleteRecord(record) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteRecord(record: WellnessRecord) {
        viewLifecycleOwner.lifecycleScope.launch {
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
            requireContext(),
            "Session expired. Please log in again.",
            Toast.LENGTH_SHORT
        ).show()
        (activity as? MainActivity)?.logout()
    }

    private fun showError(message: String) {
        binding.tvEmpty.text = message
        binding.tvEmpty.visibility = View.VISIBLE
    }
}
