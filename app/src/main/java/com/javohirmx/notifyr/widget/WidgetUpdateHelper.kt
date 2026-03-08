package com.javohirmx.notifyr.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * Helper class to trigger widget updates
 */
object WidgetUpdateHelper {

    private const val PREFS_NAME = "com.javohirmx.notifyr.widget.UpdateThrottling"
    private const val KEY_LAST_NOTIFICATION_UPDATE = "last_notification_update"
    private const val KEY_LAST_SCREENTIME_UPDATE = "last_screentime_update"
    private const val NOTIFICATION_DEBOUNCE_MS = 15_000L
    private const val SCREENTIME_DEBOUNCE_MS = 60_000L
    
    /**
     * Update all notification widgets
     */
    fun updateNotificationWidgets(context: Context, force: Boolean = false) {
        if (!shouldTrigger(context, KEY_LAST_NOTIFICATION_UPDATE, NOTIFICATION_DEBOUNCE_MS, force)) {
            return
        }

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, NotificationWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        
        if (appWidgetIds.isNotEmpty()) {
            val intent = Intent(context, NotificationWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
            }
            context.sendBroadcast(intent)
        }
    }
    
    /**
     * Update all screen time widgets
     */
    fun updateScreenTimeWidgets(context: Context, force: Boolean = false) {
        if (!shouldTrigger(context, KEY_LAST_SCREENTIME_UPDATE, SCREENTIME_DEBOUNCE_MS, force)) {
            return
        }

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, ScreenTimeWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        
        if (appWidgetIds.isNotEmpty()) {
            val intent = Intent(context, ScreenTimeWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
            }
            context.sendBroadcast(intent)
        }
    }
    
    /**
     * Update all widgets
     */
    fun updateAllWidgets(context: Context, force: Boolean = false) {
        updateNotificationWidgets(context, force)
        updateScreenTimeWidgets(context, force)
    }

    private fun shouldTrigger(
        context: Context,
        key: String,
        debounceMs: Long,
        force: Boolean
    ): Boolean {
        if (force) {
            persistUpdateTimestamp(context, key)
            return true
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = SystemClock.elapsedRealtime()
        val last = prefs.getLong(key, 0L)
        if (now - last < debounceMs) {
            return false
        }

        prefs.edit().putLong(key, now).apply()
        return true
    }

    private fun persistUpdateTimestamp(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(key, SystemClock.elapsedRealtime()).apply()
    }
}

