package com.wellnessapp.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.wellnessapp.R
import com.wellnessapp.databinding.ActivityMainBinding
import com.wellnessapp.ui.login.LoginActivity
import com.wellnessapp.ui.main.fragments.ChatFragment
import com.wellnessapp.ui.main.fragments.CoachFragment
import com.wellnessapp.ui.main.fragments.DashboardFragment
import com.wellnessapp.ui.main.fragments.KnowledgeFragment
import com.wellnessapp.ui.main.fragments.RecordsFragment
import com.wellnessapp.ui.main.fragments.SettingsFragment
import com.wellnessapp.util.TokenManager

/**
 * Main navigation shell for the wellness app.
 *
 * @author Xuhan Zhang
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var currentTabId = R.id.action_dashboard

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentTabId = savedInstanceState?.getInt(KEY_CURRENT_TAB) ?: R.id.action_dashboard
        setupGlobalHeader()
        setupBottomNavigation()
        setupBackBehavior()
        if (savedInstanceState == null) {
            showTab(R.id.action_dashboard)
        } else {
            selectTab(currentTabId)
        }
    }

    /**
     * Handles global top-bar actions.
     *
     * @author Xuhan Zhang
     */
    private fun setupGlobalHeader() {
        binding.btnLogout.setOnClickListener { logout() }
        binding.btnSettings.setOnClickListener { showTab(R.id.action_settings) }
        updateMainTitle(currentTabId)
    }

    /**
     * Wires bottom navigation tabs to fragments hosted in MainActivity.
     *
     * @author Xuhan Zhang
     */
    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            showTab(item.itemId)
        }
        binding.bottomNavigation.setOnItemReselectedListener {
            // Keep the current tab in place on repeated taps.
        }
    }

    /**
     * Allows child fragments to switch MainActivity tabs without opening activities.
     *
     * @author Xuhan Zhang
     */
    fun selectTab(itemId: Int) {
        if (itemId == R.id.action_settings) {
            showTab(itemId)
        } else {
            binding.bottomNavigation.selectedItemId = itemId
        }
    }

    /**
     * Clears the login session and returns to the login screen.
     *
     * @author Xuhan Zhang
     */
    fun logout() {
        TokenManager.logout()
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
    }

    /**
     * Replaces the current tab content while keeping bottom navigation visible.
     *
     * @author Xuhan Zhang
     */
    private fun showTab(itemId: Int): Boolean {
        updateMainTitle(itemId)
        if (itemId == currentTabId &&
            supportFragmentManager.findFragmentById(R.id.fragmentContainer) != null
        ) {
            return true
        }

        val fragment = createFragment(itemId) ?: return false
        currentTabId = itemId

        // If it's one of the bottom navigation items, ensure it's selected there.
        // If it's settings (now in top bar), we might want to unselect bottom nav.
        if (itemId == R.id.action_settings) {
            uncheckBottomNavigation()
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
        return true
    }

    /**
     * Unchecks all items in the bottom navigation view.
     */
    private fun uncheckBottomNavigation() {
        binding.bottomNavigation.menu.setGroupCheckable(0, true, false)
        for (i in 0 until binding.bottomNavigation.menu.size()) {
            binding.bottomNavigation.menu.getItem(i).isChecked = false
        }
        binding.bottomNavigation.menu.setGroupCheckable(0, true, true)
    }

    /**
     * Updates the global title for the selected tab.
     *
     * @author Xuhan Zhang
     */
    private fun updateMainTitle(itemId: Int) {
        binding.tvMainTitle.text = when (itemId) {
            R.id.action_dashboard -> "Health Dashboard"
            R.id.action_records -> "Health Records"
            R.id.action_coach -> "AI Coach"
            R.id.action_chat -> "Chat"
            R.id.action_knowledge -> "Knowledge"
            R.id.action_settings -> "Settings"
            else -> "Health Dashboard"
        }
    }

    /**
     * Creates the fragment mapped to a bottom navigation item.
     *
     * @author Xuhan Zhang
     */
    private fun createFragment(itemId: Int): Fragment? {
        return when (itemId) {
            R.id.action_dashboard -> DashboardFragment()
            R.id.action_records -> RecordsFragment()
            R.id.action_coach -> CoachFragment()
            R.id.action_chat -> ChatFragment()
            R.id.action_knowledge -> KnowledgeFragment()
            R.id.action_settings -> SettingsFragment()
            else -> null
        }
    }

    /**
     * Sends users back to Dashboard before exiting the app.
     *
     * @author Xuhan Zhang
     */
    private fun setupBackBehavior() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentTabId != R.id.action_dashboard) {
                    selectTab(R.id.action_dashboard)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_CURRENT_TAB, currentTabId)
    }

    companion object {
        private const val KEY_CURRENT_TAB = "current_tab"
    }
}
