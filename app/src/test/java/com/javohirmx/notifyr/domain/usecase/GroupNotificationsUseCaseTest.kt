package com.javohirmx.notifyr.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.javohirmx.notifyr.domain.model.NotificationData
import com.javohirmx.notifyr.domain.model.NotificationGroup
import com.javohirmx.notifyr.domain.model.NotificationImportance
import com.javohirmx.notifyr.domain.model.NotificationItem
import org.junit.Before
import org.junit.Test

class GroupNotificationsUseCaseTest {
    
    private lateinit var useCase: GroupNotificationsUseCase
    
    @Before
    fun setup() {
        useCase = GroupNotificationsUseCase()
    }
    
    @Test
    fun `empty list returns empty result`() {
        val result = useCase.execute(emptyList())
        
        assertThat(result).isEmpty()
    }
    
    @Test
    fun `single notification returns single item`() {
        val notification = createNotification(
            id = 1,
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            timestamp = System.currentTimeMillis()
        )
        
        val result = useCase.execute(listOf(notification))
        
        assertThat(result).hasSize(1)
        assertThat(result[0]).isInstanceOf(NotificationItem.Single::class.java)
        assertThat((result[0] as NotificationItem.Single).notification).isEqualTo(notification)
    }
    
    @Test
    fun `two consecutive notifications from same app dont form group (less than min size)`() {
        val baseTime = System.currentTimeMillis()
        val notifications = listOf(
            createNotification(1, "com.whatsapp", "WhatsApp", baseTime),
            createNotification(2, "com.whatsapp", "WhatsApp", baseTime - 60000) // 1 min earlier
        )
        
        val result = useCase.execute(notifications)
        
        assertThat(result).hasSize(2)
        assertThat(result[0]).isInstanceOf(NotificationItem.Single::class.java)
        assertThat(result[1]).isInstanceOf(NotificationItem.Single::class.java)
    }
    
    @Test
    fun `three consecutive notifications from same app within time window form group`() {
        val baseTime = System.currentTimeMillis()
        val notifications = listOf(
            createNotification(1, "com.whatsapp", "WhatsApp", baseTime),
            createNotification(2, "com.whatsapp", "WhatsApp", baseTime - 60000), // 1 min
            createNotification(3, "com.whatsapp", "WhatsApp", baseTime - 120000) // 2 min
        )
        
        val result = useCase.execute(notifications)
        
        assertThat(result).hasSize(1)
        assertThat(result[0]).isInstanceOf(NotificationItem.Group::class.java)
        
        val group = (result[0] as NotificationItem.Group).group
        assertThat(group.count).isEqualTo(3)
        assertThat(group.packageName).isEqualTo("com.whatsapp")
        assertThat(group.appName).isEqualTo("WhatsApp")
    }
    
    @Test
    fun `notifications from different apps dont group together`() {
        val baseTime = System.currentTimeMillis()
        val notifications = listOf(
            createNotification(1, "com.whatsapp", "WhatsApp", baseTime),
            createNotification(2, "com.whatsapp", "WhatsApp", baseTime - 60000),
            createNotification(3, "com.telegram", "Telegram", baseTime - 120000),
            createNotification(4, "com.telegram", "Telegram", baseTime - 180000),
            createNotification(5, "com.telegram", "Telegram", baseTime - 240000)
        )
        
        val result = useCase.execute(notifications)
        
        // WhatsApp: 2 singles (less than min)
        // Telegram: 1 group (3 notifications)
        assertThat(result).hasSize(3)
        assertThat(result[0]).isInstanceOf(NotificationItem.Single::class.java)
        assertThat(result[1]).isInstanceOf(NotificationItem.Single::class.java)
        assertThat(result[2]).isInstanceOf(NotificationItem.Group::class.java)
    }
    
    @Test
    fun `notifications outside time window dont group together`() {
        val baseTime = System.currentTimeMillis()
        val notifications = listOf(
            // First group - within window (notifications are consecutive)
            createNotification(1, "com.whatsapp", "WhatsApp", baseTime),
            createNotification(2, "com.whatsapp", "WhatsApp", baseTime - 60000), // 1 min from #1
            createNotification(3, "com.whatsapp", "WhatsApp", baseTime - 120000), // 1 min from #2 (2 min from #1)
            // Gap more than 10 minutes from previous notification
            createNotification(4, "com.whatsapp", "WhatsApp", baseTime - (13 * 60 * 1000)), // 13 min from #1, >10 min from #3
            createNotification(5, "com.whatsapp", "WhatsApp", baseTime - (14 * 60 * 1000)), // 1 min from #4
            createNotification(6, "com.whatsapp", "WhatsApp", baseTime - (15 * 60 * 1000)) // 1 min from #5
        )
        
        val result = useCase.execute(notifications)
        
        // Should have 2 groups (both meet min size of 3)
        assertThat(result).hasSize(2)
        assertThat(result[0]).isInstanceOf(NotificationItem.Group::class.java)
        assertThat(result[1]).isInstanceOf(NotificationItem.Group::class.java)
        assertThat((result[0] as NotificationItem.Group).group.count).isEqualTo(3)
        assertThat((result[1] as NotificationItem.Group).group.count).isEqualTo(3)
    }
    
