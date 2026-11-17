package com.javohirmx.notifyr.data.repository

import com.javohirmx.notifyr.data.database.NotificationDao
import com.javohirmx.notifyr.data.database.AppName
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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepository @Inject constructor(
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

    suspend fun getRecentNotificationsSince(since: Long, limit: Int): List<NotificationData> {
        return notificationDao.getRecentNotificationsSince(since, limit).map { it.toDomain() }
    }

    suspend fun getNotificationsByImportanceLimited(
        importance: NotificationImportance,
        limit: Int
    ): List<NotificationData> {
        return notificationDao.getNotificationsByImportanceLimited(importance.value, limit)
            .map { it.toDomain() }
    }

    suspend fun getUnreadNotificationsLimited(limit: Int): List<NotificationData> {
        return notificationDao.getUnreadNotificationsLimited(limit).map { it.toDomain() }
    }

    suspend fun getAllNotificationsLimited(limit: Int): List<NotificationData> {
        return notificationDao.getAllNotificationsLimited(limit).map { it.toDomain() }
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
     * 1. sender + title/text match (if available) - works for messaging/email apps
     * 2. Exact packageName + title + text match (fast path)
     * 3. Normalized packageName + title + text match (handles whitespace differences)
     * 4. Empty notification matching (packageName + timestamp proximity)
     */
    suspend fun findRecentDuplicate(
        packageName: String,
        title: String,
        text: String,
        since: Long,
        conversationId: String? = null,
        sender: String? = null
    ): NotificationData? {
        // Priority 1: Try sender-based matching (works for messaging/email apps)
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
        
        // Priority 2: Try exact match (fast path)
        val exactMatch = notificationDao.findRecentDuplicate(packageName, title, text, since)
        if (exactMatch != null) {
            return exactMatch.toDomain()
        }
        
        // Priority 3: Check for normalized matches within the same package
        // This handles cases where there might be whitespace differences
        val normalizedTitle = normalizeText(title)
        val normalizedText = normalizeText(text)
        
        // Handle empty notifications: use packageName + timestamp proximity
        if (normalizedTitle.isEmpty() && normalizedText.isEmpty()) {
            // For empty notifications, check if there's a recent notification from same package
            // within a very short window (5 seconds)
            val veryRecentCutoff = since.coerceAtLeast(System.currentTimeMillis() - 5_000L)
            val recentList = notificationDao.findRecentNotificationsByPackage(packageName, veryRecentCutoff)
                ?: emptyList()
            val recentEmpty = recentList
                .map { it.toDomain() }
                .firstOrNull { existing ->
                    normalizeText(existing.title).isEmpty() && 
                    normalizeText(existing.text).isEmpty()
                }
            return recentEmpty
        }
        
        // Limit the number of notifications loaded for performance
        // Only load recent notifications (last 100) to avoid memory issues
        val recentEntities = notificationDao.findRecentNotificationsByPackage(packageName, since)
            ?: emptyList()
        val recentNotifications = recentEntities
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

    /**
     * Refreshes an existing row with the latest classification.
     *
     * Deduplication used to update only the timestamp, which meant a re-posted notification
     * could change importance, tags, sender, or conversation without the history reflecting
     * it. The row ID and read state belong to the existing row and are preserved.
     */
    private suspend fun refreshDuplicate(
        existing: NotificationData,
        incoming: NotificationData,
        timestamp: Long
    ) {
        val refreshed = incoming.toEntity().copy(
            id = existing.id,
            timestamp = timestamp,
            isRead = existing.isRead
        )
        notificationDao.updateNotification(refreshed)
    }
    
    /**
     * Finds a recent continuous notification duplicate based on packageName + category.
     * This is used for continuous notifications (music, calls, etc.) where title/text changes frequently.
     * Falls back to packageName-only matching if category is null.
     */
    suspend fun findRecentContinuousDuplicate(
        packageName: String,
        category: String?,
        since: Long
    ): NotificationData? {
        if (category != null) {
            // Try category-based matching first
            val entity = notificationDao.findRecentContinuousNotification(packageName, category, since)
            if (entity != null) {
                return entity.toDomain()
            }
        }
        
        // Fallback: for continuous notifications without category, match on packageName only
        // This handles edge cases where FLAG_ONGOING_EVENT is set but no category is provided
        val entity = notificationDao.findRecentContinuousNotificationByPackageOnly(packageName, since)
        return entity?.toDomain()
    }

    suspend fun upsertWithDedup(notification: NotificationData, windowMs: Long, isContinuous: Boolean = false): Long {
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
            if (isContinuous) {
                // For continuous notifications, use category-based deduplication
                // (works even if category is null - will fall back to packageName-only matching)
                val dup = findRecentContinuousDuplicate(
                    packageName = notification.packageName,
                    category = notification.category,
                    since = safeCutoff
                )
                
                if (dup != null) {
                    // Update the existing continuous notification with the latest content
                    // (current track, call state, etc.). Use max of existing and new
                    // timestamp to handle concurrent updates.
                    val newTimestamp = max(dup.timestamp, notification.timestamp)
                    refreshDuplicate(dup, notification, newTimestamp)
                    return@withLock dup.id
                } else {
                    // No existing continuous notification found, insert new one
                    return@withLock insertNotification(notification)
                }
            } else {
                // For non-continuous notifications, use standard title+text deduplication
                val dup = findRecentDuplicate(
                    packageName = notification.packageName,
                    title = notification.title,
                    text = notification.text,
                    since = safeCutoff,
                    conversationId = notification.conversationId,
                    sender = notification.sender
                )
                
                if (dup != null) {
                    // Refresh the whole row, not just the timestamp, so re-classification
                    // (importance/tags/sender) is reflected in history.
                    val newTimestamp = max(dup.timestamp, notification.timestamp)
                    refreshDuplicate(dup, notification, newTimestamp)
                    dup.id
                } else {
                    insertNotification(notification)
                }
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

    /**
     * Marks many notifications read in one transaction.
     *
     * Preferred over looping [markAsRead]: every row-wise call is its own transaction, and each
     * commit invalidates the UI's Room flows, which re-query and re-process the full history.
     */
    suspend fun markAsReadBatch(ids: List<Long>, isRead: Boolean = true) {
        notificationDao.markAsReadBatch(ids, isRead)
    }

    /** Batched counterpart of [deleteNotification]; see [markAsReadBatch] for why it is one transaction. */
    suspend fun deleteByIds(ids: List<Long>) {
        notificationDao.deleteByIds(ids)
    }

    // ---- Aggregates & projections -------------------------------------------------
    // Screens that only need a number, or a short list, use these rather than materialising
    // the entire history into memory.

    fun countNonEmptyByImportance(importance: NotificationImportance): Flow<Int> {
        return notificationDao.countNonEmptyByImportance(importance.value)
    }

    fun getTopByImportance(importance: NotificationImportance, limit: Int): Flow<List<NotificationData>> {
        return notificationDao.getTopByImportance(importance.value, limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getDistinctApps(): Flow<List<AppName>> {
        return notificationDao.getDistinctApps()
    }

    suspend fun countUnreadByImportanceInRange(
        startTime: Long,
        endTime: Long,
        importance: NotificationImportance
    ): Int {
        return notificationDao.countUnreadByImportanceInRange(startTime, endTime, importance.value)
    }

    suspend fun getByImportanceInRange(
        startTime: Long,
        endTime: Long,
        importance: NotificationImportance
    ): List<NotificationData> {
        return notificationDao.getByImportanceInRange(startTime, endTime, importance.value)
            .map { it.toDomain() }
    }

    /**
     * History list for one importance with read/app/time/sender filters applied in SQL.
     *
     * Doing this in the database rather than in Kotlin means a filter change only re-reads the
     * matching rows, instead of re-reading and re-processing the entire history.
     *
     * @param readStatus null means "either read state".
     * @param selectedApps empty means "no app filter".
     */
    fun getFilteredNotifications(
        importance: NotificationImportance,
        readStatus: Boolean?,
        startTime: Long,
        endTime: Long,
        selectedApps: Set<String>,
        senderQuery: String = ""
    ): Flow<List<NotificationData>> {
        val source = if (selectedApps.isEmpty()) {
            notificationDao.getFilteredByImportance(
                importance = importance.value,
                readStatus = readStatus,
                startTime = startTime,
                endTime = endTime
            )
        } else {
            notificationDao.getFilteredByImportanceAndApps(
                importance = importance.value,
                readStatus = readStatus,
                startTime = startTime,
                endTime = endTime,
                selectedApps = selectedApps.toList(),
                senderQuery = senderQuery
            )
        }
        return source.map { entities -> entities.map { it.toDomain() } }
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
