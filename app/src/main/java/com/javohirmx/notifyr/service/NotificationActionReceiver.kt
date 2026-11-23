package com.javohirmx.notifyr.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.javohirmx.notifyr.data.repository.NotificationRepository
import com.javohirmx.notifyr.domain.model.NotificationImportance
import com.javohirmx.notifyr.widget.WidgetUpdateHelper
import dagger.hilt.EntryPoints
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
        val action = intent.action ?: return

        val notificationId = when (action) {
            NotificationManager.ACTION_MARK_READ,
            NotificationManager.ACTION_DISMISS -> {
                val id = intent.getLongExtra(NotificationManager.EXTRA_NOTIFICATION_ID, -1L)
                if (id == -1L) {
                    Log.w(TAG, "Invalid notification ID received")
                    return
                }
                // Cancel the notification immediately for better UX
                NotificationManagerCompat.from(context).cancel(NotificationIds.urgent(id))
                id
            }

            NotificationManager.ACTION_MARK_DIGEST_READ -> {
                NotificationManagerCompat.from(context).cancel(NotificationIds.DIGEST)
                -1L
            }

            else -> {
                Log.w(TAG, "Unknown action: $action")
                return
            }
        }

        // Database writes are suspend calls; keep the receiver alive with goAsync instead
        // of blocking the main thread.
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repository = EntryPoints.get(
                    appContext,
                    NotificationRepoEntryPoint::class.java
                ).notificationRepository()

                when (action) {
                    NotificationManager.ACTION_MARK_READ -> {
                        repository.markAsRead(notificationId, true)
                        WidgetUpdateHelper.updateNotificationWidgets(appContext)
                        Log.d(TAG, "Marked notification $notificationId as read")
                    }

                    NotificationManager.ACTION_MARK_DIGEST_READ -> {
                        repository.markAllAsReadByImportance(NotificationImportance.NORMAL)
                        WidgetUpdateHelper.updateNotificationWidgets(appContext)
                        Log.d(TAG, "Digest marked as read")
                    }

                    NotificationManager.ACTION_DISMISS -> {
                        Log.d(TAG, "Dismissed notification $notificationId")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Action $action failed: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
