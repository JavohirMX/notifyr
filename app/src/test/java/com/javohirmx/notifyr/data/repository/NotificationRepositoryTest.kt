package com.javohirmx.notifyr.data.repository

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.common.truth.Truth.assertThat
import com.javohirmx.notifyr.data.database.NotificationDao
import com.javohirmx.notifyr.data.database.NotificationEntity
import com.javohirmx.notifyr.data.database.toEntity
import com.javohirmx.notifyr.domain.model.NotificationData
import com.javohirmx.notifyr.domain.model.NotificationImportance
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.kotlin.any
import org.mockito.kotlin.never

class NotificationRepositoryTest {
    
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()
    
    private lateinit var notificationDao: NotificationDao
    private lateinit var repository: NotificationRepository
    
    @Before
    fun setup() {
        notificationDao = mock()
        repository = NotificationRepository(notificationDao)
    }
    
    @Test
    fun `getAllNotifications should return mapped domain objects`() = runTest {
        // Given
        val entities = listOf(
            createTestEntity(id = 1, title = "Test 1"),
            createTestEntity(id = 2, title = "Test 2")
        )
        whenever(notificationDao.getAllNotifications()).thenReturn(flowOf(entities))
        
        // When
        val result = repository.getAllNotifications().first()
        
        // Then
        assertThat(result).hasSize(2)
        assertThat(result[0].title).isEqualTo("Test 1")
        assertThat(result[1].title).isEqualTo("Test 2")
    }
    
    @Test
    fun `getNotificationsByImportance should filter by importance correctly`() = runTest {
        // Given
        val urgentEntities = listOf(
            createTestEntity(id = 1, importance = NotificationImportance.URGENT.value)
        )
        whenever(notificationDao.getNotificationsByImportance(NotificationImportance.URGENT.value))
            .thenReturn(flowOf(urgentEntities))
        
        // When
        val result = repository.getNotificationsByImportance(NotificationImportance.URGENT).first()
        
        // Then
        assertThat(result).hasSize(1)
        assertThat(result[0].importance).isEqualTo(NotificationImportance.URGENT)
    }
    
    @Test
    fun `getNotificationsByPackage should filter by package name`() = runTest {
        // Given
        val packageName = "com.example.test"
        val entities = listOf(
            createTestEntity(id = 1, packageName = packageName)
        )
        whenever(notificationDao.getNotificationsByPackage(packageName))
            .thenReturn(flowOf(entities))
        
        // When
        val result = repository.getNotificationsByPackage(packageName).first()
        
        // Then
        assertThat(result).hasSize(1)
        assertThat(result[0].packageName).isEqualTo(packageName)
    }
    
    @Test
    fun `searchNotifications should return matching notifications`() = runTest {
        // Given
        val query = "test query"
        val entities = listOf(
            createTestEntity(id = 1, title = "Test notification", text = "Contains query")
        )
        whenever(notificationDao.searchNotifications(query))
            .thenReturn(flowOf(entities))
        
        // When
        val result = repository.searchNotifications(query).first()
        
        // Then
        assertThat(result).hasSize(1)
        assertThat(result[0].title).isEqualTo("Test notification")
    }
    
    @Test
    fun `getNotificationsByDateRange should filter by date range`() = runTest {
        // Given
        val startTime = 1000L
        val endTime = 2000L
        val entities = listOf(
            createTestEntity(id = 1, timestamp = 1500L)
        )
        whenever(notificationDao.getNotificationsByDateRange(startTime, endTime))
            .thenReturn(flowOf(entities))
        
        // When
        val result = repository.getNotificationsByDateRange(startTime, endTime).first()
        
        // Then
        assertThat(result).hasSize(1)
        assertThat(result[0].timestamp).isEqualTo(1500L)
    }
    
    @Test
    fun `insertNotification should call dao insert`() = runTest {
        // Given
        val notification = createTestNotification()
        val expectedId = 123L
        whenever(notificationDao.insertNotification(notification.toEntity()))
            .thenReturn(expectedId)
        
        // When
        val result = repository.insertNotification(notification)
        
        // Then
        assertThat(result).isEqualTo(expectedId)
        verify(notificationDao).insertNotification(notification.toEntity())
    }
    
    @Test
    fun `insertNotifications should call dao insertNotifications`() = runTest {
        // Given
        val notifications = listOf(
            createTestNotification(id = 1),
            createTestNotification(id = 2)
        )
        
        // When
        repository.insertNotifications(notifications)
        
        // Then
        verify(notificationDao).insertNotifications(notifications.map { it.toEntity() })
    }
    
