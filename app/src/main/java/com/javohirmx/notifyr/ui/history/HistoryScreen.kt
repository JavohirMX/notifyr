package com.javohirmx.notifyr.ui.history

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
                
                // Notification List
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = notifications,
                        key = { it.id }
                    ) { notification ->
                        NotificationHistoryCard(
                            notification = notification,
                            onMarkAsRead = { onMarkAsRead(notification) },
                            onDelete = { onDelete(notification) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationHistoryCard(
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
            } catch (_: Exception) { }
        }
    }
    
    val cardElevation by animateDpAsState(
        targetValue = if (notification.isRead) 0.dp else 2.dp,
        label = "card_elevation"
    )
    
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = cardElevation
        ),
        colors = if (notification.isRead) {
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        } else {
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenApp)
                .padding(16.dp)
        ) {
            // Header with app icon, name and timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // App Icon with importance indicator
                    Box {
                        com.javohirmx.notifyr.utils.AppIconUtils.AppIconOrPlaceholder(
                            context = context,
                            packageName = notification.packageName,
                            appName = notification.appName,
                            size = 40.dp,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        // Unread indicator dot
                        if (!notification.isRead) {
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
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = notification.appName,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            
                            // Priority indicator
                            ImportanceBadge(importance = notification.importance)
                        }
                        
                        Text(
                            text = formatTimestamp(notification.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Content section
            if (notification.title.isNotBlank() || notification.text.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Title
                    if (notification.title.isNotBlank()) {
                        Text(
                            text = notification.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (notification.isRead) {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    // Content
                    if (notification.text.isNotBlank()) {
                        Text(
                            text = notification.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (notification.isRead) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            
            // Tags and metadata
            if (notification.tags.contexts.isNotEmpty() || 
                notification.tags.timeSensitivity == com.javohirmx.notifyr.domain.model.TimeSensitivity.IMMEDIATE) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Context chips
                    notification.tags.contexts.take(3).forEach { context ->
                        AssistChip(
                            onClick = { },
                            label = { 
                                Text(
                                    text = getContextDisplay(context),
                                    style = MaterialTheme.typography.labelSmall
                                ) 
                            },
                            leadingIcon = {
                                Text(
                                    text = getContextIcon(context),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            ),
                            border = null,
                            modifier = Modifier.height(28.dp)
                        )
                    }
                    
                    // Time sensitivity indicator
                    if (notification.tags.timeSensitivity == com.javohirmx.notifyr.domain.model.TimeSensitivity.IMMEDIATE) {
                        AssistChip(
                            onClick = { },
                            label = { 
                                Text(
                                    text = "Urgent",
                                    style = MaterialTheme.typography.labelSmall
                                ) 
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                            ),
                            border = null,
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
            }
            
            // Action buttons
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!notification.isRead) {
                    TextButton(
                        onClick = onMarkAsRead,
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Mark read")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                
                TextButton(
                    onClick = onDelete,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete")
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

private fun getContextDisplay(context: com.javohirmx.notifyr.domain.model.NotificationContext): String {
    return context.name.lowercase().replaceFirstChar { it.uppercase() }
}

@Composable
private fun getImportanceColor(importance: NotificationImportance): androidx.compose.ui.graphics.Color {
    return when (importance) {
        NotificationImportance.URGENT -> MaterialTheme.colorScheme.error
        NotificationImportance.NORMAL -> MaterialTheme.colorScheme.primary
        NotificationImportance.IGNORE -> MaterialTheme.colorScheme.outline
    }
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
