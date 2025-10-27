package com.javohirmx.notifyr.ui.digest

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.javohirmx.notifyr.domain.model.AppGroup
import com.javohirmx.notifyr.domain.model.ConversationGroup
import com.javohirmx.notifyr.domain.model.EnhancedDigest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DigestViewScreen(
    navController: NavController,
    viewModel: DigestViewModel = hiltViewModel()
) {
    val digest by viewModel.currentDigest.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    
    val pullRefreshState = rememberPullToRefreshState()
    
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            pullRefreshState.startRefresh()
        } else {
            pullRefreshState.endRefresh()
        }
    }
    
    LaunchedEffect(pullRefreshState.isRefreshing) {
        if (pullRefreshState.isRefreshing) {
            viewModel.refreshDigest()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notification Digest") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshDigest() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                    if (digest != null && digest!!.totalCount > 0) {
                        TextButton(onClick = { viewModel.markAllAsRead() }) {
                            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Mark All Read")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (digest == null || digest!!.totalCount == 0) {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "📬",
                        style = MaterialTheme.typography.displayLarge
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "No Digest Available",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Normal notifications will appear here in a grouped digest",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Show digest content
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(pullRefreshState.nestedScrollConnection)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Header
                        item {
                            DigestHeader(digest!!)
                        }
                        
                        // Needs Attention Section
                        if (digest!!.needsAttention.isNotEmpty()) {
                            item {
                                Text(
                                    text = "⭐ Needs Attention (${digest!!.needsAttention.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            items(digest!!.needsAttention) { notification ->
                                NotificationQuickCard(
                                    appName = notification.appName,
                                    title = notification.title,
                                    text = notification.text,
                                    onMarkRead = { viewModel.markNotificationRead(notification.id) },
                                    onDismiss = { viewModel.dismissNotification(notification.id) }
                                )
                            }
                            item {
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                        
                        // Conversations Section
                        if (digest!!.conversations.isNotEmpty()) {
                            item {
                                Text(
                                    text = "💬 Conversations (${digest!!.conversations.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            items(digest!!.conversations) { conversation ->
                                ConversationCard(conversation)
                            }
                            item {
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                        
                        // Other Apps Section
                        if (digest!!.appGroups.isNotEmpty()) {
                            item {
                                Text(
                                    text = "📱 Other Updates (${digest!!.appGroups.sumOf { it.notificationCount }})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            items(digest!!.appGroups) { appGroup ->
                                AppGroupCard(appGroup)
                            }
                        }
                    }
                
                PullToRefreshContainer(
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
                }
            }
        }
    }
}

@Composable
fun DigestHeader(digest: EnhancedDigest) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = digest.timeRange,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = digest.summary.summaryText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${digest.totalCount} total notifications",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun ConversationCard(conversation: ConversationGroup) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = conversation.sender,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = conversation.appName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Badge {
                    Text("${conversation.messageCount}")
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = conversation.latestMessage,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2
            )
        }
    }
}

@Composable
fun AppGroupCard(appGroup: AppGroup) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appGroup.appName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${appGroup.notificationCount} notifications",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun NotificationQuickCard(
    appName: String, 
    title: String, 
    text: String,
    onMarkRead: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
                if (title.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (text.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2
                    )
                }
            }
            
            Row {
                IconButton(onClick = onMarkRead) {
                    Icon(
                        Icons.Default.CheckCircle, 
                        contentDescription = "Mark as read",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close, 
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}


