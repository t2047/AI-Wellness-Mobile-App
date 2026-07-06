package com.wellnessapp.ui.login

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.wellnessapp.data.api.RetrofitClient
import com.wellnessapp.data.model.RegisterRequest
import com.wellnessapp.databinding.ActivityRegisterBinding
import kotlinx.coroutines.launch

/**
 * Register screen — creates a new account and returns to login on success.
 *
 * @author WellnessApp Team
 * @author ZHAO LEI
 */
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRegister.setOnClickListener { performRegister() }
    }

    private fun performRegister() {
        val username = binding.etUsername.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        // Validation
        var hasError = false
        if (username.isEmpty() || username.length < 3) {
            binding.usernameLayout.error = "Username must be at least 3 characters"
            hasError = true
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailLayout.error = "Enter a valid email"
            hasError = true
        }
        if (password.isEmpty() || password.length < 6) {
            binding.passwordLayout.error = "Password must be at least 6 characters"
            hasError = true
        }
        if (hasError) return

        binding.usernameLayout.error = null
        binding.emailLayout.error = null
        binding.passwordLayout.error = null

        showLoading(true)
        binding.tvError.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.register(
                    RegisterRequest(username, email, password)
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    returnToLogin(username)
                } else {
                    val errorMsg = response.body()?.message
                        ?: "Registration failed"
                    showError(errorMsg)
                }
            } catch (e: Exception) {
                showError("Connection error: ${e.localizedMessage}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnRegister.isEnabled = !loading
    }

    /**
     * Returns the registered username to LoginActivity. The password remains
     * empty so the user must explicitly authenticate.
     *
     * @author ZHAO LEI
     */
    private fun returnToLogin(username: String) {
        val result = Intent().putExtra(EXTRA_REGISTERED_USERNAME, username)
        setResult(Activity.RESULT_OK, result)
        finish()
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }

    companion object {
        const val EXTRA_REGISTERED_USERNAME = "registered_username"
    }
}
