package com.javohirmx.notifyr.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.javohirmx.notifyr.data.repository.NotificationRepository
import dagger.hilt.EntryPoints
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

class NotificationActionReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "NotificationActionReceiver"
    }
    
    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(SingletonComponent::class)
    interface NotificationRepoEntryPoint {
        fun notificationRepository(): NotificationRepository
    }

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getLongExtra(NotificationManager.EXTRA_NOTIFICATION_ID, -1L)
        
        if (notificationId == -1L) {
            Log.w(TAG, "Invalid notification ID received")
            return
        }
        
        when (intent.action) {
            NotificationManager.ACTION_MARK_READ -> {
                // Cancel the notification immediately for better UX
                val notificationManager = NotificationManagerCompat.from(context)
                notificationManager.cancel(NotificationManager.URGENT_NOTIFICATION_ID + notificationId.toInt())
                
                // Mark as read in database synchronously
                try {
                    val appContext = context.applicationContext
                    val entryPoint = EntryPoints.get(appContext, NotificationRepoEntryPoint::class.java)
                    val repo = entryPoint.notificationRepository()
                    kotlinx.coroutines.runBlocking {
                        repo.markAsRead(notificationId, true)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to mark as read: ${e.message}")
                }
                Log.d(TAG, "Marked notification $notificationId as read")
            }
            
            NotificationManager.ACTION_DISMISS -> {
                // Cancel the notification
                val notificationManager = NotificationManagerCompat.from(context)
                notificationManager.cancel(NotificationManager.URGENT_NOTIFICATION_ID + notificationId.toInt())
                
                Log.d(TAG, "Dismissed notification $notificationId")
            }
            
            else -> {
                Log.w(TAG, "Unknown action: ${intent.action}")
            }
        }
    }
}
