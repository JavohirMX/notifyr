package com.javohirmx.notifyr.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.javohirmx.notifyr.ui.theme.NotifyrTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Configuration activity for screen time widget
 * Currently no configuration needed, but kept for future use
 */
@AndroidEntryPoint
class ScreenTimeWidgetConfigureActivity : ComponentActivity() {
    
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
                ScreenTimeWidgetConfigureScreen(
                    appWidgetId = appWidgetId,
                    onConfigured = {
                        finishConfiguration()
                    }
                )
            }
        }
    }
    
    private fun finishConfiguration() {
        // Update widget
        val appWidgetManager = AppWidgetManager.getInstance(this)
        ScreenTimeWidgetProvider.updateAppWidget(this, appWidgetManager, appWidgetId)
        
        // Return result
        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, resultValue)
        finish()
    }
}

@Composable
fun ScreenTimeWidgetConfigureScreen(
    appWidgetId: Int,
    onConfigured: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Screen Time Widget",
            style = MaterialTheme.typography.headlineSmall
        )
        
        Text(
            text = "This widget displays your screen time for the last 7 days.",
            style = MaterialTheme.typography.bodyMedium
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = { onConfigured() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add Widget")
        }
    }
}

