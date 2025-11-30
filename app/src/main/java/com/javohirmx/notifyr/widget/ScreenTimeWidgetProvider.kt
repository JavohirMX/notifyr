package com.javohirmx.notifyr.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.javohirmx.notifyr.R

/**
 * Widget provider for screen time timeline widget
 * Displays daily screen time for last 7 days
 */
class ScreenTimeWidgetProvider : AppWidgetProvider() {
    
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Update all widget instances
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }
    
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        // Clean up widget configuration when deleted
        for (appWidgetId in appWidgetIds) {
            deleteWidgetConfig(context, appWidgetId)
        }
    }
    
    override fun onEnabled(context: Context) {
        // Widget enabled
    }
    
    override fun onDisabled(context: Context) {
        // All widgets disabled
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        when (intent.action) {
            ACTION_REFRESH -> {
                val appWidgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    updateAppWidget(context, appWidgetManager, appWidgetId)
                }
            }
        }
    }
    
    companion object {
        const val ACTION_REFRESH = "com.javohirmx.notifyr.widget.ACTION_REFRESH_SCREENTIME"
        
        private const val PREFS_NAME = "com.javohirmx.notifyr.widget.ScreenTimeWidget"
        
        /**
         * Update widget with latest data
         */
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            // Start update service to fetch data and update widget
            val intent = Intent(context, ScreenTimeWidgetUpdateService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            context.startService(intent)
        }
        
        /**
         * Delete widget configuration
         */
        private fun deleteWidgetConfig(context: Context, appWidgetId: Int) {
            // Currently no configuration, but keep for future use
        }
    }
}

