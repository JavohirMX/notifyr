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
import com.javohirmx.notifyr.widget.WidgetUpdateHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class NotificationListenerService : NotificationListenerService() {
    
    @Inject
    lateinit var notificationRepository: NotificationRepository
    
    @Inject
    lateinit var appRulesRepository: com.javohirmx.notifyr.data.repository.AppRulesRepository
    
    @Inject
    lateinit var rulesEngine: NotificationRulesEngine
    
    @Inject
    lateinit var enhancedRulesEngine: EnhancedNotificationRulesEngine
    
    @Inject
    lateinit var hybridClassifier: com.javohirmx.notifyr.domain.ml.HybridNotificationClassifier
    
    @Inject
    lateinit var customNotificationManager: NotificationManager
    
    @Inject
    lateinit var focusModeManager: FocusModeManager
    
    @Inject
    lateinit var digestScheduler: SmartDigestScheduler
    
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)
    
    // For preventing race conditions
    private val processingKeys = mutableSetOf<String>()
    private val processingMutex = Mutex()
    
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
        serviceJob.cancel() // Cancel all coroutines
        
        // Clear processing keys
        processingKeys.clear()
    }
    
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        
        sbn?.let { statusBarNotification ->
            serviceScope.launch {
                val key = statusBarNotification.key
                
                // Prevent race conditions by checking if already processing
                processingMutex.withLock {
                    if (processingKeys.contains(key)) {
                        Log.d(TAG, "Already processing notification: $key")
                        return@launch
                    }
                    processingKeys.add(key)
                }
                
                try {
                    processNotification(statusBarNotification)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing notification", e)
                } finally {
                    // Remove from processing set
                    processingMutex.withLock {
                        processingKeys.remove(key)
                    }
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
        
        // Check for DONT_INTERCEPT rule - let notification through unchanged
        val appRule = appRulesRepository.getAppRule(packageName)
        if (appRule?.ruleType == com.javohirmx.notifyr.domain.model.AppRuleType.DONT_INTERCEPT && appRule.isEnabled) {
            Log.d(TAG, "Don't intercept rule active for: $packageName - letting notification through")
            // Still store it in database for history, but don't modify or cancel original
            val appName = try {
                val packageManager = packageManager
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                packageName
            }
            val title = notification.extras.getCharSequence("android.title")?.toString() ?: ""
            val text = notification.extras.getCharSequence("android.text")?.toString() ?: ""
            val category = notification.category
            val notificationData = NotificationData(
                packageName = packageName,
                appName = appName,
                title = title,
                text = text,
                category = category,
                importance = NotificationImportance.NORMAL, // Don't classify
                timestamp = sbn.postTime
            )
            serviceScope.launch {
                // Use longer deduplication window for email apps even in DONT_INTERCEPT mode
                val isEmailApp = packageName == "com.google.android.apps.gmail" || 
                                packageName == "com.google.android.gm" ||
                                packageName == "com.microsoft.office.outlook" ||
                                packageName == "com.yahoo.mobile.client.android.mail"
                val dedupWindow = if (isEmailApp) 120_000L else 30_000L
                notificationRepository.upsertWithDedup(notificationData, dedupWindow)
            }
            return // Don't process further - let original notification through
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
        
        // Use HYBRID ML+Rules classifier for smart classification
        val enhancedNotification = hybridClassifier.classify(notificationData)
        
        // Check focus mode - should we show this notification?
        val currentFocusMode = focusModeManager.getCurrentMode()
        val allowedByFocusMode = focusModeManager.shouldShowNotification(enhancedNotification, currentFocusMode)
        
        // Deduplication window - increased for normal notifications to catch more duplicates
        // Email apps like Gmail get a longer window as they often send multiple notifications for the same email
        val isEmailApp = packageName == "com.google.android.apps.gmail" || 
                        packageName == "com.google.android.gm" ||
                        packageName == "com.microsoft.office.outlook" ||
                        packageName == "com.yahoo.mobile.client.android.mail"
        
        val dedupWindowMs = when {
            isCall -> 15_000L
            isOngoing || isMedia -> 60_000L
            isEmailApp -> 120_000L  // 2 minutes for email apps to catch Gmail duplicates
            else -> 30_000L  // 30 seconds for other apps
        }
        
        // Store with deduplication
        notificationRepository.upsertWithDedup(enhancedNotification, dedupWindowMs)
        
        // Update notification widgets
        WidgetUpdateHelper.updateNotificationWidgets(this)
        
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
        // List of system packages to explicitly block
        val blockedSystemPackages = setOf(
            "android",
            "com.android.systemui",
            "com.android.providers.calendar",
            "com.android.providers.contacts",
            "com.android.providers.downloads",
            "com.android.providers.media",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.samsung.android.incallui",
            "com.samsung.android.contacts",
            "com.miui.system.miwallpaper",
            "com.huawei.android.internal.app"
        )
        
        // Check if package is in blocked list
        if (blockedSystemPackages.any { packageName.startsWith(it) }) {
            return true
        }
        
        // Allow important Google apps explicitly
        val allowedGoogleApps = setOf(
            "com.google.android.apps.messaging",
            "com.google.android.apps.gmail",
            "com.google.android.gm",
            "com.google.android.calendar",
            "com.google.android.apps.maps",
            "com.google.android.dialer",
            "com.google.android.apps.photos"
        )
        
        if (allowedGoogleApps.contains(packageName)) {
            return false
        }
        
        // Heuristic: system apps without launch intent are likely background services
        return try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 ||
                (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            val hasLauncher = pm.getLaunchIntentForPackage(packageName) != null
            isSystem && !hasLauncher
        } catch (e: Exception) {
            // If we can't resolve info, be conservative and filter out
            Log.w(TAG, "Could not determine system status for $packageName", e)
            true
        }
    }
}
