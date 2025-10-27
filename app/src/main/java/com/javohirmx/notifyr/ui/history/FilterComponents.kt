package com.javohirmx.notifyr.ui.history

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.javohirmx.notifyr.domain.model.NotificationContext

/**
 * Expandable filter row component that appears below the search bar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterRow(
    filterState: FilterState,
    availableApps: List<AppInfo>,
    onFilterClick: () -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Main filter button row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Filter button with badge
                FilterChip(
                    selected = filterState.isActive,
                    onClick = onFilterClick,
                    label = { Text("Filters") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (filterState.activeCount > 0) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = "${filterState.activeCount}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                )
                
                // Clear filters button (only show when filters are active)
                AnimatedVisibility(
                    visible = filterState.isActive,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally()
                ) {
                    TextButton(
                        onClick = onClearFilters,
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear all")
                    }
                }
            }
            
            // Active filters chips
            AnimatedVisibility(
                visible = filterState.isActive,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                ActiveFiltersChips(
                    filterState = filterState,
                    availableApps = availableApps
                )
            }
        }
    }
}

/**
 * Shows active filters as chips
 */
@Composable
private fun ActiveFiltersChips(
    filterState: FilterState,
    availableApps: List<AppInfo>
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Read status filter chip
        if (filterState.readStatus != ReadStatusFilter.ALL) {
            item {
                AssistChip(
                    onClick = { },
                    label = { Text(filterState.readStatus.displayName) },
                    leadingIcon = {
                        Icon(
                            imageVector = when (filterState.readStatus) {
                                ReadStatusFilter.UNREAD_ONLY -> Icons.Default.Email
                                ReadStatusFilter.READ_ONLY -> Icons.Default.CheckCircle
                                else -> Icons.Default.Email
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }
        
        // Time range filter chip
        if (filterState.timeRange != TimeRangeFilter.ALL_TIME) {
            item {
                AssistChip(
                    onClick = { },
                    label = { Text(filterState.timeRange.displayName) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                )
            }
        }
        
        // Selected apps chips
        items(filterState.selectedApps.toList()) { packageName ->
            val appName = availableApps.find { it.packageName == packageName }?.appName ?: packageName
            AssistChip(
                onClick = { },
                label = { Text(appName) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            )
        }
        
        // Selected contexts chips
        items(filterState.selectedContexts.toList()) { context ->
            AssistChip(
                onClick = { },
                label = { Text(context.name.lowercase().replaceFirstChar { it.uppercase() }) },
                leadingIcon = {
                    Text(getContextIcon(context))
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            )
        }
        
        // Sender filter chip
        if (filterState.senderQuery.isNotBlank()) {
            item {
                AssistChip(
                    onClick = { },
                    label = { Text("Sender: ${filterState.senderQuery}") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }
    }
}

/**
 * Bottom sheet for selecting filters
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    filterState: FilterState,
    availableApps: List<AppInfo>,
    onReadStatusChange: (ReadStatusFilter) -> Unit,
    onTimeRangeChange: (TimeRangeFilter) -> Unit,
    onToggleApp: (String) -> Unit,
    onToggleContext: (NotificationContext) -> Unit,
    onSenderChange: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Text(
                text = "Filter Notifications",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )
            
            HorizontalDivider()
            
            // Read Status Section
            FilterSection(title = "Read Status") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReadStatusFilter.entries.forEach { status ->
                        FilterOptionItem(
                            selected = filterState.readStatus == status,
                            onClick = { onReadStatusChange(status) },
                            label = status.displayName,
                            icon = when (status) {
                                ReadStatusFilter.ALL -> Icons.Default.Email
                                ReadStatusFilter.UNREAD_ONLY -> Icons.Default.Email
                                ReadStatusFilter.READ_ONLY -> Icons.Default.CheckCircle
                            }
                        )
                    }
                }
            }
            
            HorizontalDivider()
            
            // Time Range Section
            FilterSection(title = "Time Range") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeRangeFilter.entries.filter { it != TimeRangeFilter.CUSTOM }.forEach { range ->
                        FilterOptionItem(
                            selected = filterState.timeRange == range,
                            onClick = { onTimeRangeChange(range) },
                            label = range.displayName,
                            icon = Icons.Default.DateRange
                        )
                    }
                }
            }
            
            HorizontalDivider()
            
            // Apps Section
            if (availableApps.isNotEmpty()) {
                FilterSection(title = "Apps (${filterState.selectedApps.size} selected)") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        availableApps.forEach { app ->
                            FilterOptionItem(
                                selected = app.packageName in filterState.selectedApps,
                                onClick = { onToggleApp(app.packageName) },
                                label = app.appName,
                                icon = Icons.Default.Notifications,
                                isCheckbox = true
                            )
                        }
                    }
                }
                
                HorizontalDivider()
            }
            
            // Context Section
            FilterSection(title = "Context (${filterState.selectedContexts.size} selected)") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NotificationContext.entries.forEach { context ->
                        FilterOptionItem(
                            selected = context in filterState.selectedContexts,
                            onClick = { onToggleContext(context) },
                            label = context.name.lowercase().replaceFirstChar { it.uppercase() },
                            emoji = getContextIcon(context),
                            isCheckbox = true
                        )
                    }
                }
            }
            
            HorizontalDivider()
            
            // Sender Section
            FilterSection(title = "Sender (for messaging apps)") {
                OutlinedTextField(
                    value = filterState.senderQuery,
                    onValueChange = onSenderChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Enter sender name...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null
                        )
                    },
                    trailingIcon = {
                        if (filterState.senderQuery.isNotBlank()) {
                            IconButton(onClick = { onSenderChange("") }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear"
                                )
                            }
                        }
                    },
                    singleLine = true
                )
            }
        }
    }
}

@Composable
private fun FilterSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        content()
    }
}

@Composable
private fun FilterOptionItem(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    emoji: String? = null,
    isCheckbox: Boolean = false
) {
    Surface(
        onClick = onClick,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = MaterialTheme.shapes.medium,
        tonalElevation = if (selected) 2.dp else 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                when {
                    icon != null -> Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    emoji != null -> Text(
                        text = emoji,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
            
            if (isCheckbox) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = null
                )
            } else if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun getContextIcon(context: NotificationContext): String {
    return when (context) {
        NotificationContext.WORK -> "💼"
        NotificationContext.FINANCIAL -> "💰"
        NotificationContext.PERSONAL -> "👤"
        NotificationContext.SOCIAL -> "📱"
        NotificationContext.SHOPPING -> "🛒"
        else -> "📋"
    }
}

