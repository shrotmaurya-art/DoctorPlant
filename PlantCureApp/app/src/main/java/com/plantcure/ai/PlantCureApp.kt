package com.plantcure.ai

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * PlantCure AI Application class.
 *
 * Responsibilities:
 * - Initialize Hilt DI
 * - Create notification channels
 * - Sign in anonymously to Firebase
 * - Configure WorkManager with Hilt
 */
@HiltAndroidApp
class PlantCureApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()

        createNotificationChannels()
        signInAnonymously()
    }

    /**
     * Create all notification channels upfront (required for Android O+).
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val channels = listOf(
                NotificationChannel(
                    CHANNEL_WEATHER_ALERT,
                    "Weather Risk Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Daily disease risk alerts based on weather conditions" },

                NotificationChannel(
                    CHANNEL_TREATMENT_REMINDER,
                    "Treatment Reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Reminders for scheduled crop treatment applications" },

                NotificationChannel(
                    CHANNEL_PRICE_ALERT,
                    "Market Price Alerts",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Alerts when crop prices cross your threshold" },

                NotificationChannel(
                    CHANNEL_COMMUNITY_ALERT,
                    "Community Disease Alerts",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Alerts about disease outbreaks reported in your area" }
            )

            channels.forEach { manager.createNotificationChannel(it) }
        }
    }

    /**
     * Sign in anonymously to Firebase for community feed features.
     * This creates a stable anonymous user ID without requiring registration.
     */
    private fun signInAnonymously() {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            auth.signInAnonymously()
        }
    }

    companion object {
        const val CHANNEL_WEATHER_ALERT = "weather_alert"
        const val CHANNEL_TREATMENT_REMINDER = "treatment_reminder"
        const val CHANNEL_PRICE_ALERT = "price_alert"
        const val CHANNEL_COMMUNITY_ALERT = "community_alert"
    }
}
