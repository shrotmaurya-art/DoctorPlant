package com.plantcure.ai.worker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.plantcure.ai.PlantCureApp
import com.plantcure.ai.R
import com.plantcure.ai.data.local.dao.TreatmentScheduleDao
import com.plantcure.ai.ui.MainActivity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import androidx.hilt.work.HiltWorker
import kotlinx.coroutines.flow.firstOrNull

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dao: TreatmentScheduleDao
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        val endOfDay = now + (24L * 60 * 60 * 1000)

        // Check if there is an upcoming treatment due today
        val upcoming = dao.getUpcomingTreatments(now, endOfDay).firstOrNull()?.firstOrNull()

        if (upcoming != null) {
            showNotification(
                "Treatment Reminder: ${upcoming.cropName}",
                upcoming.actionDescription
            )
        }

        return Result.success()
    }

    private fun showNotification(title: String, message: String) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, PlantCureApp.CHANNEL_TREATMENT_REMINDER)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    applicationContext,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                NotificationManagerCompat.from(applicationContext).notify(1001, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
