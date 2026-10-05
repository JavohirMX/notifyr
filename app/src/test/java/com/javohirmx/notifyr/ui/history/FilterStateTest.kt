package com.javohirmx.notifyr.ui.history

import com.google.common.truth.Truth.assertThat
import com.javohirmx.notifyr.domain.model.NotificationContext
import org.junit.Test

class FilterStateTest {
    
    @Test
    fun `default filter state has no active filters`() {
        val filterState = FilterState()
        
        assertThat(filterState.isActive).isFalse()
        assertThat(filterState.activeCount).isEqualTo(0)
    }
    
    @Test
    fun `read status filter activates filter state`() {
        val filterState = FilterState(readStatus = ReadStatusFilter.UNREAD_ONLY)
        
        assertThat(filterState.isActive).isTrue()
        assertThat(filterState.activeCount).isEqualTo(1)
    }
    
    @Test
    fun `app filter activates filter state`() {
        val filterState = FilterState(selectedApps = setOf("com.whatsapp"))
        
        assertThat(filterState.isActive).isTrue()
        assertThat(filterState.activeCount).isEqualTo(1)
    }
    
    @Test
    fun `time range filter activates filter state`() {
        val filterState = FilterState(timeRange = TimeRangeFilter.TODAY)
        
        assertThat(filterState.isActive).isTrue()
        assertThat(filterState.activeCount).isEqualTo(1)
    }
    
    @Test
    fun `context filter activates filter state`() {
        val filterState = FilterState(selectedContexts = setOf(NotificationContext.WORK))
        
        assertThat(filterState.isActive).isTrue()
        assertThat(filterState.activeCount).isEqualTo(1)
    }
    
    @Test
    fun `sender filter activates filter state`() {
        val filterState = FilterState(senderQuery = "John")
        
        assertThat(filterState.isActive).isTrue()
        assertThat(filterState.activeCount).isEqualTo(1)
    }
    
    @Test
    fun `multiple filters increase active count`() {
        val filterState = FilterState(
            readStatus = ReadStatusFilter.UNREAD_ONLY,
            selectedApps = setOf("com.whatsapp", "com.telegram"),
            timeRange = TimeRangeFilter.TODAY,
            selectedContexts = setOf(NotificationContext.WORK, NotificationContext.PERSONAL),
            senderQuery = "John"
        )
        
        assertThat(filterState.isActive).isTrue()
        assertThat(filterState.activeCount).isEqualTo(5)
    }
    
    @Test
    fun `reset returns default filter state`() {
        val filterState = FilterState(
            readStatus = ReadStatusFilter.UNREAD_ONLY,
            selectedApps = setOf("com.whatsapp"),
            timeRange = TimeRangeFilter.TODAY
        )
        
        val reset = filterState.reset()
        
        assertThat(reset.isActive).isFalse()
        assertThat(reset.activeCount).isEqualTo(0)
        assertThat(reset.readStatus).isEqualTo(ReadStatusFilter.ALL)
        assertThat(reset.selectedApps).isEmpty()
        assertThat(reset.timeRange).isEqualTo(TimeRangeFilter.ALL_TIME)
        assertThat(reset.selectedContexts).isEmpty()
        assertThat(reset.senderQuery).isEmpty()
    }
    
    @Test
    fun `filter state with ALL read status is not active`() {
        val filterState = FilterState(readStatus = ReadStatusFilter.ALL)
        
        assertThat(filterState.isActive).isFalse()
    }
    
    @Test
    fun `filter state with ALL_TIME is not active`() {
        val filterState = FilterState(timeRange = TimeRangeFilter.ALL_TIME)
        
        assertThat(filterState.isActive).isFalse()
    }

    // --- Translation to SQL predicates -------------------------------------------------
    // These drive the history query directly, so a mistake here silently shows the wrong rows.

    @Test
    fun `read status translates to isRead column value`() {
        assertThat(ReadStatusFilter.ALL.toIsReadOrNull()).isNull()
        assertThat(ReadStatusFilter.UNREAD_ONLY.toIsReadOrNull()).isFalse()
        assertThat(ReadStatusFilter.READ_ONLY.toIsReadOrNull()).isTrue()
    }

    @Test
    fun `all time resolves to unbounded range`() {
        val bounds = FilterState(timeRange = TimeRangeFilter.ALL_TIME).resolveTimeBounds(now = 1_000L)

        assertThat(bounds.first).isEqualTo(Long.MIN_VALUE)
        assertThat(bounds.last).isEqualTo(Long.MAX_VALUE)
    }

    @Test
    fun `last 7 days resolves to a window ending now`() {
        val now = 1_700_000_000_000L
        val dayMs = 24 * 60 * 60 * 1000L

        val bounds = FilterState(timeRange = TimeRangeFilter.LAST_7_DAYS).resolveTimeBounds(now = now)

        assertThat(bounds.first).isEqualTo(now - 7 * dayMs)
        assertThat(bounds.last).isEqualTo(Long.MAX_VALUE)
    }

    @Test
    fun `last 30 days resolves to a window ending now`() {
        val now = 1_700_000_000_000L
        val dayMs = 24 * 60 * 60 * 1000L

        val bounds = FilterState(timeRange = TimeRangeFilter.LAST_30_DAYS).resolveTimeBounds(now = now)

        assertThat(bounds.first).isEqualTo(now - 30 * dayMs)
        assertThat(bounds.last).isEqualTo(Long.MAX_VALUE)
    }

    @Test
    fun `custom range resolves to the supplied bounds`() {
        val filterState = FilterState(
            timeRange = TimeRangeFilter.CUSTOM,
            customTimeRange = 1_000L to 2_000L
        )

        val bounds = filterState.resolveTimeBounds(now = 9_000L)

        assertThat(bounds.first).isEqualTo(1_000L)
        assertThat(bounds.last).isEqualTo(2_000L)
    }

    @Test
    fun `custom range without bounds falls back to unbounded`() {
        val filterState = FilterState(timeRange = TimeRangeFilter.CUSTOM, customTimeRange = null)

        val bounds = filterState.resolveTimeBounds(now = 9_000L)

        assertThat(bounds.first).isEqualTo(Long.MIN_VALUE)
        assertThat(bounds.last).isEqualTo(Long.MAX_VALUE)
    }

    @Test
    fun `today starts at the beginning of the current day`() {
        val now = 1_700_000_000_000L

        val bounds = FilterState(timeRange = TimeRangeFilter.TODAY).resolveTimeBounds(now = now)

        assertThat(bounds.first).isAtMost(now)
        assertThat(bounds.last).isEqualTo(Long.MAX_VALUE)

        val startOfDay = java.util.Calendar.getInstance().apply {
            timeInMillis = now
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        assertThat(bounds.first).isEqualTo(startOfDay)
    }
}

