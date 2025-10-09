package com.javohirmx.notifyr.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class NotificationInsights(
    val timeRange: TimeRange,
    val totalNotifications: Int,
    val urgentCount: Int,
    val normalCount: Int,
    val ignoredCount: Int,
    val spamFilteredPercentage: Int,
    val estimatedTimeSavedMinutes: Int,
    val topSpammyApps: List<AppNotificationStats>,
    val topImportantApps: List<AppNotificationStats>,
    val peakNotificationHours: List<Int>,
    val notificationsByDay: Map<String, Int>,
    val focusModeEffectiveness: FocusModeStats?,
    val averageNotificationsPerDay: Int,
    val mostActiveConversations: List<ConversationStats>
)

@Serializable
data class AppNotificationStats(
    val appName: String,
    val packageName: String,
    val count: Int,
    val percentage: Float,
    val mostCommonImportance: NotificationImportance
)

@Serializable
data class ConversationStats(
    val sender: String,
    val appName: String,
    val messageCount: Int,
    val lastMessageTime: Long
)

@Serializable
data class FocusModeStats(
    val totalTimeInFocusMode: Long, // milliseconds
    val notificationsSuppressed: Int,
    val productivityGainPercentage: Int
)

enum class InsightTimeRange {
    TODAY,
    WEEK,
    MONTH,
    ALL_TIME;
    
    fun getDisplayName(): String {
        return when (this) {
            TODAY -> "Today"
            WEEK -> "This Week"
            MONTH -> "This Month"
            ALL_TIME -> "All Time"
        }
    }
    
    fun getDaysCount(): Int {
        return when (this) {
            TODAY -> 1
            WEEK -> 7
            MONTH -> 30
            ALL_TIME -> Int.MAX_VALUE
        }
    }
}

data class TimeRange(
    val startTime: Long,
    val endTime: Long,
    val label: String
)

