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
            val hasPermission = com.javohirmx.notifyr.utils.PermissionUtils.isUsageStatsPermissionGranted(applicationContext)
            if (!hasPermission) {
                // P1 FIX: Log permission revocation
                android.util.Log.w(TAG, "Usage stats permission revoked, stopping collection")
                return Result.success()
            }
            
            android.util.Log.d(TAG, "Starting screen time collection")
            val startTime = System.currentTimeMillis()
            
            // P0 FIX: Use only sessions-based collection (accurate)
            // Removed redundant hourly stats collection which was using maxOf (data loss)
            val success = collectScreenTimeUseCase()
            
            val duration = System.currentTimeMillis() - startTime
            if (success) {
                android.util.Log.d(TAG, "Collection succeeded in ${duration}ms")
                
                // Update widgets
                try {
                    WidgetUpdateHelper.updateScreenTimeWidgets(applicationContext)
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "Failed to update widgets: ${e.message}", e)
                }
                
                Result.success()
            } else {
                android.util.Log.w(TAG, "Collection failed after ${duration}ms (likely permission issue)")
                Result.retry()
            }
        } catch (e: Exception) {
            // P1 FIX: Log detailed error context
            android.util.Log.e(TAG, 
                "Unexpected error during collection: ${e.javaClass.simpleName} - ${e.message}", e)
            Result.retry()
        }
    }
    
    companion object {
        private const val TAG = "ScreenTimeCollectionWorker"
    }
}
