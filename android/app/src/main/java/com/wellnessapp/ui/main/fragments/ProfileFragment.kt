/**
 * @author Tao Yuchen
 */
package com.wellnessapp.ui.main.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.wellnessapp.R
import com.wellnessapp.databinding.FragmentProfileBinding
import com.wellnessapp.ui.login.LoginActivity
import com.wellnessapp.util.TokenManager

/**
 * Profile tab showing user info, settings access, and logout.
 *
 * @author WellnessApp Team
 */
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindUserInfo()
        setupListeners()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Populate the UI with the logged-in user's info.
     */
    private fun bindUserInfo() {
        val username = TokenManager.getUsername() ?: "User"
        val userId = TokenManager.getUserId()

        // Avatar initial
        binding.tvAvatarInitial.text = username.first().uppercase()

        // Username header
        binding.tvProfileUsername.text = username

        // User ID
        binding.tvProfileUserId.text = "ID: $userId"

        // Info card details
        binding.tvInfoUsername.text = getString(R.string.username) + ": $username"
        binding.tvInfoUserId.text = "User ID: $userId"
    }

    /**
     * Wire button actions.
     */
    private fun setupListeners() {
        // Open Settings (from top-bar) via the parent activity
        binding.btnProfileSettings.setOnClickListener {
            val activity = requireActivity() as? com.wellnessapp.ui.main.MainActivity
            activity?.showSettings()
        }

        // Logout
        binding.btnProfileLogout.setOnClickListener {
            TokenManager.logout()
            val intent = Intent(requireContext(), LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            requireActivity().finish()
        }
    }
}
