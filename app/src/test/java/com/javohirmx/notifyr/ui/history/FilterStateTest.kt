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
}

