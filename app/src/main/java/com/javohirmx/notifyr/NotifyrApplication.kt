package com.javohirmx.notifyr

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.javohirmx.notifyr.service.DigestNotificationWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class NotifyrApplication : Application(), Configuration.Provider {
    
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    
    override fun onCreate() {
        super.onCreate()
        scheduleDigestNotifications()
    }
    
    private fun scheduleDigestNotifications() {
        val digestWorkRequest = PeriodicWorkRequestBuilder<DigestNotificationWorker>(
            24, TimeUnit.HOURS // Run once daily
        ).build()
        
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            DigestNotificationWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            digestWorkRequest
        )
    }
}
