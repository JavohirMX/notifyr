package com.javohirmx.notifyr.domain.usecase

import com.javohirmx.notifyr.domain.model.NotificationData
import com.javohirmx.notifyr.domain.model.NotificationGroup
import com.javohirmx.notifyr.domain.model.NotificationItem

/**
 * Use case for grouping consecutive notifications from the same app
 * within a time window.
 */
class GroupNotificationsUseCase {
    
    /**
     * Groups notifications based on:
     * 1. Consecutive notifications from same app
     * 2. Within TIME_WINDOW_MS (10 minutes)
     * 3. Minimum MIN_GROUP_SIZE (3) notifications to form a group
     * 
     * @param notifications List of notifications sorted by timestamp DESC
     * @return List of NotificationItems (Single or Group)
     */
    fun execute(notifications: List<NotificationData>): List<NotificationItem> {
        if (notifications.isEmpty()) return emptyList()
        
        val result = mutableListOf<NotificationItem>()
        var currentGroup = mutableListOf<NotificationData>()
        var currentPackageName: String? = null
        
        for (notification in notifications) {
            when {
                // First notification
                currentPackageName == null -> {
                    currentPackageName = notification.packageName
                    currentGroup.add(notification)
                }
                
                // Same app and within time window
                notification.packageName == currentPackageName &&
                isWithinTimeWindow(currentGroup.last(), notification) -> {
                    currentGroup.add(notification)
                }
                
                // Different app or outside time window - finalize current group
                else -> {
                    addGroupOrIndividuals(result, currentGroup)
                    currentGroup = mutableListOf(notification)
                    currentPackageName = notification.packageName
                }
            }
        }
        
        // Handle the last group
        if (currentGroup.isNotEmpty()) {
            addGroupOrIndividuals(result, currentGroup)
        }
        
        return result
    }
    
    /**
     * Check if two notifications are within the time window
     * (considering notifications are sorted DESC by timestamp)
     */
    private fun isWithinTimeWindow(
        newer: NotificationData,
        older: NotificationData
    ): Boolean {
        return newer.timestamp - older.timestamp <= NotificationGroup.TIME_WINDOW_MS
    }
    
    /**
     * Add notifications as a group if they meet minimum size,
     * otherwise add them as individual items
     */
    private fun addGroupOrIndividuals(
        result: MutableList<NotificationItem>,
        notifications: List<NotificationData>
    ) {
        if (notifications.size >= NotificationGroup.MIN_GROUP_SIZE) {
            // Create a group
            val group = NotificationGroup(
                packageName = notifications.first().packageName,
                appName = notifications.first().appName,
                notifications = notifications,
                firstTimestamp = notifications.last().timestamp,  // Oldest
                lastTimestamp = notifications.first().timestamp,  // Newest
                unreadCount = notifications.count { !it.isRead },
                importance = notifications.first().importance  // Use importance of latest
            )
            result.add(NotificationItem.Group(group))
        } else {
            // Add as individual notifications
            notifications.forEach { notification ->
                result.add(NotificationItem.Single(notification))
            }
        }
    }
}

