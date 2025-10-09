package com.javohirmx.notifyr.service

import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.javohirmx.notifyr.data.repository.NotificationRepository
import com.javohirmx.notifyr.domain.digest.SmartDigestScheduler
import com.javohirmx.notifyr.domain.focus.FocusModeManager
import com.javohirmx.notifyr.domain.model.NotificationData
import com.javohirmx.notifyr.domain.model.NotificationImportance
import com.javohirmx.notifyr.domain.model.shouldShowImmediately
import com.javohirmx.notifyr.domain.rules.EnhancedNotificationRulesEngine
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
    lateinit var enhancedRulesEngine: EnhancedNotificationRulesEngine
    
    @Inject
    lateinit var customNotificationManager: NotificationManager
    
    @Inject
    lateinit var focusModeManager: FocusModeManager
    
    @Inject
    lateinit var digestScheduler: SmartDigestScheduler
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    companion object {
        private const val TAG = "NotificationListener"
    }
    
    override fun onCreate() {
        super.onCreate()
        // Initialize digest scheduler
        digestScheduler.initialize()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        digestScheduler.shutdown()
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
        val notificationKey = sbn.key
        
        // Skip our own notifications to avoid loops
        if (packageName == this.packageName) {
            return
        }

        // Filter out system-like notifications (Android System, System UI, core services)
        if (isSystemLikePackage(packageName)) {
            Log.d(TAG, "Filtered system package: $packageName")
            return
        }
        
        // Don't suppress ongoing notifications (music players, calls, etc.) - let them through
        val isOngoing = (notification.flags and android.app.Notification.FLAG_ONGOING_EVENT) != 0
        val category = notification.category
        val isCall = category == android.app.Notification.CATEGORY_CALL || category == "call"
        val isMedia = category == android.app.Notification.CATEGORY_TRANSPORT || category == "transport"
        val shouldPreserveOriginal = isOngoing || isCall || isMedia
        
        // Extract notification data
        val title = notification.extras.getCharSequence("android.title")?.toString() ?: ""
        val text = notification.extras.getCharSequence("android.text")?.toString() ?: ""
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
        
        // Apply OLD rules engine for backward compatibility (sets importance)
        val classifiedNotification = rulesEngine.classifyNotification(notificationData)
        
        // Apply ENHANCED rules engine for smart tags
        val enhancedNotification = enhancedRulesEngine.classifyNotificationWithTags(classifiedNotification)
        
        // Check focus mode - should we show this notification?
        val currentFocusMode = focusModeManager.getCurrentMode()
        val allowedByFocusMode = focusModeManager.shouldShowNotification(enhancedNotification, currentFocusMode)
        
        // Deduplication window
        val dedupWindowMs = when {
            isCall -> 15_000L
            isOngoing || isMedia -> 60_000L
            else -> 3_000L
        }
        
        // Store with deduplication
        notificationRepository.upsertWithDedup(enhancedNotification, dedupWindowMs)
        
        // NOTIFICATION SUPPRESSION LOGIC
        if (!shouldPreserveOriginal) {
            // Cancel original notification based on importance and focus mode
            when {
                // URGENT: Cancel original, show our enhanced version
                enhancedNotification.importance == NotificationImportance.URGENT && allowedByFocusMode -> {
                    cancelNotification(notificationKey)
                    handleUrgentNotification(enhancedNotification)
                    Log.d(TAG, "Suppressed original, showing urgent: $appName - $title")
                }
                
                // NORMAL: Cancel original, add to digest
                enhancedNotification.importance == NotificationImportance.NORMAL -> {
                    cancelNotification(notificationKey)
                    // Will be shown in digest
                    Log.d(TAG, "Suppressed normal notification: $appName - $title")
                    
                    // Trigger digest check
                    digestScheduler.checkAndShowDigest()
                }
                
                // IGNORE: Cancel original, silently archive
                enhancedNotification.importance == NotificationImportance.IGNORE -> {
                    cancelNotification(notificationKey)
                    Log.d(TAG, "Suppressed ignored notification: $appName - $title")
                }
                
                // Blocked by focus mode: Cancel and archive
                !allowedByFocusMode -> {
                    cancelNotification(notificationKey)
                    Log.d(TAG, "Suppressed by focus mode ($currentFocusMode): $appName - $title")
                }
            }
        } else {
            // Preserve ongoing notifications but still log them
            Log.d(TAG, "Preserved ongoing notification: $appName - $title")
        }
        
        Log.d(TAG, "Processed: $appName | ${enhancedNotification.importance} | Tags: ${enhancedNotification.tags.priority}, ${enhancedNotification.tags.contexts}")
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

    private fun isSystemLikePackage(packageName: String): Boolean {
        if (packageName == "android") return true
        if (packageName.startsWith("com.android.systemui")) return true
        if (packageName.startsWith("com.google.android.gms")) return true
        if (packageName.startsWith("com.google.android")) return true
        if (packageName.startsWith("com.samsung.android")) return true
        if (packageName.startsWith("com.huawei.android")) return true
        if (packageName.startsWith("com.miui.system")) return true
        // Heuristic: system apps without launch intent
        return try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 ||
                (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            val hasLauncher = pm.getLaunchIntentForPackage(packageName) != null
            isSystem && !hasLauncher
        } catch (e: Exception) {
            // If we can't resolve info, be conservative and filter out
            true
        }
    }
}
