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

/**
 * Resolves the read-status filter to a concrete `isRead` column value for SQL.
 *
 * null means "either state" and is passed straight through to a `:readStatus IS NULL` clause.
 */
fun ReadStatusFilter.toIsReadOrNull(): Boolean? = when (this) {
    ReadStatusFilter.ALL -> null
    ReadStatusFilter.UNREAD_ONLY -> false
    ReadStatusFilter.READ_ONLY -> true
}

private const val DAY_MS = 24 * 60 * 60 * 1000L

private fun startOfDay(timestamp: Long): Long {
    val calendar = java.util.Calendar.getInstance()
    calendar.timeInMillis = timestamp
    calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
    calendar.set(java.util.Calendar.MINUTE, 0)
    calendar.set(java.util.Calendar.SECOND, 0)
    calendar.set(java.util.Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

/**
 * Inclusive timestamp bounds for the selected time range, for use as SQL predicates.
 *
 * An unbounded range yields [Long.MIN_VALUE]..[Long.MAX_VALUE] rather than a nullable pair,
 * which keeps the query a simple range scan that the `timestamp` index can serve.
 */
fun FilterState.resolveTimeBounds(now: Long = System.currentTimeMillis()): LongRange = when (timeRange) {
    TimeRangeFilter.ALL_TIME -> Long.MIN_VALUE..Long.MAX_VALUE
    TimeRangeFilter.TODAY -> startOfDay(now)..Long.MAX_VALUE
    TimeRangeFilter.LAST_7_DAYS -> (now - 7 * DAY_MS)..Long.MAX_VALUE
    TimeRangeFilter.LAST_30_DAYS -> (now - 30 * DAY_MS)..Long.MAX_VALUE
    TimeRangeFilter.CUSTOM -> {
        val range = customTimeRange
        if (range != null) range.first..range.second else Long.MIN_VALUE..Long.MAX_VALUE
    }
}

