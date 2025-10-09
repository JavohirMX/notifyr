package com.javohirmx.notifyr.domain.usecase

import com.javohirmx.notifyr.data.repository.NotificationRepository
import com.javohirmx.notifyr.domain.model.*
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class GenerateInsightsUseCase @Inject constructor(
    private val notificationRepository: NotificationRepository
) {
    
    suspend fun generateInsights(timeRange: InsightTimeRange): NotificationInsights {
        val range = calculateTimeRange(timeRange)
        val notifications = notificationRepository
            .getNotificationsByDateRange(range.startTime, range.endTime)
            .first()
        
        if (notifications.isEmpty()) {
            return createEmptyInsights(range)
        }
        
        val urgentCount = notifications.count { it.importance == NotificationImportance.URGENT }
        val normalCount = notifications.count { it.importance == NotificationImportance.NORMAL }
        val ignoredCount = notifications.count { it.importance == NotificationImportance.IGNORE }
        
        // Calculate spam filtered percentage
        val spamFilteredPercentage = if (notifications.isNotEmpty()) {
            ((ignoredCount.toFloat() / notifications.size) * 100).toInt()
        } else 0
        
        // Estimate time saved (assuming 5 seconds per spam notification avoided)
        val timeSavedMinutes = (ignoredCount * 5) / 60
        
        // Top spammy apps
        val topSpammyApps = notifications
            .filter { it.importance == NotificationImportance.IGNORE }
            .groupBy { it.packageName }
            .map { (packageName, notifs) ->
                AppNotificationStats(
                    appName = notifs.first().appName,
                    packageName = packageName,
                    count = notifs.size,
                    percentage = (notifs.size.toFloat() / ignoredCount) * 100,
                    mostCommonImportance = NotificationImportance.IGNORE
                )
            }
            .sortedByDescending { it.count }
            .take(5)
        
        // Top important apps
        val topImportantApps = notifications
            .filter { it.importance == NotificationImportance.URGENT }
            .groupBy { it.packageName }
            .map { (packageName, notifs) ->
                AppNotificationStats(
                    appName = notifs.first().appName,
                    packageName = packageName,
                    count = notifs.size,
                    percentage = (notifs.size.toFloat() / urgentCount) * 100,
                    mostCommonImportance = NotificationImportance.URGENT
                )
            }
            .sortedByDescending { it.count }
            .take(5)
        
        // Peak notification hours
        val peakHours = notifications
            .groupBy { 
                Calendar.getInstance().apply { 
                    timeInMillis = it.timestamp 
                }.get(Calendar.HOUR_OF_DAY)
            }
            .entries
            .sortedByDescending { it.value.size }
            .take(3)
            .map { it.key }
        
        // Notifications by day
        val notificationsByDay = notifications
            .groupBy { 
                Calendar.getInstance().apply { 
                    timeInMillis = it.timestamp 
                }.get(Calendar.DAY_OF_WEEK)
            }
            .mapKeys { getDayName(it.key) }
            .mapValues { it.value.size }
        
        // Most active conversations
        val mostActiveConversations = notifications
            .filter { it.conversationId != null }
            .groupBy { it.conversationId }
            .map { (_, notifs) ->
                ConversationStats(
                    sender = notifs.first().sender ?: notifs.first().appName,
                    appName = notifs.first().appName,
                    messageCount = notifs.size,
                    lastMessageTime = notifs.maxOf { it.timestamp }
                )
            }
            .sortedByDescending { it.messageCount }
            .take(5)
        
        val daysInRange = TimeUnit.MILLISECONDS.toDays(range.endTime - range.startTime).toInt() + 1
        val averagePerDay = notifications.size / daysInRange
        
        return NotificationInsights(
            timeRange = range,
            totalNotifications = notifications.size,
            urgentCount = urgentCount,
            normalCount = normalCount,
            ignoredCount = ignoredCount,
            spamFilteredPercentage = spamFilteredPercentage,
            estimatedTimeSavedMinutes = timeSavedMinutes,
            topSpammyApps = topSpammyApps,
            topImportantApps = topImportantApps,
            peakNotificationHours = peakHours,
            notificationsByDay = notificationsByDay,
            focusModeEffectiveness = null, // TODO: Calculate from focus mode usage
            averageNotificationsPerDay = averagePerDay,
            mostActiveConversations = mostActiveConversations
        )
    }
    
    private fun calculateTimeRange(timeRange: InsightTimeRange): TimeRange {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        
        return when (timeRange) {
            InsightTimeRange.TODAY -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                TimeRange(
                    startTime = calendar.timeInMillis,
                    endTime = now,
                    label = "Today"
                )
            }
            InsightTimeRange.WEEK -> {
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                TimeRange(
                    startTime = calendar.timeInMillis,
                    endTime = now,
                    label = "This Week"
                )
            }
            InsightTimeRange.MONTH -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                TimeRange(
                    startTime = calendar.timeInMillis,
                    endTime = now,
                    label = "This Month"
                )
            }
            InsightTimeRange.ALL_TIME -> {
                TimeRange(
                    startTime = 0,
                    endTime = now,
                    label = "All Time"
                )
            }
        }
    }
    
    private fun getDayName(dayOfWeek: Int): String {
        return when (dayOfWeek) {
            Calendar.SUNDAY -> "Sun"
            Calendar.MONDAY -> "Mon"
            Calendar.TUESDAY -> "Tue"
            Calendar.WEDNESDAY -> "Wed"
            Calendar.THURSDAY -> "Thu"
            Calendar.FRIDAY -> "Fri"
            Calendar.SATURDAY -> "Sat"
            else -> "Unknown"
        }
    }
    
    private fun createEmptyInsights(range: TimeRange): NotificationInsights {
        return NotificationInsights(
            timeRange = range,
            totalNotifications = 0,
            urgentCount = 0,
            normalCount = 0,
            ignoredCount = 0,
            spamFilteredPercentage = 0,
            estimatedTimeSavedMinutes = 0,
            topSpammyApps = emptyList(),
            topImportantApps = emptyList(),
            peakNotificationHours = emptyList(),
            notificationsByDay = emptyMap(),
            focusModeEffectiveness = null,
            averageNotificationsPerDay = 0,
            mostActiveConversations = emptyList()
        )
    }
}

