/**
 * @author Jia Qianrui
 */
package com.wellnessapp.util

import android.content.Context
import android.content.SharedPreferences
import com.wellnessapp.WellnessApplication
import com.wellnessapp.data.api.RetrofitClient

/**
 * Manages JWT token persistence and session state using SharedPreferences.
 *
 * @author WellnessApp Team
 */
object TokenManager {

    private const val PREFS_NAME = "wellness_prefs"
    private const val KEY_TOKEN = "jwt_token"
    private const val KEY_USERNAME = "username"
    private const val KEY_USER_ID = "user_id"

    private val prefs: SharedPreferences by lazy {
        WellnessApplication.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveLoginSession(token: String, username: String, userId: Long) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USERNAME, username)
            .putLong(KEY_USER_ID, userId)
            .apply()
        RetrofitClient.setToken(token)
    }

    fun getToken(): String? {
        return prefs.getString(KEY_TOKEN, null)
    }

    fun getUsername(): String? {
        return prefs.getString(KEY_USERNAME, null)
    }

    fun getUserId(): Long {
        return prefs.getLong(KEY_USER_ID, -1)
    }

    fun isLoggedIn(): Boolean {
        return getToken() != null
    }

    fun logout() {
        prefs.edit().clear().apply()
        RetrofitClient.setToken(null)
    }

    fun restoreToken() {
        val token = getToken()
        if (token != null) {
            RetrofitClient.setToken(token)
        }
    }
}
