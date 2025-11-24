package com.javohirmx.notifyr

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.javohirmx.notifyr.domain.digest.SmartDigestScheduler
import com.javohirmx.notifyr.service.ScreenTimeCollectorService
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NotifyrApplication : Application(), Configuration.Provider {
    
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    
    @Inject
    lateinit var smartDigestScheduler: SmartDigestScheduler
    
    @Inject
    lateinit var screenTimeCollectorService: ScreenTimeCollectorService
    
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    
    override fun onCreate() {
        super.onCreate()
        initializeDigestScheduler()
        initializeScreenTimeCollector()
    }
    
    private fun initializeDigestScheduler() {
        // Initialize smart digest scheduler (context-aware)
        smartDigestScheduler.initialize()
    }
    
    private fun initializeScreenTimeCollector() {
        // Start periodic screen time collection
        screenTimeCollectorService.startPeriodicCollection()
    }
    
    override fun onTerminate() {
        super.onTerminate()
        smartDigestScheduler.shutdown()
    }
}
