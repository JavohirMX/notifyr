package com.javohirmx.notifyr.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Helper class to trigger widget updates
 */
object WidgetUpdateHelper {
    
    /**
     * Update all notification widgets
     */
    fun updateNotificationWidgets(context: Context) {
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
    fun updateScreenTimeWidgets(context: Context) {
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
    fun updateAllWidgets(context: Context) {
        updateNotificationWidgets(context)
        updateScreenTimeWidgets(context)
    }
}

