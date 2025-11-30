package com.javohirmx.notifyr.domain.usecase

import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import com.javohirmx.notifyr.data.database.ScreenTimeEntity
import com.javohirmx.notifyr.data.database.ScreenTimeSessionEntity
import com.javohirmx.notifyr.data.repository.ScreenTimeRepository
import com.javohirmx.notifyr.utils.PermissionUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CollectScreenTimeUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val screenTimeRepository: ScreenTimeRepository
) {
    
    /**
     * Collect usage stats and store them in the database
     * Collects data for the last 24 hours with accurate hourly breakdown
     */
    suspend operator fun invoke(): Boolean {
        if (!PermissionUtils.isUsageStatsPermissionGranted(context)) {
            return false
        }
        
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return false
        
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = now
        calendar.add(Calendar.HOUR, -24) // Get last 24 hours
        val startTime = calendar.timeInMillis
        
        // Use INTERVAL_BEST for more accurate granular data
        // This provides better hourly breakdown compared to INTERVAL_DAILY
        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            startTime,
            now
        ) ?: return false
        
        val packageManager = context.packageManager
        val screenTimeEntities = mutableListOf<ScreenTimeEntity>()
        
        // Group by package name and aggregate stats to prevent overcounting
        // Track aggregated values since UsageStats is final
        data class AggregatedStats(
            var totalTimeInForeground: Long = 0L,
            var lastTimeUsed: Long = 0L,
            var firstTimeStamp: Long = Long.MAX_VALUE,
            var lastTimeStamp: Long = 0L
        )
        
        val aggregatedStatsMap = mutableMapOf<String, AggregatedStats>()
        usageStatsList.forEach { stat ->
            val aggregated = aggregatedStatsMap.getOrPut(stat.packageName) {
                AggregatedStats()
            }
            // Sum totalTimeInForeground (but be careful not to double-count)
            // For INTERVAL_BEST, entries might overlap, so we take the maximum
            // to avoid overcounting
            aggregated.totalTimeInForeground = maxOf(
                aggregated.totalTimeInForeground,
                stat.totalTimeInForeground
            )
            aggregated.lastTimeUsed = maxOf(aggregated.lastTimeUsed, stat.lastTimeUsed)
            aggregated.firstTimeStamp = minOf(aggregated.firstTimeStamp, stat.firstTimeStamp)
            aggregated.lastTimeStamp = maxOf(aggregated.lastTimeStamp, stat.lastTimeStamp)
        }
        
        // Process aggregated stats and group by hour
        aggregatedStatsMap.forEach { (packageName, aggregated) ->
            // Skip system packages
            if (isSystemPackage(packageName)) {
                return@forEach
            }
            
            // Get app name
            val appName = try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                packageName
            }
            
            // Calculate time spent in each hour
            val lastTimeUsed = aggregated.lastTimeUsed
            val totalTimeInForeground = aggregated.totalTimeInForeground
            
            // Only process if there's actual usage and it's within our query window
            if (totalTimeInForeground > 0 && lastTimeUsed >= startTime) {
                // Calculate the actual start time of usage, but ensure it doesn't go beyond query window
                // Use the firstTimeStamp if available, otherwise calculate from lastTimeUsed
                val calculatedStartTime = if (aggregated.firstTimeStamp != Long.MAX_VALUE) {
                    aggregated.firstTimeStamp
                } else {
                    lastTimeUsed - totalTimeInForeground
                }
                val actualStartTime = maxOf(calculatedStartTime, startTime)
                
                // Validate that we're not overcounting
                // Ensure duration doesn't exceed the actual time window
                val maxPossibleDuration = minOf(
                    totalTimeInForeground,
                    lastTimeUsed - actualStartTime,
                    now - actualStartTime,
                    aggregated.lastTimeStamp - actualStartTime
                )
                
                if (maxPossibleDuration > 0 && actualStartTime < now) {
                    // Distribute time across hours
                    val hourEntities = distributeTimeAcrossHours(
                        packageName = packageName,
                        appName = appName,
                        startTime = actualStartTime,
                        endTime = minOf(lastTimeUsed, now),
                        totalTime = maxPossibleDuration,
                        queryStartTime = startTime,
                        queryEndTime = now
                    )
                    screenTimeEntities.addAll(hourEntities)
                }
            }
        }
        
        // Store in database (upsert will handle duplicates)
        if (screenTimeEntities.isNotEmpty()) {
            screenTimeRepository.insertScreenTimeList(screenTimeEntities)
        }
        
        return true
    }
    
    /**
     * Distribute usage time across hours with proper validation to prevent overcounting
     */
    private fun distributeTimeAcrossHours(
        packageName: String,
        appName: String,
        startTime: Long,
        endTime: Long,
        totalTime: Long,
        queryStartTime: Long,
        queryEndTime: Long
    ): List<ScreenTimeEntity> {
        val entities = mutableListOf<ScreenTimeEntity>()
        val calendar = Calendar.getInstance()
        
        // Ensure we don't process time outside the query window
        val actualStart = maxOf(startTime, queryStartTime)
        val actualEnd = minOf(endTime, queryEndTime)
        
        if (actualStart >= actualEnd || totalTime <= 0) {
            return entities
        }
        
        // Track remaining time to ensure we don't exceed totalTime
        var remainingTime = minOf(totalTime, actualEnd - actualStart)
        var currentTime = actualStart
        
        // Use a map to aggregate by (date, hour) to prevent duplicates
        val hourMap = mutableMapOf<Pair<Long, Int>, Long>()
        
        while (currentTime < actualEnd && remainingTime > 0) {
            calendar.timeInMillis = currentTime
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            
            // Get start of current hour
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val hourStart = calendar.timeInMillis
            
            // Get end of current hour
            calendar.add(Calendar.HOUR_OF_DAY, 1)
            val hourEnd = calendar.timeInMillis
            
            // Calculate time spent in this hour (don't exceed hour boundaries or remaining time)
            val timeInThisHour = minOf(
                hourEnd - maxOf(currentTime, hourStart),
                remainingTime,
                actualEnd - currentTime
            )
            
            if (timeInThisHour > 0) {
                // Get day start timestamp (midnight)
                calendar.timeInMillis = currentTime
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val dayStart = calendar.timeInMillis
                
                // Aggregate by (date, hour) to handle overlapping sessions
                val key = Pair(dayStart, hour)
                hourMap[key] = (hourMap[key] ?: 0L) + timeInThisHour
                
                remainingTime -= timeInThisHour
            }
            
            // Move to next hour
            currentTime = hourEnd
        }
        
        // Convert aggregated map to entities
        hourMap.forEach { (key, duration) ->
            val (date, hour) = key
            entities.add(
                ScreenTimeEntity(
                    packageName = packageName,
                    appName = appName,
                    date = date,
                    hour = hour,
                    durationMs = duration
                )
            )
        }
        
        return entities
    }
    
    /**
     * Check if a package is a system package
     */
    private fun isSystemPackage(packageName: String): Boolean {
        val systemPackages = setOf(
            "android",
            "com.android.systemui",
            "com.android.providers",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.qualcomm",
            context.packageName // Exclude our own app
        )
        
        return systemPackages.any { packageName.startsWith(it) }
    }
    
    /**
     * Collect usage sessions with minute-level precision using UsageEvents API
     * This provides exact start and end times for each app usage session
     */
    suspend fun collectSessions(): Boolean {
        if (!PermissionUtils.isUsageStatsPermissionGranted(context)) {
            return false
        }
        
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return false
        
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = now
        calendar.add(Calendar.HOUR, -24) // Get last 24 hours
        val startTime = calendar.timeInMillis
        
        // Query usage events for minute-level precision
        val usageEvents = usageStatsManager.queryEvents(startTime, now)
            ?: return false
        
        val packageManager = context.packageManager
        val sessions = mutableListOf<ScreenTimeSessionEntity>()
        
        // Track active sessions: packageName -> startTime
        val activeSessions = mutableMapOf<String, Long>()
        val appNames = mutableMapOf<String, String>()
        
        // Process events chronologically
        while (usageEvents.hasNextEvent()) {
            val event = UsageEvents.Event()
            if (!usageEvents.getNextEvent(event)) {
                break
            }
            
            val packageName = event.packageName
            
            // Skip system packages
            if (isSystemPackage(packageName)) {
                continue
            }
            
            // Get app name (cache it)
            if (!appNames.containsKey(packageName)) {
                appNames[packageName] = try {
                    val appInfo = packageManager.getApplicationInfo(packageName, 0)
                    packageManager.getApplicationLabel(appInfo).toString()
                } catch (e: PackageManager.NameNotFoundException) {
                    packageName
                }
            }
            val appName = appNames[packageName]!!
            
            val eventTime = event.timeStamp
            
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    // App moved to foreground - start session
                    activeSessions[packageName] = eventTime
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    // App moved to background - end session
                    val sessionStart = activeSessions.remove(packageName)
                    if (sessionStart != null && sessionStart < eventTime) {
                        val duration = eventTime - sessionStart
                        
                        // Get day start timestamp (midnight)
                        calendar.timeInMillis = sessionStart
                        calendar.set(Calendar.HOUR_OF_DAY, 0)
                        calendar.set(Calendar.MINUTE, 0)
                        calendar.set(Calendar.SECOND, 0)
                        calendar.set(Calendar.MILLISECOND, 0)
                        val dayStart = calendar.timeInMillis
                        
                        // Only include sessions that are within our query window and have valid duration
                        if (sessionStart >= startTime && duration > 0) {
                            sessions.add(
                                ScreenTimeSessionEntity(
                                    packageName = packageName,
                                    appName = appName,
                                    date = dayStart,
                                    startTime = sessionStart,
                                    endTime = eventTime,
                                    durationMs = duration
                                )
                            )
                        }
                    }
                }
            }
        }
        
        // Handle any remaining active sessions (still running)
        activeSessions.forEach { (packageName, sessionStart) ->
            val appName = appNames[packageName] ?: packageName
            val duration = now - sessionStart
            
            if (sessionStart >= startTime && duration > 0) {
                calendar.timeInMillis = sessionStart
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val dayStart = calendar.timeInMillis
                
                sessions.add(
                    ScreenTimeSessionEntity(
                        packageName = packageName,
                        appName = appName,
                        date = dayStart,
                        startTime = sessionStart,
                        endTime = now,
                        durationMs = duration
                    )
                )
            }
        }
        
        // Store sessions in database
        if (sessions.isNotEmpty()) {
            screenTimeRepository.insertSessions(sessions)
        }
        
        return true
    }
}

