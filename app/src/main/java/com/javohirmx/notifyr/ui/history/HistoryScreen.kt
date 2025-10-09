package com.javohirmx.notifyr.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    navController: NavController,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Search Bar
        SearchBar(
            query = searchQuery,
            onQueryChange = viewModel::updateSearchQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )
        
        // Tab Layout
        var selectedTabIndex by remember { mutableIntStateOf(0) }
        val tabs = listOf(
            "Urgent" to uiState.urgentNotifications.size,
            "Normal" to uiState.normalNotifications.size,
            "Ignored" to uiState.ignoredNotifications.size
        )
        
        TabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, (title, count) ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { 
                        Text("$title ($count)")
                    }
                )
            }
        }
        
        // Tab Content
        when (selectedTabIndex) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text("Search notifications...") },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search"
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
        singleLine = true
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
    Column {
        // Action Bar
        if (notifications.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onMarkAllAsRead,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Done,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Mark All Read")
                }
            }
        }
        
        // Notification List
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (notifications.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No ${importance.name.lowercase()} notifications",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Notifications will appear here as they are processed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(notifications) { notification ->
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (notification.isRead) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenApp)
                .padding(16.dp)
        ) {
            // Header with app name and timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    com.javohirmx.notifyr.utils.AppIconUtils.AppIconOrPlaceholder(
                        context = context,
                        packageName = notification.packageName,
                        appName = notification.appName,
                        size = 24.dp,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = notification.appName,
                        style = MaterialTheme.typography.labelMedium,
                        color = getImportanceColor(notification.importance),
                        fontWeight = FontWeight.Medium
                    )
                    if (!notification.isRead) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Badge {
                            Text("NEW", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Text(
                    text = formatTimestamp(notification.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Title
            if (notification.title.isNotBlank()) {
                Text(
                    text = notification.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (notification.isRead) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            
            // Content
            if (notification.text.isNotBlank()) {
                Text(
                    text = notification.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (notification.isRead) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 3
                )
            }
            
            // Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                if (!notification.isRead) {
                    TextButton(onClick = onMarkAsRead) {
                        Text("Mark Read")
                    }
                }
                TextButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete")
                }
            }
        }
    }
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
