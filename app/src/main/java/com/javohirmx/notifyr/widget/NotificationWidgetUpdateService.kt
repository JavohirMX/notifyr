package com.javohirmx.notifyr.widget

import android.app.Service
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.IBinder
import android.util.Log
import android.widget.RemoteViews
import com.javohirmx.notifyr.MainActivity
import com.javohirmx.notifyr.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
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

        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)

        val layoutId = when {
            minWidth < 110 -> R.layout.widget_notification_small
            minWidth < 250 -> R.layout.widget_notification_medium
            else -> R.layout.widget_notification_large
        }

        val loadingViews = RemoteViews(packageName, layoutId)
        renderLoadingState(loadingViews, layoutId, filter)
        applyCommonIntents(loadingViews, appWidgetId)
        appWidgetManager.updateAppWidget(appWidgetId, loadingViews)

        runCatching {
            val notifications = withContext(Dispatchers.IO) {
                widgetRepository.getFilteredNotifications(filter, limit = 10)
            }

            val views = RemoteViews(packageName, layoutId)

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

            applyCommonIntents(views, appWidgetId)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }.onFailure { error ->
            Log.w(TAG, "Failed to render notification widget", error)
            val errorViews = RemoteViews(packageName, layoutId)
            renderErrorState(errorViews, layoutId, filter)
            applyCommonIntents(errorViews, appWidgetId)
            appWidgetManager.updateAppWidget(appWidgetId, errorViews)
        }
    }

    private fun applyCommonIntents(views: RemoteViews, appWidgetId: Int) {
        val refreshIntent = Intent(this, NotificationWidgetProvider::class.java).apply {
            action = NotificationWidgetProvider.ACTION_REFRESH
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(
            this,
            appWidgetId,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_refresh_button, refreshPendingIntent)

        val openHistoryIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_tab", "history")
        }
        val openHistoryPendingIntent = PendingIntent.getActivity(
            this,
            appWidgetId + 10_000,
            openHistoryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_container, openHistoryPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_title, openHistoryPendingIntent)

        listOf(
            R.id.widget_item_1,
            R.id.widget_item_2,
            R.id.widget_item_3,
            R.id.widget_item_4,
            R.id.widget_item_5,
            R.id.widget_item_6,
            R.id.widget_item_7,
            R.id.widget_item_8,
            R.id.widget_item_9,
            R.id.widget_item_10
        ).forEach { itemId ->
            runCatching {
                views.setOnClickPendingIntent(itemId, openHistoryPendingIntent)
            }
        }
    }

    private fun renderLoadingState(
        views: RemoteViews,
        layoutId: Int,
        filter: WidgetRepository.NotificationFilter
    ) {
        views.setTextViewText(R.id.widget_title, getFilterDisplayName(filter))
        when (layoutId) {
            R.layout.widget_notification_small -> {
                views.setTextViewText(R.id.widget_count, getString(R.string.widget_loading))
            }
            R.layout.widget_notification_medium,
            R.layout.widget_notification_large -> {
                views.setTextViewText(R.id.widget_item_1, getString(R.string.widget_loading))
                views.setViewVisibility(R.id.widget_item_1, android.view.View.VISIBLE)
            }
        }
    }

    private fun renderErrorState(
        views: RemoteViews,
        layoutId: Int,
        filter: WidgetRepository.NotificationFilter
    ) {
        views.setTextViewText(R.id.widget_title, getFilterDisplayName(filter))
        when (layoutId) {
            R.layout.widget_notification_small -> {
                views.setTextViewText(R.id.widget_count, getString(R.string.widget_error_short))
            }
            R.layout.widget_notification_medium,
            R.layout.widget_notification_large -> {
                views.setTextViewText(R.id.widget_item_1, getString(R.string.widget_error))
                views.setViewVisibility(R.id.widget_item_1, android.view.View.VISIBLE)
            }
        }
    }
    
    private fun updateSmallWidget(views: RemoteViews, notifications: List<com.javohirmx.notifyr.domain.model.NotificationData>, filter: WidgetRepository.NotificationFilter) {
        views.setTextViewText(R.id.widget_title, getFilterDisplayName(filter))
        views.setTextViewText(R.id.widget_count, notifications.size.toString())
    }
    
    private fun updateMediumWidget(views: RemoteViews, notifications: List<com.javohirmx.notifyr.domain.model.NotificationData>, filter: WidgetRepository.NotificationFilter) {
        views.setTextViewText(R.id.widget_title, getFilterDisplayName(filter))

        if (notifications.isEmpty()) {
            views.setTextViewText(R.id.widget_item_1, getString(R.string.widget_no_notifications))
            views.setViewVisibility(R.id.widget_item_1, android.view.View.VISIBLE)
            for (i in 1 until 5) {
                val itemId = when (i) {
                    1 -> R.id.widget_item_2
                    2 -> R.id.widget_item_3
                    3 -> R.id.widget_item_4
                    4 -> R.id.widget_item_5
                    else -> null
                }
                itemId?.let { views.setViewVisibility(it, android.view.View.GONE) }
            }
            return
        }
        
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

        if (notifications.isEmpty()) {
            views.setTextViewText(R.id.widget_item_1, getString(R.string.widget_no_notifications))
            views.setViewVisibility(R.id.widget_item_1, android.view.View.VISIBLE)
            for (i in 1 until 10) {
                val itemId = when (i) {
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
            return
        }
        
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
            diff < 60000 -> getString(R.string.widget_time_just_now)
            diff < 3600000 -> getString(R.string.widget_time_minutes_ago, diff / 60000)
            diff < 86400000 -> getString(R.string.widget_time_hours_ago, diff / 3600000)
            else -> {
                val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
                sdf.format(Date(timestamp))
            }
        }
    }
    
    private fun getFilterDisplayName(filter: WidgetRepository.NotificationFilter): String {
        return when (filter) {
            WidgetRepository.NotificationFilter.RECENT -> getString(R.string.widget_filter_recent)
            WidgetRepository.NotificationFilter.URGENT -> getString(R.string.widget_filter_urgent)
            WidgetRepository.NotificationFilter.NORMAL -> getString(R.string.widget_filter_normal)
            WidgetRepository.NotificationFilter.UNREAD -> getString(R.string.widget_filter_unread)
            WidgetRepository.NotificationFilter.ALL -> getString(R.string.widget_filter_all)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "NotificationWidgetUpdate"

        fun enqueueUpdate(context: android.content.Context, appWidgetId: Int) {
            val intent = Intent(context, NotificationWidgetUpdateService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }

            runCatching {
                context.startService(intent)
            }.onFailure {
                Log.w(TAG, "Unable to start notification widget update service", it)
            }
        }
    }
}

