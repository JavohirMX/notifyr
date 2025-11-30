package com.javohirmx.notifyr.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.javohirmx.notifyr.ui.theme.NotifyrTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Configuration activity for notification widget
 * Allows user to select filter type (Recent, Urgent, Normal, Unread, All)
 */
@AndroidEntryPoint
class NotificationWidgetConfigureActivity : ComponentActivity() {
    
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Get widget ID from intent
        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }
        
        // If no widget ID, finish
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        
        setContent {
            NotifyrTheme {
                NotificationWidgetConfigureScreen(
                    appWidgetId = appWidgetId,
                    onConfigured = { filter ->
                        saveConfiguration(filter)
                        finishConfiguration()
                    }
                )
            }
        }
    }
    
    private fun saveConfiguration(filter: WidgetRepository.NotificationFilter) {
        NotificationWidgetProvider.saveWidgetConfig(this, appWidgetId, filter)
    }
    
    private fun finishConfiguration() {
        // Update widget
        val appWidgetManager = AppWidgetManager.getInstance(this)
        NotificationWidgetProvider.updateAppWidget(this, appWidgetManager, appWidgetId)
        
        // Return result
        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, resultValue)
        finish()
    }
}

@Composable
fun NotificationWidgetConfigureScreen(
    appWidgetId: Int,
    onConfigured: (WidgetRepository.NotificationFilter) -> Unit
) {
    var selectedFilter by remember {
        mutableStateOf(WidgetRepository.NotificationFilter.RECENT)
    }
    
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Configure Notification Widget",
            style = MaterialTheme.typography.headlineSmall
        )
        
        Text(
            text = "Select which notifications to display:",
            style = MaterialTheme.typography.bodyMedium
        )
        
        // Filter options
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WidgetRepository.NotificationFilter.values().forEach { filter ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter }
                        )
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = getFilterDisplayName(filter),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = getFilterDescription(filter),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = { onConfigured(selectedFilter) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save")
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

private fun getFilterDescription(filter: WidgetRepository.NotificationFilter): String {
    return when (filter) {
        WidgetRepository.NotificationFilter.RECENT -> "Notifications from last 24 hours"
        WidgetRepository.NotificationFilter.URGENT -> "Only urgent notifications"
        WidgetRepository.NotificationFilter.NORMAL -> "Only normal notifications"
        WidgetRepository.NotificationFilter.UNREAD -> "Only unread notifications"
        WidgetRepository.NotificationFilter.ALL -> "All notifications"
    }
}