    @Test
    fun `markAsRead should call dao markAsRead`() = runTest {
        // Given
        val notificationId = 123L
        
        // When
        repository.markAsRead(notificationId, true)
        
        // Then
        verify(notificationDao).markAsRead(notificationId, true)
    }
    
    @Test
    fun `markAllAsReadByImportance should call dao with correct importance value`() = runTest {
        // Given
        val importance = NotificationImportance.URGENT
        
        // When
        repository.markAllAsReadByImportance(importance)
        
        // Then
        verify(notificationDao).markAllAsReadByImportance(importance.value)
    }
    
    @Test
    fun `deleteNotification should call dao delete`() = runTest {
        // Given
        val notification = createTestNotification()
        
        // When
        repository.deleteNotification(notification)
        
        // Then
        verify(notificationDao).deleteNotification(notification.toEntity())
    }
    
    @Test
    fun `deleteOldNotifications should return count from dao`() = runTest {
        // Given
        val cutoffTime = 1000L
        val expectedCount = 5
        whenever(notificationDao.deleteOldNotifications(cutoffTime))
            .thenReturn(expectedCount)
        
        // When
        val result = repository.deleteOldNotifications(cutoffTime)
        
        // Then
        assertThat(result).isEqualTo(expectedCount)
    }
    
    @Test
    fun `deleteAllNotifications should call dao deleteAll`() = runTest {
        // When
        repository.deleteAllNotifications()
        
        // Then
        verify(notificationDao).deleteAllNotifications()
    }
    
    @Test
    fun `getNotificationCount should return count from dao`() = runTest {
        // Given
        val expectedCount = 10
        whenever(notificationDao.getNotificationCount()).thenReturn(expectedCount)
        
        // When
        val result = repository.getNotificationCount()
        
        // Then
        assertThat(result).isEqualTo(expectedCount)
    }
    
    @Test
    fun `getNotificationCountByImportance should return count for specific importance`() = runTest {
        // Given
        val importance = NotificationImportance.URGENT
        val expectedCount = 3
        whenever(notificationDao.getNotificationCountByImportance(importance.value))
            .thenReturn(expectedCount)
        
        // When
        val result = repository.getNotificationCountByImportance(importance)
        
        // Then
        assertThat(result).isEqualTo(expectedCount)
    }
    
    @Test
    fun `getUnreadNotificationCount should return unread count from dao`() = runTest {
        // Given
        val expectedCount = 7
        whenever(notificationDao.getUnreadNotificationCount()).thenReturn(expectedCount)
        
        // When
        val result = repository.getUnreadNotificationCount()
        
        // Then
        assertThat(result).isEqualTo(expectedCount)
    }
    
    @Test
    fun `upsertWithDedup inserts when no duplicate in window`() = runTest {
        // Given
        val notification = createTestNotification(
            id = 0,
            packageName = "com.example.app",
            appName = "Example",
            title = "Track playing",
            text = "Song A",
            category = "transport",
            importance = NotificationImportance.NORMAL,
            timestamp = System.currentTimeMillis(),
            isRead = false
        )
        whenever(notificationDao.findRecentDuplicate(any(), any(), any(), any())).thenReturn(null)
        whenever(notificationDao.findRecentNotificationsByPackage(any(), any())).thenReturn(emptyList())
        whenever(notificationDao.insertNotification(notification.toEntity())).thenReturn(42L)
        
        // When
        val id = repository.upsertWithDedup(notification, 60_000)
        
        // Then
        assertThat(id).isEqualTo(42L)
        verify(notificationDao).insertNotification(notification.toEntity())
    }
    
    @Test
    fun `upsertWithDedup updates timestamp when duplicate found`() = runTest {
        // Given
        val now = System.currentTimeMillis()
        val existing = NotificationEntity(
            id = 5,
            packageName = "com.example.app",
            appName = "Example",
            title = "Incoming call",
            text = "John",
            category = "call",
            importance = NotificationImportance.URGENT.value,
            timestamp = now - 5_000,
            isRead = false
        )
        whenever(notificationDao.findRecentDuplicate(any(), any(), any(), any())).thenReturn(existing)
        
        val newData = NotificationData(
            id = 0,
            packageName = existing.packageName,
            appName = existing.appName,
            title = existing.title,
            text = existing.text,
            category = existing.category,
            importance = NotificationImportance.URGENT,
            timestamp = now,
            isRead = false
        )
        
        // When
        val id = repository.upsertWithDedup(newData, 15_000)
        
        // Then
        assertThat(id).isEqualTo(existing.id)
        verify(notificationDao).updateTimestamp(existing.id, now)
        // Should not call insertNotification when duplicate is found
        verify(notificationDao, never()).insertNotification(any())
    }
    
