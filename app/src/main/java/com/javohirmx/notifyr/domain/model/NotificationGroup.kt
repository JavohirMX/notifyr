package com.javohirmx.notifyr.domain.model

/**
 * Represents a group of consecutive notifications from the same app
 * within a time window.
 */
data class NotificationGroup(
    val packageName: String,
    val appName: String,
    val notifications: List<NotificationData>,
    val firstTimestamp: Long,
    val lastTimestamp: Long,
    val unreadCount: Int,
    val importance: NotificationImportance
) {
    val count: Int get() = notifications.size
    val isAllRead: Boolean get() = unreadCount == 0
    
    companion object {
        /**
         * Time window for grouping notifications (10 minutes)
         */
        const val TIME_WINDOW_MS = 10 * 60 * 1000L
        
        /**
         * Minimum number of notifications to form a group
         */
        const val MIN_GROUP_SIZE = 3
    }
}

/**
 * Represents either a single notification or a group of notifications
 */
sealed class NotificationItem {
    data class Single(val notification: NotificationData) : NotificationItem()
    data class Group(val group: NotificationGroup) : NotificationItem()
    
    /**
     * Get the timestamp for sorting purposes
     */
    val timestamp: Long
        get() = when (this) {
            is Single -> notification.timestamp
            is Group -> group.lastTimestamp
        }
    
    /**
     * Get the package name
     */
    val packageName: String
        get() = when (this) {
            is Single -> notification.packageName
            is Group -> group.packageName
        }
}

