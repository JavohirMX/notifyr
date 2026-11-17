package com.javohirmx.notifyr.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    
    companion object {
        /**
         * Conservative cap on bind arguments per statement. SQLite's limit on bound parameters
         * is 999 on older Android releases, so batch operations chunk well below that rather
         * than relying on the newer, much higher limit.
         */
        const val MAX_SQL_BIND_ARGS = 900
    }
    
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

    @Query("SELECT * FROM notifications WHERE timestamp >= :since ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentNotificationsSince(since: Long, limit: Int): List<NotificationEntity>

    @Query("SELECT * FROM notifications WHERE importance = :importance ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getNotificationsByImportanceLimited(importance: Int, limit: Int): List<NotificationEntity>

    @Query("SELECT * FROM notifications WHERE isRead = 0 ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getUnreadNotificationsLimited(limit: Int): List<NotificationEntity>

    @Query("SELECT * FROM notifications ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getAllNotificationsLimited(limit: Int): List<NotificationEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotifications(notifications: List<NotificationEntity>)
    
    @Update
    suspend fun updateNotification(notification: NotificationEntity)
    
    @Query("UPDATE notifications SET isRead = :isRead WHERE id = :id")
    suspend fun markAsRead(id: Long, isRead: Boolean = true)
    
    @Query("UPDATE notifications SET isRead = :isRead WHERE id IN (:ids)")
    suspend fun markAsReadChunk(ids: List<Long>, isRead: Boolean): Int

    @Query("DELETE FROM notifications WHERE id IN (:ids)")
    suspend fun deleteByIdsChunk(ids: List<Long>): Int

    /**
     * Marks a whole group read in a single transaction.
     *
     * Iterating `markAsRead` per row instead would commit one transaction per notification and,
     * because every UI flow re-queries on any table change, each commit would re-read and
     * re-process the entire history. Batching keeps it to a single invalidation.
     */
    @Transaction
    suspend fun markAsReadBatch(ids: List<Long>, isRead: Boolean = true) {
        if (ids.isEmpty()) return
        ids.chunked(MAX_SQL_BIND_ARGS).forEach { markAsReadChunk(it, isRead) }
    }

    /** Batched counterpart of [deleteNotification]; see [markAsReadBatch] for why this is one transaction. */
    @Transaction
    suspend fun deleteByIds(ids: List<Long>) {
        if (ids.isEmpty()) return
        ids.chunked(MAX_SQL_BIND_ARGS).forEach { deleteByIdsChunk(it) }
    }
    
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

    // ---- Aggregates & projections -------------------------------------------------
    // These exist so that screens which only need a number (or a short list) do not have to
    // materialise the whole history. The dashboard, app-rules screen and digest scheduler
    // previously loaded every row just to count or de-duplicate them.

    /**
     * Non-empty notifications of a given importance, newest first.
     *
     * Mirrors the `title != '' OR text != ''` rule the UI applies in Kotlin so callers can
     * count without loading rows they would immediately discard.
     */
    @Query(
        """
        SELECT COUNT(*) FROM notifications
        WHERE importance = :importance AND (title != '' OR text != '')
        """
    )
    fun countNonEmptyByImportance(importance: Int): Flow<Int>

    /** Top-N slice of [countNonEmptyByImportance], for dashboard previews. */
    @Query(
        """
        SELECT * FROM notifications
        WHERE importance = :importance AND (title != '' OR text != '')
        ORDER BY timestamp DESC
        LIMIT :limit
        """
    )
    fun getTopByImportance(importance: Int, limit: Int): Flow<List<NotificationEntity>>

    /** Distinct apps seen in the history, so the app list never loads the notification rows. */
    @Query("SELECT DISTINCT packageName, appName FROM notifications")
    fun getDistinctApps(): Flow<List<AppName>>

    /** Unread count of one importance within a window — used by the digest scheduler. */
    @Query(
        """
        SELECT COUNT(*) FROM notifications
        WHERE timestamp >= :startTime AND timestamp <= :endTime
          AND isRead = 0 AND importance = :importance
        """
    )
    suspend fun countUnreadByImportanceInRange(
        startTime: Long,
        endTime: Long,
        importance: Int
    ): Int

    /** Notifications of one importance within a window; a suspend query rather than `Flow.first()`. */
    @Query(
        """
        SELECT * FROM notifications
        WHERE timestamp >= :startTime AND timestamp <= :endTime AND importance = :importance
        ORDER BY timestamp DESC
        """
    )
    suspend fun getByImportanceInRange(startTime: Long, endTime: Long, importance: Int): List<NotificationEntity>

    // ---- History list queries ------------------------------------------------------
    // The history screen used to read every row of an importance bucket and then apply the
    // read/app/time filters in Kotlin, so each filter toggle re-read and re-processed the whole
    // history. These push the filters into SQL, so toggling one only re-reads matching rows.
    //
    // Split in two rather than using a single optional `IN` list: an empty collection expands to
    // an invalid `IN ()`, and "no app filter" is the common case anyway.
    //
    // `title != '' OR text != ''` mirrors the empty-notification rule the UI previously applied
    // in Kotlin, so rows that would be discarded never leave the database.

    @Query(
        """
        SELECT * FROM notifications
        WHERE importance = :importance
          AND (:readStatus IS NULL OR isRead = :readStatus)
          AND timestamp >= :startTime AND timestamp <= :endTime
          AND (title != '' OR text != '')
        ORDER BY timestamp DESC
        """
    )
    fun getFilteredByImportance(
        importance: Int,
        readStatus: Boolean?,
        startTime: Long,
        endTime: Long
    ): Flow<List<NotificationEntity>>

    @Query(
        """
        SELECT * FROM notifications
        WHERE importance = :importance
          AND (:readStatus IS NULL OR isRead = :readStatus)
          AND timestamp >= :startTime AND timestamp <= :endTime
          AND (title != '' OR text != '')
          AND packageName IN (:selectedApps)
          AND (:senderQuery = '' OR sender LIKE '%' || :senderQuery || '%')
        ORDER BY timestamp DESC
        """
    )
    fun getFilteredByImportanceAndApps(
        importance: Int,
        readStatus: Boolean?,
        startTime: Long,
        endTime: Long,
        selectedApps: List<String>,
        senderQuery: String
    ): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE packageName = :packageName AND title = :title AND text = :text AND timestamp >= :since ORDER BY timestamp DESC LIMIT 1")
    suspend fun findRecentDuplicate(packageName: String, title: String, text: String, since: Long): NotificationEntity?

    @Query("SELECT * FROM notifications WHERE packageName = :packageName AND timestamp >= :since ORDER BY timestamp DESC LIMIT 100")
    suspend fun findRecentNotificationsByPackage(packageName: String, since: Long): List<NotificationEntity>

    @Query("SELECT * FROM notifications WHERE packageName = :packageName AND conversationId = :conversationId AND timestamp >= :since ORDER BY timestamp DESC LIMIT 1")
    suspend fun findRecentDuplicateByConversationId(packageName: String, conversationId: String, since: Long): NotificationEntity?

    @Query("SELECT * FROM notifications WHERE packageName = :packageName AND sender = :sender AND timestamp >= :since ORDER BY timestamp DESC LIMIT 1")
    suspend fun findRecentDuplicateBySender(packageName: String, sender: String, since: Long): NotificationEntity?

    @Query("SELECT * FROM notifications WHERE packageName = :packageName AND (category = :category OR (category IS NULL AND :category IS NULL)) AND timestamp >= :since ORDER BY timestamp DESC LIMIT 1")
    suspend fun findRecentContinuousNotification(packageName: String, category: String?, since: Long): NotificationEntity?
    
    @Query("SELECT * FROM notifications WHERE packageName = :packageName AND category IS NULL AND timestamp >= :since ORDER BY timestamp DESC LIMIT 1")
    suspend fun findRecentContinuousNotificationByPackageOnly(packageName: String, since: Long): NotificationEntity?

    @Query("UPDATE notifications SET timestamp = :timestamp WHERE id = :id")
    suspend fun updateTimestamp(id: Long, timestamp: Long)
}
