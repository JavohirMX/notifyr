package com.javohirmx.notifyr.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.javohirmx.notifyr.data.repository.NotificationRepository
import com.javohirmx.notifyr.data.repository.NotificationRetentionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Deletes notification history older than the configured retention window.
 *
 * Runs daily so the database does not grow without bound for users who never open
 * Settings. A retention of 0 disables cleanup.
 */
@HiltWorker
class NotificationCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notificationRepository: NotificationRepository,
    private val retentionRepository: NotificationRetentionRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val retentionDays = retentionRepository.currentRetentionDays()
            if (retentionDays <= 0) {
                return Result.success()
            }

            val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays.toLong())
            val deleted = notificationRepository.deleteOldNotifications(cutoff)
            Result.success(
                androidx.work.workDataOf(KEY_DELETED_COUNT to deleted)
            )
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "notification_retention_cleanup"
        const val KEY_DELETED_COUNT = "deleted_count"
    }
}
