package com.wellnessapp.ui.health

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.wellnessapp.data.api.RetrofitClient
import com.wellnessapp.data.model.WellnessRecord
import com.wellnessapp.databinding.ActivityRecordFormBinding
import com.wellnessapp.ui.login.LoginActivity
import com.wellnessapp.util.TokenManager
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Form activity for creating and editing wellness records.
 *
 * @author WellnessApp Team
 */
class RecordFormActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RECORD_ID = "record_id"
        const val EXTRA_SLEEP_HOURS = "sleep_hours"
        const val EXTRA_ACTIVITY_NAME = "activity_name"
        const val EXTRA_DURATION = "duration"
        const val EXTRA_DATE = "date"
        const val EXTRA_NOTES = "notes"
    }

    private lateinit var binding: ActivityRecordFormBinding
    private var recordId: Long? = null
    private var isEdit = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordFormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        recordId = intent.getLongExtra(EXTRA_RECORD_ID, -1)
        isEdit = recordId != null && recordId != -1L

        if (isEdit) {
            title = "Edit Record"
            populateForm()
        } else {
            title = "New Record"
            binding.etDate.setText(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE))
        }

        // Date picker on date field click
        binding.etDate.setOnClickListener { showDatePicker() }

        binding.btnSave.setOnClickListener { saveRecord() }
    }

    private fun populateForm() {
        val sleepHours = intent.getDoubleExtra(EXTRA_SLEEP_HOURS, -1.0)
        if (sleepHours > 0) binding.etSleepHours.setText(sleepHours.toString())

        intent.getStringExtra(EXTRA_ACTIVITY_NAME)?.let {
            binding.etActivityName.setText(it)
        }

        val duration = intent.getIntExtra(EXTRA_DURATION, -1)
        if (duration > 0) binding.etDuration.setText(duration.toString())

        intent.getStringExtra(EXTRA_DATE)?.let {
            binding.etDate.setText(it)
        }

        intent.getStringExtra(EXTRA_NOTES)?.let {
            binding.etNotes.setText(it)
        }
    }

    private fun showDatePicker() {
        val currentDate = try {
            LocalDate.parse(binding.etDate.text.toString(), DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (e: Exception) {
            LocalDate.now()
        }

        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val date = LocalDate.of(year, month + 1, dayOfMonth)
                binding.etDate.setText(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
            },
            currentDate.year,
            currentDate.monthValue - 1,
            currentDate.dayOfMonth
        ).show()
    }

    private fun saveRecord() {
        val sleepStr = binding.etSleepHours.text.toString().trim()
        val activityName = binding.etActivityName.text.toString().trim()
        val durationStr = binding.etDuration.text.toString().trim()
        val date = binding.etDate.text.toString().trim()
        val notes = binding.etNotes.text.toString().trim()

        binding.sleepLayout.error = null
        binding.durationLayout.error = null
        binding.dateLayout.error = null

        if (date.isEmpty()) {
            binding.dateLayout.error = "Date is required"
            return
        }
        try {
            LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (e: Exception) {
            binding.dateLayout.error = "Use date format YYYY-MM-DD"
            return
        }

        val sleepHours = sleepStr.toDoubleOrNull()
        if (sleepStr.isNotEmpty() && (sleepHours == null || sleepHours < 0.0 || sleepHours > 24.0)) {
            binding.sleepLayout.error = "Enter sleep hours from 0 to 24"
            return
        }

        val durationMinutes = durationStr.toIntOrNull()
        if (durationStr.isNotEmpty() && (durationMinutes == null || durationMinutes < 0 || durationMinutes > 1440)) {
            binding.durationLayout.error = "Enter activity minutes from 0 to 1440"
            return
        }

        if (sleepHours == null && activityName.isEmpty() && durationMinutes == null) {
            binding.sleepLayout.error = "Add sleep or activity data"
            return
        }

        val record = WellnessRecord(
            sleepHours = sleepHours,
            activityName = activityName.ifEmpty { null },
            activityDurationMinutes = durationMinutes,
            recordDate = date,
            notes = notes.ifEmpty { null }
        )

        showLoading(true)
        binding.tvError.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val response = if (isEdit) {
                    RetrofitClient.apiService.updateWellnessRecord(recordId!!, record)
                } else {
                    RetrofitClient.apiService.createWellnessRecord(record)
                }

                if (response.isSuccessful && response.body()?.success == true) {
                    finish()
                } else if (response.code() == 401 || response.code() == 403) {
                    handleSessionExpired()
                } else {
                    val errorMessage = response.body()?.message
                        ?: response.errorBody()?.string()
                        ?: "Save failed"
                    showError("Save failed (${response.code()}): $errorMessage")
                }
            } catch (e: Exception) {
                showError("Connection error: ${e.localizedMessage}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun handleSessionExpired() {
        Toast.makeText(
            this,
            "Session expired. Please log in again.",
            Toast.LENGTH_SHORT
        ).show()
        TokenManager.logout()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun showLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSave.isEnabled = !loading
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }
}
