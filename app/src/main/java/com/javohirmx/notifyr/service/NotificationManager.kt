package com.javohirmx.notifyr.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.javohirmx.notifyr.MainActivity
import com.javohirmx.notifyr.R
import com.javohirmx.notifyr.domain.digest.NaturalLanguageDigestGenerator
import com.javohirmx.notifyr.domain.model.EnhancedDigest
import com.javohirmx.notifyr.domain.model.NotificationData
import com.javohirmx.notifyr.domain.model.NotificationImportance
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val nlDigestGenerator: NaturalLanguageDigestGenerator
) {
    
    private val notificationManager = NotificationManagerCompat.from(context)
    
    companion object {
        private const val TAG = "NotificationManager"
        
        const val URGENT_CHANNEL_ID = "urgent_notifications"
        const val NORMAL_CHANNEL_ID = "normal_notifications"
        const val DIGEST_CHANNEL_ID = "digest_notifications"
        
        const val URGENT_NOTIFICATION_ID = 1001
        const val DIGEST_NOTIFICATION_ID = 1002
        
        const val ACTION_MARK_READ = "com.javohirmx.notifyr.MARK_READ"
        const val ACTION_DISMISS = "com.javohirmx.notifyr.DISMISS"
        const val ACTION_OPEN_APP = "com.javohirmx.notifyr.OPEN_APP"
        
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
    
    init {
        createNotificationChannels()
    }
    
    /**
     * Check if we have permission to post notifications
     */
    private fun canPostNotifications(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Pre-Android 13, notifications are enabled by default
            notificationManager.areNotificationsEnabled()
        }
    }
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val urgentChannel = NotificationChannel(
                URGENT_CHANNEL_ID,
                "Urgent Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical notifications that require immediate attention"
                enableLights(true)
                lightColor = android.graphics.Color.RED
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250, 250, 250)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            
            val normalChannel = NotificationChannel(
                NORMAL_CHANNEL_ID,
                "Normal Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Regular notifications from filtered apps"
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }
            
            val digestChannel = NotificationChannel(
                DIGEST_CHANNEL_ID,
                "Daily Digest",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Summary of normal notifications"
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }
            
            val systemNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            systemNotificationManager.createNotificationChannel(urgentChannel)
            systemNotificationManager.createNotificationChannel(normalChannel)
            systemNotificationManager.createNotificationChannel(digestChannel)
        }
    }
    
    fun showUrgentNotification(notification: NotificationData) {
        if (!canPostNotifications()) {
            Log.w(TAG, "Cannot post notifications - permission not granted")
            return
        }
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 
            0, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Mark as read action
        val markReadIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_MARK_READ
            putExtra(EXTRA_NOTIFICATION_ID, notification.id)
        }
        val markReadPendingIntent = PendingIntent.getBroadcast(
            context,
            notification.id.toInt(),
            markReadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Dismiss action
        val dismissIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_DISMISS
            putExtra(EXTRA_NOTIFICATION_ID, notification.id)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            notification.id.toInt() + 10000,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notificationBuilder = NotificationCompat.Builder(context, URGENT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_urgent)
            .setContentTitle("🚨 ${notification.appName}")
            .setContentText(notification.title.ifBlank { notification.text })
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(buildString {
                        if (notification.title.isNotBlank()) {
                            append(notification.title)
                            if (notification.text.isNotBlank()) {
                                append("\n\n")
                                append(notification.text)
                            }
                        } else {
                            append(notification.text)
                        }
                    })
                    .setBigContentTitle("🚨 URGENT: ${notification.appName}")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_check,
                "Mark Read",
                markReadPendingIntent
            )
            .addAction(
                R.drawable.ic_close,
                "Dismiss",
                dismissPendingIntent
            )
            .setColor(context.getColor(android.R.color.holo_red_dark))
            .setColorized(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
        
        // Show heads-up notification for urgent items
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            notificationBuilder.setFullScreenIntent(pendingIntent, false)
        }
        
        try {
            notificationManager.notify(
                URGENT_NOTIFICATION_ID + notification.id.toInt(),
                notificationBuilder.build()
            )
        } catch (e: SecurityException) {
            // Handle case where notification permission is not granted
            android.util.Log.w("NotificationManager", "Failed to show urgent notification", e)
        }
    }
    
    fun showDigestNotification(normalNotifications: List<NotificationData>) {
        if (normalNotifications.isEmpty()) return
        
        if (!canPostNotifications()) {
            Log.w(TAG, "Cannot post digest notification - permission not granted")
            return
        }
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("open_tab", "history")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle("📋 Daily Digest (${normalNotifications.size} notifications)")
        
        normalNotifications.take(5).forEach { notification ->
            inboxStyle.addLine("${notification.appName}: ${notification.title.ifBlank { notification.text }}")
        }
        
        if (normalNotifications.size > 5) {
            inboxStyle.addLine("... and ${normalNotifications.size - 5} more")
        }
        
        val notificationBuilder = NotificationCompat.Builder(context, DIGEST_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_normal)
            .setContentTitle("Daily Digest")
            .setContentText("${normalNotifications.size} normal notifications")
            .setStyle(inboxStyle)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setNumber(normalNotifications.size)
            .setOnlyAlertOnce(true)
        
        try {
            notificationManager.notify(DIGEST_NOTIFICATION_ID, notificationBuilder.build())
        } catch (e: SecurityException) {
            android.util.Log.w("NotificationManager", "Failed to show digest notification", e)
        }
    }
    
    /**
     * Show enhanced digest notification with smart grouping and summary
     */
    fun showEnhancedDigestNotification(digest: EnhancedDigest) {
        if (digest.totalCount == 0) return
        
        if (!canPostNotifications()) {
            Log.w(TAG, "Cannot post enhanced digest notification - permission not granted")
            return
        }
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("open_tab", "digest")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Generate natural language summary
        val nlSummary = nlDigestGenerator.generateNaturalLanguageSummary(digest)
        val shortSummary = nlDigestGenerator.generateShortSummary(digest)
        
        val bigTextStyle = NotificationCompat.BigTextStyle()
            .setBigContentTitle("📬 Notification Digest")
            .bigText(nlSummary)
            .setSummaryText(shortSummary)
        
        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle("📬 ${digest.timeRange}")
            .setSummaryText(shortSummary)
        
        // Show priority items first
        if (digest.needsAttention.isNotEmpty()) {
            inboxStyle.addLine("⭐ ${digest.needsAttention.size} need attention")
            digest.needsAttention.take(2).forEach {
                inboxStyle.addLine("  ${it.appName}: ${it.title}")
            }
        }
        
        // Show conversations
        if (digest.conversations.isNotEmpty()) {
            digest.conversations.take(3).forEach { conv ->
                val preview = if (conv.messageCount > 1) {
                    "${conv.sender} sent ${conv.messageCount} messages"
                } else {
                    "${conv.sender}: ${conv.latestMessage}"
                }
                inboxStyle.addLine("💬 $preview")
            }
        }
        
        // Show other apps
        if (digest.appGroups.isNotEmpty()) {
            digest.appGroups.take(2).forEach { group ->
                if (group.appName !in digest.conversations.map { it.appName }) {
                    inboxStyle.addLine("📱 ${group.appName} (${group.notificationCount})")
                }
            }
        }
        
        val notificationBuilder = NotificationCompat.Builder(context, DIGEST_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_normal)
            .setContentTitle("Notification Digest")
            .setContentText(shortSummary)
            .setStyle(bigTextStyle)  // Use natural language style
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setNumber(digest.totalCount)
            .setOnlyAlertOnce(true)
        
        try {
            notificationManager.notify(DIGEST_NOTIFICATION_ID, notificationBuilder.build())
        } catch (e: SecurityException) {
            android.util.Log.w("NotificationManager", "Failed to show enhanced digest notification", e)
        }
    }
    
    fun cancelUrgentNotification(notificationId: Long) {
        notificationManager.cancel(URGENT_NOTIFICATION_ID + notificationId.toInt())
    }
    
    fun cancelDigestNotification() {
        notificationManager.cancel(DIGEST_NOTIFICATION_ID)
    }
    
    fun cancelAllNotifications() {
        notificationManager.cancelAll()
    }
}
