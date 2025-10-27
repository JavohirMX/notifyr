package com.javohirmx.notifyr.ui.history

import com.javohirmx.notifyr.domain.model.NotificationContext

/**
 * Represents the current filter state for notification history
 */
data class FilterState(
    val readStatus: ReadStatusFilter = ReadStatusFilter.ALL,
    val selectedApps: Set<String> = emptySet(), // Package names
    val timeRange: TimeRangeFilter = TimeRangeFilter.ALL_TIME,
    val customTimeRange: Pair<Long, Long>? = null, // Start and end timestamps
    val selectedContexts: Set<NotificationContext> = emptySet(),
    val senderQuery: String = "" // For messaging apps
) {
    /**
     * Check if any filter is active
     */
    val isActive: Boolean
        get() = readStatus != ReadStatusFilter.ALL ||
                selectedApps.isNotEmpty() ||
                timeRange != TimeRangeFilter.ALL_TIME ||
                selectedContexts.isNotEmpty() ||
                senderQuery.isNotBlank()
    
    /**
     * Count of active filters
     */
    val activeCount: Int
        get() = listOf(
            readStatus != ReadStatusFilter.ALL,
            selectedApps.isNotEmpty(),
            timeRange != TimeRangeFilter.ALL_TIME,
            selectedContexts.isNotEmpty(),
            senderQuery.isNotBlank()
        ).count { it }
    
    /**
     * Reset all filters to default state
     */
    fun reset() = FilterState()
}

/**
 * Filter options for read status
 */
enum class ReadStatusFilter(val displayName: String) {
    ALL("All"),
    UNREAD_ONLY("Unread only"),
    READ_ONLY("Read only")
}

/**
 * Filter options for time range
 */
enum class TimeRangeFilter(val displayName: String) {
    ALL_TIME("All time"),
    TODAY("Today"),
    LAST_7_DAYS("Last 7 days"),
    LAST_30_DAYS("Last 30 days"),
    CUSTOM("Custom range")
}

