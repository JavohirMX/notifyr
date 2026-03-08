package com.javohirmx.notifyr.domain.rules

import com.google.common.truth.Truth.assertThat
import com.javohirmx.notifyr.domain.model.NotificationData
import com.javohirmx.notifyr.domain.model.NotificationImportance
import org.junit.Test

class SyncStatusNotificationDetectorTest {

    private val detector = SyncStatusNotificationDetector()

    @Test
    fun `should detect whatsapp checking message as sync status`() {
        val notification = createNotification(
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            title = "WhatsApp",
            text = "Checking for new messages"
        )

        assertThat(detector.isSyncStatusNotification(notification)).isTrue()
    }

    @Test
    fun `should detect email syncing message as sync status`() {
        val notification = createNotification(
            packageName = "com.google.android.gm",
            appName = "Gmail",
            title = "Gmail",
            text = "Syncing mail"
        )

        assertThat(detector.isSyncStatusNotification(notification)).isTrue()
    }

    @Test
    fun `should not detect real chat message as sync status`() {
        val notification = createNotification(
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            title = "Alice",
            text = "Can you call me now?"
        )

        assertThat(detector.isSyncStatusNotification(notification)).isFalse()
    }

    @Test
    fun `should detect progress category with syncing text`() {
        val notification = createNotification(
            packageName = "com.example.app",
            appName = "Example App",
            title = "Sync",
            text = "Updating inbox",
            category = "progress"
        )

        assertThat(detector.isSyncStatusNotification(notification)).isTrue()
    }

    @Test
    fun `should not match unrelated progress status without sync text`() {
        val notification = createNotification(
            packageName = "com.example.app",
            appName = "Example App",
            title = "Download complete",
            text = "Photo saved to device",
            category = "progress"
        )

        assertThat(detector.isSyncStatusNotification(notification)).isFalse()
    }

    private fun createNotification(
        packageName: String,
        appName: String,
        title: String,
        text: String,
        category: String? = null
    ): NotificationData {
        return NotificationData(
            packageName = packageName,
            appName = appName,
            title = title,
            text = text,
            category = category,
            importance = NotificationImportance.NORMAL,
            timestamp = System.currentTimeMillis()
        )
    }
}
