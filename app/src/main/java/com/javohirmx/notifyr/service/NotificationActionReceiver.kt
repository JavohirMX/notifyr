package com.javohirmx.notifyr.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat

class NotificationActionReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "NotificationActionReceiver"
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
                
                // TODO: Mark as read in database - this will be handled by a service call
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