    @Test
    fun `upsertWithDedup uses default window when windowMs is zero`() = runTest {
        // Given
        val notification = createTestNotification()
        whenever(notificationDao.findRecentDuplicate(any(), any(), any(), any())).thenReturn(null)
        whenever(notificationDao.findRecentNotificationsByPackage(any(), any())).thenReturn(emptyList())
        whenever(notificationDao.insertNotification(notification.toEntity())).thenReturn(42L)
        
        // When
        val id = repository.upsertWithDedup(notification, 0)
        
        // Then - should use default 30 second window
        verify(notificationDao).findRecentDuplicate(any(), any(), any(), any())
        assertThat(id).isEqualTo(42L)
    }
    
    @Test
    fun `upsertWithDedup handles normalized text matching`() = runTest {
        // Given
        val now = System.currentTimeMillis()
        val existing = NotificationEntity(
            id = 5,
            packageName = "com.example.app",
            appName = "Example",
            title = "  Test Title  ",  // Has whitespace
            text = "Test Text",
            category = null,
            importance = NotificationImportance.NORMAL.value,
            timestamp = now - 5_000,
            isRead = false
        )
        
        // Exact match returns null, but normalized match should find it
        whenever(notificationDao.findRecentDuplicate(any(), any(), any(), any())).thenReturn(null)
        whenever(notificationDao.findRecentNotificationsByPackage(any(), any()))
            .thenReturn(listOf(existing))
        
        val newData = NotificationData(
            id = 0,
            packageName = existing.packageName,
            appName = existing.appName,
            title = "Test Title",  // No whitespace - should match normalized
            text = existing.text,
            category = existing.category,
            importance = NotificationImportance.NORMAL,
            timestamp = now,
            isRead = false
        )
        
        // When
        val id = repository.upsertWithDedup(newData, 15_000)
        
        // Then - should find duplicate via normalized matching
        assertThat(id).isEqualTo(existing.id)
        verify(notificationDao).updateTimestamp(existing.id, now)
    }
    
    @Test
    fun `importNotifications should deduplicate notifications`() = runTest {
        // Given
        val notifications = listOf(
            createTestNotification(id = 1, title = "Test 1", text = "Text 1"),
            createTestNotification(id = 2, title = "Test 1", text = "Text 1"),  // Duplicate
            createTestNotification(id = 3, title = "Test 2", text = "Text 2"),
            createTestNotification(id = 4, title = "  Test 2  ", text = "  Text 2  ")  // Duplicate with whitespace
        )
        
        // When
        repository.importNotifications(notifications)
        
        // Then - should only insert 2 unique notifications (after deduplication)
        verify(notificationDao).insertNotifications(org.mockito.kotlin.argThat { list ->
            list.size == 2 && 
            list.any { it.title.trim() == "Test 1" } &&
            list.any { it.title.trim() == "Test 2" }
        })
    }
    
    private fun createTestEntity(
        id: Long = 1,
        packageName: String = "com.example.test",
        appName: String = "Test App",
        title: String = "Test Title",
        text: String = "Test Text",
        category: String? = null,
        importance: Int = NotificationImportance.NORMAL.value,
        timestamp: Long = System.currentTimeMillis(),
        isRead: Boolean = false
    ): NotificationEntity {
        return NotificationEntity(
            id = id,
            packageName = packageName,
            appName = appName,
            title = title,
            text = text,
            category = category,
            importance = importance,
            timestamp = timestamp,
            isRead = isRead
        )
    }
    
    private fun createTestNotification(
        id: Long = 1,
        packageName: String = "com.example.test",
        appName: String = "Test App",
        title: String = "Test Title",
        text: String = "Test Text",
        category: String? = null,
        importance: NotificationImportance = NotificationImportance.NORMAL,
        timestamp: Long = System.currentTimeMillis(),
        isRead: Boolean = false
    ): NotificationData {
        return NotificationData(
            id = id,
            packageName = packageName,
            appName = appName,
            title = title,
            text = text,
            category = category,
            importance = importance,
            timestamp = timestamp,
            isRead = isRead
        )
    }
}
