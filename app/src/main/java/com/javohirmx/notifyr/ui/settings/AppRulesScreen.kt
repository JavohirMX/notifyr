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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.javohirmx.notifyr.domain.model.AppRule
import com.javohirmx.notifyr.domain.model.AppRuleType
import com.javohirmx.notifyr.domain.model.TemporaryAppStatus
import com.javohirmx.notifyr.domain.model.TemporaryStatus
import com.javohirmx.notifyr.domain.model.description
import com.javohirmx.notifyr.domain.model.displayName
import com.javohirmx.notifyr.ui.history.AppInfo
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRulesScreen(
    navController: NavController,
    viewModel: AppRulesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showRuleDialog by remember { mutableStateOf(false) }
    var showTemporaryStatusDialog by remember { mutableStateOf(false) }
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
                            temporaryStatus = uiState.temporaryStatuses[app.packageName],
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
                            },
                            onRemoveTemporaryStatus = {
                                viewModel.removeTemporaryStatus(app.packageName)
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
            },
            onSetTemporaryStatus = {
                showRuleDialog = false
                showTemporaryStatusDialog = true
            }
        )
    }
    
    // Temporary status dialog
    if (showTemporaryStatusDialog && selectedApp != null) {
        TemporaryStatusDialog(
            app = selectedApp!!,
            currentTemporaryStatus = uiState.temporaryStatuses[selectedApp!!.packageName],
            onDismiss = { showTemporaryStatusDialog = false },
            onSetStatus = { status, durationMinutes ->
                viewModel.setTemporaryStatus(
                    selectedApp!!.packageName,
                    selectedApp!!.appName,
                    status,
                    durationMinutes
                )
                showTemporaryStatusDialog = false
            },
            onRemoveStatus = {
                viewModel.removeTemporaryStatus(selectedApp!!.packageName)
                showTemporaryStatusDialog = false
            }
        )
    }
}

