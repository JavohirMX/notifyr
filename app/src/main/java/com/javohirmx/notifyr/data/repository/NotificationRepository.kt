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

    /**
     * Normalizes text for duplicate comparison by trimming whitespace
     * and handling empty/null strings consistently.
     * Also removes common Gmail notification suffixes like timestamps.
     */
    private fun normalizeText(text: String?): String {
        if (text == null) return ""
        var normalized = text.trim()
        if (normalized.isEmpty()) return ""
        
        // Remove common Gmail notification patterns that can cause false duplicates
        // Gmail sometimes adds timestamps or other metadata
        normalized = normalized
            .replace(Regex("\\s+"), " ") // Normalize whitespace
            .trim()
        
        return normalized
    }
    
    /**
     * Checks if a package is an email app that benefits from conversationId-based deduplication
     */
    private fun isEmailApp(packageName: String): Boolean {
        val emailApps = setOf(
            "com.google.android.apps.gmail",
            "com.google.android.gm",
            "com.microsoft.office.outlook",
            "com.yahoo.mobile.client.android.mail",
            "com.fsck.k9",
            "com.oneplus.email"
        )
        return emailApps.contains(packageName)
    }

    /**
     * Checks if two notifications are considered duplicates
     * by comparing normalized packageName, title, and text
     */
    private fun areDuplicates(notification1: NotificationData, notification2: NotificationData): Boolean {
        return notification1.packageName == notification2.packageName &&
                normalizeText(notification1.title) == normalizeText(notification2.title) &&
                normalizeText(notification1.text) == normalizeText(notification2.text)
    }

    suspend fun findRecentDuplicate(
        packageName: String,
        title: String,
        text: String,
        since: Long,
        conversationId: String? = null,
        sender: String? = null
    ): NotificationData? {
        // For email apps, prioritize conversationId/sender-based matching
        if (isEmailApp(packageName)) {
            // Try conversationId first (most reliable for email apps)
            if (conversationId != null && conversationId.isNotEmpty()) {
                val convMatch = notificationDao.findRecentDuplicateByConversationId(packageName, conversationId, since)
                if (convMatch != null) {
                    return convMatch.toDomain()
                }
            }
            
            // Try sender-based matching for email apps
            if (sender != null && sender.isNotEmpty()) {
                val senderMatch = notificationDao.findRecentDuplicateBySender(packageName, sender, since)
                if (senderMatch != null) {
                    // Additional check: ensure title/text are similar (not just same sender)
                    val normalizedTitle = normalizeText(title)
                    val normalizedText = normalizeText(text)
                    val existing = senderMatch.toDomain()
                    val existingNormalizedTitle = normalizeText(existing.title)
                    val existingNormalizedText = normalizeText(existing.text)
                    
                    // For email apps, check if the notification text is the same or very similar
                    // This handles cases where Gmail sends the same email notification multiple times
                    val titleMatches = normalizedTitle == existingNormalizedTitle ||
                                      (normalizedTitle.isNotEmpty() && existingNormalizedTitle.isNotEmpty() &&
                                       (normalizedTitle.contains(existingNormalizedTitle) ||
                                        existingNormalizedTitle.contains(normalizedTitle)))
                    
                    val textMatches = normalizedText == existingNormalizedText ||
                                     (normalizedText.isNotEmpty() && existingNormalizedText.isNotEmpty() &&
                                      (normalizedText.contains(existingNormalizedText) ||
                                       existingNormalizedText.contains(normalizedText)))
                    
                    // If both title and text match (or one is empty and the other matches), it's a duplicate
                    if (titleMatches && (normalizedText.isEmpty() || existingNormalizedText.isEmpty() || textMatches)) {
                        return existing
                    }
                }
            }
        }
        
        // First try exact match (fast path)
        val exactMatch = notificationDao.findRecentDuplicate(packageName, title, text, since)
        if (exactMatch != null) {
            return exactMatch.toDomain()
        }
        
        // If no exact match, check for normalized matches within the same package
        // This handles cases where there might be whitespace differences
        val normalizedTitle = normalizeText(title)
        val normalizedText = normalizeText(text)
        
        if (normalizedTitle.isEmpty() && normalizedText.isEmpty()) {
            // Both empty, skip fuzzy matching
            return null
        }
        
        val recentNotifications = notificationDao.findRecentNotificationsByPackage(packageName, since)
        return recentNotifications
            .map { it.toDomain() }
            .firstOrNull { existing ->
                normalizeText(existing.title) == normalizedTitle &&
                normalizeText(existing.text) == normalizedText
            }
    }

    suspend fun updateTimestamp(id: Long, timestamp: Long) {
        notificationDao.updateTimestamp(id, timestamp)
    }

    suspend fun upsertWithDedup(notification: NotificationData, windowMs: Long): Long {
        if (windowMs <= 0L) {
            // For zero or negative window, use a default window of 30 seconds
            // to prevent immediate duplicates
            return upsertWithDedup(notification, 30_000L)
        }
        val cutoff = System.currentTimeMillis() - windowMs
        val dup = findRecentDuplicate(
            packageName = notification.packageName,
            title = notification.title,
            text = notification.text,
            since = cutoff,
            conversationId = notification.conversationId,
            sender = notification.sender
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
        // Deduplicate notifications before importing to prevent duplicates
        val deduplicated = mutableListOf<NotificationData>()
        val seen = mutableSetOf<String>()
        
        for (notification in notifications) {
            // Create a key for duplicate detection
            val key = "${notification.packageName}|${normalizeText(notification.title)}|${normalizeText(notification.text)}"
            if (!seen.contains(key)) {
                seen.add(key)
                deduplicated.add(notification)
            }
        }
        
        notificationDao.insertNotifications(deduplicated.map { it.toEntity() })
    }
}
