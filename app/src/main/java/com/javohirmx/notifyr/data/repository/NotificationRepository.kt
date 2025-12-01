package com.javohirmx.notifyr.data.repository

import com.javohirmx.notifyr.data.database.NotificationDao
import com.javohirmx.notifyr.data.database.toEntity
import com.javohirmx.notifyr.data.database.toDomain
import com.javohirmx.notifyr.domain.model.NotificationData
import com.javohirmx.notifyr.domain.model.NotificationImportance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.Normalizer
import kotlin.math.max

class NotificationRepository(
    private val notificationDao: NotificationDao
) {
    // Mutex for preventing race conditions in timestamp updates
    private val dedupMutex = Mutex()
    
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
     * Normalizes text for duplicate comparison by trimming whitespace,
     * handling empty/null strings consistently, and applying Unicode normalization.
     */
    private fun normalizeText(text: String?): String {
        if (text == null) return ""
        var normalized = text.trim()
        if (normalized.isEmpty()) return ""
        
        // Apply Unicode normalization (NFD -> NFC) to handle composed vs decomposed characters
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFC)
        
        // Normalize whitespace
        normalized = normalized
            .replace(Regex("\\s+"), " ") // Normalize whitespace
            .trim()
        
        return normalized
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

    /**
     * Unified deduplication that works for all apps.
     * Priority order:
     * 1. conversationId match (if available) - works for messaging/email apps
     * 2. sender + title/text match (if available) - works for messaging/email apps
     * 3. Exact packageName + title + text match (fast path)
     * 4. Normalized packageName + title + text match (handles whitespace differences)
     * 5. Empty notification matching (packageName + category + timestamp proximity)
     */
    suspend fun findRecentDuplicate(
        packageName: String,
        title: String,
        text: String,
        since: Long,
        conversationId: String? = null,
        sender: String? = null
    ): NotificationData? {
        // Priority 1: Try conversationId match (works for messaging/email apps)
        if (conversationId != null && conversationId.isNotEmpty()) {
            val convMatch = notificationDao.findRecentDuplicateByConversationId(packageName, conversationId, since)
            if (convMatch != null) {
                return convMatch.toDomain()
            }
        }
        
        // Priority 2: Try sender-based matching (works for messaging/email apps)
        // Only if sender is available and we also check title/text similarity
        if (sender != null && sender.isNotEmpty()) {
            val senderMatch = notificationDao.findRecentDuplicateBySender(packageName, sender, since)
            if (senderMatch != null) {
                val normalizedTitle = normalizeText(title)
                val normalizedText = normalizeText(text)
                val existing = senderMatch.toDomain()
                val existingNormalizedTitle = normalizeText(existing.title)
                val existingNormalizedText = normalizeText(existing.text)
                
                // Require title and text to match (exact or normalized)
                val titleMatches = normalizedTitle == existingNormalizedTitle
                val textMatches = normalizedText == existingNormalizedText
                
                // If both match, it's a duplicate
                if (titleMatches && textMatches) {
                    return existing
                }
            }
        }
        
        // Priority 3: Try exact match (fast path)
        val exactMatch = notificationDao.findRecentDuplicate(packageName, title, text, since)
        if (exactMatch != null) {
            return exactMatch.toDomain()
        }
        
        // Priority 4: Check for normalized matches within the same package
        // This handles cases where there might be whitespace differences
        val normalizedTitle = normalizeText(title)
        val normalizedText = normalizeText(text)
        
        // Handle empty notifications: use packageName + timestamp proximity
        if (normalizedTitle.isEmpty() && normalizedText.isEmpty()) {
            // For empty notifications, check if there's a recent notification from same package
            // within a very short window (5 seconds)
            val veryRecentCutoff = since.coerceAtLeast(System.currentTimeMillis() - 5_000L)
            val recentEmpty = notificationDao.findRecentNotificationsByPackage(packageName, veryRecentCutoff)
                .map { it.toDomain() }
                .firstOrNull { existing ->
                    normalizeText(existing.title).isEmpty() && 
                    normalizeText(existing.text).isEmpty()
                }
            return recentEmpty
        }
        
        // Limit the number of notifications loaded for performance
        // Only load recent notifications (last 100) to avoid memory issues
        val recentNotifications = notificationDao.findRecentNotificationsByPackage(packageName, since)
            .take(100) // Limit to 100 most recent for performance
            .map { it.toDomain() }
            .firstOrNull { existing ->
                normalizeText(existing.title) == normalizedTitle &&
                normalizeText(existing.text) == normalizedText
            }
        return recentNotifications
    }

    suspend fun updateTimestamp(id: Long, timestamp: Long) {
        notificationDao.updateTimestamp(id, timestamp)
    }

    suspend fun upsertWithDedup(notification: NotificationData, windowMs: Long): Long {
        // Fix recursive call risk: ensure we always use a positive window
        val safeWindowMs = if (windowMs <= 0L) {
            30_000L // Default 30 seconds
        } else {
            windowMs
        }
        
        // Validate timestamp to handle system clock changes
        val currentTime = System.currentTimeMillis()
        val cutoff = currentTime - safeWindowMs
        
        // If cutoff is in the future (system clock went backward), use a minimal window
        val safeCutoff = if (cutoff > currentTime) {
            currentTime - 5_000L // Use 5 second window as fallback
        } else {
            cutoff
        }
        
        // Use mutex to prevent race conditions in duplicate detection and timestamp updates
        return dedupMutex.withLock {
            val dup = findRecentDuplicate(
                packageName = notification.packageName,
                title = notification.title,
                text = notification.text,
                since = safeCutoff,
                conversationId = notification.conversationId,
                sender = notification.sender
            )
            
            if (dup != null) {
                // Refresh timestamp to keep it recent (atomic update)
                // Use max of existing and new timestamp to handle concurrent updates
                val existingTimestamp = dup.timestamp
                val newTimestamp = max(existingTimestamp, notification.timestamp)
                updateTimestamp(dup.id, newTimestamp)
                dup.id
            } else {
                insertNotification(notification)
            }
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
        // Deduplicate notifications before importing using time windows
        // Group notifications by time windows to handle duplicates properly
        val deduplicated = mutableListOf<NotificationData>()
        val seen = mutableSetOf<String>()
        
        // Sort by timestamp to process in chronological order
        val sortedNotifications = notifications.sortedBy { it.timestamp }
        
        // Use a time window for import deduplication (5 minutes)
        val importWindowMs = 5 * 60 * 1000L
        
        for (notification in sortedNotifications) {
            // Create a key for duplicate detection (escape pipe character in package name)
            val escapedPackageName = notification.packageName.replace("|", "\\|")
            val key = "$escapedPackageName|${normalizeText(notification.title)}|${normalizeText(notification.text)}"
            
            // Check if we've seen this exact notification
            if (seen.contains(key)) {
                continue
            }
            
            // Also check against existing database entries within time window
            val cutoff = notification.timestamp - importWindowMs
            val existing = findRecentDuplicate(
                packageName = notification.packageName,
                title = notification.title,
                text = notification.text,
                since = cutoff,
                conversationId = notification.conversationId,
                sender = notification.sender
            )
            
            // Only add if not a duplicate in database and not seen in import batch
            if (existing == null) {
                seen.add(key)
                deduplicated.add(notification)
            }
        }
        
        notificationDao.insertNotifications(deduplicated.map { it.toEntity() })
    }
}