@Composable
fun AppRuleCard(
    app: AppInfo,
    rule: AppRule?,
    temporaryStatus: TemporaryAppStatus?,
    onClick: () -> Unit,
    onToggleRule: (Boolean) -> Unit,
    onRemoveRule: () -> Unit,
    onRemoveTemporaryStatus: () -> Unit
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
                    
                        Spacer(modifier = Modifier.height(4.dp))
                    
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                        ) {
                        // Temporary status badge
                        temporaryStatus?.let { tempStatus ->
                            if (!tempStatus.isExpired()) {
                                TemporaryStatusBadge(
                                    temporaryStatus = tempStatus,
                                    onRemove = onRemoveTemporaryStatus
                                )
                            }
                        }
                        
                        // Permanent rule badge
                        if (rule != null) {
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
                                            AppRuleType.ALWAYS_DROP_SYNC_STATUS -> Icons.Default.SyncDisabled
                                            AppRuleType.NEVER_DROP_SYNC_STATUS -> Icons.Default.Sync
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
                                        AppRuleType.ALWAYS_DROP_SYNC_STATUS -> MaterialTheme.colorScheme.secondaryContainer
                                        AppRuleType.NEVER_DROP_SYNC_STATUS -> MaterialTheme.colorScheme.primaryContainer
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
                        } else if (temporaryStatus == null || temporaryStatus.isExpired()) {
                        Text(
                            text = "No rule set",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        }
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

@Composable
fun TemporaryStatusBadge(
    temporaryStatus: TemporaryAppStatus,
    onRemove: () -> Unit
) {
    var remainingMinutes by remember { mutableStateOf(temporaryStatus.getRemainingMinutes()) }
    
    // Update remaining time every minute
    LaunchedEffect(temporaryStatus.expiresAt) {
        while (remainingMinutes > 0) {
            delay(60_000) // Wait 1 minute
            remainingMinutes = temporaryStatus.getRemainingMinutes()
            if (remainingMinutes <= 0) break
        }
    }
    
    val statusText = when (temporaryStatus.status) {
        TemporaryStatus.DONT_IGNORE -> "Don't Ignore"
        TemporaryStatus.IGNORE -> "Ignore"
        TemporaryStatus.URGENT -> "Urgent"
    }
    
    val statusColor = when (temporaryStatus.status) {
        TemporaryStatus.DONT_IGNORE -> MaterialTheme.colorScheme.primaryContainer
        TemporaryStatus.IGNORE -> MaterialTheme.colorScheme.surfaceVariant
        TemporaryStatus.URGENT -> MaterialTheme.colorScheme.errorContainer
    }
    
    val statusIcon = when (temporaryStatus.status) {
        TemporaryStatus.DONT_IGNORE -> Icons.Default.CheckCircle
        TemporaryStatus.IGNORE -> Icons.Default.Close
        TemporaryStatus.URGENT -> Icons.Default.Warning
    }
    
    AssistChip(
        onClick = {},
        label = {
            Text(
                text = "$statusText (${remainingMinutes}m)",
                style = MaterialTheme.typography.labelSmall
            )
        },
        leadingIcon = {
            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
        },
        trailingIcon = {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove temporary status",
                    modifier = Modifier.size(12.dp)
                )
            }
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = statusColor
        ),
        modifier = Modifier.height(24.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRuleDialog(
    app: AppInfo,
    currentRule: AppRule?,
    onDismiss: () -> Unit,
    onSelectRule: (AppRuleType) -> Unit,
    onRemoveRule: () -> Unit,
    onSetTemporaryStatus: () -> Unit
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
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                OutlinedButton(
                    onClick = onSetTemporaryStatus,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Info, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Set Temporary Status")
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
                            AppRuleType.ALWAYS_DROP_SYNC_STATUS -> Icons.Default.SyncDisabled
                            AppRuleType.NEVER_DROP_SYNC_STATUS -> Icons.Default.Sync
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemporaryStatusDialog(
    app: AppInfo,
    currentTemporaryStatus: TemporaryAppStatus?,
    onDismiss: () -> Unit,
    onSetStatus: (TemporaryStatus, Int) -> Unit,
    onRemoveStatus: () -> Unit
) {
    var selectedStatus by remember { 
        mutableStateOf(currentTemporaryStatus?.status ?: TemporaryStatus.URGENT) 
    }
    var selectedDuration by remember { mutableStateOf(15) }
    var customDuration by remember { mutableStateOf("") }
    var useCustomDuration by remember { mutableStateOf(false) }
    
    val presetDurations = listOf(5, 15, 30, 60)
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Set Temporary Status for ${app.appName}")
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Status selection
                Text(
                    text = "Status",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TemporaryStatus.values().forEach { status ->
                        FilterChip(
                            selected = selectedStatus == status,
                            onClick = { selectedStatus = status },
                            label = {
                                Text(
                                    when (status) {
                                        TemporaryStatus.DONT_IGNORE -> "Don't Ignore"
                                        TemporaryStatus.IGNORE -> "Ignore"
                                        TemporaryStatus.URGENT -> "Urgent"
                                    }
                                )
                            },
                            leadingIcon = if (selectedStatus == status) {
                                {
                                    Icon(
                                        imageVector = when (status) {
                                            TemporaryStatus.DONT_IGNORE -> Icons.Default.CheckCircle
                                            TemporaryStatus.IGNORE -> Icons.Default.Close
                                            TemporaryStatus.URGENT -> Icons.Default.Warning
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else null,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                // Duration selection
                Text(
                    text = "Duration (minutes)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                
                // Preset duration buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetDurations.forEach { duration ->
                        FilterChip(
                            selected = !useCustomDuration && selectedDuration == duration,
                            onClick = {
                                useCustomDuration = false
                                selectedDuration = duration
                            },
                            label = { Text("${duration}m") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                // Custom duration option
                FilterChip(
                    selected = useCustomDuration,
                    onClick = { useCustomDuration = true },
                    label = { Text("Custom") }
                )
                
                if (useCustomDuration) {
                    TextField(
                        value = customDuration,
                        onValueChange = { 
                            customDuration = it.filter { char -> char.isDigit() }
                            if (customDuration.isNotEmpty()) {
                                selectedDuration = customDuration.toIntOrNull() ?: 1
                            }
                        },
                        label = { Text("Minutes") },
                        placeholder = { Text("Enter minutes") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        )
                    )
                }
                
                // Show current temporary status if exists
                currentTemporaryStatus?.let { tempStatus ->
                    if (!tempStatus.isExpired()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Current temporary status:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = when (tempStatus.status) {
                                            TemporaryStatus.DONT_IGNORE -> "Don't Ignore"
                                            TemporaryStatus.IGNORE -> "Ignore"
                                            TemporaryStatus.URGENT -> "Urgent"
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${tempStatus.getRemainingMinutes()} minutes remaining",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                IconButton(onClick = onRemoveStatus) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remove",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                
                Button(
                    onClick = {
                        val duration = if (useCustomDuration && customDuration.isNotEmpty()) {
                            customDuration.toIntOrNull() ?: 1
                        } else {
                            selectedDuration
                        }
                        onSetStatus(selectedStatus, duration)
                    },
                    enabled = if (useCustomDuration) customDuration.isNotEmpty() else true
                ) {
                    Text("Set")
                }
            }
        }
    )
}
