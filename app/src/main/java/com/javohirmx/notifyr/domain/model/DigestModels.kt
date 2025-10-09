package com.javohirmx.notifyr.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class EnhancedDigest(
    val timeRange: String,
    val totalCount: Int,
    val needsAttention: List<NotificationData>,
    val conversations: List<ConversationGroup>,
    val appGroups: List<AppGroup>,
    val summary: DigestSummary
)

@Serializable
data class ConversationGroup(
    val sender: String,
    val appName: String,
    val appPackage: String,
    val messageCount: Int,
    val latestMessage: String,
    val latestTimestamp: Long,
    val notifications: List<NotificationData>
)

@Serializable
data class AppGroup(
    val appName: String,
    val appPackage: String,
    val notificationCount: Int,
    val categories: Set<String>,
    val latestTimestamp: Long,
    val notifications: List<NotificationData>
)

@Serializable
data class DigestSummary(
    val totalNotifications: Int,
    val conversationCount: Int,
    val appCount: Int,
    val needsAttentionCount: Int,
    val summaryText: String,
    val topApps: List<Pair<String, Int>>, // App name to count
    val topSenders: List<Pair<String, Int>> // Sender to count
)

@Serializable
enum class DigestMode {
    HOURLY,          // Every hour during waking hours
    WORK_BREAKS,     // 10 AM, 12 PM, 3 PM, 6 PM
    TIME_BASED,      // User sets custom times
    CONTEXT_AWARE,   // When unlocking phone after 30+ min
    ON_DEMAND;       // Manual trigger only
    
    fun getDisplayName(): String {
        return when (this) {
            HOURLY -> "Every Hour"
            WORK_BREAKS -> "During Breaks"
            TIME_BASED -> "Custom Times"
            CONTEXT_AWARE -> "Smart (On Phone Unlock)"
            ON_DEMAND -> "Manual Only"
        }
    }
}

@Serializable
data class DigestSettings(
    val mode: DigestMode = DigestMode.CONTEXT_AWARE,
    val customTimes: List<TimeOfDay> = emptyList(),
    val minNotificationThreshold: Int = 3, // Min notifications to show digest
    val unlockDelayMinutes: Int = 30, // Minutes of inactivity before showing on unlock
    val groupConversations: Boolean = true,
    val groupByApp: Boolean = true,
    val showSummary: Boolean = true
)

@Serializable
data class TimeOfDay(
    val hour: Int,      // 0-23
    val minute: Int     // 0-59
) {
    fun getDisplayString(): String {
        val period = if (hour < 12) "AM" else "PM"
        val displayHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return String.format("%d:%02d %s", displayHour, minute, period)
    }
}

