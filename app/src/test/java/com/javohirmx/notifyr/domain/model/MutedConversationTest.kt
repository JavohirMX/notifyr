package com.javohirmx.notifyr.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MutedConversationTest {

    private fun notification(conversationId: String?) = NotificationData(
        id = 1L,
        packageName = "com.whatsapp",
        appName = "WhatsApp",
        title = "Title",
        text = "Body",
        category = "msg",
        importance = NotificationImportance.NORMAL,
        timestamp = 1_700_000_000_000L,
        conversationId = conversationId
    )

    @Test
    fun `keys are scoped by app and conversation`() {
        assertThat(conversationMuteKey("com.whatsapp", "com.whatsapp:Alice"))
            .isEqualTo("com.whatsapp|com.whatsapp:Alice")
        assertThat(conversationMuteKey("com.whatsapp", "com.whatsapp:Alice"))
            .isNotEqualTo(conversationMuteKey("com.telegram.messenger", "com.whatsapp:Alice"))
    }

    @Test
    fun `sender conversation ids are muteable`() {
        assertThat(notification("com.whatsapp:Alice").muteableConversationId)
            .isEqualTo("com.whatsapp:Alice")
    }

    @Test
    fun `app-level fallback ids are not muteable`() {
        // The classifier uses the package name when it finds no sender, so muting that would
        // silently mute the whole app instead of a conversation.
        assertThat(notification("com.whatsapp").muteableConversationId).isNull()
        assertThat(notification(null).muteableConversationId).isNull()
    }
}
