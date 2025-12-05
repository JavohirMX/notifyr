package com.javohirmx.notifyr.ui.screentime

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.javohirmx.notifyr.domain.model.*
import com.javohirmx.notifyr.utils.AppIconUtils
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun ScreenTimeScreen(
    @Suppress("UNUSED_PARAMETER") navController: androidx.navigation.NavController,
    viewModel: ScreenTimeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    // Check permission when screen becomes visible
    LaunchedEffect(Unit) {
        viewModel.checkPermission()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Screen Time") },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Permission prompt
            if (!uiState.hasUsageStatsPermission) {
                PermissionPromptCard(
                    onRequestPermission = { viewModel.requestPermission() },
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                // Time range selector
                TimeRangeSelector(
                    selectedRange = uiState.selectedRange,
                    onRangeSelected = { viewModel.changeRange(it) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                
                // Content
                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (uiState.error != null) {
                    ErrorCard(
                        message = uiState.error ?: "Unknown error",
                        onRetry = { viewModel.refresh() },
                        modifier = Modifier.padding(16.dp)
                    )
                } else if (uiState.dailyScreenTime.isEmpty()) {
                    EmptyStateCard(
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.dailyScreenTime) { dailyData ->
                            DailyScreenTimeCard(
                                dailyScreenTime = dailyData,
                                context = context,
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionPromptCard(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Usage Stats Permission Required",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "To track screen time, Notifyr needs access to usage statistics. Tap the button below to grant permission.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRequestPermission) {
                Text("Grant Permission")
            }
        }
    }
}

@Composable
fun TimeRangeSelector(
    selectedRange: ScreenTimeRange,
    onRangeSelected: (ScreenTimeRange) -> Unit,
    modifier: Modifier = Modifier
) {
    val ranges = ScreenTimeRange.values()
    
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ranges.forEach { range ->
            FilterChip(
                selected = selectedRange == range,
                onClick = { onRangeSelected(range) },
                label = { Text(range.getDisplayName()) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun DailyScreenTimeCard(
    dailyScreenTime: DailyScreenTime,
    context: android.content.Context,
    viewModel: ScreenTimeViewModel
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        onClick = { isExpanded = !isExpanded },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = dailyScreenTime.getFormattedDate(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = dailyScreenTime.getDayOfWeek(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = dailyScreenTime.getFormattedDuration(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (dailyScreenTime.appBreakdown.isNotEmpty()) {
                        Text(
                            text = "${dailyScreenTime.appBreakdown.size} apps",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Top apps preview
            if (dailyScreenTime.appBreakdown.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    dailyScreenTime.appBreakdown.take(3).forEach { appTime ->
                        AppTimeChip(
                            appScreenTime = appTime,
                            context = context,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            
            // Expandable content with smooth animation
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeIn(
                    animationSpec = tween(300)
                ),
                exit = shrinkVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeOut(
                    animationSpec = tween(200)
                )
            ) {
                Column(
                    modifier = Modifier.padding(top = 20.dp)
                ) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    
                    // App breakdown
                    Text(
                        text = "App Usage",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        dailyScreenTime.appBreakdown.forEach { appTime ->
                            AppTimeRow(
                                appScreenTime = appTime,
                                context = context,
                                totalDayDuration = dailyScreenTime.totalDurationMs
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Timeline view
                    Text(
                        text = "Usage Timeline",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    EnhancedTimelineView(
                        date = dailyScreenTime.date,
                        viewModel = viewModel,
                        context = context
                    )
                }
            }
            
            // Expand indicator with rotation animation
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier
                        .size(28.dp)
                        .graphicsLayer {
                            rotationZ = if (isExpanded) 180f else 0f
                        }
                        .animateContentSize(),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun AppTimeChip(
    appScreenTime: AppScreenTime,
    context: android.content.Context,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AppIconUtils.AppIconOrPlaceholder(
                context = context,
                packageName = appScreenTime.packageName,
                appName = appScreenTime.appName,
                size = 20.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appScreenTime.appName,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = appScreenTime.getFormattedDuration(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AppTimeRow(
    appScreenTime: AppScreenTime,
    context: android.content.Context,
    totalDayDuration: Long,
    modifier: Modifier = Modifier
) {
    // Calculate progress percentage (0.0 to 1.0)
    val progress = remember(appScreenTime.totalDurationMs, totalDayDuration) {
        if (totalDayDuration > 0) {
            (appScreenTime.totalDurationMs.toFloat() / totalDayDuration.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    }
    
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppIconUtils.AppIconOrPlaceholder(
            context = context,
            packageName = appScreenTime.packageName,
            appName = appScreenTime.appName,
            size = 40.dp
        )
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = appScreenTime.appName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = appScreenTime.getFormattedDuration(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Progress bar showing relative usage as percentage of day's total
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .width(80.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
fun HourlyTimelineView(
    hourlyData: List<HourlyScreenTime>,
    context: android.content.Context
) {
    // Group by hour - ensure we only process data for the current day
    val hourlyGroups = hourlyData
        .filter { it.hour in 0..23 } // Validate hour range
        .groupBy { it.hour }
    
    // Calculate max duration for proportional visualization
    val maxDuration = hourlyGroups.values.maxOfOrNull { group ->
        group.sumOf { it.durationMs }
    } ?: 1L
    
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Show all 24 hours for better visualization
        (0..23).forEach { hour ->
            val hourData = hourlyGroups[hour] ?: emptyList()
            val totalDuration = hourData.sumOf { it.durationMs }
            
            // Show all hours, even if empty (with reduced opacity)
            HourlyTimelineRow(
                hour = hour,
                durationMs = totalDuration,
                apps = hourData,
                context = context,
                maxDuration = maxDuration,
                modifier = Modifier.alpha(if (totalDuration > 0) 1f else 0.3f)
            )
        }
    }
}

@Composable
fun HourlyTimelineRow(
    hour: Int,
    durationMs: Long,
    apps: List<HourlyScreenTime>,
    context: android.content.Context,
    maxDuration: Long,
    modifier: Modifier = Modifier
) {
    // Calculate proportional width (0.0 to 1.0)
    val progress = if (maxDuration > 0 && durationMs > 0) {
        (durationMs.toFloat() / maxDuration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = formatHour(hour),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(50.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        // Timeline bar with proportional visualization
        Box(
            modifier = Modifier
                .weight(1f)
                .height(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (durationMs > 0 && apps.isNotEmpty()) {
                // Show proportional bar with app segments using Row
                val sortedApps = apps.sortedByDescending { it.durationMs }
                val totalDuration = durationMs.toFloat()
                
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    sortedApps.take(8).forEachIndexed { index, app ->
                        val appDuration = app.durationMs
                        val appWeight = (appDuration.toFloat() / totalDuration).coerceIn(0f, 1f)
                        
                        if (appWeight > 0.01f) { // Only show if > 1% of hour
                            Box(
                                modifier = Modifier
                                    .weight(appWeight)
                                    .fillMaxHeight()
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = if (index == 0) 6.dp else 0.dp,
                                            topEnd = if (index == sortedApps.take(8).size - 1) 6.dp else 0.dp,
                                            bottomStart = if (index == 0) 6.dp else 0.dp,
                                            bottomEnd = if (index == sortedApps.take(8).size - 1) 6.dp else 0.dp
                                        )
                                    )
                                    .background(getColorForApp(app.packageName))
                            )
                        }
                    }
                }
            }
        }
        
        Text(
            text = if (durationMs > 0) formatDuration(durationMs) else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(50.dp)
        )
    }
}

// Helper function to get consistent color for app
private fun getColorForApp(packageName: String): Color {
    val hash = packageName.hashCode()
    val colors = listOf(
        Color(0xFF6200EA), // Purple
        Color(0xFF00BCD4), // Cyan
        Color(0xFF4CAF50), // Green
        Color(0xFFFF9800), // Orange
        Color(0xFFF44336), // Red
        Color(0xFF2196F3), // Blue
        Color(0xFFE91E63), // Pink
        Color(0xFF009688), // Teal
    )
    return colors[hash.mod(colors.size)]
}

@Composable
fun ErrorCard(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Composable
fun EmptyStateCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Screen Time Data",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Screen time data will appear here once usage statistics are collected.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatHour(hour: Int): String {
    val hour12 = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
    val amPm = if (hour < 12) "AM" else "PM"
    return "$hour12 $amPm"
}

@Composable
fun EnhancedTimelineView(
    date: Long,
    viewModel: ScreenTimeViewModel,
    context: android.content.Context
) {
    var sessions by remember { mutableStateOf<List<UsageSession>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var groupByApp by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(date) {
        isLoading = true
        sessions = viewModel.loadSessionsForDate(date)
        isLoading = false
    }
    
    // Get day start and end times
    val calendar = remember(date) {
        Calendar.getInstance().apply {
            timeInMillis = date
        }
    }
    val dayStart = remember(date) {
        calendar.timeInMillis
    }
    val dayEnd = remember(date) {
        Calendar.getInstance().apply {
            timeInMillis = date
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
    }
    val dayDuration = dayEnd - dayStart
    
    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp
                )
                Text(
                    text = "Loading timeline...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else if (sessions.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Text(
                    text = "No usage data for this day",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Screen time data will appear here once apps are used",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Visual timeline bar
            VisualTimelineBar(
                sessions = sessions,
                dayStart = dayStart,
                dayEnd = dayEnd,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(vertical = 8.dp)
            )
            
            // Group by app toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (groupByApp) "Grouped by App" else "Chronological",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Group by app",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = groupByApp,
                        onCheckedChange = { groupByApp = it }
                    )
                }
            }
            
            // Sessions list
            if (groupByApp) {
                // Group sessions by app
                val groupedSessions = sessions.groupBy { it.packageName }
                groupedSessions.forEach { (packageName, appSessions) ->
                    val firstSession = appSessions.first()
                    val totalDuration = appSessions.sumOf { it.durationMs }
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // App header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AppIconUtils.AppIconOrPlaceholder(
                                        context = context,
                                        packageName = packageName,
                                        appName = firstSession.appName,
                                        size = 32.dp
                                    )
                                    Column {
                                        Text(
                                            text = firstSession.appName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "${appSessions.size} session${if (appSessions.size > 1) "s" else ""}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(
                                    text = formatDuration(totalDuration),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            // Sessions for this app
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                appSessions.sortedBy { it.startTime }.forEach { session ->
                                    EnhancedSessionRow(
                                        session = session,
                                        dayStart = dayStart,
                                        dayDuration = dayDuration,
                                        context = context,
                                        showAppName = false
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Chronological view
                val timelineItems = buildTimelineItems(sessions, dayStart, dayEnd)
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    timelineItems.forEach { item ->
                        when (item) {
                            is TimelineSessionItem -> {
                                EnhancedSessionRow(
                                    session = item.session,
                                    dayStart = dayStart,
                                    dayDuration = dayDuration,
                                    context = context,
                                    showAppName = true
                                )
                            }
                            is TimelineGapItem -> {
                                GapTimelineRow(
                                    startTime = item.startTime,
                                    endTime = item.endTime
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VisualTimelineBar(
    sessions: List<UsageSession>,
    dayStart: Long,
    dayEnd: Long,
    modifier: Modifier = Modifier
) {
    val dayDuration = dayEnd - dayStart
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            // Background grid (24 hours)
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                repeat(24) { hour ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .let {
                                if (hour % 6 == 0) {
                                    it.border(
                                        width = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                    )
                                } else {
                                    it
                                }
                            }
                    )
                }
            }
            
            // Session bars
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
            ) {
                val maxWidth = maxWidth
                sessions.forEach { session ->
                    val startOffset = ((session.startTime - dayStart).toFloat() / dayDuration.toFloat())
                        .coerceIn(0f, 1f)
                    val endOffset = ((session.endTime - dayStart).toFloat() / dayDuration.toFloat())
                        .coerceIn(0f, 1f)
                    val width = (endOffset - startOffset).coerceIn(0.01f, 1f)
                    val startX = startOffset * maxWidth.value
                    val barWidth = width * maxWidth.value
                    
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(barWidth.dp)
                            .offset(x = startX.dp)
                            .background(
                                color = getColorForApp(session.packageName).copy(alpha = 0.7f),
                                shape = RoundedCornerShape(4.dp)
                            )
                    )
                }
            }
            
            // Hour labels at bottom
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                repeat(5) { i ->
                    val hour = i * 6
                    Text(
                        text = String.format("%02d:00", hour),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.alpha(if (i == 0 || i == 4) 1f else 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
fun EnhancedSessionRow(
    session: UsageSession,
    dayStart: Long,
    dayDuration: Long,
    context: android.content.Context,
    showAppName: Boolean
) {
    // Calculate position in day (0.0 to 1.0)
    val startPosition = ((session.startTime - dayStart).toFloat() / dayDuration.toFloat())
        .coerceIn(0f, 1f)
    val endPosition = ((session.endTime - dayStart).toFloat() / dayDuration.toFloat())
        .coerceIn(0f, 1f)
    val durationRatio = (endPosition - startPosition).coerceIn(0f, 1f)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showAppName) {
                AppIconUtils.AppIconOrPlaceholder(
                    context = context,
                    packageName = session.packageName,
                    appName = session.appName,
                    size = 40.dp
                )
            } else {
                // Small indicator dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = getColorForApp(session.packageName),
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                )
            }
            
            Column(modifier = Modifier.weight(1f)) {
                if (showAppName) {
                    Text(
                        text = session.appName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = session.getFormattedTimeRange(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    // Visual indicator of position in day
                    BoxWithConstraints(
                        modifier = Modifier
                            .width(60.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        val barWidth = maxWidth.value * durationRatio
                        val startX = maxWidth.value * startPosition
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(barWidth.dp)
                                .offset(x = startX.dp)
                                .background(
                                    color = getColorForApp(session.packageName),
                                    shape = RoundedCornerShape(2.dp)
                                )
                        )
                    }
                }
            }
            
            Text(
                text = session.getFormattedDuration(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

sealed class TimelineItem
data class TimelineSessionItem(val session: UsageSession) : TimelineItem()
data class TimelineGapItem(val startTime: Long, val endTime: Long) : TimelineItem()

private fun buildTimelineItems(
    sessions: List<UsageSession>,
    dayStart: Long,
    dayEnd: Long
): List<TimelineItem> {
    if (sessions.isEmpty()) {
        return listOf(TimelineGapItem(dayStart, dayEnd))
    }
    
    val items = mutableListOf<TimelineItem>()
    val sortedSessions = sessions.sortedBy { it.startTime }
    
    // Add gap before first session if needed
    if (sortedSessions.first().startTime > dayStart) {
        items.add(TimelineGapItem(dayStart, sortedSessions.first().startTime))
    }
    
    // Add sessions and gaps between them
    sortedSessions.forEachIndexed { index, session ->
        items.add(TimelineSessionItem(session))
        
        // Add gap after this session if there's a gap before next session
        if (index < sortedSessions.size - 1) {
            val nextSession = sortedSessions[index + 1]
            if (session.endTime < nextSession.startTime) {
                items.add(TimelineGapItem(session.endTime, nextSession.startTime))
            }
        }
    }
    
    // Add gap after last session if needed
    if (sortedSessions.last().endTime < dayEnd) {
        items.add(TimelineGapItem(sortedSessions.last().endTime, dayEnd))
    }
    
    return items
}

@Composable
fun SessionTimelineRow(
    session: UsageSession,
    context: android.content.Context
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppIconUtils.AppIconOrPlaceholder(
                context = context,
                packageName = session.packageName,
                appName = session.appName,
                size = 32.dp
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.getFormattedTimeRange(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = session.appName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Text(
                text = session.getFormattedDuration(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun GapTimelineRow(
    startTime: Long,
    endTime: Long
) {
    val duration = endTime - startTime
    // Only show gaps longer than 1 minute
    if (duration < 60000) {
        return
    }
    
    val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    val startStr = timeFormat.format(java.util.Date(startTime))
    val endStr = timeFormat.format(java.util.Date(endTime))
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
        Text(
            text = "$startStr - $endStr",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    }
}