    @Test
    fun `unread count is calculated correctly in group`() {
        val baseTime = System.currentTimeMillis()
        val notifications = listOf(
            createNotification(1, "com.whatsapp", "WhatsApp", baseTime, isRead = false),
            createNotification(2, "com.whatsapp", "WhatsApp", baseTime - 60000, isRead = true),
            createNotification(3, "com.whatsapp", "WhatsApp", baseTime - 120000, isRead = false)
        )
        
        val result = useCase.execute(notifications)
        
        assertThat(result).hasSize(1)
        val group = (result[0] as NotificationItem.Group).group
        assertThat(group.unreadCount).isEqualTo(2)
        assertThat(group.isAllRead).isFalse()
    }
    
    @Test
    fun `all read notifications have correct isAllRead flag`() {
        val baseTime = System.currentTimeMillis()
        val notifications = listOf(
            createNotification(1, "com.whatsapp", "WhatsApp", baseTime, isRead = true),
            createNotification(2, "com.whatsapp", "WhatsApp", baseTime - 60000, isRead = true),
            createNotification(3, "com.whatsapp", "WhatsApp", baseTime - 120000, isRead = true)
        )
        
        val result = useCase.execute(notifications)
        
        assertThat(result).hasSize(1)
        val group = (result[0] as NotificationItem.Group).group
        assertThat(group.unreadCount).isEqualTo(0)
        assertThat(group.isAllRead).isTrue()
    }
    
    @Test
    fun `group importance uses latest notification importance`() {
        val baseTime = System.currentTimeMillis()
        val notifications = listOf(
            createNotification(1, "com.whatsapp", "WhatsApp", baseTime, importance = NotificationImportance.URGENT),
            createNotification(2, "com.whatsapp", "WhatsApp", baseTime - 60000, importance = NotificationImportance.NORMAL),
            createNotification(3, "com.whatsapp", "WhatsApp", baseTime - 120000, importance = NotificationImportance.NORMAL)
        )
        
        val result = useCase.execute(notifications)
        
        assertThat(result).hasSize(1)
        val group = (result[0] as NotificationItem.Group).group
        assertThat(group.importance).isEqualTo(NotificationImportance.URGENT)
    }
    
    @Test
    fun `complex scenario with multiple apps and groups`() {
        val baseTime = System.currentTimeMillis()
        val notifications = listOf(
            // WhatsApp group (3)
            createNotification(1, "com.whatsapp", "WhatsApp", baseTime),
            createNotification(2, "com.whatsapp", "WhatsApp", baseTime - 60000),
            createNotification(3, "com.whatsapp", "WhatsApp", baseTime - 120000),
            // Gmail single
            createNotification(4, "com.google.gmail", "Gmail", baseTime - 180000),
            // Telegram group (4)
            createNotification(5, "com.telegram", "Telegram", baseTime - 240000),
            createNotification(6, "com.telegram", "Telegram", baseTime - 300000),
            createNotification(7, "com.telegram", "Telegram", baseTime - 360000),
            createNotification(8, "com.telegram", "Telegram", baseTime - 420000),
            // Slack singles (2)
            createNotification(9, "com.slack", "Slack", baseTime - 480000),
            createNotification(10, "com.slack", "Slack", baseTime - 540000)
        )
        
        val result = useCase.execute(notifications)
        
        assertThat(result).hasSize(5)
        
        // WhatsApp group
        assertThat(result[0]).isInstanceOf(NotificationItem.Group::class.java)
        assertThat((result[0] as NotificationItem.Group).group.count).isEqualTo(3)
        
        // Gmail single
        assertThat(result[1]).isInstanceOf(NotificationItem.Single::class.java)
        
        // Telegram group
        assertThat(result[2]).isInstanceOf(NotificationItem.Group::class.java)
        assertThat((result[2] as NotificationItem.Group).group.count).isEqualTo(4)
        
        // Slack singles
        assertThat(result[3]).isInstanceOf(NotificationItem.Single::class.java)
        assertThat(result[4]).isInstanceOf(NotificationItem.Single::class.java)
    }
    
    private fun createNotification(
        id: Long,
        packageName: String,
        appName: String,
        timestamp: Long,
        isRead: Boolean = false,
        importance: NotificationImportance = NotificationImportance.NORMAL
    ): NotificationData {
        return NotificationData(
            id = id,
            packageName = packageName,
            appName = appName,
            title = "Test notification $id",
            text = "Test content",
            category = null,
            importance = importance,
            timestamp = timestamp,
            isRead = isRead
        )
    }
}

