package com.javohirmx.notifyr.ui.settings

import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.javohirmx.notifyr.domain.model.FocusMode
import com.javohirmx.notifyr.domain.model.TimeRange
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusModeScreen(
    navController: NavController,
    viewModel: FocusModeViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedMode = uiState.selectedMode
    val autoSwitch = uiState.autoSwitch
    val context = LocalContext.current
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Focus Modes") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Current Mode Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Current Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${selectedMode.getIcon()} ${selectedMode.getDisplayName()}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = selectedMode.getDescription(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Mode Selection
            Text(
                text = "Select Focus Mode",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            FocusMode.values().forEach { mode ->
                FocusModeCard(
                    mode = mode,
                    isSelected = selectedMode == mode,
                    onClick = { viewModel.selectMode(mode) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Auto-Switch Settings
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-Switch Modes",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Automatically change modes based on time and location",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoSwitch,
                            onCheckedChange = { viewModel.toggleAutoSwitch(it) }
                        )
                    }
                    
                    if (autoSwitch) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Work Hours Setting
                        OutlinedButton(
                            onClick = {
                                showTimeRangePicker(context, uiState.workHours) { range ->
                                    viewModel.updateWorkHours(range)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                uiState.workHours?.let { "Work hours: ${formatTimeRange(it)}" }
                                    ?: "Set Work Hours"
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Sleep Hours Setting
                        OutlinedButton(
                            onClick = {
                                showTimeRangePicker(context, uiState.sleepHours) { range ->
                                    viewModel.updateSleepHours(range)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Sleep hours: ${formatTimeRange(uiState.sleepHours)}")
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ℹ️ How Focus Modes Work",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Focus modes filter notifications based on context. For example, Work Mode only shows work-related and critical notifications, while Sleep Mode blocks everything except emergencies.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusModeCard(
    mode: FocusMode,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = if (isSelected) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = mode.getIcon(),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(end = 16.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mode.getDisplayName(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = mode.getDescription(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.ArrowBack, // Using as checkmark placeholder
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

private fun showTimeRangePicker(
    context: Context,
    initial: TimeRange?,
    onPicked: (TimeRange) -> Unit
) {
    val range = initial ?: TimeRange(9, 0, 17, 0)

    TimePickerDialog(
        context,
        { _, startHour, startMinute ->
            TimePickerDialog(
                context,
                { _, endHour, endMinute ->
                    onPicked(TimeRange(startHour, startMinute, endHour, endMinute))
                },
                range.endHour,
                range.endMinute,
                true
            ).show()
        },
        range.startHour,
        range.startMinute,
        true
    ).show()
}

private fun formatTimeRange(range: TimeRange): String {
    return String.format(
        Locale.getDefault(),
        "%02d:%02d–%02d:%02d",
        range.startHour,
        range.startMinute,
        range.endHour,
        range.endMinute
    )
}
