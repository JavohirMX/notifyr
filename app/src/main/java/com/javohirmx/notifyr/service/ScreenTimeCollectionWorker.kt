package com.javohirmx.notifyr.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.javohirmx.notifyr.domain.usecase.CollectScreenTimeUseCase
import com.javohirmx.notifyr.widget.WidgetUpdateHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ScreenTimeCollectionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val collectScreenTimeUseCase: CollectScreenTimeUseCase
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        return try {
            // Check permission first - if revoked, stop periodic work
            val hasPermission = com.javohirmx.notifyr.utils.PermissionUtils.isUsageStatsPermissionGranted(applicationContext)
            if (!hasPermission) {
                // Permission revoked - return success to stop retrying
                return Result.success()
            }
            
            // Collect both hourly aggregates and minute-level sessions
            val hourlySuccess = collectScreenTimeUseCase()
            val sessionSuccess = collectScreenTimeUseCase.collectSessions()
            
            // Update screen time widgets
            WidgetUpdateHelper.updateScreenTimeWidgets(applicationContext)
            
            // Both should succeed for complete data, but partial success is acceptable
            // Log warning if only one succeeds
            if (hourlySuccess && sessionSuccess) {
                Result.success()
            } else if (hourlySuccess || sessionSuccess) {
                // Partial success - log but don't retry immediately
                // The next periodic run will try again
                Result.success()
            } else {
                // Both failed - likely permission issue, retry later
                Result.retry()
            }
        } catch (e: Exception) {
            // Retry with exponential backoff on exceptions
            Result.retry()
        }
    }
}

