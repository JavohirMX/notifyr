package com.javohirmx.notifyr.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents screen time for a specific app
 */
@Serializable
data class AppScreenTime(
    val packageName: String,
    val appName: String,
    val totalDurationMs: Long,
    val sessions: List<HourlyScreenTime> = emptyList()
) {
    fun getFormattedDuration(): String {
        return formatDuration(totalDurationMs)
    }
}

/**
 * Represents screen time for a single day
 */
@Serializable
data class DailyScreenTime(
    val date: Long, // Day start timestamp (midnight in milliseconds)
    val totalDurationMs: Long,
    val appBreakdown: List<AppScreenTime> = emptyList(),
    val hourlyData: List<HourlyScreenTime> = emptyList()
) {
    fun getFormattedDuration(): String {
        return formatDuration(totalDurationMs)
    }
    
    fun getFormattedDate(): String {
        val dateFormat = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
        return dateFormat.format(java.util.Date(date))
    }
    
    fun getDayOfWeek(): String {
        val dateFormat = java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault())
        return dateFormat.format(java.util.Date(date))
    }
}

/**
 * Represents screen time for a specific hour
 */
@Serializable
data class HourlyScreenTime(
    val hour: Int, // 0-23
    val durationMs: Long,
    val packageName: String,
    val appName: String
) {
    fun getFormattedDuration(): String {
        return formatDuration(durationMs)
    }
    
    fun getFormattedHour(): String {
        val hour12 = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
        val amPm = if (hour < 12) "AM" else "PM"
        return "$hour12:00 $amPm"
    }
}

/**
 * Time range options for screen time queries
 */
enum class ScreenTimeRange {
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
    
    fun getStartTimeMillis(): Long {
        val now = System.currentTimeMillis()
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = now
        
        return when (this) {
            TODAY -> {
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                calendar.timeInMillis
            }
            WEEK -> {
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                calendar.add(java.util.Calendar.DAY_OF_YEAR, -6) // Include today, so 6 days back
                calendar.timeInMillis
            }
            MONTH -> {
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                calendar.add(java.util.Calendar.DAY_OF_MONTH, -29) // Include today, so 29 days back
                calendar.timeInMillis
            }
            ALL_TIME -> 0L
        }
    }
    
    fun getEndTimeMillis(): Long {
        return System.currentTimeMillis()
    }
}

/**
 * Represents a single usage session with exact start and end times
 */
@Serializable
data class UsageSession(
    val packageName: String,
    val appName: String,
    val startTime: Long, // Session start timestamp in milliseconds
    val endTime: Long, // Session end timestamp in milliseconds
    val durationMs: Long // Duration in milliseconds
) {
    fun getFormattedTimeRange(): String {
        val startFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        val endFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        val start = startFormat.format(java.util.Date(startTime))
        val end = endFormat.format(java.util.Date(endTime))
        return "$start to $end"
    }
    
    fun getFormattedDuration(): String {
        return formatDuration(durationMs)
    }
}

/**
 * Helper function to format duration in milliseconds to human-readable string
 */
fun formatDuration(durationMs: Long): String {
    val seconds = durationMs / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    
    return when {
        hours > 0 -> {
            val remainingMinutes = minutes % 60
            if (remainingMinutes > 0) {
                "${hours}h ${remainingMinutes}m"
            } else {
                "${hours}h"
            }
        }
        minutes > 0 -> {
            val remainingSeconds = seconds % 60
            if (remainingSeconds > 0) {
                "${minutes}m ${remainingSeconds}s"
            } else {
                "${minutes}m"
            }
        }
        seconds > 0 -> "${seconds}s"
        else -> "0s"
    }
}

