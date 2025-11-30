package com.javohirmx.notifyr.widget

import com.javohirmx.notifyr.data.repository.NotificationRepository
import com.javohirmx.notifyr.data.repository.ScreenTimeRepository
import com.javohirmx.notifyr.domain.model.DailyScreenTime
import com.javohirmx.notifyr.domain.model.NotificationData
import com.javohirmx.notifyr.domain.model.NotificationImportance
import kotlinx.coroutines.flow.first
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for widget data access
 * Provides methods to fetch filtered notifications and screen time data
 */
@Singleton
class WidgetRepository @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val screenTimeRepository: ScreenTimeRepository
) {
    
    /**
     * Filter types for notification widget
     */
    enum class NotificationFilter {
        RECENT,    // Last 24 hours
        URGENT,    // Urgent importance
        NORMAL,    // Normal importance
        UNREAD,    // Unread notifications
        ALL        // All notifications
    }
    
    /**
     * Get filtered notifications based on filter type
     */
    suspend fun getFilteredNotifications(filter: NotificationFilter, limit: Int = 10): List<NotificationData> {
        val allNotifications = notificationRepository.getAllNotifications().first()
        
        val filtered = when (filter) {
            NotificationFilter.RECENT -> {
                val now = System.currentTimeMillis()
                val yesterday = now - (24 * 60 * 60 * 1000) // 24 hours ago
                allNotifications.filter { it.timestamp >= yesterday }
            }
            NotificationFilter.URGENT -> {
                allNotifications.filter { it.importance == NotificationImportance.URGENT }
            }
            NotificationFilter.NORMAL -> {
                allNotifications.filter { it.importance == NotificationImportance.NORMAL }
            }
            NotificationFilter.UNREAD -> {
                allNotifications.filter { !it.isRead }
            }
            NotificationFilter.ALL -> {
                allNotifications
            }
        }
        
        return filtered
            .sortedByDescending { it.timestamp }
            .take(limit)
    }
    
    /**
     * Get screen time data for last 7 days
     */
    suspend fun getLast7DaysScreenTime(): List<DailyScreenTime> {
        val calendar = Calendar.getInstance()
        val now = System.currentTimeMillis()
        
        // Get start of today
        calendar.timeInMillis = now
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val endDate = calendar.timeInMillis
        
        // Get start date (7 days ago)
        calendar.add(Calendar.DAY_OF_YEAR, -6) // Include today, so 6 days back
        val startDate = calendar.timeInMillis
        
        return screenTimeRepository.getDailyScreenTime(startDate, endDate)
            .sortedByDescending { it.date }
    }
    
    /**
     * Get today's screen time
     */
    suspend fun getTodayScreenTime(): DailyScreenTime? {
        val calendar = Calendar.getInstance()
        val now = System.currentTimeMillis()
        
        // Get start of today
        calendar.timeInMillis = now
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startDate = calendar.timeInMillis
        
        // Get end of today
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val endDate = calendar.timeInMillis - 1
        
        val dailyData = screenTimeRepository.getDailyScreenTime(startDate, endDate)
        return dailyData.firstOrNull()
    }
}

