package com.javohirmx.notifyr.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.javohirmx.notifyr.data.repository.NotificationRepository
import com.javohirmx.notifyr.domain.model.NotificationImportance
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class DigestNotificationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val notificationRepository: NotificationRepository,
    private val notificationManager: NotificationManager
) : CoroutineWorker(context, workerParams) {
    
    companion object {
        const val WORK_NAME = "digest_notification_work"
    }
    
    override suspend fun doWork(): Result {
        return try {
            // Get unread normal notifications from the last 24 hours
            val oneDayAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
            val normalNotifications = notificationRepository
                .getNotificationsByDateRange(oneDayAgo, System.currentTimeMillis())
                .first()
                .filter { 
                    it.importance == NotificationImportance.NORMAL && !it.isRead 
                }
            
            // Show digest notification if there are unread normal notifications
            if (normalNotifications.isNotEmpty()) {
                notificationManager.showDigestNotification(normalNotifications)
            }
            
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("DigestNotificationWorker", "Failed to create digest notification", e)
            Result.failure()
        }
    }
}
