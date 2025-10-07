package com.javohirmx.notifyr.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import com.javohirmx.notifyr.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    // Show toast when developer mode is activated
    LaunchedEffect(uiState.isDeveloperModeEnabled) {
        if (uiState.isDeveloperModeEnabled) {
            Toast.makeText(context, "🔧 Developer Mode Activated!", Toast.LENGTH_SHORT).show()
        }
    }
    
    LaunchedEffect(Unit) {
        viewModel.refreshSettings()
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        // Permissions Section
        SettingsSection(title = "Permissions") {
            PermissionSettingCard(
                title = "Notification Access",
                description = if (uiState.isNotificationListenerEnabled) {
                    "Notification filtering is active"
                } else {
                    "Required to read and filter notifications"
                },
                isEnabled = uiState.isNotificationListenerEnabled,
                onClick = viewModel::requestNotificationListenerPermission
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // App Rules Section
        SettingsSection(title = "Filtering Rules") {
            SettingCard(
                title = "App Rules",
                description = "Configure which apps are urgent, normal, or ignored",
                icon = Icons.Default.Settings,
                onClick = { navController.navigate(Screen.AppRules.route) }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            SettingCard(
                title = "Keywords",
                description = "Manage urgent keywords and phrases",
                icon = Icons.Default.Edit,
                onClick = { navController.navigate(Screen.KeywordManagement.route) }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            SettingCard(
                title = "Contacts",
                description = "Mark specific contacts as always urgent",
                icon = Icons.Default.Person,
                onClick = { /* TODO: Navigate to contacts screen */ }
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Data Management Section
        SettingsSection(title = "Data Management") {
            SettingCard(
                title = "Notification History",
                description = "${uiState.totalNotifications} notifications stored",
                icon = Icons.Default.List,
                onClick = { /* TODO: Navigate to history management */ }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            SettingCard(
                title = "Clear Old Notifications",
                description = "Remove notifications older than 7 days",
                icon = Icons.Default.Clear,
                onClick = { viewModel.clearOldNotifications() }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            SettingCard(
                title = "Digest Notifications",
                description = "Get periodic summaries of normal notifications",
                icon = Icons.Default.Notifications,
                onClick = { viewModel.toggleDigestNotifications() }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            SettingCard(
                title = "Clear All Data",
                description = "Delete all notification history",
                icon = Icons.Default.Delete,
                onClick = { showClearDialog = true },
                isDestructive = true
            )
        }
        
        // Developer Mode Section (Hidden by default)
        if (uiState.isDeveloperModeEnabled) {
            Spacer(modifier = Modifier.height(24.dp))
            
            SettingsSection(title = "🔧 Developer Mode") {
                // Developer mode indicator
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Developer Mode Active",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Testing features are now available",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { viewModel.disableDeveloperMode() }) {
                            Text("Disable")
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Test notification buttons
                SettingCard(
                    title = "Test Urgent Notification",
                    description = "Create a test urgent notification with custom styling",
                    icon = Icons.Default.Warning,
                    onClick = { viewModel.createTestUrgentNotification() }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                SettingCard(
                    title = "Test Normal Notification",
                    description = "Create a test normal notification",
                    icon = Icons.Default.Notifications,
                    onClick = { viewModel.createTestNormalNotification() }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                SettingCard(
                    title = "Test Ignored Notification",
                    description = "Create a test ignored notification",
                    icon = Icons.Default.Close,
                    onClick = { viewModel.createTestIgnoredNotification() }
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
        
        // About Section
        SettingsSection(title = "About") {
            SettingCard(
                title = "App Version",
                description = if (uiState.versionTapCount > 0 && !uiState.isDeveloperModeEnabled) {
                    "1.0.0 (Beta) - ${7 - uiState.versionTapCount} more taps"
                } else {
                    "1.0.0 (Beta)"
                },
                icon = Icons.Default.Info,
                onClick = { viewModel.onVersionTapped() }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            SettingCard(
                title = "Privacy Policy",
                description = "Learn how we handle your data",
                icon = Icons.Default.Lock,
                onClick = { /* TODO: Show privacy policy */ }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            SettingCard(
                title = "Help & Support",
                description = "Get help using the app",
                icon = Icons.Default.Info,
                onClick = { navController.navigate(Screen.Help.route) }
            )
        }
    }
    
    // Clear All Data Confirmation Dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Data") },
            text = { 
                Text("This will permanently delete all notification history. This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllNotifications()
                        showClearDialog = false
                    }
                ) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionSettingCard(
    title: String,
    description: String,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = if (isEnabled) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isEnabled) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isEnabled) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                },
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isEnabled) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    }
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isEnabled) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                    }
                )
            }
            
            if (!isEnabled) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    Card(
        onClick = onClick,
        colors = if (isDestructive) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
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
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDestructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isDestructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDestructive) {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = if (isDestructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}
