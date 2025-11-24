package com.javohirmx.notifyr.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getAllNotifications(): Flow<List<NotificationEntity>>
    
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    suspend fun getAllNotificationsSync(): List<NotificationEntity>
    
    @Query("SELECT * FROM notifications WHERE importance = :importance ORDER BY timestamp DESC")
    fun getNotificationsByImportance(importance: Int): Flow<List<NotificationEntity>>
    
    @Query("SELECT * FROM notifications WHERE packageName = :packageName ORDER BY timestamp DESC")
    fun getNotificationsByPackage(packageName: String): Flow<List<NotificationEntity>>
    
    @Query("SELECT * FROM notifications WHERE title LIKE '%' || :query || '%' OR text LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchNotifications(query: String): Flow<List<NotificationEntity>>
    
    @Query("SELECT * FROM notifications WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    fun getNotificationsByDateRange(startTime: Long, endTime: Long): Flow<List<NotificationEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotifications(notifications: List<NotificationEntity>)
    
    @Update
    suspend fun updateNotification(notification: NotificationEntity)
    
    @Query("UPDATE notifications SET isRead = :isRead WHERE id = :id")
    suspend fun markAsRead(id: Long, isRead: Boolean = true)
    
    @Query("UPDATE notifications SET isRead = 1 WHERE importance = :importance")
    suspend fun markAllAsReadByImportance(importance: Int)
    
    @Delete
    suspend fun deleteNotification(notification: NotificationEntity)
    
    @Query("DELETE FROM notifications WHERE timestamp < :cutoffTime")
    suspend fun deleteOldNotifications(cutoffTime: Long): Int
    
    @Query("DELETE FROM notifications")
    suspend fun deleteAllNotifications()
    
    @Query("SELECT COUNT(*) FROM notifications")
    suspend fun getNotificationCount(): Int
    
    @Query("SELECT COUNT(*) FROM notifications WHERE importance = :importance")
    suspend fun getNotificationCountByImportance(importance: Int): Int
    
    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0")
    suspend fun getUnreadNotificationCount(): Int

    @Query("SELECT * FROM notifications WHERE packageName = :packageName AND title = :title AND text = :text AND timestamp > :since ORDER BY timestamp DESC LIMIT 1")
    suspend fun findRecentDuplicate(packageName: String, title: String, text: String, since: Long): NotificationEntity?

    @Query("SELECT * FROM notifications WHERE packageName = :packageName AND timestamp > :since ORDER BY timestamp DESC")
    suspend fun findRecentNotificationsByPackage(packageName: String, since: Long): List<NotificationEntity>

    @Query("UPDATE notifications SET timestamp = :timestamp WHERE id = :id")
    suspend fun updateTimestamp(id: Long, timestamp: Long)
}
