package com.wellnessapp.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.wellnessapp.data.api.RetrofitClient
import com.wellnessapp.data.model.RegisterRequest
import com.wellnessapp.databinding.ActivityRegisterBinding
import com.wellnessapp.ui.health.HealthRecordActivity
import com.wellnessapp.util.TokenManager
import kotlinx.coroutines.launch

/**
 * Register screen — creates a new account and navigates to main on success.
 *
 * @author WellnessApp Team
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
                    val auth = response.body()!!.data!!
                    TokenManager.saveLoginSession(
                        token = auth.token,
                        username = auth.username,
                        userId = auth.userId
                    )
                    Toast.makeText(
                        this@RegisterActivity,
                        "Account created!",
                        Toast.LENGTH_SHORT
                    ).show()
                    navigateToMain()
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

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }

    private fun navigateToMain() {
        val intent = Intent(this, HealthRecordActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
