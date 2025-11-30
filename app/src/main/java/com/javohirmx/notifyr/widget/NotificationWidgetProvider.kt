package com.javohirmx.notifyr.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.javohirmx.notifyr.R

/**
 * Widget provider for notification widget
 * Handles widget updates, clicks, and refresh actions
 */
class NotificationWidgetProvider : AppWidgetProvider() {
    
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
        const val ACTION_REFRESH = "com.javohirmx.notifyr.widget.ACTION_REFRESH"
        
        private const val PREFS_NAME = "com.javohirmx.notifyr.widget.NotificationWidget"
        private const val PREF_FILTER_PREFIX = "filter_"
        private const val DEFAULT_FILTER = "RECENT"
        
        /**
         * Update widget with latest data
         */
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            // Start update service to fetch data and update widget
            val intent = Intent(context, NotificationWidgetUpdateService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            context.startService(intent)
        }
        
        /**
         * Save widget configuration
         */
        fun saveWidgetConfig(context: Context, appWidgetId: Int, filter: WidgetRepository.NotificationFilter) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString("$PREF_FILTER_PREFIX$appWidgetId", filter.name)
                .apply()
        }
        
        /**
         * Load widget configuration
         */
        fun loadWidgetConfig(context: Context, appWidgetId: Int): WidgetRepository.NotificationFilter {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val filterName = prefs.getString("$PREF_FILTER_PREFIX$appWidgetId", DEFAULT_FILTER)
            return try {
                WidgetRepository.NotificationFilter.valueOf(filterName ?: DEFAULT_FILTER)
            } catch (e: IllegalArgumentException) {
                WidgetRepository.NotificationFilter.RECENT
            }
        }
        
        /**
         * Delete widget configuration
         */
        private fun deleteWidgetConfig(context: Context, appWidgetId: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .remove("$PREF_FILTER_PREFIX$appWidgetId")
                .apply()
        }
    }
}

