package com.javohirmx.notifyr.domain.digest

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import com.javohirmx.notifyr.data.repository.NotificationRepository
import com.javohirmx.notifyr.domain.focus.FocusModeManager
import com.javohirmx.notifyr.domain.model.*
import com.javohirmx.notifyr.service.NotificationManager as CustomNotificationManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmartDigestScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationRepository: NotificationRepository,
    private val digestGenerator: DigestGenerator,
    private val focusModeManager: FocusModeManager,
    private val customNotificationManager: CustomNotificationManager
) {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private var lastDigestTime = 0L
    private var lastUnlockTime = 0L
    private var phoneWasLocked = true
    
    private val _digestSettings = MutableStateFlow(DigestSettings())
    val digestSettings: StateFlow<DigestSettings> = _digestSettings.asStateFlow()
    
    private val _currentDigest = MutableStateFlow<EnhancedDigest?>(null)
    val currentDigest: StateFlow<EnhancedDigest?> = _currentDigest.asStateFlow()
    
    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_USER_PRESENT -> {
                    onPhoneUnlocked()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    phoneWasLocked = true
                }
            }
        }
    }
    
    fun initialize() {
        // Register unlock listener
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        context.registerReceiver(unlockReceiver, filter)
    }
    
    fun shutdown() {
        try {
            context.unregisterReceiver(unlockReceiver)
        } catch (e: Exception) {
            // Already unregistered
        }
    }
    
    /**
     * Called when phone is unlocked
     */
    fun onPhoneUnlocked() {
        val now = System.currentTimeMillis()
        val timeSinceUnlock = now - lastUnlockTime
        
        // Only trigger if phone was actually locked (not just screen turned on)
        if (phoneWasLocked && timeSinceUnlock > TimeUnit.MINUTES.toMillis(5)) {
            lastUnlockTime = now
            phoneWasLocked = false
            
            scope.launch {
                checkAndShowDigest()
            }
        }
    }
    
    /**
     * Check if digest should be shown and show it
     */
    suspend fun checkAndShowDigest(): Boolean {
        val settings = _digestSettings.value
        
        // Skip if mode is ON_DEMAND
        if (settings.mode == DigestMode.ON_DEMAND) {
            return false
        }
        
        // Skip if in quiet hours
        if (focusModeManager.isInQuietHours()) {
            return false
        }
        
        val now = System.currentTimeMillis()
        val timeSinceLastDigest = now - lastDigestTime
        
        // Get pending notifications
        val pendingCount = getUnreadNormalCount()
        
        // Check if we should show digest based on mode
        val shouldShow = when (settings.mode) {
            DigestMode.CONTEXT_AWARE -> {
                // Show on unlock after delay, with minimum notifications
                val unlockDelay = TimeUnit.MINUTES.toMillis(settings.unlockDelayMinutes.toLong())
                val timeSinceUnlock = now - lastUnlockTime
                
                timeSinceUnlock < TimeUnit.MINUTES.toMillis(2) && // Just unlocked
                phoneWasLocked &&
                pendingCount >= settings.minNotificationThreshold &&
                timeSinceLastDigest > TimeUnit.HOURS.toMillis(1) // At least 1 hour since last
            }
            
            DigestMode.HOURLY -> {
                // Show every hour if there are notifications
                timeSinceLastDigest > TimeUnit.HOURS.toMillis(1) &&
                pendingCount >= settings.minNotificationThreshold
            }
            
            DigestMode.WORK_BREAKS -> {
                // Show at break times: 10 AM, 12 PM, 3 PM, 6 PM
                isBreakTime() &&
                timeSinceLastDigest > TimeUnit.MINUTES.toMillis(30) &&
                pendingCount > 0
            }
            
            DigestMode.TIME_BASED -> {
                // Show at custom times
                isCustomDigestTime(settings.customTimes) &&
                timeSinceLastDigest > TimeUnit.MINUTES.toMillis(30) &&
                pendingCount > 0
            }
            
            DigestMode.ON_DEMAND -> false
        }
        
        if (shouldShow) {
            showDigest()
            return true
        }
        
        return false
    }
    
    /**
     * Manually trigger digest display
     */
    suspend fun showDigest() {
        // Get notifications from last 4 hours
        val fourHoursAgo = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(4)
        val notifications = notificationRepository
            .getNotificationsByDateRange(fourHoursAgo, System.currentTimeMillis())
            .first()
            .filter { !it.isRead && it.importance == NotificationImportance.NORMAL }
        
        if (notifications.isEmpty()) {
            return
        }
        
        // Generate digest
        val digest = digestGenerator.generateDigest(notifications, timeRangeMinutes = 240)
        _currentDigest.value = digest
        
        lastDigestTime = System.currentTimeMillis()
        
        // Show enhanced digest notification
        customNotificationManager.showEnhancedDigestNotification(digest)
    }
    
    private suspend fun getUnreadNormalCount(): Int {
        val fourHoursAgo = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(4)
        return notificationRepository
            .getNotificationsByDateRange(fourHoursAgo, System.currentTimeMillis())
            .first()
            .count { !it.isRead && it.importance == NotificationImportance.NORMAL }
    }
    
    private fun isBreakTime(): Boolean {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        
        // Check if within 15 minutes of break times
        val breakHours = listOf(10, 12, 15, 18) // 10 AM, 12 PM, 3 PM, 6 PM
        
        return breakHours.any { breakHour ->
            hour == breakHour && minute < 15
        }
    }
    
    private fun isCustomDigestTime(customTimes: List<TimeOfDay>): Boolean {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        
        return customTimes.any { time ->
            hour == time.hour && minute >= time.minute && minute < time.minute + 15
        }
    }
    
    fun updateDigestSettings(settings: DigestSettings) {
        _digestSettings.value = settings
    }
}

