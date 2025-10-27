package com.javohirmx.notifyr.ui.settings

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.javohirmx.notifyr.domain.model.AppRule
import com.javohirmx.notifyr.domain.model.AppRuleType
import com.javohirmx.notifyr.domain.model.description
import com.javohirmx.notifyr.domain.model.displayName
import com.javohirmx.notifyr.ui.history.AppInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRulesScreen(
    navController: NavController,
    viewModel: AppRulesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showRuleDialog by remember { mutableStateOf(false) }
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Rules") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    placeholder = { Text("Search apps...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    )
                )
            }
            
            HorizontalDivider()
            
            // Filter by rule type
            var showOnlyRuled by remember { mutableStateOf(false) }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${uiState.apps.size} apps",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                FilterChip(
                    selected = showOnlyRuled,
                    onClick = { showOnlyRuled = !showOnlyRuled },
                    label = { Text("With rules only") }
                )
            }
            
            // Apps list
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                val filteredApps = uiState.apps.filter { app ->
                    val matchesSearch = searchQuery.isBlank() || 
                        app.appName.contains(searchQuery, ignoreCase = true) ||
                        app.packageName.contains(searchQuery, ignoreCase = true)
                    
                    val matchesFilter = !showOnlyRuled || uiState.rules[app.packageName] != null
                    
                    matchesSearch && matchesFilter
                }
                
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        AppRuleCard(
                            app = app,
                            rule = uiState.rules[app.packageName],
                            onClick = {
                                selectedApp = app
                                showRuleDialog = true
                            },
                            onToggleRule = { enabled ->
                                uiState.rules[app.packageName]?.let { rule ->
                                    viewModel.updateAppRule(
                                        app.packageName,
                                        app.appName,
                                        rule.ruleType,
                                        enabled
                                    )
                                }
                            },
                            onRemoveRule = {
                                viewModel.removeAppRule(app.packageName)
                            }
                        )
                    }
                }
            }
        }
    }
    
    // Rule selection dialog
    if (showRuleDialog && selectedApp != null) {
        AppRuleDialog(
            app = selectedApp!!,
            currentRule = uiState.rules[selectedApp!!.packageName],
            onDismiss = { showRuleDialog = false },
            onSelectRule = { ruleType ->
                viewModel.updateAppRule(
                    selectedApp!!.packageName,
                    selectedApp!!.appName,
                    ruleType,
                    true
                )
                showRuleDialog = false
            },
            onRemoveRule = {
                viewModel.removeAppRule(selectedApp!!.packageName)
                showRuleDialog = false
            }
        )
    }
}

@Composable
fun AppRuleCard(
    app: AppInfo,
    rule: AppRule?,
    onClick: () -> Unit,
    onToggleRule: (Boolean) -> Unit,
    onRemoveRule: () -> Unit
) {
    val context = LocalContext.current
    
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (rule != null) 3.dp else 1.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // App icon
                com.javohirmx.notifyr.utils.AppIconUtils.AppIconOrPlaceholder(
                    context = context,
                    packageName = app.packageName,
                    appName = app.appName,
                    size = 48.dp,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    if (rule != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AssistChip(
                                onClick = onClick,
                                label = {
                                    Text(
                                        text = rule.ruleType.displayName,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = when (rule.ruleType) {
                                            AppRuleType.DONT_INTERCEPT -> Icons.Default.CheckCircle
                                            AppRuleType.ALWAYS_URGENT -> Icons.Default.Warning
                                            AppRuleType.FILTER_KEYWORDS -> Icons.Default.Settings
                                            AppRuleType.ALWAYS_IGNORE -> Icons.Default.Close
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = when (rule.ruleType) {
                                        AppRuleType.DONT_INTERCEPT -> MaterialTheme.colorScheme.primaryContainer
                                        AppRuleType.ALWAYS_URGENT -> MaterialTheme.colorScheme.errorContainer
                                        AppRuleType.FILTER_KEYWORDS -> MaterialTheme.colorScheme.tertiaryContainer
                                        AppRuleType.ALWAYS_IGNORE -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ),
                                modifier = Modifier.height(24.dp)
                            )
                            
                            if (!rule.isEnabled) {
                                Text(
                                    text = "(Disabled)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "No rule set",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Actions
            if (rule != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { onToggleRule(!rule.isEnabled) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (rule.isEnabled) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = if (rule.isEnabled) "Disable" else "Enable",
                            tint = if (rule.isEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    
                    IconButton(
                        onClick = onRemoveRule,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remove rule",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            } else {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add rule",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRuleDialog(
    app: AppInfo,
    currentRule: AppRule?,
    onDismiss: () -> Unit,
    onSelectRule: (AppRuleType) -> Unit,
    onRemoveRule: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Set Rule for ${app.appName}")
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppRuleType.entries.forEach { ruleType ->
                    RuleOptionCard(
                        ruleType = ruleType,
                        isSelected = currentRule?.ruleType == ruleType,
                        onClick = { onSelectRule(ruleType) }
                    )
                }
                
                if (currentRule != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    OutlinedButton(
                        onClick = onRemoveRule,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Remove Rule")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun RuleOptionCard(
    ruleType: AppRuleType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (ruleType) {
                            AppRuleType.DONT_INTERCEPT -> Icons.Default.CheckCircle
                            AppRuleType.ALWAYS_URGENT -> Icons.Default.Warning
                            AppRuleType.FILTER_KEYWORDS -> Icons.Default.Settings
                            AppRuleType.ALWAYS_IGNORE -> Icons.Default.Close
                        },
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    
                    Text(
                        text = ruleType.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = ruleType.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
