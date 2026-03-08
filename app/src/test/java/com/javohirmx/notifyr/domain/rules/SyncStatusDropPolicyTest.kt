package com.javohirmx.notifyr.domain.rules

import com.google.common.truth.Truth.assertThat
import com.javohirmx.notifyr.domain.model.AppRule
import com.javohirmx.notifyr.domain.model.AppRuleType
import com.javohirmx.notifyr.domain.model.NotificationData
import com.javohirmx.notifyr.domain.model.NotificationImportance
import org.junit.Test

class SyncStatusDropPolicyTest {

    private val detector = SyncStatusNotificationDetector()
    private val policy = SyncStatusDropPolicy(detector)

    @Test
    fun `never drop rule should keep even sync status notifications`() {
        val notification = notification("Checking for new messages")
        val rule = AppRule(
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            ruleType = AppRuleType.NEVER_DROP_SYNC_STATUS,
            isEnabled = true
        )

        assertThat(policy.shouldDrop(notification, rule)).isFalse()
    }

    @Test
    fun `always drop sync status should not drop regular messages`() {
        val notification = notification("Hey, are you free for a call?")
        val rule = AppRule(
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            ruleType = AppRuleType.ALWAYS_DROP_SYNC_STATUS,
            isEnabled = true
        )

        assertThat(policy.shouldDrop(notification, rule)).isFalse()
    }

    @Test
    fun `always drop sync status should drop when custom phrase matches`() {
        val notification = notification("Refreshing channel state")
        val rule = AppRule(
            packageName = "com.custom.app",
            appName = "Custom App",
            ruleType = AppRuleType.ALWAYS_DROP_SYNC_STATUS,
            isEnabled = true,
            syncStatusPhrases = listOf("refreshing channel state")
        )

        assertThat(policy.shouldDrop(notification, rule)).isTrue()
    }

    @Test
    fun `without override should use auto detector`() {
        val notification = notification("Syncing mail")

        assertThat(policy.shouldDrop(notification, null)).isTrue()
    }

    private fun notification(text: String): NotificationData {
        return NotificationData(
            packageName = "com.test",
            appName = "Test",
            title = "Test",
            text = text,
            category = null,
            importance = NotificationImportance.NORMAL,
            timestamp = System.currentTimeMillis()
        )
    }
}
