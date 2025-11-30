package com.javohirmx.notifyr.ui.history

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import com.javohirmx.notifyr.domain.model.NotificationItem
import com.javohirmx.notifyr.domain.model.NotificationGroup
import com.javohirmx.notifyr.domain.model.NotificationTags
import com.javohirmx.notifyr.domain.model.Priority
import com.javohirmx.notifyr.domain.model.NotificationContext
import com.javohirmx.notifyr.domain.model.TimeSensitivity
import com.javohirmx.notifyr.domain.model.ActionType
import com.javohirmx.notifyr.data.repository.CustomTagRepository
import com.javohirmx.notifyr.data.database.CustomTagEntity
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    @Suppress("UNUSED_PARAMETER") navController: NavController,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    
    var showFilterSheet by remember { mutableStateOf(false) }
    var showAppRuleDialog by remember { mutableStateOf(false) }
    var selectedAppForRule by remember { mutableStateOf<Pair<String, String>?>(null) } // packageName, appName
    
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
                
                // Filter Row
                FilterRow(
                    filterState = filterState,
                    availableApps = uiState.availableApps,
                    onFilterClick = { showFilterSheet = true },
                    onClearFilters = viewModel::clearFilters
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Enhanced Tab Layout with Pager
            val pagerState = rememberPagerState(pageCount = { 3 })
            val scope = rememberCoroutineScope()
            
            // Restore last selected tab on first load
            LaunchedEffect(Unit) {
                val savedTab = uiState.selectedTab.coerceIn(0, 2)
                if (pagerState.currentPage != savedTab) {
                    pagerState.animateScrollToPage(savedTab)
                }
            }
            
            // Save tab when it changes
            LaunchedEffect(pagerState.currentPage) {
                viewModel.saveSelectedTab(pagerState.currentPage)
            }
            
            val tabs = listOf(
                TabData("Urgent", uiState.urgentNotifications.size, Icons.Default.Warning),
                TabData("Normal", uiState.normalNotifications.size, Icons.Default.Notifications),
                TabData("Ignored", uiState.ignoredNotifications.size, Icons.Default.Close)
            )
            
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.surface,
                edgePadding = 16.dp
            ) {
                tabs.forEachIndexed { index, tabData ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { 
                            scope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
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
            
            // Swipeable Tab Content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> NotificationList(
                        notifications = uiState.urgentNotifications,
                        importance = NotificationImportance.URGENT,
                        onMarkAsRead = viewModel::markAsRead,
                        onDelete = viewModel::deleteNotification,
                        onMarkAllAsRead = { viewModel.markAllAsRead(NotificationImportance.URGENT) },
                        isLoading = uiState.isLoading,
                        onMarkGroupAsRead = viewModel::markGroupAsRead,
                        onDeleteGroup = viewModel::deleteGroup,
                        onSetAppRule = { packageName, appName ->
                            selectedAppForRule = Pair(packageName, appName)
                            showAppRuleDialog = true
                        },
                        onImportanceChange = viewModel::changeNotificationImportance,
                        onTagsChange = viewModel::updateNotificationTags
                    )
                    1 -> NotificationList(
                        notifications = uiState.normalNotifications,
                        importance = NotificationImportance.NORMAL,
                        onMarkAsRead = viewModel::markAsRead,
                        onDelete = viewModel::deleteNotification,
                        onMarkAllAsRead = { viewModel.markAllAsRead(NotificationImportance.NORMAL) },
                        isLoading = uiState.isLoading,
                        onMarkGroupAsRead = viewModel::markGroupAsRead,
                        onDeleteGroup = viewModel::deleteGroup,
                        onSetAppRule = { packageName, appName ->
                            selectedAppForRule = Pair(packageName, appName)
                            showAppRuleDialog = true
                        },
                        onImportanceChange = viewModel::changeNotificationImportance,
                        onTagsChange = viewModel::updateNotificationTags
                    )
                    2 -> NotificationList(
                        notifications = uiState.ignoredNotifications,
                        importance = NotificationImportance.IGNORE,
                        onMarkAsRead = viewModel::markAsRead,
                        onDelete = viewModel::deleteNotification,
                        onMarkAllAsRead = { viewModel.markAllAsRead(NotificationImportance.IGNORE) },
                        isLoading = uiState.isLoading,
                        onMarkGroupAsRead = viewModel::markGroupAsRead,
                        onDeleteGroup = viewModel::deleteGroup,
                        onSetAppRule = { packageName, appName ->
                            selectedAppForRule = Pair(packageName, appName)
                            showAppRuleDialog = true
                        },
                        onImportanceChange = viewModel::changeNotificationImportance,
                        onTagsChange = viewModel::updateNotificationTags
                    )
                }
            }
        }
        
        // Filter Bottom Sheet
        if (showFilterSheet) {
            FilterBottomSheet(
                filterState = filterState,
                availableApps = uiState.availableApps,
                onReadStatusChange = viewModel::updateReadStatusFilter,
                onTimeRangeChange = { range -> viewModel.updateTimeRangeFilter(range) },
                onToggleApp = viewModel::toggleAppFilter,
                onToggleContext = viewModel::toggleContextFilter,
                onSenderChange = viewModel::updateSenderFilter,
                onDismiss = { showFilterSheet = false }
            )
        }
        
        // App Rule Dialog
        if (showAppRuleDialog && selectedAppForRule != null) {
            val (packageName, appName) = selectedAppForRule!!
            com.javohirmx.notifyr.ui.settings.AppRuleDialog(
                app = AppInfo(packageName, appName),
                currentRule = null, // Could load current rule if needed
                onDismiss = { showAppRuleDialog = false },
                onSelectRule = { ruleType ->
                    viewModel.setAppRule(packageName, appName, ruleType)
                    showAppRuleDialog = false
                },
                onRemoveRule = {
                    viewModel.removeAppRule(packageName)
                    showAppRuleDialog = false
                },
                onSetTemporaryStatus = {
                    // Temporary status feature not available from history screen
                    showAppRuleDialog = false
                }
            )
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
    notifications: List<NotificationItem>,
    importance: NotificationImportance,
    onMarkAsRead: (NotificationData) -> Unit,
    onDelete: (NotificationData) -> Unit,
    onMarkAllAsRead: () -> Unit,
    isLoading: Boolean,
    onMarkGroupAsRead: (NotificationGroup) -> Unit = {},
    onDeleteGroup: (NotificationGroup) -> Unit = {},
    onSetAppRule: ((String, String) -> Unit)? = null,
    onImportanceChange: ((NotificationData, NotificationImportance) -> Unit)? = null,
    onTagsChange: ((NotificationData, NotificationTags) -> Unit)? = null
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
                        
                        val unreadCount = notifications.sumOf { item ->
                            when (item) {
                                is NotificationItem.Single -> if (item.notification.isRead) 0 else 1
                                is NotificationItem.Group -> item.group.unreadCount
                            }
                        }
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
                        key = { item ->
                            when (item) {
                                is NotificationItem.Single -> "single_${item.notification.id}"
                                is NotificationItem.Group -> "group_${item.group.packageName}_${item.group.firstTimestamp}"
                            }
                        }
                    ) { item ->
                        when (item) {
                            is NotificationItem.Single -> {
                                NotificationHistoryCard(
                                    notification = item.notification,
                                    onMarkAsRead = { onMarkAsRead(item.notification) },
                                    onDelete = { onDelete(item.notification) },
                                    onSetAppRule = onSetAppRule,
                                    onImportanceChange = onImportanceChange,
                                    onTagsChange = onTagsChange
                                )
                            }
                            is NotificationItem.Group -> {
                                NotificationGroupCard(
                                    group = item.group,
                                    onMarkAllAsRead = { onMarkGroupAsRead(item.group) },
                                    onDeleteAll = { onDeleteGroup(item.group) },
                                    onMarkSingleAsRead = onMarkAsRead,
                                    onDeleteSingle = onDelete
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationGroupCard(
    group: NotificationGroup,
    onMarkAllAsRead: () -> Unit,
    onDeleteAll: () -> Unit,
    onMarkSingleAsRead: (NotificationData) -> Unit,
    onDeleteSingle: (NotificationData) -> Unit
) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    var isExpanded by remember { mutableStateOf(false) }
    
    val onOpenApp = remember(group.packageName) {
        {
            try {
                val launchIntent: Intent? = packageManager.getLaunchIntentForPackage(group.packageName)
                if (launchIntent != null) {
                    context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    // Mark all notifications in group as read when opening the app
                    if (!group.isAllRead) {
                        onMarkAllAsRead()
                    }
                }
            } catch (_: Exception) { }
        }
    }
    
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 3.dp
        ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (group.isAllRead) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Group Header - Clickable to expand/collapse
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // App Icon with unread indicator
                    Box {
                        com.javohirmx.notifyr.utils.AppIconUtils.AppIconOrPlaceholder(
                            context = context,
                            packageName = group.packageName,
                            appName = group.appName,
                            size = 40.dp,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        // Unread indicator
                        if (!group.isAllRead) {
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
                                text = group.appName,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            
                            // Group count badge
                            Badge(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "${group.count}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            // Unread count badge
                            if (group.unreadCount > 0) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Text(
                                        text = "${group.unreadCount} unread",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                        
                        Text(
                            text = formatTimestamp(group.lastTimestamp) + " - " + formatTimestamp(group.firstTimestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Expand/Collapse Icon
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            // Group Actions
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Open App button (left side)
                IconButton(
                    onClick = onOpenApp
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Open App",
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                // Mark read and delete buttons (right side)
                Row {
                    if (!group.isAllRead) {
                        IconButton(
                            onClick = onMarkAllAsRead
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Mark all read",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    
                    IconButton(
                        onClick = onDeleteAll
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete all",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            // Expanded Content - Individual Notifications
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider()
                    
                    Text(
                        text = "Individual Notifications",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    
                    group.notifications.forEach { notification ->
                        GroupedNotificationItem(
                            notification = notification,
                            onMarkAsRead = { onMarkSingleAsRead(notification) },
                            onDelete = { onDeleteSingle(notification) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupedNotificationItem(
    notification: NotificationData,
    onMarkAsRead: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (notification.isRead) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Timestamp and unread indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!notification.isRead) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape
                                )
                        )
                    }
                    Text(
                        text = formatTimestamp(notification.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (notification.isRead) FontWeight.Normal else FontWeight.SemiBold
                    )
                }
            }
            
            // Title and Content
            if (notification.title.isNotBlank() || notification.text.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                
                if (notification.title.isNotBlank()) {
                    Text(
                        text = notification.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (notification.isRead) {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                if (notification.text.isNotBlank()) {
                    Text(
                        text = notification.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (notification.isRead) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // Individual actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (!notification.isRead) {
                    IconButton(
                        onClick = onMarkAsRead
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Read",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                
                IconButton(
                    onClick = onDelete
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
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
    onDelete: () -> Unit,
    onSetAppRule: ((String, String) -> Unit)? = null,
    onImportanceChange: ((NotificationData, NotificationImportance) -> Unit)? = null,
    onTagsChange: ((NotificationData, NotificationTags) -> Unit)? = null
) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val onOpenApp = remember(notification.packageName) {
        {
            try {
                val launchIntent: Intent? = packageManager.getLaunchIntentForPackage(notification.packageName)
                if (launchIntent != null) {
                    context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    // Mark notification as read when opening the app
                    if (!notification.isRead) {
                        onMarkAsRead()
                    }
                }
            } catch (_: Exception) { }
        }
    }
    
    var showAppRuleMenu by remember { mutableStateOf(false) }
    var showImportanceMenu by remember { mutableStateOf(false) }
    var showTagEditDialog by remember { mutableStateOf(false) }
    
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 2.dp
        ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (notification.isRead) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(12.dp)
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
                        Text(
                            text = notification.appName,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
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
            val hasCustomTags = notification.tags.customTags.isNotEmpty() || notification.tags.globalTagIds.isNotEmpty()
            if (hasCustomTags || 
                notification.tags.timeSensitivity == com.javohirmx.notifyr.domain.model.TimeSensitivity.IMMEDIATE) {
                Spacer(modifier = Modifier.height(12.dp))
                
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Custom tags chips
                    notification.tags.customTags.take(5).forEach { tag ->
                        AssistChip(
                            onClick = { },
                            label = { 
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall
                                ) 
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            ),
                            border = null,
                            modifier = Modifier.height(28.dp)
                        )
                    }
                    
                    // Global tags (we'll need to load them, but for now show IDs)
                    // Note: In a real implementation, you'd load global tags from repository
                    // For now, we'll just show a placeholder or skip them in the card
                    
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App rule and importance buttons (left side)
                Row {
                    if (onSetAppRule != null) {
                        IconButton(
                            onClick = { showAppRuleMenu = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "App Rule",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    
                    // Importance change button
                    if (onImportanceChange != null) {
                        Box {
                            IconButton(
                                onClick = { showImportanceMenu = true }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Change Importance",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            
                            DropdownMenu(
                                expanded = showImportanceMenu,
                                onDismissRequest = { showImportanceMenu = false }
                            ) {
                                // Show options for all importance levels except current
                                NotificationImportance.values().forEach { importance ->
                                    if (importance != notification.importance) {
                                        DropdownMenuItem(
                                            text = { 
                                                Text(
                                                    when (importance) {
                                                        NotificationImportance.URGENT -> "Mark as Urgent"
                                                        NotificationImportance.NORMAL -> "Mark as Normal"
                                                        NotificationImportance.IGNORE -> "Mark as Ignore"
                                                    }
                                                )
                                            },
                                            onClick = {
                                                showImportanceMenu = false
                                                onImportanceChange(notification, importance)
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = when (importance) {
                                                        NotificationImportance.URGENT -> Icons.Default.Warning
                                                        NotificationImportance.NORMAL -> Icons.Default.Notifications
                                                        NotificationImportance.IGNORE -> Icons.Default.Close
                                                    },
                                                    contentDescription = null
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    // Tag edit button
                    if (onTagsChange != null) {
                        IconButton(
                            onClick = { showTagEditDialog = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Edit Tags",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Mark read and delete buttons (right side)
                Row {
                    if (!notification.isRead) {
                        IconButton(
                            onClick = onMarkAsRead
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Mark read",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    
                    IconButton(
                        onClick = onDelete
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
        
        // App Rule Quick Menu
        DropdownMenu(
            expanded = showAppRuleMenu,
            onDismissRequest = { showAppRuleMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Set app rule...") },
                onClick = {
                    showAppRuleMenu = false
                    onSetAppRule?.invoke(notification.packageName, notification.appName)
                },
                leadingIcon = {
                    Icon(Icons.Default.Settings, contentDescription = null)
                }
            )
        }
    }
    
    // Tag Edit Dialog
    if (showTagEditDialog && onTagsChange != null) {
        val historyViewModel: HistoryViewModel = hiltViewModel()
        TagEditDialog(
            notification = notification,
            customTagRepository = historyViewModel.customTagRepository,
            onDismiss = { showTagEditDialog = false },
            onSave = { updatedTags ->
                onTagsChange(notification, updatedTags)
                showTagEditDialog = false
            }
        )
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
    val fiveHours = 5 * 60 * 60 * 1000L // 5 hours in milliseconds
    
    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < fiveHours -> "${diff / 3600_000}h ago"
        else -> {
            // Show actual date and time for notifications over 5 hours old
            val dateFormat = java.text.SimpleDateFormat("MMM dd, h:mm a", java.util.Locale.getDefault())
            dateFormat.format(java.util.Date(timestamp))
        }
    }
}

@Composable
private fun TagListOptionItem(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
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
                if (emoji != null) {
                    Text(
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TagEditDialog(
    notification: NotificationData,
    customTagRepository: CustomTagRepository,
    onDismiss: () -> Unit,
    onSave: (NotificationTags) -> Unit
) {
    var selectedPriority by remember { mutableStateOf(notification.tags.priority) }
    var selectedCustomTags by remember { mutableStateOf(notification.tags.customTags.toSet()) }
    var selectedGlobalTagIds by remember { mutableStateOf(notification.tags.globalTagIds.toSet()) }
    var selectedTimeSensitivity by remember { mutableStateOf(notification.tags.timeSensitivity) }
    var selectedActionType by remember { mutableStateOf(notification.tags.actionType) }
    
    // State for adding new custom tag
    var newCustomTagText by remember { mutableStateOf("") }
    var showCreateGlobalTagDialog by remember { mutableStateOf(false) }
    var newGlobalTagName by remember { mutableStateOf("") }
    
    // Load global tags
    val globalTags by customTagRepository.getAllTags().collectAsState(initial = emptyList())
    
    // Map global tag IDs to tag entities
    val selectedGlobalTags = remember(selectedGlobalTagIds, globalTags) {
        globalTags.filter { it.id.toString() in selectedGlobalTagIds }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit Tags",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Priority Section
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Priority",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Priority.values().forEach { priority ->
                                TagListOptionItem(
                                    selected = selectedPriority == priority,
                                    onClick = { selectedPriority = priority },
                                    label = when (priority) {
                                        Priority.CRITICAL -> "Critical"
                                        Priority.IMPORTANT -> "Important"
                                        Priority.NORMAL -> "Normal"
                                        Priority.LOW -> "Low"
                                    }
                                )
                            }
                        }
                    }
                }
                
                // Custom Tags Section
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Custom Tags",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        
                        // Add new custom tag input
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newCustomTagText,
                                onValueChange = { newCustomTagText = it },
                                label = { Text("Add custom tag") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        if (newCustomTagText.isNotBlank() && newCustomTagText !in selectedCustomTags) {
                                            selectedCustomTags = selectedCustomTags + newCustomTagText.trim()
                                            newCustomTagText = ""
                                        }
                                    }
                                )
                            )
                            IconButton(
                                onClick = {
                                    if (newCustomTagText.isNotBlank() && newCustomTagText.trim() !in selectedCustomTags) {
                                        selectedCustomTags = selectedCustomTags + newCustomTagText.trim()
                                        newCustomTagText = ""
                                    }
                                },
                                enabled = newCustomTagText.isNotBlank() && newCustomTagText.trim() !in selectedCustomTags
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add tag")
                            }
                        }
                        
                        // Display selected custom tags
                        if (selectedCustomTags.isNotEmpty()) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                selectedCustomTags.forEach { tag ->
                                    AssistChip(
                                        onClick = {
                                            selectedCustomTags = selectedCustomTags - tag
                                        },
                                        label = { Text(tag) },
                                        trailingIcon = {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Remove",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Global Tags Section
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Global Tags",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            TextButton(
                                onClick = { showCreateGlobalTagDialog = true }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("New")
                            }
                        }
                        
                        // Display global tags
                        if (globalTags.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                globalTags.forEach { tag ->
                                    TagListOptionItem(
                                        selected = tag.id.toString() in selectedGlobalTagIds,
                                        onClick = {
                                            selectedGlobalTagIds = if (tag.id.toString() in selectedGlobalTagIds) {
                                                selectedGlobalTagIds - tag.id.toString()
                                            } else {
                                                selectedGlobalTagIds + tag.id.toString()
                                            }
                                        },
                                        label = tag.name,
                                        isCheckbox = true
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "No global tags. Create one to reuse across notifications.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                // Time Sensitivity Section
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Time Sensitivity",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TimeSensitivity.values().forEach { sensitivity ->
                                TagListOptionItem(
                                    selected = selectedTimeSensitivity == sensitivity,
                                    onClick = { selectedTimeSensitivity = sensitivity },
                                    label = sensitivity.getDisplayName()
                                )
                            }
                        }
                    }
                }
                
                // Action Type Section
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Action Type",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionType.values().forEach { actionType ->
                                TagListOptionItem(
                                    selected = selectedActionType == actionType,
                                    onClick = { selectedActionType = actionType },
                                    label = actionType.getDisplayName()
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        NotificationTags(
                            priority = selectedPriority,
                            customTags = selectedCustomTags,
                            globalTagIds = selectedGlobalTagIds,
                            timeSensitivity = selectedTimeSensitivity,
                            actionType = selectedActionType
                        )
                    )
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
    
    // Create Global Tag Dialog
    if (showCreateGlobalTagDialog) {
        AlertDialog(
            onDismissRequest = { 
                showCreateGlobalTagDialog = false
                newGlobalTagName = ""
            },
            title = { Text("Create Global Tag") },
            text = {
                OutlinedTextField(
                    value = newGlobalTagName,
                    onValueChange = { newGlobalTagName = it },
                    label = { Text("Tag name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                val scope = rememberCoroutineScope()
                TextButton(
                    onClick = {
                        if (newGlobalTagName.isNotBlank()) {
                            scope.launch {
                                try {
                                    val tagId = customTagRepository.insertTag(newGlobalTagName.trim())
                                    // Automatically select the newly created tag
                                    selectedGlobalTagIds = selectedGlobalTagIds + tagId.toString()
                                    showCreateGlobalTagDialog = false
                                    newGlobalTagName = ""
                                } catch (e: Exception) {
                                    android.util.Log.e("TagEditDialog", "Failed to create global tag", e)
                                }
                            }
                        }
                    },
                    enabled = newGlobalTagName.isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCreateGlobalTagDialog = false
                        newGlobalTagName = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
