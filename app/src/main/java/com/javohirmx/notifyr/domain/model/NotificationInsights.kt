package com.javohirmx.notifyr.domain.model

import kotlinx.serialization.Serializable
import java.util.concurrent.TimeUnit

@Serializable
data class NotificationInsights(
    val totalNotifications: Int = 0,
    val urgentNotifications: Int = 0,
    val normalNotifications: Int = 0,
    val ignoredNotifications: Int = 0,
    val spamFilteredPercentage: Int = 0, // Percentage of ignored + normal
    val estimatedTimeSavedMinutes: Long = 0,
    val topSpammyApps: List<AppStat> = emptyList(),
    val topUrgentApps: List<AppStat> = emptyList(),
    val peakNotificationHours: List<HourStat> = emptyList(),
    val activeConversationCount: Int = 0,
    val averageNotificationsPerDay: Int = 0,
    val insightsGeneratedTimestamp: Long = System.currentTimeMillis()
) {
    companion object {
        // Average time a user spends on a notification if not filtered
        const val AVG_TIME_PER_NOTIFICATION_SECONDS = 5L
    }

    fun getFormattedTimeSaved(): String {
        val hours = estimatedTimeSavedMinutes / 60
        val minutes = estimatedTimeSavedMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            minutes > 0 -> "${minutes}m"
            else -> "0m"
        }
    }
}

@Serializable
data class AppStat(val appName: String, val count: Int)

@Serializable
data class HourStat(val hour: Int, val count: Int)

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
    
    fun getDurationMillis(): Long {
        return when (this) {
            TODAY -> TimeUnit.DAYS.toMillis(1)
            WEEK -> TimeUnit.DAYS.toMillis(7)
            MONTH -> TimeUnit.DAYS.toMillis(30)
            ALL_TIME -> 0L // 0L indicates no time filter
        }
    }
}

