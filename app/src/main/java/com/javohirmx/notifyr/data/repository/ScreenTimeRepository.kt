package com.javohirmx.notifyr.data.repository

import com.javohirmx.notifyr.data.database.ScreenTimeDao
import com.javohirmx.notifyr.data.database.ScreenTimeEntity
import com.javohirmx.notifyr.data.database.toDomain
import com.javohirmx.notifyr.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Calendar

class ScreenTimeRepository(
    private val screenTimeDao: ScreenTimeDao
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
            val dateEntities = entitiesByDate[aggregate.date] ?: emptyList()
            
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
            
            // Get hourly data for this day
            val hourlyData = dateEntities.map { it.toDomain() }
            
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
     * Insert multiple screen time entries
     */
    suspend fun insertScreenTimeList(screenTimeList: List<ScreenTimeEntity>) {
        screenTimeDao.insertScreenTimeList(screenTimeList)
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

/**
 * Extension function to convert HourlyScreenTime to entity
 */
fun HourlyScreenTime.toEntity(date: Long): ScreenTimeEntity {
    return ScreenTimeEntity(
        packageName = packageName,
        appName = appName,
        date = date,
        hour = hour,
        durationMs = durationMs
    )
}

