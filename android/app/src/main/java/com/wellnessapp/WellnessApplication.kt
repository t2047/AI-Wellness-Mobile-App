package com.wellnessapp

import android.app.Application
import com.wellnessapp.util.TokenManager

/**
 * Application class for the Wellness App.
 *
 * @author WellnessApp Team
 * @author ZHAO LEI
 */
class WellnessApplication : Application() {

    companion object {
        lateinit var instance: WellnessApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // ZHAO LEI: every new app process starts logged out. A session is
        // established only after the user explicitly submits the login form.
        TokenManager.logout()
    }
}
