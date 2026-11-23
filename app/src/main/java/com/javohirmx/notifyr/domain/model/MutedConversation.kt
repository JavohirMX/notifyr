package com.javohirmx.notifyr.domain.model

import kotlinx.serialization.Serializable

/**
 * A conversation the user silenced.
 *
 * Conversations are identified by the app plus the classifier's conversation ID (app + sender
 * for messaging and mail apps). Muting is persisted separately from the notification history so
 * it keeps applying after retention deletes the original rows.
 */
@Serializable
data class MutedConversation(
    val packageName: String,
    val appName: String,
    val conversationId: String,
    /** Human-readable sender/title shown in the management list. */
    val label: String,
    val mutedAt: Long = 0L
)

/** Stable key for looking a conversation up in the muted set. */
fun conversationMuteKey(packageName: String, conversationId: String): String =
    "$packageName|$conversationId"

/**
 * The conversation ID to mute, or null when this notification has no distinct conversation.
 *
 * The classifier falls back to the package name for one-off app alerts; muting those would be
 * an app-level mute disguised as a conversation, so only app + sender IDs qualify.
 */
val NotificationData.muteableConversationId: String?
    get() = conversationId?.takeIf { it != packageName }
