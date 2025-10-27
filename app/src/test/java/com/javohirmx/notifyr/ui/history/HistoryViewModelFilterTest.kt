package com.javohirmx.notifyr.ui.history

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.common.truth.Truth.assertThat
import com.javohirmx.notifyr.data.repository.NotificationRepository
import com.javohirmx.notifyr.domain.model.NotificationContext
import com.javohirmx.notifyr.domain.model.NotificationData
import com.javohirmx.notifyr.domain.model.NotificationImportance
import com.javohirmx.notifyr.domain.model.NotificationTags
import com.javohirmx.notifyr.domain.usecase.GroupNotificationsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class HistoryViewModelFilterTest {
    
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()
    
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var notificationRepository: NotificationRepository
    private lateinit var groupNotificationsUseCase: GroupNotificationsUseCase
    private lateinit var appRulesRepository: com.javohirmx.notifyr.data.repository.AppRulesRepository
    private lateinit var viewModel: HistoryViewModel
    
    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        application = mock()
        notificationRepository = mock()
        appRulesRepository = mock()
        groupNotificationsUseCase = GroupNotificationsUseCase()
        
        // Setup default repository responses
        whenever(notificationRepository.getNotificationsByImportance(NotificationImportance.URGENT))
            .thenReturn(flowOf(emptyList()))
        whenever(notificationRepository.getNotificationsByImportance(NotificationImportance.NORMAL))
            .thenReturn(flowOf(emptyList()))
        whenever(notificationRepository.getNotificationsByImportance(NotificationImportance.IGNORE))
            .thenReturn(flowOf(emptyList()))
        
        viewModel = HistoryViewModel(
            application,
            notificationRepository,
            groupNotificationsUseCase,
            appRulesRepository
        )
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }
    
    @Test
    fun `initial filter state has no active filters`() = runTest {
        assertThat(viewModel.filterState.value.isActive).isFalse()
        assertThat(viewModel.filterState.value.activeCount).isEqualTo(0)
    }
    
    @Test
    fun `updateReadStatusFilter updates filter state`() = runTest {
        viewModel.updateReadStatusFilter(ReadStatusFilter.UNREAD_ONLY)
        advanceUntilIdle()
        
        assertThat(viewModel.filterState.value.readStatus).isEqualTo(ReadStatusFilter.UNREAD_ONLY)
        assertThat(viewModel.filterState.value.isActive).isTrue()
    }
    
    @Test
    fun `toggleAppFilter adds app to filter`() = runTest {
        val packageName = "com.whatsapp"
        
        viewModel.toggleAppFilter(packageName)
        advanceUntilIdle()
        
        assertThat(viewModel.filterState.value.selectedApps).contains(packageName)
        assertThat(viewModel.filterState.value.isActive).isTrue()
    }
    
    @Test
    fun `toggleAppFilter removes app when already selected`() = runTest {
        val packageName = "com.whatsapp"
        
        viewModel.toggleAppFilter(packageName)
        advanceUntilIdle()
        assertThat(viewModel.filterState.value.selectedApps).contains(packageName)
        
        viewModel.toggleAppFilter(packageName)
        advanceUntilIdle()
        
        assertThat(viewModel.filterState.value.selectedApps).doesNotContain(packageName)
    }
    
    @Test
    fun `updateTimeRangeFilter updates filter state`() = runTest {
        viewModel.updateTimeRangeFilter(TimeRangeFilter.TODAY)
        advanceUntilIdle()
        
        assertThat(viewModel.filterState.value.timeRange).isEqualTo(TimeRangeFilter.TODAY)
        assertThat(viewModel.filterState.value.isActive).isTrue()
    }
    
    @Test
    fun `updateTimeRangeFilter with custom range sets custom time range`() = runTest {
        val start = 1000L
        val end = 2000L
        
        viewModel.updateTimeRangeFilter(TimeRangeFilter.CUSTOM, Pair(start, end))
        advanceUntilIdle()
        
        assertThat(viewModel.filterState.value.timeRange).isEqualTo(TimeRangeFilter.CUSTOM)
        assertThat(viewModel.filterState.value.customTimeRange).isEqualTo(Pair(start, end))
    }
    
    @Test
    fun `toggleContextFilter adds context to filter`() = runTest {
        viewModel.toggleContextFilter(NotificationContext.WORK)
        advanceUntilIdle()
        
        assertThat(viewModel.filterState.value.selectedContexts).contains(NotificationContext.WORK)
        assertThat(viewModel.filterState.value.isActive).isTrue()
    }
    
    @Test
    fun `toggleContextFilter removes context when already selected`() = runTest {
        viewModel.toggleContextFilter(NotificationContext.WORK)
        advanceUntilIdle()
        assertThat(viewModel.filterState.value.selectedContexts).contains(NotificationContext.WORK)
        
        viewModel.toggleContextFilter(NotificationContext.WORK)
        advanceUntilIdle()
        
        assertThat(viewModel.filterState.value.selectedContexts).doesNotContain(NotificationContext.WORK)
    }
    
    @Test
    fun `updateSenderFilter updates filter state`() = runTest {
        val sender = "John Doe"
        
        viewModel.updateSenderFilter(sender)
        advanceUntilIdle()
        
        assertThat(viewModel.filterState.value.senderQuery).isEqualTo(sender)
        assertThat(viewModel.filterState.value.isActive).isTrue()
    }
    
    @Test
    fun `clearFilters resets all filters`() = runTest {
        // Set multiple filters
        viewModel.updateReadStatusFilter(ReadStatusFilter.UNREAD_ONLY)
        viewModel.toggleAppFilter("com.whatsapp")
        viewModel.updateTimeRangeFilter(TimeRangeFilter.TODAY)
        viewModel.toggleContextFilter(NotificationContext.WORK)
        viewModel.updateSenderFilter("John")
        advanceUntilIdle()
        
        assertThat(viewModel.filterState.value.isActive).isTrue()
        
        // Clear filters
        viewModel.clearFilters()
        advanceUntilIdle()
        
        val filterState = viewModel.filterState.value
        assertThat(filterState.isActive).isFalse()
        assertThat(filterState.readStatus).isEqualTo(ReadStatusFilter.ALL)
        assertThat(filterState.selectedApps).isEmpty()
        assertThat(filterState.timeRange).isEqualTo(TimeRangeFilter.ALL_TIME)
        assertThat(filterState.selectedContexts).isEmpty()
        assertThat(filterState.senderQuery).isEmpty()
    }
    
    @Test
    fun `multiple filters increase active count`() = runTest {
        viewModel.updateReadStatusFilter(ReadStatusFilter.UNREAD_ONLY)
        viewModel.toggleAppFilter("com.whatsapp")
        viewModel.updateTimeRangeFilter(TimeRangeFilter.TODAY)
        advanceUntilIdle()
        
        assertThat(viewModel.filterState.value.activeCount).isEqualTo(3)
    }
    
    private fun createTestNotification(
        id: Long,
        packageName: String = "com.test",
        appName: String = "Test App",
        title: String = "Test",
        text: String = "Test content",
        isRead: Boolean = false,
        timestamp: Long = System.currentTimeMillis(),
        importance: NotificationImportance = NotificationImportance.NORMAL,
        contexts: Set<NotificationContext> = emptySet(),
        sender: String? = null
    ): NotificationData {
        return NotificationData(
            id = id,
            packageName = packageName,
            appName = appName,
            title = title,
            text = text,
            category = null,
            importance = importance,
            timestamp = timestamp,
            isRead = isRead,
            tags = NotificationTags(contexts = contexts),
            sender = sender
        )
    }
}

