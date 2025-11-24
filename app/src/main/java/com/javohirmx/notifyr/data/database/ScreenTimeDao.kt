package com.javohirmx.notifyr.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ScreenTimeDao {
    
    /**
     * Get all screen time entries for a date range
     */
    @Query("SELECT * FROM screen_time WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC, hour DESC")
    fun getScreenTimeByDateRange(startDate: Long, endDate: Long): Flow<List<ScreenTimeEntity>>
    
    /**
     * Get screen time entries for a specific date
     */
    @Query("SELECT * FROM screen_time WHERE date = :date ORDER BY hour ASC")
    suspend fun getScreenTimeByDate(date: Long): List<ScreenTimeEntity>
    
    /**
     * Get daily aggregated screen time for a date range
     */
    @Query("""
        SELECT 
            date,
            SUM(durationMs) as totalDurationMs,
            COUNT(DISTINCT packageName) as appCount
        FROM screen_time
        WHERE date >= :startDate AND date <= :endDate
        GROUP BY date
        ORDER BY date DESC
    """)
    suspend fun getDailyAggregates(startDate: Long, endDate: Long): List<DailyAggregate>
    
    /**
     * Get hourly screen time for a specific date
     */
    @Query("SELECT * FROM screen_time WHERE date = :date ORDER BY hour ASC")
    fun getHourlyScreenTime(date: Long): Flow<List<ScreenTimeEntity>>
    
    /**
     * Get screen time grouped by app for a date range
     */
    @Query("""
        SELECT 
            packageName,
            appName,
            SUM(durationMs) as totalDurationMs
        FROM screen_time
        WHERE date >= :startDate AND date <= :endDate
        GROUP BY packageName, appName
        ORDER BY totalDurationMs DESC
    """)
    suspend fun getAppScreenTime(startDate: Long, endDate: Long): List<AppAggregate>
    
    /**
     * Get screen time for a specific app in a date range
     */
    @Query("SELECT * FROM screen_time WHERE packageName = :packageName AND date >= :startDate AND date <= :endDate ORDER BY date DESC, hour DESC")
    fun getScreenTimeByPackage(packageName: String, startDate: Long, endDate: Long): Flow<List<ScreenTimeEntity>>
    
    /**
     * Get hourly screen time for a specific app on a specific date
     */
    @Query("SELECT * FROM screen_time WHERE packageName = :packageName AND date = :date ORDER BY hour ASC")
    suspend fun getHourlyScreenTimeByApp(packageName: String, date: Long): List<ScreenTimeEntity>
    
    /**
     * Insert or update screen time entry (upsert)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScreenTime(screenTime: ScreenTimeEntity)
    
    /**
     * Insert multiple screen time entries
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScreenTimeList(screenTimeList: List<ScreenTimeEntity>)
    
    /**
     * Delete old screen time data before a cutoff date
     */
    @Query("DELETE FROM screen_time WHERE date < :cutoffDate")
    suspend fun deleteOldScreenTime(cutoffDate: Long): Int
    
    /**
     * Delete all screen time data
     */
    @Query("DELETE FROM screen_time")
    suspend fun deleteAllScreenTime()
    
    /**
     * Get total screen time for a date range
     */
    @Query("SELECT SUM(durationMs) FROM screen_time WHERE date >= :startDate AND date <= :endDate")
    suspend fun getTotalScreenTime(startDate: Long, endDate: Long): Long?
}

/**
 * Data class for daily aggregate queries
 */
data class DailyAggregate(
    val date: Long,
    val totalDurationMs: Long,
    val appCount: Int
)

/**
 * Data class for app aggregate queries
 */
data class AppAggregate(
    val packageName: String,
    val appName: String,
    val totalDurationMs: Long
)

