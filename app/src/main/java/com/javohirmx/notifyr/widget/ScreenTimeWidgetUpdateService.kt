package com.javohirmx.notifyr.widget

import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.IBinder
import android.util.Log
import android.widget.RemoteViews
import com.javohirmx.notifyr.R
import com.javohirmx.notifyr.domain.model.formatDuration
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
 * Service to update screen time timeline widget
 * Fetches screen time data for last 7 days and updates widget RemoteViews
 */
@AndroidEntryPoint
class ScreenTimeWidgetUpdateService : Service() {
    
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
        val dailyData = withContext(Dispatchers.IO) {
            widgetRepository.getLast7DaysScreenTime()
        }
        
        // Determine widget size and update accordingly
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        
        val layoutId = when {
            minWidth < 110 -> R.layout.widget_screentime_small
            minWidth < 250 -> R.layout.widget_screentime_medium
            else -> R.layout.widget_screentime_large
        }
        
        val views = RemoteViews(packageName, layoutId)
        
        // Update widget content based on size
        when (layoutId) {
            R.layout.widget_screentime_small -> {
                updateSmallWidget(views, dailyData)
            }
            R.layout.widget_screentime_medium -> {
                updateMediumWidget(views, dailyData)
            }
            R.layout.widget_screentime_large -> {
                updateLargeWidget(views, dailyData)
            }
        }
        
        // Set refresh button click
        val refreshIntent = Intent(this, ScreenTimeWidgetProvider::class.java).apply {
            action = ScreenTimeWidgetProvider.ACTION_REFRESH
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
        if (appIntent != null) {
            val appPendingIntent = android.app.PendingIntent.getActivity(
                this,
                appWidgetId,
                appIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, appPendingIntent)
        }
        
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
    
    private fun updateSmallWidget(views: RemoteViews, dailyData: List<com.javohirmx.notifyr.domain.model.DailyScreenTime>) {
        // Show today's total
        val today = dailyData.firstOrNull()
        if (today != null) {
            views.setTextViewText(R.id.widget_title, getString(R.string.widget_screen_time_today))
            views.setTextViewText(R.id.widget_total_time, formatDuration(today.totalDurationMs))
        } else {
            views.setTextViewText(R.id.widget_title, getString(R.string.widget_screen_time))
            views.setTextViewText(R.id.widget_total_time, getString(R.string.widget_screen_time_zero))
        }
    }
    
    private fun updateMediumWidget(views: RemoteViews, dailyData: List<com.javohirmx.notifyr.domain.model.DailyScreenTime>) {
        views.setTextViewText(R.id.widget_title, getString(R.string.widget_screen_time_last_7_days))
        
        // Show last 3-4 days
        val displayCount = minOf(dailyData.size, 4)
        for (i in 0 until displayCount) {
            val day = dailyData[i]
            val itemId = when (i) {
                0 -> R.id.widget_day_1
                1 -> R.id.widget_day_2
                2 -> R.id.widget_day_3
                3 -> R.id.widget_day_4
                else -> null
            }
            
            itemId?.let { id ->
                val dayLabel = formatDayLabel(day.date, i == 0)
                val duration = formatDuration(day.totalDurationMs)
                views.setTextViewText(id, getString(R.string.widget_day_duration_format, dayLabel, duration))
                views.setViewVisibility(id, android.view.View.VISIBLE)
            }
        }
        
        // Hide remaining items
        for (i in displayCount until 4) {
            val itemId = when (i) {
                0 -> R.id.widget_day_1
                1 -> R.id.widget_day_2
                2 -> R.id.widget_day_3
                3 -> R.id.widget_day_4
                else -> null
            }
            itemId?.let { views.setViewVisibility(it, android.view.View.GONE) }
        }
    }
    
    private fun updateLargeWidget(views: RemoteViews, dailyData: List<com.javohirmx.notifyr.domain.model.DailyScreenTime>) {
        views.setTextViewText(R.id.widget_title, getString(R.string.widget_screen_time_last_7_days))
        
        // Show all 7 days
        val displayCount = minOf(dailyData.size, 7)
        for (i in 0 until displayCount) {
            val day = dailyData[i]
            val itemId = when (i) {
                0 -> R.id.widget_day_1
                1 -> R.id.widget_day_2
                2 -> R.id.widget_day_3
                3 -> R.id.widget_day_4
                4 -> R.id.widget_day_5
                5 -> R.id.widget_day_6
                6 -> R.id.widget_day_7
                else -> null
            }
            
            itemId?.let { id ->
                val dayLabel = formatDayLabel(day.date, i == 0)
                val duration = formatDuration(day.totalDurationMs)
                views.setTextViewText(id, getString(R.string.widget_day_duration_format, dayLabel, duration))
                views.setViewVisibility(id, android.view.View.VISIBLE)
            }
        }
        
        // Hide remaining items
        for (i in displayCount until 7) {
            val itemId = when (i) {
                0 -> R.id.widget_day_1
                1 -> R.id.widget_day_2
                2 -> R.id.widget_day_3
                3 -> R.id.widget_day_4
                4 -> R.id.widget_day_5
                5 -> R.id.widget_day_6
                6 -> R.id.widget_day_7
                else -> null
            }
            itemId?.let { views.setViewVisibility(it, android.view.View.GONE) }
        }
    }
    
    private fun formatDayLabel(date: Long, isToday: Boolean): String {
        return if (isToday) {
            getString(R.string.widget_screen_time_today)
        } else {
            val sdf = SimpleDateFormat("EEE", Locale.getDefault())
            sdf.format(Date(date))
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "ScreenTimeWidgetUpdate"

        fun enqueueUpdate(context: android.content.Context, appWidgetId: Int) {
            val intent = Intent(context, ScreenTimeWidgetUpdateService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }

            runCatching {
                context.startService(intent)
            }.onFailure {
                Log.w(TAG, "Unable to start screen time widget update service", it)
            }
        }
    }
}

