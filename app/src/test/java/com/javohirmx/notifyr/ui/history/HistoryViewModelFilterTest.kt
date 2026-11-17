package com.javohirmx.notifyr.ui.history

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.common.truth.Truth.assertThat
import com.javohirmx.notifyr.data.repository.NotificationRepository
import com.javohirmx.notifyr.domain.ml.HybridNotificationClassifier
import com.javohirmx.notifyr.domain.model.NotificationContext
import com.javohirmx.notifyr.domain.model.NotificationData
import com.javohirmx.notifyr.domain.model.NotificationGroup
import com.javohirmx.notifyr.domain.model.NotificationImportance
import com.javohirmx.notifyr.domain.model.NotificationTags
import com.javohirmx.notifyr.domain.usecase.GroupNotificationsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
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
    private lateinit var hybridClassifier: HybridNotificationClassifier
    private lateinit var viewModel: HistoryViewModel
    private lateinit var customTagRepository: com.javohirmx.notifyr.data.repository.CustomTagRepository

    private val urgentFlow = MutableStateFlow<List<NotificationData>>(emptyList())
    private val normalFlow = MutableStateFlow<List<NotificationData>>(emptyList())
    private val ignoreFlow = MutableStateFlow<List<NotificationData>>(emptyList())
    
    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        application = mock()
        notificationRepository = mock()
        appRulesRepository = mock()
        customTagRepository = mock()
        groupNotificationsUseCase = GroupNotificationsUseCase()
        hybridClassifier = mock()
        
        // Setup Application mock for SharedPreferences
        val sharedPreferences = android.content.Context.MODE_PRIVATE
        val mockSharedPreferences = mock<android.content.SharedPreferences>()
        val mockEditor = mock<android.content.SharedPreferences.Editor>()
        whenever(mockEditor.putInt(org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(mockEditor)
        whenever(mockEditor.apply()).then {}
        whenever(mockSharedPreferences.getInt(org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(0)
        whenever(mockSharedPreferences.edit()).thenReturn(mockEditor)
        whenever(application.getSharedPreferences(org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(mockSharedPreferences)
        
        // Setup default repository responses.
        // The history screen now pushes its filters down into SQL, so it observes
        // getFilteredNotifications()/getDistinctApps() rather than the raw per-importance flows.
        // Each importance gets its own controllable flow so tests can seed the visible list.
        whenever(
            notificationRepository.getFilteredNotifications(
                any(),
                anyOrNull(),
                any(),
                any(),
                any(),
                any()
            )
        ).thenAnswer { invocation ->
            flowFor(invocation.getArgument<NotificationImportance>(0))
        }
        whenever(notificationRepository.getDistinctApps())
            .thenReturn(flowOf(emptyList()))
        
        viewModel = HistoryViewModel(
            application,
            notificationRepository,
            groupNotificationsUseCase,
            appRulesRepository,
            hybridClassifier,
            customTagRepository
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
    
    @Test
    fun `marking a group read issues one batched repository call`() = runTest {
        // Regression guard. Marking rows one at a time means one transaction per notification,
        // and every commit re-invalidates the history flows, which re-read the whole database.
        val group = NotificationGroup(
            packageName = "com.test",
            appName = "Test App",
            notifications = (1L..25L).map { id ->
                createTestNotification(id = id, timestamp = id * 1000L)
            },
            firstTimestamp = 1000L,
            lastTimestamp = 25_000L,
            unreadCount = 25,
            importance = NotificationImportance.NORMAL
        )

        viewModel.markGroupAsRead(group)
        advanceUntilIdle()

        verify(notificationRepository, times(1)).markAsReadBatch(listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L, 20L, 21L, 22L, 23L, 24L, 25L))
        verify(notificationRepository, never()).markAsRead(org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }
    
    @Test
    fun `deleting a group issues one batched repository call`() = runTest {
        val group = NotificationGroup(
            packageName = "com.test",
            appName = "Test App",
            notifications = (1L..25L).map { id ->
                createTestNotification(id = id, timestamp = id * 1000L)
            },
            firstTimestamp = 1000L,
            lastTimestamp = 25_000L,
            unreadCount = 25,
            importance = NotificationImportance.NORMAL
        )

        viewModel.deleteGroup(group)
        advanceUntilIdle()

        verify(notificationRepository, times(1)).deleteByIds(listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L, 20L, 21L, 22L, 23L, 24L, 25L))
        verify(notificationRepository, never()).deleteNotification(org.mockito.kotlin.any())
    }
    
    @Test
    fun `mark all read with active search marks only matching visible notifications`() = runTest {
        normalFlow.value = listOf(
            createTestNotification(id = 1, title = "alpha alert"),
            createTestNotification(id = 2, title = "beta alert")
        )
        advanceUntilIdle()

        viewModel.updateSearchQuery("alpha")
        advanceUntilIdle()

        viewModel.markAllAsRead(NotificationImportance.NORMAL)
        advanceUntilIdle()

        verify(notificationRepository, times(1)).markAsReadBatch(listOf(1L))
        verify(notificationRepository, never()).markAllAsReadByImportance(any())
    }

    @Test
    fun `mark all read with active filter marks only the filtered notifications`() = runTest {
        normalFlow.value = listOf(
            createTestNotification(id = 1, packageName = "com.one", title = "One"),
            createTestNotification(id = 2, packageName = "com.two", title = "Two")
        )
        advanceUntilIdle()

        viewModel.toggleAppFilter("com.one")
        advanceUntilIdle()

        // The mock DAO does not apply SQL predicates, so narrow the flow as the real query would.
        normalFlow.value = normalFlow.value.filter { it.packageName == "com.one" }
        advanceUntilIdle()

        viewModel.markAllAsRead(NotificationImportance.NORMAL)
        advanceUntilIdle()

        verify(notificationRepository, times(1)).markAsReadBatch(listOf(1L))
        verify(notificationRepository, never()).markAllAsReadByImportance(any())
    }

    @Test
    fun `mark all read without search or filters keeps marking the whole tab`() = runTest {
        normalFlow.value = listOf(
            createTestNotification(id = 1),
            createTestNotification(id = 2)
        )
        advanceUntilIdle()

        viewModel.markAllAsRead(NotificationImportance.NORMAL)
        advanceUntilIdle()

        verify(notificationRepository, times(1)).markAllAsReadByImportance(NotificationImportance.NORMAL)
        verify(notificationRepository, never()).markAsReadBatch(any<List<Long>>(), any())
    }

    @Test
    fun `mark all read with active filter includes every notification in a visible group`() = runTest {
        normalFlow.value = listOf(
            createTestNotification(id = 3, timestamp = 3000L),
            createTestNotification(id = 2, timestamp = 2000L),
            createTestNotification(id = 1, timestamp = 1000L)
        )
        advanceUntilIdle()

        viewModel.toggleAppFilter("com.test")
        advanceUntilIdle()

        viewModel.markAllAsRead(NotificationImportance.NORMAL)
        advanceUntilIdle()

        verify(notificationRepository, times(1)).markAsReadBatch(listOf(3L, 2L, 1L))
        verify(notificationRepository, never()).markAllAsReadByImportance(any())
    }

    @Test
    fun `mark all read with active filter and nothing visible writes nothing`() = runTest {
        viewModel.toggleAppFilter("com.nothing")
        advanceUntilIdle()

        viewModel.markAllAsRead(NotificationImportance.NORMAL)
        advanceUntilIdle()

        verify(notificationRepository, never()).markAllAsReadByImportance(any())
        verify(notificationRepository, never()).markAsReadBatch(any<List<Long>>(), any())
    }

    @Test
    fun `refresh does not accumulate collectors`() = runTest {
        // Each refresh used to launch an additional set of collectors without cancelling the
        // previous ones, so repeated refreshes multiplied the work per database change.
        // One live subscription per importance tab is expected, i.e. 3 and not 3 x (1 + refreshes).
        viewModel.refreshData()
        viewModel.refreshData()
        viewModel.refreshData()
        advanceUntilIdle()

        verify(notificationRepository, times(3)).getFilteredNotifications(
            org.mockito.kotlin.any(),
            org.mockito.kotlin.anyOrNull(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any()
        )
    }
    
    private fun flowFor(importance: NotificationImportance): MutableStateFlow<List<NotificationData>> =
        when (importance) {
            NotificationImportance.URGENT -> urgentFlow
            NotificationImportance.NORMAL -> normalFlow
            NotificationImportance.IGNORE -> ignoreFlow
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

