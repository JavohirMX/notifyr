package com.javohirmx.notifyr

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.javohirmx.notifyr.domain.digest.SmartDigestScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NotifyrApplication : Application(), Configuration.Provider {
    
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    
    @Inject
    lateinit var smartDigestScheduler: SmartDigestScheduler
    
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    
    override fun onCreate() {
        super.onCreate()
        initializeDigestScheduler()
    }
    
    private fun initializeDigestScheduler() {
        // Initialize smart digest scheduler (context-aware)
        smartDigestScheduler.initialize()
    }
    
    override fun onTerminate() {
        super.onTerminate()
        smartDigestScheduler.shutdown()
    }
}
