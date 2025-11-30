package com.javohirmx.notifyr.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ScreenTimeSessionDao {
    
    /**
     * Get all sessions for a specific date, ordered by start time
     */
    @Query("SELECT * FROM screen_time_sessions WHERE date = :date ORDER BY startTime ASC")
    suspend fun getSessionsByDate(date: Long): List<ScreenTimeSessionEntity>
    
    /**
     * Get all sessions for a date range, ordered by start time
     */
    @Query("SELECT * FROM screen_time_sessions WHERE date >= :startDate AND date <= :endDate ORDER BY startTime ASC")
    fun getSessionsByDateRange(startDate: Long, endDate: Long): Flow<List<ScreenTimeSessionEntity>>
    
    /**
     * Get sessions for a specific app on a specific date
     */
    @Query("SELECT * FROM screen_time_sessions WHERE packageName = :packageName AND date = :date ORDER BY startTime ASC")
    suspend fun getSessionsByAppAndDate(packageName: String, date: Long): List<ScreenTimeSessionEntity>
    
    /**
     * Insert or update a session (upsert)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ScreenTimeSessionEntity)
    
    /**
     * Insert multiple sessions
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<ScreenTimeSessionEntity>)
    
    /**
     * Delete old sessions before a cutoff date
     */
    @Query("DELETE FROM screen_time_sessions WHERE date < :cutoffDate")
    suspend fun deleteOldSessions(cutoffDate: Long): Int
    
    /**
     * Delete all sessions
     */
    @Query("DELETE FROM screen_time_sessions")
    suspend fun deleteAllSessions()
}

