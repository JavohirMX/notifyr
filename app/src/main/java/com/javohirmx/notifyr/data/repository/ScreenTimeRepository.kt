package com.javohirmx.notifyr.data.repository

import com.javohirmx.notifyr.data.database.ScreenTimeDao
import com.javohirmx.notifyr.data.database.ScreenTimeEntity
import com.javohirmx.notifyr.data.database.ScreenTimeSessionDao
import com.javohirmx.notifyr.data.database.ScreenTimeSessionEntity
import com.javohirmx.notifyr.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Calendar

class ScreenTimeRepository(
    private val screenTimeDao: ScreenTimeDao,
    private val screenTimeSessionDao: ScreenTimeSessionDao
) {
    
    /**
     * Get daily screen time summaries for a date range
     */
    suspend fun getDailyScreenTime(startDate: Long, endDate: Long): List<DailyScreenTime> {
        val dailyAggregates = screenTimeDao.getDailyAggregates(startDate, endDate)
        val allEntitiesFlow = screenTimeDao.getScreenTimeByDateRange(startDate, endDate)
        val allEntities = allEntitiesFlow.first()
        
        // Group entities by date (before converting to domain)
        val entitiesByDate = allEntities.groupBy { it.date }
        
        return dailyAggregates.map { aggregate ->
            // Strictly filter entities by date to prevent cross-day contamination
            val dateEntities = (entitiesByDate[aggregate.date] ?: emptyList())
                .filter { it.date == aggregate.date } // Double-check date match
            
            // Group by app for this day
            val appBreakdown = dateEntities
                .groupBy { it.packageName }
                .map { (packageName, entities) ->
                    val firstEntity = entities.first()
                    AppScreenTime(
                        packageName = packageName,
                        appName = firstEntity.appName,
                        totalDurationMs = entities.sumOf { it.durationMs },
                        sessions = entities.map { it.toDomain() }
                    )
                }
                .sortedByDescending { it.totalDurationMs }
            
            // Get hourly data for this day - ensure it's strictly filtered by date
            val hourlyData = dateEntities
                .filter { it.date == aggregate.date } // Ensure date match
                .map { it.toDomain() }
            
            DailyScreenTime(
                date = aggregate.date,
                totalDurationMs = aggregate.totalDurationMs,
                appBreakdown = appBreakdown,
                hourlyData = hourlyData
            )
        }
    }
    
    /**
     * Get hourly screen time for a specific date
     */
    fun getHourlyScreenTime(date: Long): Flow<List<HourlyScreenTime>> {
        return screenTimeDao.getHourlyScreenTime(date).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    /**
     * Get screen time grouped by app for a date range
     */
    suspend fun getAppScreenTime(startDate: Long, endDate: Long): List<AppScreenTime> {
        val appAggregates = screenTimeDao.getAppScreenTime(startDate, endDate)
        val allEntitiesFlow = screenTimeDao.getScreenTimeByDateRange(startDate, endDate)
        val allEntities = allEntitiesFlow.first()
        
        return appAggregates.map { aggregate ->
            val appEntities = allEntities.filter { it.packageName == aggregate.packageName }
            AppScreenTime(
                packageName = aggregate.packageName,
                appName = aggregate.appName,
                totalDurationMs = aggregate.totalDurationMs,
                sessions = appEntities.map { it.toDomain() }
            )
        }
    }
    
    /**
     * Get screen time for a specific app in a date range
     */
    fun getScreenTimeByPackage(packageName: String, startDate: Long, endDate: Long): Flow<List<HourlyScreenTime>> {
        return screenTimeDao.getScreenTimeByPackage(packageName, startDate, endDate).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    /**
     * Insert screen time data
     */
    suspend fun insertScreenTime(screenTime: ScreenTimeEntity) {
        screenTimeDao.insertScreenTime(screenTime)
    }
    
    /**
     * Insert multiple screen time entries with validation
     * P0 FIX: Added validation to prevent invalid data from corrupting database
     */
    suspend fun insertScreenTimeList(screenTimeList: List<ScreenTimeEntity>) {
        try {
            val validated = mutableListOf<ScreenTimeEntity>()
            var skipped = 0
            
            screenTimeList.forEach { entity ->
                try {
                    // P0: Validate all constraints before insertion
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
                    android.util.Log.w("ScreenTimeRepository", "Skipping invalid entry: ${e.message}")
                }
            }
            
            if (skipped > 0) {
                android.util.Log.w("ScreenTimeRepository", "Validated $skipped/${screenTimeList.size} entries")
            }
            
            if (validated.isNotEmpty()) {
                screenTimeDao.insertScreenTimeList(validated)
            }
        } catch (e: Exception) {
            android.util.Log.e("ScreenTimeRepository", "Failed to insert screen time list", e)
            throw e
        }
    }
    
    /**
     * Delete old screen time data before a cutoff date
     */
    suspend fun deleteOldScreenTime(cutoffDate: Long): Int {
        return screenTimeDao.deleteOldScreenTime(cutoffDate)
    }
    
    /**
     * Get total screen time for a date range
     */
    suspend fun getTotalScreenTime(startDate: Long, endDate: Long): Long {
        return screenTimeDao.getTotalScreenTime(startDate, endDate) ?: 0L
    }
    
    /**
     * Get all sessions for a specific date, ordered by start time
     */
    suspend fun getSessionsByDate(date: Long): List<UsageSession> {
        val entities = screenTimeSessionDao.getSessionsByDate(date)
        return entities.map { it.toDomain() }
    }
    
    /**
     * Insert sessions with validation
     * P0 FIX: Added validation to ensure session data integrity
     */
    suspend fun insertSessions(sessions: List<ScreenTimeSessionEntity>) {
        try {
            val validated = mutableListOf<ScreenTimeSessionEntity>()
            var skipped = 0
            
            sessions.forEach { session ->
                try {
                    // P0: Validate session data
                    require(session.durationMs > 0) {
                        "Duration must be positive (got ${session.durationMs}ms)"
                    }
                    require(session.startTime < session.endTime) {
                        "Start time must be before end time"
                    }
                    require(session.packageName.isNotBlank()) {
                        "Package name cannot be blank"
                    }
                    require(session.appName.isNotBlank()) {
                        "App name cannot be blank"
                    }
                    require(session.date > 0) {
                        "Date must be valid timestamp"
                    }
                    
                    validated.add(session)
                    
                } catch (e: IllegalArgumentException) {
                    skipped++
                    android.util.Log.w("ScreenTimeRepository", "Skipping invalid session: ${e.message}")
                }
            }
            
            if (skipped > 0) {
                android.util.Log.w("ScreenTimeRepository", "Validated $skipped/${sessions.size} sessions")
            }
            
            if (validated.isNotEmpty()) {
                screenTimeSessionDao.insertSessions(validated)
            }
        } catch (e: Exception) {
            android.util.Log.e("ScreenTimeRepository", "Failed to insert sessions", e)
            throw e
        }
    }
    
    /**
     * Delete old sessions before a cutoff date
     */
    suspend fun deleteOldSessions(cutoffDate: Long): Int {
        return screenTimeSessionDao.deleteOldSessions(cutoffDate)
    }
    
    /**
     * Helper function to get day start timestamp (midnight)
     */
    private fun getDayStart(timestamp: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}

/**
 * Extension function to convert ScreenTimeSessionEntity to domain model
 */
private fun ScreenTimeSessionEntity.toDomain(): UsageSession {
    return UsageSession(
        packageName = packageName,
        appName = appName,
        startTime = startTime,
        endTime = endTime,
        durationMs = durationMs
    )
}

/**
 * Extension function to convert ScreenTimeEntity to domain model
 */
private fun ScreenTimeEntity.toDomain(): HourlyScreenTime {
    return HourlyScreenTime(
        hour = hour,
        durationMs = durationMs,
        packageName = packageName,
        appName = appName
    )
}

