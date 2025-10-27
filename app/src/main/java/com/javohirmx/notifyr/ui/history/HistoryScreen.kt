package com.javohirmx.notifyr.ui.history

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.javohirmx.notifyr.domain.model.NotificationData
import com.javohirmx.notifyr.domain.model.NotificationImportance

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun HistoryScreen(
    @Suppress("UNUSED_PARAMETER") navController: NavController,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    
    Scaffold(
        topBar = {
            Column {
                // Enhanced Search Bar
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp
                ) {
                    SearchBar(
                        query = searchQuery,
                        onQueryChange = viewModel::updateSearchQuery,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Enhanced Tab Layout
            var selectedTabIndex by remember { mutableIntStateOf(0) }
            val tabs = listOf(
                TabData("Urgent", uiState.urgentNotifications.size, Icons.Default.Warning),
                TabData("Normal", uiState.normalNotifications.size, Icons.Default.Notifications),
                TabData("Ignored", uiState.ignoredNotifications.size, Icons.Default.Close)
            )
            
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                edgePadding = 16.dp
            ) {
                tabs.forEachIndexed { index, tabData ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        icon = {
                            Icon(
                                imageVector = tabData.icon,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        text = { 
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(tabData.title)
                                if (tabData.count > 0) {
                                    Badge(
                                        containerColor = when(index) {
                                            0 -> MaterialTheme.colorScheme.errorContainer
                                            1 -> MaterialTheme.colorScheme.primaryContainer
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    ) {
                                        Text(
                                            text = "${tabData.count}",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }
            
            HorizontalDivider()
            
            // Tab Content with animation
            AnimatedContent(
                targetState = selectedTabIndex,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                },
                label = "tab_content"
            ) { tabIndex ->
                when (tabIndex) {
                    0 -> NotificationList(
                        notifications = uiState.urgentNotifications,
                        importance = NotificationImportance.URGENT,
                        onMarkAsRead = viewModel::markAsRead,
                        onDelete = viewModel::deleteNotification,
                        onMarkAllAsRead = { viewModel.markAllAsRead(NotificationImportance.URGENT) },
                        isLoading = uiState.isLoading
                    )
                    1 -> NotificationList(
                        notifications = uiState.normalNotifications,
                        importance = NotificationImportance.NORMAL,
                        onMarkAsRead = viewModel::markAsRead,
                        onDelete = viewModel::deleteNotification,
                        onMarkAllAsRead = { viewModel.markAllAsRead(NotificationImportance.NORMAL) },
                        isLoading = uiState.isLoading
                    )
                    2 -> NotificationList(
                        notifications = uiState.ignoredNotifications,
                        importance = NotificationImportance.IGNORE,
                        onMarkAsRead = viewModel::markAsRead,
                        onDelete = viewModel::deleteNotification,
                        onMarkAllAsRead = { viewModel.markAllAsRead(NotificationImportance.IGNORE) },
                        isLoading = uiState.isLoading
                    )
                }
            }
        }
    }
}

data class TabData(
    val title: String,
    val count: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { 
            Text(
                "Search by app, title, or content...",
                style = MaterialTheme.typography.bodyMedium
            ) 
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.primary
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear search"
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        )
    )
}

@Composable
fun NotificationList(
    notifications: List<NotificationData>,
    importance: NotificationImportance,
    onMarkAsRead: (NotificationData) -> Unit,
    onDelete: (NotificationData) -> Unit,
    onMarkAllAsRead: () -> Unit,
    isLoading: Boolean
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "Loading notifications...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (notifications.isEmpty()) {
            // Enhanced empty state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = when(importance) {
                            NotificationImportance.URGENT -> Icons.Default.Warning
                            NotificationImportance.NORMAL -> Icons.Default.Notifications
                            NotificationImportance.IGNORE -> Icons.Default.Close
                        },
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "No ${importance.name.lowercase()} notifications",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = when(importance) {
                            NotificationImportance.URGENT -> "Critical notifications will appear here"
                            NotificationImportance.NORMAL -> "Regular notifications will appear here"
                            NotificationImportance.IGNORE -> "Filtered notifications will appear here"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            Column {
                // Enhanced Action Bar
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${notifications.size} notification${if (notifications.size != 1) "s" else ""}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )

                        val unreadCount = notifications.count { !it.isRead }
                        if (unreadCount > 0) {
                            FilledTonalButton(
                                onClick = onMarkAllAsRead,
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Done,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Mark all read")
                            }
                        }
                    }
                }

                val groupedNotifications = remember(notifications) {
                    groupConsecutiveNotifications(notifications)
                }

                // Notification List
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = groupedNotifications,
                        key = { it.groupId }
                    ) { group ->
                        NotificationGroupCard(
                            group = group,
                            importance = importance,
                            onMarkAsRead = onMarkAsRead,
                            onDelete = onDelete
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NotificationGroupCard(
    group: NotificationGroup,
    importance: NotificationImportance,
    onMarkAsRead: (NotificationData) -> Unit,
    onDelete: (NotificationData) -> Unit
) {
    val context = LocalContext.current
    val unreadCount = group.notifications.count { !it.isRead }
    val latestTimestamp = group.notifications.firstOrNull()?.timestamp ?: 0L
    var expanded by remember(group.groupId) { mutableStateOf(group.notifications.size <= 2) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (unreadCount > 0) {
                MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
            } else {
                MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box {
                        com.javohirmx.notifyr.utils.AppIconUtils.AppIconOrPlaceholder(
                            context = context,
                            packageName = group.packageName,
                            appName = group.appName,
                            size = 46.dp,
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )

                        if (unreadCount > 0) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .align(Alignment.TopEnd)
                                    .offset(x = 2.dp, y = (-2).dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = CircleShape
                                    )
                            )
                        }
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = group.appName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            ImportanceBadge(importance = importance)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Latest ${formatTimestamp(latestTimestamp)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (group.notifications.size > 1) {
                        SuggestionChip(
                            onClick = { expanded = !expanded },
                            label = {
                                Text(
                                    text = if (expanded) "Hide details" else "Show ${group.notifications.size} updates",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            icon = {
                                Icon(
                                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }

                    if (unreadCount > 0) {
                        AssistChip(
                            onClick = { },
                            label = {
                                Text(
                                    text = "$unreadCount unread",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            border = null
                        )
                    }
                }
            }

            val combinedContexts = remember(group.groupId) {
                group.notifications.flatMap { it.tags.contexts }.toSet()
            }

            val hasImmediate = remember(group.groupId) {
                group.notifications.any { it.tags.timeSensitivity == com.javohirmx.notifyr.domain.model.TimeSensitivity.IMMEDIATE }
            }

            if (combinedContexts.isNotEmpty() || hasImmediate) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    combinedContexts.forEach { contextTag ->
                        AssistChip(
                            onClick = { },
                            label = {
                                Text(
                                    text = "${getContextIcon(contextTag)} ${contextTag.getDisplayName()}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            border = null,
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                            )
                        )
                    }

                    if (hasImmediate) {
                        AssistChip(
                            onClick = { },
                            label = {
                                Text(
                                    text = "Time sensitive",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            border = null,
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                            )
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    group.notifications.forEach { notification ->
                        NotificationSummaryRow(
                            notification = notification,
                            onMarkAsRead = { onMarkAsRead(notification) },
                            onDelete = { onDelete(notification) }
                        )
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (group.notifications.size > 1) {
                        "${group.notifications.size} notifications from ${group.appName}"
                    } else {
                        group.notifications.firstOrNull()?.title ?: "Notification"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (unreadCount > 0) {
                        TextButton(
                            onClick = {
                                group.notifications.filterNot { it.isRead }.forEach(onMarkAsRead)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.MarkEmailRead,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Mark group read")
                        }
                    }

                    TextButton(
                        onClick = {
                            group.notifications.forEach(onDelete)
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear")
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationSummaryRow(
    notification: NotificationData,
    onMarkAsRead: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val onOpenApp = remember(notification.packageName) {
        {
            try {
                val launchIntent: Intent? = packageManager.getLaunchIntentForPackage(notification.packageName)
                if (launchIntent != null) {
                    context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            } catch (_: Exception) {
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (notification.isRead) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                } else {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                }
            )
            .clickable(onClick = onOpenApp)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = notification.title.ifBlank { "No title" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (notification.text.isNotBlank()) {
                Text(
                    text = notification.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = formatTimestamp(notification.timestamp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!notification.isRead) {
                    IconButton(
                        onClick = onMarkAsRead,
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onDelete,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportanceBadge(importance: NotificationImportance) {
    val (color, text, icon) = when (importance) {
        NotificationImportance.URGENT -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            "Urgent",
            Icons.Default.Warning
        )
        NotificationImportance.NORMAL -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            "Normal",
            Icons.Default.Notifications
        )
        NotificationImportance.IGNORE -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            "Ignored",
            Icons.Default.Close
        )
    }
    
    Surface(
        color = color,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.height(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

private fun getContextIcon(context: com.javohirmx.notifyr.domain.model.NotificationContext): String {
    return when (context) {
        com.javohirmx.notifyr.domain.model.NotificationContext.WORK -> "💼"
        com.javohirmx.notifyr.domain.model.NotificationContext.FINANCIAL -> "💰"
        com.javohirmx.notifyr.domain.model.NotificationContext.PERSONAL -> "👤"
        com.javohirmx.notifyr.domain.model.NotificationContext.SOCIAL -> "📱"
        com.javohirmx.notifyr.domain.model.NotificationContext.SHOPPING -> "🛒"
        else -> "📋"
    }
}

private data class NotificationGroup(
    val packageName: String,
    val appName: String,
    val notifications: List<NotificationData>
) {
    val groupId: String = "$packageName-${notifications.firstOrNull()?.id ?: hashCode()}-${notifications.size}"
}

private fun groupConsecutiveNotifications(notifications: List<NotificationData>): List<NotificationGroup> {
    if (notifications.isEmpty()) return emptyList()

    val groups = mutableListOf<NotificationGroup>()
    var currentPackage = notifications.first().packageName
    var currentAppName = notifications.first().appName
    val currentList = mutableListOf<NotificationData>()

    notifications.forEach { notification ->
        if (notification.packageName != currentPackage) {
            groups.add(NotificationGroup(currentPackage, currentAppName, currentList.toList()))
            currentList.clear()
            currentPackage = notification.packageName
            currentAppName = notification.appName
        }
        currentList.add(notification)
    }

    if (currentList.isNotEmpty()) {
        groups.add(NotificationGroup(currentPackage, currentAppName, currentList.toList()))
    }

    return groups
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        diff < 604800_000 -> "${diff / 86400_000}d ago"
        else -> {
            val date = java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault())
            date.format(java.util.Date(timestamp))
        }
    }
}
