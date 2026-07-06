package com.wellnessapp.ui.login

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.wellnessapp.data.api.RetrofitClient
import com.wellnessapp.data.model.LoginRequest
import com.wellnessapp.databinding.ActivityLoginBinding
import com.wellnessapp.ui.main.MainActivity
import com.wellnessapp.util.TokenManager
import kotlinx.coroutines.launch

/**
 * Login screen handles user login, navigation to register,
 * and redirects to main screen on success.
 *
 * @author WellnessApp Team
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "LoginActivity onCreate")
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.d(TAG, "LoginActivity setContentView done")

        TokenManager.restoreToken()
        if (TokenManager.isLoggedIn()) {
            navigateToMain()
            return
        }

        binding.btnLogin.setOnClickListener { performLogin() }
        binding.tvRegisterLink.setOnClickListener { navigateToRegister() }
    }

    private fun performLogin() {
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (username.isEmpty()) {
            binding.usernameLayout.error = "Username is required"
            return
        }
        if (password.isEmpty()) {
            binding.passwordLayout.error = "Password is required"
            return
        }
        binding.usernameLayout.error = null
        binding.passwordLayout.error = null

        showLoading(true)
        binding.tvError.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.login(
                    LoginRequest(username, password)
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val auth = response.body()!!.data!!
                    TokenManager.saveLoginSession(
                        token = auth.token,
                        username = auth.username,
                        userId = auth.userId
                    )
                    navigateToMain()
                } else {
                    val errorMsg = response.body()?.message
                        ?: "Login failed. Please check your credentials."
                    showError(errorMsg)
                }
            } catch (e: Exception) {
                showError("Connection error: ${e.localizedMessage}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun navigateToRegister() {
        startActivity(Intent(this, RegisterActivity::class.java))
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun showLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !loading
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "LoginActivity"
    }
}
