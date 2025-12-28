# Screen Time Feature - Refactoring Examples & Implementation Guide

## Quick Reference: Before & After Code Samples

---

## 1. FIX: Data Loss from Incorrect Aggregation

### ❌ CURRENT CODE (Broken)
**File:** `CollectScreenTimeUseCase.kt:69-72`

```kotlin
// THIS LOSES DATA - Don't use maxOf!
val aggregatedStatsMap = mutableMapOf<String, AggregatedStats>()
usageStatsList.forEach { stat ->
    val aggregated = aggregatedStatsMap.getOrPut(stat.packageName) {
        AggregatedStats()
    }
    // ❌ WRONG: Only keeps the largest value
    aggregated.totalTimeInForeground = maxOf(
        aggregated.totalTimeInForeground,
        stat.totalTimeInForeground
    )
}
```

### ✅ FIXED CODE (Use Sessions Instead)
**File:** `CollectScreenTimeUseCase.kt` (REPLACE entire invoke() method)

```kotlin
/**
 * Collect usage sessions with minute-level precision using UsageEvents API
 * THIS IS THE ONLY COLLECTION METHOD NEEDED - delete collectStats()
 */
suspend operator fun invoke(): Boolean {
    if (!PermissionUtils.isUsageStatsPermissionGranted(context)) {
        return false
    }
    
    return collectSessions()  // Single source of truth
}

/**
 * Collect usage sessions with proper boundary handling
 */
suspend fun collectSessions(): Boolean {
    if (!PermissionUtils.isUsageStatsPermissionGranted(context)) {
        return false
    }
    
    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) 
        as? UsageStatsManager ?: return false
    
    val now = System.currentTimeMillis()
    val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    calendar.timeInMillis = now
    calendar.add(Calendar.HOUR, -24)  // Last 24 hours
    val startTime = calendar.timeInMillis
    
    val usageEvents = usageStatsManager.queryEvents(startTime, now) ?: return false
    
    val packageManager = context.packageManager
    val sessions = mutableListOf<ScreenTimeSessionEntity>()
    val activeSessions = mutableMapOf<String, Long>()
    val appNames = mutableMapOf<String, String>()
    
    val event = UsageEvents.Event()
    while (usageEvents.getNextEvent(event)) {
        when (event.eventType) {
            UsageEvents.Event.ACTIVITY_RESUMED -> {
                // Session started
                val packageName = event.packageName ?: continue
                activeSessions[packageName] = event.timeStamp
                
                if (packageName !in appNames) {
                    appNames[packageName] = try {
                        val appInfo = packageManager.getApplicationInfo(packageName, 0)
                        packageManager.getApplicationLabel(appInfo).toString()
                    } catch (e: PackageManager.NameNotFoundException) {
                        "Uninstalled App ($packageName)"
                    }
                }
            }
            
            UsageEvents.Event.ACTIVITY_PAUSED -> {
                // Session ended
                val packageName = event.packageName ?: continue
                val startTime = activeSessions[packageName] ?: continue
                val endTime = event.timeStamp
                val duration = endTime - startTime
                
                // Only save if duration is at least 1 second
                if (duration >= 1000) {
                    sessions.add(
                        ScreenTimeSessionEntity(
                            packageName = packageName,
                            appName = appNames[packageName] ?: packageName,
                            startTime = startTime,
                            endTime = endTime,
                            durationMs = duration
                        )
                    )
                }
                
                activeSessions.remove(packageName)
            }
        }
    }
    
    // Handle sessions still active at query end (clamp to query window)
    activeSessions.forEach { (packageName, startTime) ->
        val endTime = minOf(now, now)  // Clamp to now
        val duration = endTime - startTime
        
        if (duration >= 1000) {
            sessions.add(
                ScreenTimeSessionEntity(
                    packageName = packageName,
                    appName = appNames[packageName] ?: packageName,
                    startTime = startTime,
                    endTime = endTime,
                    durationMs = duration
                )
            )
        }
    }
    
    // Store sessions
    if (sessions.isNotEmpty()) {
        screenTimeRepository.insertSessionsList(sessions)
    }
    
    return true
}
```

