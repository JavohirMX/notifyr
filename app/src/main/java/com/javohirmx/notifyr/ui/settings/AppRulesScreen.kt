package com.javohirmx.notifyr.ui.settings

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.javohirmx.notifyr.utils.AppIconUtils
import androidx.navigation.NavController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.javohirmx.notifyr.domain.model.AppRule
import com.javohirmx.notifyr.domain.model.AppRuleType

data class InstalledApp(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val currentRule: AppRuleType?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRulesScreen(
    navController: NavController,
    viewModel: AppRulesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        viewModel.loadInstalledApps()
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top App Bar
        TopAppBar(
            title = { Text("App Rules") },
            navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search apps...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Filter Options
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "App Rules Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "System apps",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = showSystemApps,
                        onCheckedChange = { showSystemApps = it }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Legend
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = "Rule Types:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        LegendItem(
                            color = MaterialTheme.colorScheme.error,
                            text = "Always Urgent"
                        )
                        LegendItem(
                            color = MaterialTheme.colorScheme.primary,
                            text = "Filter Keywords"
                        )
                        LegendItem(
                            color = MaterialTheme.colorScheme.outline,
                            text = "Always Ignore"
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Apps List
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                val filteredApps = uiState.installedApps.filter { app ->
                    val matchesSearch = app.appName.contains(searchQuery, ignoreCase = true) ||
                            app.packageName.contains(searchQuery, ignoreCase = true)
                    val matchesSystemFilter = showSystemApps || !app.isSystemApp
                    matchesSearch && matchesSystemFilter
                }
                
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredApps) { app ->
                        AppRuleCard(
                            app = app,
                            onRuleChanged = { newRule ->
                                viewModel.updateAppRule(app.packageName, app.appName, newRule)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LegendItem(
    color: Color,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .padding(2.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = color),
                modifier = Modifier.fillMaxSize()
            ) {}
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRuleCard(
    app: InstalledApp,
    onRuleChanged: (AppRuleType?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when (app.currentRule) {
                AppRuleType.ALWAYS_URGENT -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                AppRuleType.FILTER_KEYWORDS -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                AppRuleType.ALWAYS_IGNORE -> MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                null -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val context = LocalContext.current
                val painter = AppIconUtils.rememberAppIconPainter(context, app.packageName, 24.dp)
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    if (painter != null) {
                        Image(painter = painter, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (app.isSystemApp) {
                        Text(
                            text = "System App",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    }
                }
                
                // Current rule indicator
                Box {
                    IconButton(onClick = { expanded = true }) {
                        Icon(
                            imageVector = when (app.currentRule) {
                                AppRuleType.ALWAYS_URGENT -> Icons.Default.Warning
                                AppRuleType.FILTER_KEYWORDS -> Icons.Default.Search
                                AppRuleType.ALWAYS_IGNORE -> Icons.Default.Close
                                null -> Icons.Default.MoreVert
                            },
                            contentDescription = "Rule options",
                            tint = when (app.currentRule) {
                                AppRuleType.ALWAYS_URGENT -> MaterialTheme.colorScheme.error
                                AppRuleType.FILTER_KEYWORDS -> MaterialTheme.colorScheme.primary
                                AppRuleType.ALWAYS_IGNORE -> MaterialTheme.colorScheme.outline
                                null -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Default (No Rule)") },
                            onClick = {
                                onRuleChanged(null)
                                expanded = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Clear, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Always Urgent") },
                            onClick = {
                                onRuleChanged(AppRuleType.ALWAYS_URGENT)
                                expanded = false
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Filter by Keywords") },
                            onClick = {
                                onRuleChanged(AppRuleType.FILTER_KEYWORDS)
                                expanded = false
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Always Ignore") },
                            onClick = {
                                onRuleChanged(AppRuleType.ALWAYS_IGNORE)
                                expanded = false
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline
                                )
                            }
                        )
                    }
                }
            }
            
            // Show current rule description
            app.currentRule?.let { rule ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when (rule) {
                        AppRuleType.ALWAYS_URGENT -> "All notifications from this app will be marked as urgent"
                        AppRuleType.FILTER_KEYWORDS -> "Notifications will be filtered based on keywords"
                        AppRuleType.ALWAYS_IGNORE -> "All notifications from this app will be ignored"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
