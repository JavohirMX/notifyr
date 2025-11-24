package com.javohirmx.notifyr.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.javohirmx.notifyr.domain.usecase.CollectScreenTimeUseCase
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
            val success = collectScreenTimeUseCase()
            if (success) {
                Result.success()
            } else {
                // Permission not granted, but don't fail - just retry later
                Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