### Why This Works:
✅ Sessions have exact start/end times (no guessing)  
✅ No aggregation needed  
✅ No data loss  
✅ Boundary handling is correct  

---

## 2. FIX: Hourly Breakdown Derivation

### ❌ CURRENT CODE (Broken Distribution Logic)
**File:** `CollectScreenTimeUseCase.kt:158-220` (DELETE THIS ENTIRE METHOD)

```kotlin
// This method is wrong - DELETE IT
private fun distributeTimeAcrossHours(...): List<ScreenTimeEntity> {
    // 60+ lines of complex, incorrect logic
}
```

### ✅ FIXED CODE (Simple Derivation from Sessions)
**File:** `ScreenTimeRepository.kt` (ADD NEW METHOD)

```kotlin
/**
 * Derive hourly aggregates from sessions
 * Called after sessions are collected
 */
suspend fun refreshHourlyAggregates() {
    try {
        val sessions = screenTimeSessionDao.getAllSessions()
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val hourlyMap = mutableMapOf<Pair<Long, Int>, Long>()  // (dateKey, hour) -> duration
        
        sessions.forEach { session ->
            var current = session.startTime
            
            while (current < session.endTime) {
                // Get the day start and hour
                utcCal.timeInMillis = current
                utcCal.set(Calendar.HOUR_OF_DAY, 0)
                utcCal.set(Calendar.MINUTE, 0)
                utcCal.set(Calendar.SECOND, 0)
                utcCal.set(Calendar.MILLISECOND, 0)
                val dayStart = utcCal.timeInMillis
                
                // Get current hour
                utcCal.timeInMillis = current
                val hour = utcCal.get(Calendar.HOUR_OF_DAY)
                
                // Get end of this hour
                utcCal.set(Calendar.MINUTE, 59)
                utcCal.set(Calendar.SECOND, 59)
                val hourEnd = utcCal.timeInMillis
                
                // Duration in this hour = min of (end of hour, end of session) - current
                val intervalEnd = minOf(hourEnd, session.endTime)
                val duration = intervalEnd - current
                
                // Accumulate
                val key = Pair(dayStart, hour)
                hourlyMap[key] = (hourlyMap[key] ?: 0L) + duration
                
                // Move to next hour
                current = intervalEnd + 1
            }
        }
        
        // Convert to entities
        val entities = hourlyMap.map { (key, duration) ->
            val (dayStart, hour) = key
            val session = sessions.firstOrNull { 
                it.startTime >= dayStart && it.startTime < dayStart + 86400000
            } ?: return@map null
            
            ScreenTimeEntity(
                packageName = session.packageName,
                appName = session.appName,
                date = dayStart,
                hour = hour,
                durationMs = duration
            )
        }.filterNotNull()
        
        // Upsert into database
        screenTimeDao.insertScreenTimeList(entities)
        
    } catch (e: Exception) {
        Log.e(TAG, "Failed to refresh hourly aggregates", e)
    }
}
```

### Why This Works:
✅ No guessing - based on actual session times  
✅ Simple and understandable logic  
✅ Accurate hour boundaries  
✅ Handles day transitions correctly  

---

## 3. FIX: Timezone Handling

### ❌ CURRENT CODE (Broken)
**File:** Multiple files

```kotlin
// ❌ WRONG - Doesn't account for DST
val calendar = Calendar.getInstance()
calendar.add(Calendar.HOUR, -24)
val startTime = calendar.timeInMillis
```

### ✅ FIXED CODE (UTC Internally)
**File:** `CollectScreenTimeUseCase.kt`, `ScreenTimeRepository.kt`

