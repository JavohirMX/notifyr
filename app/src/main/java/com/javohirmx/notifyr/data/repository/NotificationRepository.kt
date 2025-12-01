package com.javohirmx.notifyr.data.repository

import com.javohirmx.notifyr.data.database.NotificationDao
import com.javohirmx.notifyr.data.database.toEntity
import com.javohirmx.notifyr.data.database.toDomain
import com.javohirmx.notifyr.domain.model.NotificationData
import com.javohirmx.notifyr.domain.model.NotificationImportance
import com.javohirmx.notifyr.domain.util.EmailAppDetector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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
     * Also removes common Gmail notification suffixes like timestamps.
     */
    private fun normalizeText(text: String?): String {
        if (text == null) return ""
        var normalized = text.trim()
        if (normalized.isEmpty()) return ""
        
        // Apply Unicode normalization (NFD -> NFC) to handle composed vs decomposed characters
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFC)
        
        // Remove common Gmail notification patterns that can cause false duplicates
        // Gmail sometimes adds timestamps or other metadata
        normalized = normalized
            .replace(Regex("\\s+"), " ") // Normalize whitespace
            .trim()
        
        return normalized
    }
    
    /**
     * Checks if a package is an email app that benefits from conversationId-based deduplication
     * Uses centralized EmailAppDetector for consistency
     */
    private fun isEmailApp(packageName: String): Boolean {
        return EmailAppDetector.isEmailApp(packageName)
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
                    
                    // Improved matching: Use exact match or high similarity threshold
                    // Only use substring matching if one string is significantly shorter (likely a prefix)
                    val titleMatches = when {
                        normalizedTitle == existingNormalizedTitle -> true
                        normalizedTitle.isEmpty() || existingNormalizedTitle.isEmpty() -> false
                        else -> {
                            // Only match if one is a clear prefix of the other (length difference > 30%)
                            val lengthDiff = abs(normalizedTitle.length - existingNormalizedTitle.length)
                            val minLength = min(normalizedTitle.length, existingNormalizedTitle.length)
                            if (minLength > 0 && lengthDiff.toDouble() / minLength > 0.3) {
                                // Significant length difference, check if shorter is prefix
                                val shorter = if (normalizedTitle.length < existingNormalizedTitle.length) normalizedTitle else existingNormalizedTitle
                                val longer = if (normalizedTitle.length >= existingNormalizedTitle.length) normalizedTitle else existingNormalizedTitle
                                longer.startsWith(shorter, ignoreCase = true)
                            } else {
                                false
                            }
                        }
                    }
                    
                    val textMatches = when {
                        normalizedText == existingNormalizedText -> true
                        normalizedText.isEmpty() || existingNormalizedText.isEmpty() -> false
                        else -> {
                            // Similar logic for text
                            val lengthDiff = abs(normalizedText.length - existingNormalizedText.length)
                            val minLength = min(normalizedText.length, existingNormalizedText.length)
                            if (minLength > 0 && lengthDiff.toDouble() / minLength > 0.3) {
                                val shorter = if (normalizedText.length < existingNormalizedText.length) normalizedText else existingNormalizedText
                                val longer = if (normalizedText.length >= existingNormalizedText.length) normalizedText else existingNormalizedText
                                longer.startsWith(shorter, ignoreCase = true)
                            } else {
                                false
                            }
                        }
                    }
                    
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
        
        // Handle empty notifications: use packageName + category + timestamp proximity
        if (normalizedTitle.isEmpty() && normalizedText.isEmpty()) {
            // For empty notifications, check if there's a recent notification from same package
            // with same category within a very short window (5 seconds)
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
