package com.javohirmx.notifyr.service

import android.content.Context
import androidx.work.*
import com.javohirmx.notifyr.utils.PermissionUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenTimeCollectorService @Inject constructor(
    private val workManager: WorkManager,
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val WORK_NAME = "screen_time_collection"
        private const val COLLECTION_INTERVAL_HOURS = 1L
    }
    
    /**
     * Start periodic collection of screen time data
     */
    fun startPeriodicCollection() {
        if (!PermissionUtils.isUsageStatsPermissionGranted(context)) {
            return
        }
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(false)
            .setRequiresCharging(false)
            .build()
        
        val workRequest = PeriodicWorkRequestBuilder<ScreenTimeCollectionWorker>(
            COLLECTION_INTERVAL_HOURS,
            TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setInitialDelay(COLLECTION_INTERVAL_HOURS, TimeUnit.HOURS)
            .addTag(WORK_NAME)
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
    
    /**
     * Stop periodic collection
     */
    fun stopPeriodicCollection() {
        workManager.cancelUniqueWork(WORK_NAME)
    }
    
    /**
     * Trigger immediate collection (one-time work)
     */
    fun triggerImmediateCollection() {
        if (!PermissionUtils.isUsageStatsPermissionGranted(context)) {
            return
        }
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(false)
            .setRequiresCharging(false)
            .build()
        
        val workRequest = OneTimeWorkRequestBuilder<ScreenTimeCollectionWorker>()
            .setConstraints(constraints)
            .addTag(WORK_NAME)
            .build()
        
        workManager.enqueue(workRequest)
    }
}

