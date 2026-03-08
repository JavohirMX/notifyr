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
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)

        val layoutId = when {
            minWidth < 110 -> R.layout.widget_screentime_small
            minWidth < 250 -> R.layout.widget_screentime_medium
            else -> R.layout.widget_screentime_large
        }

        val loadingViews = RemoteViews(packageName, layoutId)
        renderLoadingState(loadingViews, layoutId)
        applyCommonIntents(loadingViews, appWidgetId)
        appWidgetManager.updateAppWidget(appWidgetId, loadingViews)

        runCatching {
            val dailyData = withContext(Dispatchers.IO) {
                widgetRepository.getLast7DaysScreenTime()
            }

            val views = RemoteViews(packageName, layoutId)

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

            applyCommonIntents(views, appWidgetId)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }.onFailure { error ->
            Log.w(TAG, "Failed to render screen time widget", error)
            val errorViews = RemoteViews(packageName, layoutId)
            renderErrorState(errorViews, layoutId)
            applyCommonIntents(errorViews, appWidgetId)
            appWidgetManager.updateAppWidget(appWidgetId, errorViews)
        }
    }

    private fun applyCommonIntents(views: RemoteViews, appWidgetId: Int) {
        val refreshIntent = Intent(this, ScreenTimeWidgetProvider::class.java).apply {
            action = ScreenTimeWidgetProvider.ACTION_REFRESH
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(
            this,
            appWidgetId,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_refresh_button, refreshPendingIntent)

        val openScreenTimeIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_tab", "screen_time")
        }
        val openScreenTimePendingIntent = PendingIntent.getActivity(
            this,
            appWidgetId + 20_000,
            openScreenTimeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_container, openScreenTimePendingIntent)
        views.setOnClickPendingIntent(R.id.widget_title, openScreenTimePendingIntent)

        listOf(
            R.id.widget_day_1,
            R.id.widget_day_2,
            R.id.widget_day_3,
            R.id.widget_day_4,
            R.id.widget_day_5,
            R.id.widget_day_6,
            R.id.widget_day_7
        ).forEach { itemId ->
            runCatching {
                views.setOnClickPendingIntent(itemId, openScreenTimePendingIntent)
            }
        }
    }

    private fun renderLoadingState(views: RemoteViews, layoutId: Int) {
        when (layoutId) {
            R.layout.widget_screentime_small -> {
                views.setTextViewText(R.id.widget_title, getString(R.string.widget_screen_time_today))
                views.setTextViewText(R.id.widget_total_time, getString(R.string.widget_loading))
            }
            R.layout.widget_screentime_medium,
            R.layout.widget_screentime_large -> {
                views.setTextViewText(R.id.widget_title, getString(R.string.widget_screen_time_last_7_days))
                views.setTextViewText(R.id.widget_day_1, getString(R.string.widget_loading))
                views.setViewVisibility(R.id.widget_day_1, android.view.View.VISIBLE)
            }
        }
    }

    private fun renderErrorState(views: RemoteViews, layoutId: Int) {
        when (layoutId) {
            R.layout.widget_screentime_small -> {
                views.setTextViewText(R.id.widget_title, getString(R.string.widget_screen_time))
                views.setTextViewText(R.id.widget_total_time, getString(R.string.widget_error_short))
            }
            R.layout.widget_screentime_medium,
            R.layout.widget_screentime_large -> {
                views.setTextViewText(R.id.widget_title, getString(R.string.widget_screen_time_last_7_days))
                views.setTextViewText(R.id.widget_day_1, getString(R.string.widget_error))
                views.setViewVisibility(R.id.widget_day_1, android.view.View.VISIBLE)
            }
        }
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

