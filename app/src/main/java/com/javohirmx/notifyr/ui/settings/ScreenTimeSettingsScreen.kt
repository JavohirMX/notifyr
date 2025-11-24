package com.javohirmx.notifyr.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.javohirmx.notifyr.data.repository.ScreenTimeRepository
import com.javohirmx.notifyr.data.repository.ScreenTimeSettingsRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.util.Calendar
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenTimeSettingsScreen(
    navController: NavController,
    viewModel: ScreenTimeSettingsViewModel = hiltViewModel()
) {
    val retentionDays by viewModel.retentionDays.collectAsStateWithLifecycle(initialValue = 30)
    val scope = rememberCoroutineScope()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Screen Time Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "Data Retention",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = "Choose how long to keep screen time data. Older data will be automatically deleted.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            
            val retentionOptions = listOf(30, 60, 90, -1) // -1 means unlimited
            
            retentionOptions.forEach { days ->
                val label = when (days) {
                    30 -> "30 days"
                    60 -> "60 days"
                    90 -> "90 days"
                    -1 -> "Unlimited"
                    else -> "$days days"
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    RadioButton(
                        selected = retentionDays == days,
                        onClick = {
                            scope.launch {
                                viewModel.setRetentionDays(days)
                            }
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = {
                    scope.launch {
                        viewModel.cleanOldData()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Clean Old Data Now")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "This will immediately delete screen time data older than your retention period.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@HiltViewModel
class ScreenTimeSettingsViewModel @Inject constructor(
    application: Application,
    private val settingsRepository: ScreenTimeSettingsRepository,
    private val screenTimeRepository: ScreenTimeRepository
) : AndroidViewModel(application) {
    
    val retentionDays = settingsRepository.retentionDays
    
    suspend fun setRetentionDays(days: Int) {
        settingsRepository.setRetentionDays(days)
    }
    
    suspend fun cleanOldData() {
        val retentionDays = settingsRepository.retentionDays.first()
        if (retentionDays > 0) {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, -retentionDays)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val cutoffDate = calendar.timeInMillis
            
            screenTimeRepository.deleteOldScreenTime(cutoffDate)
        }
    }
}