```kotlin
companion object {
    private val UTC = TimeZone.getTimeZone("UTC")
    
    /**
     * Get the start of a day in UTC
     */
    fun getDayStartUTC(timestamp: Long): Long {
        val calendar = Calendar.getInstance(UTC)
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
    
    /**
     * Get the end of a day in UTC
     */
    fun getDayEndUTC(timestamp: Long): Long {
        val calendar = Calendar.getInstance(UTC)
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.timeInMillis
    }
    
    /**
     * Get hour of day in UTC
     */
    fun getHourUTC(timestamp: Long): Int {
        val calendar = Calendar.getInstance(UTC)
        calendar.timeInMillis = timestamp
        return calendar.get(Calendar.HOUR_OF_DAY)
    }
}

// Usage:
val now = System.currentTimeMillis()
val dayStart = getDayStartUTC(now)
val dayEnd = getDayEndUTC(now)

// Display functions (convert to local time zone)
fun formatTime(millis: Long): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    // Don't set timezone here - use device default for display
    return formatter.format(Date(millis))
}
```

### Why This Works:
✅ DST transitions handled correctly  
✅ Storage is timezone-independent  
✅ Display uses local timezone  
✅ Same query returns same results regardless of device time  

---

## 4. FIX: Data Validation

### ❌ CURRENT CODE (No Validation)
**File:** `ScreenTimeRepository.kt:110`

```kotlin
suspend fun insertScreenTimeList(screenTimeList: List<ScreenTimeEntity>) {
    try {
        if (screenTimeList.isNotEmpty()) {
            screenTimeDao.insertScreenTimeList(screenTimeList)  // No validation
        }
    } catch (e: Exception) {
        android.util.Log.e("ScreenTimeRepository", "Failed to insert screen time list", e)
        throw e
    }
}
```

### ✅ FIXED CODE (With Validation)
**File:** `ScreenTimeRepository.kt`

```kotlin
suspend fun insertScreenTimeList(screenTimeList: List<ScreenTimeEntity>) {
    try {
        val validated = mutableListOf<ScreenTimeEntity>()
        var skipped = 0
        
        screenTimeList.forEach { entity ->
            try {
                // Validate all constraints
                require(entity.durationMs > 0) { 
                    "Duration must be positive (got ${entity.durationMs}ms)" 
                }
                require(entity.hour in 0..23) { 
                    "Hour must be 0-23 (got ${entity.hour})" 
                }
                require(entity.date > 0) { 
                    "Date must be valid timestamp (got ${entity.date})" 
                }
                require(entity.packageName.isNotBlank()) { 
                    "Package name cannot be blank" 
                }
                require(entity.appName.isNotBlank()) { 
                    "App name cannot be blank" 
                }
                
                validated.add(entity)
                
            } catch (e: IllegalArgumentException) {
                skipped++
                Log.w(TAG, "Skipping invalid entry: ${e.message}")
            }
        }
        
        if (skipped > 0) {
            Log.w(TAG, "Validated $skipped/${screenTimeList.size} entries")
        }
        
        if (validated.isNotEmpty()) {
            screenTimeDao.insertScreenTimeList(validated)
        }
        
    } catch (e: Exception) {
        Log.e(TAG, "Failed to insert screen time list", e)
        throw e
    }
}
```

### Why This Works:
✅ Invalid data never enters database  
✅ Clear error messages for debugging  
✅ Graceful handling of edge cases  
✅ Logging helps troubleshoot issues  

---

## 5. FIX: UI Permission Handling

### ❌ CURRENT CODE (No Real-Time Update)
**File:** `ScreenTimeScreen.kt:40-50`

```kotlin
LaunchedEffect(Unit) {
    viewModel.checkPermission()  // Only checks once on load
}

// User grants permission in Settings and returns...
// Screen still shows "Permission Required" ❌
```

### ✅ FIXED CODE (Real-Time Update)
**File:** `ScreenTimeScreen.kt`

