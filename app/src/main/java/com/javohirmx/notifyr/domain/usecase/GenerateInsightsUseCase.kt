package com.javohirmx.notifyr.domain.usecase

import com.javohirmx.notifyr.data.repository.NotificationRepository
import com.javohirmx.notifyr.domain.model.NotificationData
import com.javohirmx.notifyr.domain.model.NotificationImportance
import com.javohirmx.notifyr.domain.model.NotificationInsights
import com.javohirmx.notifyr.domain.model.AppStat
import com.javohirmx.notifyr.domain.model.HourStat
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GenerateInsightsUseCase @Inject constructor(
    private val notificationRepository: NotificationRepository
) {

    suspend operator fun invoke(timeRangeMillis: Long = 0L): NotificationInsights {
        val now = System.currentTimeMillis()
        val startTime = if (timeRangeMillis > 0) now - timeRangeMillis else 0L

        val notifications = notificationRepository.getNotificationsByDateRange(startTime, now).first()

        if (notifications.isEmpty()) {
            return NotificationInsights()
        }

        val urgent = notifications.count { it.importance == NotificationImportance.URGENT }
        val normal = notifications.count { it.importance == NotificationImportance.NORMAL }
        val ignored = notifications.count { it.importance == NotificationImportance.IGNORE }
        val total = notifications.size

        val spamFiltered = normal + ignored
        val spamFilteredPercentage = if (total > 0) (spamFiltered * 100) / total else 0

        val estimatedTimeSavedMinutes = TimeUnit.SECONDS.toMinutes(
            spamFiltered * NotificationInsights.AVG_TIME_PER_NOTIFICATION_SECONDS
        )

        val topSpammyApps = notifications
            .filter { it.importance == NotificationImportance.NORMAL || it.importance == NotificationImportance.IGNORE }
            .groupBy { it.appName }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(5)
            .map { AppStat(it.first, it.second) }

        val topUrgentApps = notifications
            .filter { it.importance == NotificationImportance.URGENT }
            .groupBy { it.appName }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(5)
            .map { AppStat(it.first, it.second) }

        val peakNotificationHours = notifications
            .groupBy { Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.HOUR_OF_DAY) }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(3)
            .map { HourStat(it.first, it.second) }

        val activeConversationCount = notifications
            .filter { it.conversationId != null }
            .distinctBy { it.conversationId }
            .size

        val daysCovered = if (timeRangeMillis > 0) {
            (timeRangeMillis / TimeUnit.DAYS.toMillis(1)).toInt().coerceAtLeast(1)
        } else {
            // Calculate days from first notification to last
            val firstTimestamp = notifications.minOfOrNull { it.timestamp } ?: now
            val lastTimestamp = notifications.maxOfOrNull { it.timestamp } ?: now
            ((lastTimestamp - firstTimestamp) / TimeUnit.DAYS.toMillis(1)).toInt().coerceAtLeast(1)
        }
        val averageNotificationsPerDay = if (daysCovered > 0) total / daysCovered else total

        return NotificationInsights(
            totalNotifications = total,
            urgentNotifications = urgent,
            normalNotifications = normal,
            ignoredNotifications = ignored,
            spamFilteredPercentage = spamFilteredPercentage,
            estimatedTimeSavedMinutes = estimatedTimeSavedMinutes,
            topSpammyApps = topSpammyApps,
            topUrgentApps = topUrgentApps,
            peakNotificationHours = peakNotificationHours,
            activeConversationCount = activeConversationCount,
            averageNotificationsPerDay = averageNotificationsPerDay
        )
    }
}
