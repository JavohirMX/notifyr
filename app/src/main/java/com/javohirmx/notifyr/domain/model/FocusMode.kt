package com.javohirmx.notifyr.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class FocusMode {
    NORMAL,         // All rules apply as configured
    WORK,          // Only work + critical personal
    PERSONAL,      // Only personal + critical work
    DEEP_FOCUS,    // Only CRITICAL priority
    SLEEP;         // Only emergency contacts
    
    fun getDisplayName(): String {
        return when (this) {
            NORMAL -> "Normal"
            WORK -> "Work Mode"
            PERSONAL -> "Personal Mode"
            DEEP_FOCUS -> "Deep Focus"
            SLEEP -> "Sleep Mode"
        }
    }
    
    fun getDescription(): String {
        return when (this) {
            NORMAL -> "All notifications based on your rules"
            WORK -> "Work notifications + critical personal alerts"
            PERSONAL -> "Personal notifications + critical work alerts"
            DEEP_FOCUS -> "Only critical notifications"
            SLEEP -> "Only emergency contacts"
        }
    }
    
    fun getIcon(): String {
        return when (this) {
            NORMAL -> "✨"
            WORK -> "💼"
            PERSONAL -> "🏠"
            DEEP_FOCUS -> "🎯"
            SLEEP -> "😴"
        }
    }
}

@Serializable
data class FocusSettings(
    val currentMode: FocusMode = FocusMode.NORMAL,
    val autoSwitch: Boolean = false,
    val workHours: TimeRange? = null,
    val sleepHours: TimeRange = TimeRange(22, 0, 7, 0), // 10 PM - 7 AM
    val quietHours: TimeRange? = null,
    val workDays: Set<Int> = setOf(2, 3, 4, 5, 6), // Monday-Friday (Calendar.MONDAY = 2)
    val emergencyContacts: Set<String> = emptySet()
)

@Serializable
data class TimeRange(
    val startHour: Int,     // 0-23
    val startMinute: Int,   // 0-59
    val endHour: Int,       // 0-23
    val endMinute: Int      // 0-59
) {
    fun isInRange(hour: Int, minute: Int): Boolean {
        val currentMinutes = hour * 60 + minute
        val startMinutes = startHour * 60 + startMinute
        val endMinutes = endHour * 60 + endMinute
        
        return if (startMinutes < endMinutes) {
            // Same day range (e.g., 9 AM - 5 PM)
            currentMinutes in startMinutes until endMinutes
        } else {
            // Crosses midnight (e.g., 10 PM - 7 AM)
            currentMinutes >= startMinutes || currentMinutes < endMinutes
        }
    }
    
    fun getDisplayString(): String {
        return "${formatTime(startHour, startMinute)} - ${formatTime(endHour, endMinute)}"
    }
    
    private fun formatTime(hour: Int, minute: Int): String {
        val period = if (hour < 12) "AM" else "PM"
        val displayHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return String.format("%d:%02d %s", displayHour, minute, period)
    }
}

