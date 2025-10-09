package com.javohirmx.notifyr.data.repository

import com.javohirmx.notifyr.data.database.NotificationDao
import com.javohirmx.notifyr.data.database.toEntity
import com.javohirmx.notifyr.data.database.toDomain
import com.javohirmx.notifyr.domain.model.NotificationData
import com.javohirmx.notifyr.domain.model.NotificationImportance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NotificationRepository(
    private val notificationDao: NotificationDao
) {
    
    fun getAllNotifications(): Flow<List<NotificationData>> {
        return notificationDao.getAllNotifications().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    fun getNotificationsByImportance(importance: NotificationImportance): Flow<List<NotificationData>> {
        return notificationDao.getNotificationsByImportance(importance.value).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    fun getNotificationsByPackage(packageName: String): Flow<List<NotificationData>> {
        return notificationDao.getNotificationsByPackage(packageName).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    fun searchNotifications(query: String): Flow<List<NotificationData>> {
        return notificationDao.searchNotifications(query).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    fun getNotificationsByDateRange(startTime: Long, endTime: Long): Flow<List<NotificationData>> {
        return notificationDao.getNotificationsByDateRange(startTime, endTime).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    suspend fun insertNotification(notification: NotificationData): Long {
        return notificationDao.insertNotification(notification.toEntity())
    }
    
    suspend fun insertNotifications(notifications: List<NotificationData>) {
        notificationDao.insertNotifications(notifications.map { it.toEntity() })
    }
    
    suspend fun updateNotification(notification: NotificationData) {
        notificationDao.updateNotification(notification.toEntity())
    }

    suspend fun findRecentDuplicate(
        packageName: String,
        title: String,
        text: String,
        since: Long
    ): NotificationData? {
        val entity = notificationDao.findRecentDuplicate(packageName, title, text, since)
        return entity?.toDomain()
    }

    suspend fun updateTimestamp(id: Long, timestamp: Long) {
        notificationDao.updateTimestamp(id, timestamp)
    }

    suspend fun upsertWithDedup(notification: NotificationData, windowMs: Long): Long {
        if (windowMs <= 0L) {
            return insertNotification(notification)
        }
        val cutoff = System.currentTimeMillis() - windowMs
        val dup = findRecentDuplicate(
            packageName = notification.packageName,
            title = notification.title,
            text = notification.text,
            since = cutoff
        )
        return if (dup != null) {
            // Refresh timestamp to keep it recent
            updateTimestamp(dup.id, notification.timestamp)
            dup.id
        } else {
            insertNotification(notification)
        }
    }
    
    suspend fun markAsRead(id: Long, isRead: Boolean = true) {
        notificationDao.markAsRead(id, isRead)
    }
    
    suspend fun markAllAsReadByImportance(importance: NotificationImportance) {
        notificationDao.markAllAsReadByImportance(importance.value)
    }
    
    suspend fun deleteNotification(notification: NotificationData) {
        notificationDao.deleteNotification(notification.toEntity())
    }
    
    suspend fun deleteOldNotifications(cutoffTime: Long): Int {
        return notificationDao.deleteOldNotifications(cutoffTime)
    }
    
    suspend fun deleteAllNotifications() {
        notificationDao.deleteAllNotifications()
    }
    
    suspend fun getNotificationCount(): Int {
        return notificationDao.getNotificationCount()
    }
    
    suspend fun getNotificationCountByImportance(importance: NotificationImportance): Int {
        return notificationDao.getNotificationCountByImportance(importance.value)
    }
    
    suspend fun getUnreadNotificationCount(): Int {
        return notificationDao.getUnreadNotificationCount()
    }
    
    suspend fun exportNotifications(): List<NotificationData> {
        return notificationDao.getAllNotificationsSync().map { it.toDomain() }
    }
    
    suspend fun importNotifications(notifications: List<NotificationData>) {
        notificationDao.insertNotifications(notifications.map { it.toEntity() })
    }
}
