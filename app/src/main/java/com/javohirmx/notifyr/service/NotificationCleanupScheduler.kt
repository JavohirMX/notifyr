package com.javohirmx.notifyr.service

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the daily retention cleanup. Extracted from the Application so the
 * scheduling policy lives with the worker it drives.
 */
@Singleton
class NotificationCleanupScheduler @Inject constructor(
    private val workManager: WorkManager
) {

    fun schedule() {
        val request = PeriodicWorkRequestBuilder<NotificationCleanupWorker>(1, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            NotificationCleanupWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
