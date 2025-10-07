package com.javohirmx.notifyr

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.javohirmx.notifyr.service.DigestScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NotifyrApplication : Application(), Configuration.Provider {
    
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    
    @Inject
    lateinit var digestScheduler: DigestScheduler
    
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    
    override fun onCreate() {
        super.onCreate()
        scheduleDigestNotifications()
    }
    
    private fun scheduleDigestNotifications() {
        // Schedule digest notifications every 6 hours
        digestScheduler.scheduleDigestNotifications(6L)
    }
}
