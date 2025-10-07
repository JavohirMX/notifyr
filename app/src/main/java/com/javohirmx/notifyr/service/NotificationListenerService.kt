package com.javohirmx.notifyr.service

import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.javohirmx.notifyr.data.repository.NotificationRepository
import com.javohirmx.notifyr.domain.model.NotificationData
import com.javohirmx.notifyr.domain.model.NotificationImportance
import com.javohirmx.notifyr.domain.rules.NotificationRulesEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class NotificationListenerService : NotificationListenerService() {
    
    @Inject
    lateinit var notificationRepository: NotificationRepository
    
    @Inject
    lateinit var rulesEngine: NotificationRulesEngine
    
    @Inject
    lateinit var customNotificationManager: NotificationManager
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    companion object {
        private const val TAG = "NotificationListener"
    }
    
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        
        sbn?.let { statusBarNotification ->
            serviceScope.launch {
                try {
                    processNotification(statusBarNotification)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing notification", e)
                }
            }
        }
    }
    
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        // Handle notification removal if needed
        Log.d(TAG, "Notification removed: ${sbn?.packageName}")
    }
    
    private suspend fun processNotification(sbn: StatusBarNotification) {
        val notification = sbn.notification
        val packageName = sbn.packageName
        
        // Skip our own notifications to avoid loops
        if (packageName == this.packageName) {
            return
        }
        
        // Extract notification data
        val title = notification.extras.getCharSequence("android.title")?.toString() ?: ""
        val text = notification.extras.getCharSequence("android.text")?.toString() ?: ""
        val category = notification.category
        val timestamp = sbn.postTime
        
        // Get app name
        val appName = try {
            val packageManager = packageManager
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
        
        // Create notification data object
        val notificationData = NotificationData(
            packageName = packageName,
            appName = appName,
            title = title,
            text = text,
            category = category,
            importance = NotificationImportance.NORMAL, // Will be classified by rules engine
            timestamp = timestamp
        )
        
        // Apply rules engine to classify importance
        val classifiedNotification = rulesEngine.classifyNotification(notificationData)
        
        // Store in database
        notificationRepository.insertNotification(classifiedNotification)
        
        // Handle urgent notifications
        if (classifiedNotification.importance == NotificationImportance.URGENT) {
            handleUrgentNotification(classifiedNotification)
        }
        
        Log.d(TAG, "Processed notification: $appName - $title (${classifiedNotification.importance})")
    }
    
    private fun handleUrgentNotification(notification: NotificationData) {
        // Show custom urgent notification with enhanced styling
        customNotificationManager.showUrgentNotification(notification)
        Log.i(TAG, "Urgent notification displayed: ${notification.appName} - ${notification.title}")
    }
    
    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Notification Listener Service connected")
    }
    
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Notification Listener Service disconnected")
    }
}
