package com.wellnessapp.ui.login

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
 * @author ZHAO LEI
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val registerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                return@registerForActivityResult
            }
            val username = result.data
                ?.getStringExtra(RegisterActivity.EXTRA_REGISTERED_USERNAME)
                .orEmpty()
            if (username.isNotBlank()) {
                binding.etUsername.setText(username)
                binding.etPassword.requestFocus()
            }
            Toast.makeText(
                this,
                getString(com.wellnessapp.R.string.account_created_sign_in),
                Toast.LENGTH_LONG
            ).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "LoginActivity onCreate")
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.d(TAG, "LoginActivity setContentView done")

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

    /**
     * Opens registration and waits for the new username without authenticating it.
     *
     * @author ZHAO LEI
     */
    private fun navigateToRegister() {
        registerLauncher.launch(Intent(this, RegisterActivity::class.java))
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
