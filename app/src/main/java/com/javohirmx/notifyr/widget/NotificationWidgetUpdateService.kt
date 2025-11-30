package com.javohirmx.notifyr.widget

import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.IBinder
import android.widget.RemoteViews
import androidx.core.app.JobIntentService
import com.javohirmx.notifyr.R
import com.javohirmx.notifyr.domain.model.NotificationImportance
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * Service to update notification widget
 * Fetches filtered notifications from repository and updates widget RemoteViews
 */
@AndroidEntryPoint
class NotificationWidgetUpdateService : Service() {
    
    @Inject
    lateinit var widgetRepository: WidgetRepository
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            serviceScope.launch {
                updateWidget(appWidgetId)
                stopSelf()
            }
        } else {
            stopSelf()
        }
        
        return START_NOT_STICKY
    }
    
    private suspend fun updateWidget(appWidgetId: Int) {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val filter = NotificationWidgetProvider.loadWidgetConfig(this, appWidgetId)
        val notifications = widgetRepository.getFilteredNotifications(filter, limit = 10)
        
        // Determine widget size and update accordingly
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        
        val layoutId = when {
            minWidth < 110 -> R.layout.widget_notification_small
            minWidth < 250 -> R.layout.widget_notification_medium
            else -> R.layout.widget_notification_large
        }
        
        val views = RemoteViews(packageName, layoutId)
        
        // Update widget content based on size
        when (layoutId) {
            R.layout.widget_notification_small -> {
                updateSmallWidget(views, notifications, filter)
            }
            R.layout.widget_notification_medium -> {
                updateMediumWidget(views, notifications, filter)
            }
            R.layout.widget_notification_large -> {
                updateLargeWidget(views, notifications, filter)
            }
        }
        
        // Set refresh button click
        val refreshIntent = Intent(this, NotificationWidgetProvider::class.java).apply {
            action = NotificationWidgetProvider.ACTION_REFRESH
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        val refreshPendingIntent = android.app.PendingIntent.getBroadcast(
            this,
            appWidgetId,
            refreshIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_refresh_button, refreshPendingIntent)
        
        // Set click to open app
        val appIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val appPendingIntent = android.app.PendingIntent.getActivity(
            this,
            appWidgetId,
            appIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_container, appPendingIntent)
        
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
    
    private fun updateSmallWidget(views: RemoteViews, notifications: List<com.javohirmx.notifyr.domain.model.NotificationData>, filter: WidgetRepository.NotificationFilter) {
        views.setTextViewText(R.id.widget_title, getFilterDisplayName(filter))
        views.setTextViewText(R.id.widget_count, notifications.size.toString())
    }
    
    private fun updateMediumWidget(views: RemoteViews, notifications: List<com.javohirmx.notifyr.domain.model.NotificationData>, filter: WidgetRepository.NotificationFilter) {
        views.setTextViewText(R.id.widget_title, getFilterDisplayName(filter))
        
        // Update first 3-5 items
        val displayCount = minOf(notifications.size, 5)
        for (i in 0 until displayCount) {
            val notification = notifications[i]
            val itemId = when (i) {
                0 -> R.id.widget_item_1
                1 -> R.id.widget_item_2
                2 -> R.id.widget_item_3
                3 -> R.id.widget_item_4
                4 -> R.id.widget_item_5
                else -> null
            }
            
            itemId?.let { id ->
                views.setTextViewText(id, formatNotificationText(notification))
                views.setViewVisibility(id, android.view.View.VISIBLE)
            }
        }
        
        // Hide remaining items
        for (i in displayCount until 5) {
            val itemId = when (i) {
                0 -> R.id.widget_item_1
                1 -> R.id.widget_item_2
                2 -> R.id.widget_item_3
                3 -> R.id.widget_item_4
                4 -> R.id.widget_item_5
                else -> null
            }
            itemId?.let { views.setViewVisibility(it, android.view.View.GONE) }
        }
    }
    
    private fun updateLargeWidget(views: RemoteViews, notifications: List<com.javohirmx.notifyr.domain.model.NotificationData>, filter: WidgetRepository.NotificationFilter) {
        views.setTextViewText(R.id.widget_title, getFilterDisplayName(filter))
        
        // Update first 10 items
        val displayCount = minOf(notifications.size, 10)
        for (i in 0 until displayCount) {
            val notification = notifications[i]
            val itemId = when (i) {
                0 -> R.id.widget_item_1
                1 -> R.id.widget_item_2
                2 -> R.id.widget_item_3
                3 -> R.id.widget_item_4
                4 -> R.id.widget_item_5
                5 -> R.id.widget_item_6
                6 -> R.id.widget_item_7
                7 -> R.id.widget_item_8
                8 -> R.id.widget_item_9
                9 -> R.id.widget_item_10
                else -> null
            }
            
            itemId?.let { id ->
                views.setTextViewText(id, formatNotificationText(notification))
                views.setViewVisibility(id, android.view.View.VISIBLE)
            }
        }
        
        // Hide remaining items
        for (i in displayCount until 10) {
            val itemId = when (i) {
                0 -> R.id.widget_item_1
                1 -> R.id.widget_item_2
                2 -> R.id.widget_item_3
                3 -> R.id.widget_item_4
                4 -> R.id.widget_item_5
                5 -> R.id.widget_item_6
                6 -> R.id.widget_item_7
                7 -> R.id.widget_item_8
                8 -> R.id.widget_item_9
                9 -> R.id.widget_item_10
                else -> null
            }
            itemId?.let { views.setViewVisibility(it, android.view.View.GONE) }
        }
    }
    
    private fun formatNotificationText(notification: com.javohirmx.notifyr.domain.model.NotificationData): String {
        val timeAgo = formatTimeAgo(notification.timestamp)
        return "${notification.appName}: ${notification.title}\n$timeAgo"
    }
    
    private fun formatTimeAgo(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        return when {
            diff < 60000 -> "Just now"
            diff < 3600000 -> "${diff / 60000}m ago"
            diff < 86400000 -> "${diff / 3600000}h ago"
            else -> {
                val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
                sdf.format(Date(timestamp))
            }
        }
    }
    
    private fun getFilterDisplayName(filter: WidgetRepository.NotificationFilter): String {
        return when (filter) {
            WidgetRepository.NotificationFilter.RECENT -> "Recent"
            WidgetRepository.NotificationFilter.URGENT -> "Urgent"
            WidgetRepository.NotificationFilter.NORMAL -> "Normal"
            WidgetRepository.NotificationFilter.UNREAD -> "Unread"
            WidgetRepository.NotificationFilter.ALL -> "All"
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}

