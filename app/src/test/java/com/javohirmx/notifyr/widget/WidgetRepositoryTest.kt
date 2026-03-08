package com.javohirmx.notifyr.widget

import com.google.common.truth.Truth.assertThat
import com.javohirmx.notifyr.data.repository.NotificationRepository
import com.javohirmx.notifyr.data.repository.ScreenTimeRepository
import com.javohirmx.notifyr.domain.model.NotificationImportance
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class WidgetRepositoryTest {

    private lateinit var notificationRepository: NotificationRepository
    private lateinit var screenTimeRepository: ScreenTimeRepository
    private lateinit var widgetRepository: WidgetRepository

    @Before
    fun setup() {
        notificationRepository = mock()
        screenTimeRepository = mock()
        widgetRepository = WidgetRepository(notificationRepository, screenTimeRepository)
    }

    @Test
    fun `recent filter uses bounded recent query`() = runTest {
        whenever(notificationRepository.getRecentNotificationsSince(any(), eq(7))).thenReturn(emptyList())

        val result = widgetRepository.getFilteredNotifications(WidgetRepository.NotificationFilter.RECENT, limit = 7)

        assertThat(result).isEmpty()
        verify(notificationRepository).getRecentNotificationsSince(any(), eq(7))
    }

    @Test
    fun `urgent filter uses importance limited query`() = runTest {
        whenever(notificationRepository.getNotificationsByImportanceLimited(NotificationImportance.URGENT, 5))
            .thenReturn(emptyList())

        val result = widgetRepository.getFilteredNotifications(WidgetRepository.NotificationFilter.URGENT, limit = 5)

        assertThat(result).isEmpty()
        verify(notificationRepository).getNotificationsByImportanceLimited(NotificationImportance.URGENT, 5)
    }

    @Test
    fun `unread filter uses unread limited query`() = runTest {
        whenever(notificationRepository.getUnreadNotificationsLimited(9)).thenReturn(emptyList())

        val result = widgetRepository.getFilteredNotifications(WidgetRepository.NotificationFilter.UNREAD, limit = 9)

        assertThat(result).isEmpty()
        verify(notificationRepository).getUnreadNotificationsLimited(9)
    }

    @Test
    fun `all filter uses all limited query`() = runTest {
        whenever(notificationRepository.getAllNotificationsLimited(11)).thenReturn(emptyList())

        val result = widgetRepository.getFilteredNotifications(WidgetRepository.NotificationFilter.ALL, limit = 11)

        assertThat(result).isEmpty()
        verify(notificationRepository).getAllNotificationsLimited(11)
    }
}
