package com.javohirmx.notifyr.service

import android.content.Context
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DigestScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private val workManager = WorkManager.getInstance(context)
    
    companion object {
        const val DIGEST_WORK_NAME = "digest_notification_work"
        private const val DEFAULT_DIGEST_INTERVAL_HOURS = 6L // Every 6 hours
    }
    
    fun scheduleDigestNotifications(intervalHours: Long = DEFAULT_DIGEST_INTERVAL_HOURS) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(true)
            .build()
        
        val digestWorkRequest = PeriodicWorkRequestBuilder<DigestNotificationWorker>(
            intervalHours, TimeUnit.HOURS,
            15, TimeUnit.MINUTES // Flex interval
        )
            .setConstraints(constraints)
            .setInitialDelay(intervalHours, TimeUnit.HOURS)
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            DIGEST_WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            digestWorkRequest
        )
    }
    
    fun cancelDigestNotifications() {
        workManager.cancelUniqueWork(DIGEST_WORK_NAME)
    }
    
    fun isDigestScheduled(): Boolean {
        val workInfos = workManager.getWorkInfosForUniqueWork(DIGEST_WORK_NAME)
        return try {
            workInfos.get().any { workInfo ->
                workInfo.state == WorkInfo.State.ENQUEUED || workInfo.state == WorkInfo.State.RUNNING
            }
        } catch (e: Exception) {
            false
        }
    }
    
    fun scheduleOneTimeDigest() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()
        
        val oneTimeWorkRequest = OneTimeWorkRequestBuilder<DigestNotificationWorker>()
            .setConstraints(constraints)
            .setInitialDelay(5, TimeUnit.MINUTES) // Small delay to avoid immediate execution
            .build()
        
        workManager.enqueue(oneTimeWorkRequest)
    }
}
