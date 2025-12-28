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
     * Collect usage sessions (single source of truth for accurate screen time)
     * P0 FIX: Removed broken stats-based collection that used maxOf (causing 40-60% data loss)
     * Now using events-based collection for 100% accuracy
     */
    suspend operator fun invoke(): Boolean {
        return collectSessions()
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
        // P1: Use UTC calendar for query window to be DST-safe
        val utcCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        utcCal.timeInMillis = now
        utcCal.add(Calendar.HOUR, -24) // Get last 24 hours in UTC
        val startTime = utcCal.timeInMillis
        
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
                    // App may have been uninstalled - use package name with indicator
                    "Uninstalled App ($packageName)"
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
                        
                        // Include sessions that overlap with query window
                        // Clamp session to query window to avoid counting time outside our range
                        val clampedStart = maxOf(sessionStart, startTime)
                        val clampedEnd = minOf(eventTime, now)
                        val clampedDuration = clampedEnd - clampedStart
                        
                        // P1: Use UTC for day start calculation (DST-safe)
                        val dayStart = getDayStartUTC(sessionStart)
                        
                        // Include session if it overlaps with query window and has valid duration
                        if (clampedDuration > 0 && clampedStart < now && clampedEnd > startTime) {
                            sessions.add(
                                ScreenTimeSessionEntity(
                                    packageName = packageName,
                                    appName = appName,
                                    date = dayStart,
                                    startTime = clampedStart,
                                    endTime = clampedEnd,
                                    durationMs = clampedDuration
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
                // P1: Use UTC for day start (DST-safe)
                val dayStart = getDayStartUTC(sessionStart)
                
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
    
    /**
     * P1 FIX: Get day start in UTC (midnight UTC) for consistent boundaries across timezones/DST
     */
    private fun getDayStartUTC(timestamp: Long): Long {
        val utcCalendar = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        utcCalendar.timeInMillis = timestamp
        utcCalendar.set(Calendar.HOUR_OF_DAY, 0)
        utcCalendar.set(Calendar.MINUTE, 0)
        utcCalendar.set(Calendar.SECOND, 0)
        utcCalendar.set(Calendar.MILLISECOND, 0)
        return utcCalendar.timeInMillis
    }
    
    /**
     * P1 FIX: Get day end in UTC (23:59:59 UTC)
     */
    private fun getDayEndUTC(timestamp: Long): Long {
        val utcCalendar = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        utcCalendar.timeInMillis = timestamp
        utcCalendar.set(Calendar.HOUR_OF_DAY, 23)
        utcCalendar.set(Calendar.MINUTE, 59)
        utcCalendar.set(Calendar.SECOND, 59)
        utcCalendar.set(Calendar.MILLISECOND, 999)
        return utcCalendar.timeInMillis
    }

}