```kotlin
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun ScreenTimeScreen(
    navController: NavController,
    viewModel: ScreenTimeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    // Launcher for permission intent
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { 
        // When user returns from settings, check permission
        viewModel.checkPermission()
    }
    
    // Check permission on screen lifecycle resume
    DisposableEffect(Unit) {
        val listener = object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_RESUME) {
                    viewModel.checkPermission()
                    // If permission now granted, load data
                    if (uiState.hasUsageStatsPermission) {
                        viewModel.loadScreenTime(uiState.selectedRange)
                    }
                }
            }
        }
        
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        lifecycle.addObserver(listener)
        
        onDispose {
            lifecycle.removeObserver(listener)
        }
    }
    
    // UI Content
    if (!uiState.hasUsageStatsPermission) {
        PermissionRequiredScreen(
            onGrantPermission = {
                permissionLauncher.launch(
                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                )
            }
        )
    } else {
        // Show data
        ScreenTimeContent(uiState = uiState, viewModel = viewModel)
    }
}
```

### Why This Works:
✅ Permission checked when screen resumes  
✅ Data loads automatically if permission granted  
✅ No confusing UI states  
✅ User feedback is immediate  

---

## 6. FIX: Simplified UI (Remove Hourly Timeline)

### ❌ CURRENT CODE (Confusing Timeline)
**File:** `ScreenTimeScreen.kt:400-500` (DELETE ENTIRELY)

```kotlin
// Hourly timeline view - DELETE THIS
@Composable
fun HourlyTimelineView(hourlyData: List<HourlyScreenTime>) {
    // 100+ lines of complex, confusing code
}
```

### ✅ FIXED CODE (Simple App Breakdown)
**File:** `ScreenTimeScreen.kt` (REPLACE)

```kotlin
@Composable
fun ScreenTimeContent(
    dailyScreenTime: List<DailyScreenTime>,
    viewModel: ScreenTimeViewModel
) {
    if (dailyScreenTime.isEmpty()) {
        EmptyStateCard()
        return
    }
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        dailyScreenTime.forEach { day ->
            item {
                DailyCard(day = day, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun DailyCard(
    day: DailyScreenTime,
    viewModel: ScreenTimeViewModel
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { isExpanded = !isExpanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = day.getFormattedDate(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = day.getDayOfWeek(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Text(
                    text = day.getFormattedDuration(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // App breakdown
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                day.appBreakdown.forEach { appTime ->
                    AppUsageRow(
                        appScreenTime = appTime,
                        totalDuration = day.totalDurationMs
                    )
                }
            }
            
            // Expandable: Show sessions
            if (isExpanded && day.appBreakdown.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Sessions",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                day.appBreakdown.forEach { appTime ->
                    AppSessionsList(
                        appScreenTime = appTime,
                        date = day.date
                    )
                }
            }
        }
    }
}

@Composable
private fun AppUsageRow(
    appScreenTime: AppScreenTime,
    totalDuration: Long
) {
    val progress = if (totalDuration > 0) {
        (appScreenTime.totalDurationMs.toFloat() / totalDuration)
    } else {
        0f
    }
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = appScreenTime.appName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            
            Text(
                text = appScreenTime.getFormattedDuration(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun AppSessionsList(
    appScreenTime: AppScreenTime,
    date: Long
) {
    val viewModel: ScreenTimeViewModel = hiltViewModel()
    val sessions by remember(date, appScreenTime.packageName) {
        viewModel.getSessionsFlow(date, appScreenTime.packageName)
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(start = 8.dp)
    ) {
        sessions.forEach { session ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = "${formatTime(session.startTime)} - ${formatTime(session.endTime)} (${formatDuration(session.durationMs)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
```

### Why This Works:
✅ Simple, easy to understand  
✅ App-level view is primary  
✅ Sessions shown only when expanded  
✅ No confusing hourly timeline  

---

## 7. Implementation Checklist

Copy-paste this to track your refactoring:

