package com.javohirmx.notifyr.utils

import android.content.Context
import com.javohirmx.notifyr.data.repository.NotificationRepository
import com.javohirmx.notifyr.domain.model.NotificationData
import com.javohirmx.notifyr.domain.model.NotificationImportance
import com.javohirmx.notifyr.service.NotificationManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TestNotificationHelper @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val notificationManager: NotificationManager
) {
    
    suspend fun createTestUrgentNotification() {
        val testNotification = NotificationData(
            id = System.currentTimeMillis(),
            packageName = "com.test.urgent",
            appName = "Test Banking App",
            title = "Security Alert",
            text = "Urgent: Suspicious activity detected on your account. Please verify immediately.",
            category = "security",
            importance = NotificationImportance.URGENT,
            timestamp = System.currentTimeMillis(),
            isRead = false
        )
        
        // Store in database
        notificationRepository.insertNotification(testNotification)
        
        // Show urgent notification
        notificationManager.showUrgentNotification(testNotification)
    }
    
    suspend fun createTestNormalNotification() {
        val testNotification = NotificationData(
            id = System.currentTimeMillis(),
            packageName = "com.test.normal",
            appName = "Test Social App",
            title = "New Message",
            text = "You have a new message from a friend.",
            category = "social",
            importance = NotificationImportance.NORMAL,
            timestamp = System.currentTimeMillis(),
            isRead = false
        )
        
        // Store in database
        notificationRepository.insertNotification(testNotification)
    }
    
    suspend fun createTestIgnoredNotification() {
        val testNotification = NotificationData(
            id = System.currentTimeMillis(),
            packageName = "com.test.ignored",
            appName = "Test Promo App",
            title = "Special Offer",
            text = "Get 50% off on all items today only!",
            category = "promo",
            importance = NotificationImportance.IGNORE,
            timestamp = System.currentTimeMillis(),
            isRead = false
        )
        
        // Store in database
        notificationRepository.insertNotification(testNotification)
    }
}