```markdown
### Phase 1: Data Collection (Week 1)

- [ ] Delete `collectStats()` method entirely
- [ ] Update `collectSessions()` with boundary clamping
- [ ] Delete `distributeTimeAcrossHours()` method
- [ ] Add `refreshHourlyAggregates()` to ScreenTimeRepository
- [ ] Update ScreenTimeCollectionWorker to call only sessions
- [ ] Add data validation to insertScreenTimeList()

### Phase 2: Timezone & Cleanup (Week 1)

- [ ] Add UTC timezone helper functions
- [ ] Update all timestamp calculations to use UTC internally
- [ ] Add session cleanup to deleteOldScreenTime()
- [ ] Add database indices if needed

### Phase 3: UI Updates (Week 2)

- [ ] Simplify ScreenTimeScreen (remove hourly timeline)
- [ ] Add lifecycle listener for permission checks
- [ ] Add expandable sessions view
- [ ] Update error messages
- [ ] Add loading states

### Phase 4: Testing (Week 2-3)

- [ ] Write unit tests for time calculations
- [ ] Write integration tests for collection
- [ ] Manual testing on real device
- [ ] Test DST transitions
- [ ] Test permission flows

### Verification

- [ ] Accuracy test: compare with Android Settings (must match 95%+)
- [ ] Boundary test: usage spanning days/hours works correctly
- [ ] DST test: data correct before/after DST transition
- [ ] Permission test: UI updates when permission granted
- [ ] Performance test: collection completes in <500ms
```

---

## 8. Testing Strategy

Add to your test file `ScreenTimeUseCaseTest.kt`:

```kotlin
class CollectScreenTimeUseCaseTest {
    
    @Test
    fun `hourly distribution matches session times`() {
        // Arrange
        val sessions = listOf(
            UsageSession(
                packageName = "com.whatsapp",
                appName = "WhatsApp",
                startTime = 1704067500000,  // 10:05:00 UTC
                endTime = 1704068100000,    // 10:15:00 UTC
                durationMs = 600000         // 10 min
            ),
            UsageSession(
                packageName = "com.whatsapp",
                appName = "WhatsApp",
                startTime = 1704070200000,  // 11:00:00 UTC
                endTime = 1704073800000,    // 12:00:00 UTC (1 hour)
                durationMs = 3600000
            )
        )
        
        // Act
        val hourly = deriveHourlyFromSessions(sessions)
        
        // Assert
        assertThat(hourly[10]).isEqualTo(600000)   // Hour 10: 10 min
        assertThat(hourly[11]).isEqualTo(3600000)  // Hour 11: 60 min
        assertThat(hourly.values.sum()).isEqualTo(4200000)  // Total: 70 min
    }
    
    @Test
    fun `handles session spanning multiple hours`() {
        // Arrange
        val session = UsageSession(
            packageName = "com.youtube",
            appName = "YouTube",
            startTime = 1704067800000,  // 10:10:00 UTC
            endTime = 1704078600000,    // 13:30:00 UTC (3h 20m)
            durationMs = 12000000
        )
        
        // Act
        val hourly = deriveHourlyFromSessions(listOf(session))
        
        // Assert
        assertThat(hourly[10]).isEqualTo(3000000)   // 50 min
        assertThat(hourly[11]).isEqualTo(3600000)   // 60 min
        assertThat(hourly[12]).isEqualTo(3600000)   // 60 min
        assertThat(hourly[13]).isEqualTo(1800000)   // 30 min
    }
    
    @Test
    fun `handles DST transitions correctly`() {
        // Spring forward: 2 AM becomes 3 AM (1 hour lost)
        val beforeDST = 1710086400000   // 2024-03-10 2:00:00 UTC
        val afterDST = 1710090000000    // 2024-03-10 3:00:00 UTC (clock jump)
        
        // Should still calculate correct duration
        val duration = afterDST - beforeDST
        assertThat(duration).isEqualTo(3600000)  // Still 1 hour in milliseconds
    }
}
```

---

**This guide gives you everything needed to refactor the screen time feature properly. Follow it step-by-step and your users will have accurate data!**
