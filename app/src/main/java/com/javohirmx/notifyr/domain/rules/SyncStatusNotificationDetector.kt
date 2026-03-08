package com.javohirmx.notifyr.domain.rules

import com.javohirmx.notifyr.domain.model.NotificationData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncStatusNotificationDetector @Inject constructor() {

    private val exactStatusPhrases = listOf(
        "checking for new messages",
        "checking for messages",
        "checking for new mail",
        "checking for mail",
        "syncing mail",
        "syncing emails",
        "syncing messages",
        "sync in progress",
        "syncing",
        "updating inbox",
        "refreshing inbox",
        "fetching messages",
        "updating messages",
        "background sync"
    )

    private val syncKeywords = listOf(
        "sync",
        "syncing",
        "checking",
        "refreshing",
        "fetching",
        "updating",
        "retrieving"
    )

    private val statusTargets = listOf(
        "message",
        "messages",
        "mail",
        "email",
        "emails",
        "inbox",
        "chat",
        "notifications"
    )

    fun isSyncStatusNotification(
        notification: NotificationData,
        additionalPhrases: List<String> = emptyList()
    ): Boolean {
        val content = listOf(notification.title, notification.text)
            .joinToString(" ")
            .trim()
            .lowercase()

        if (content.isBlank()) return false

        if (exactStatusPhrases.any { content.contains(it) }) {
            return true
        }

        val normalizedCustomPhrases = additionalPhrases
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }

        if (normalizedCustomPhrases.any { content.contains(it) }) {
            return true
        }

        val hasSyncKeyword = syncKeywords.any { keyword ->
            content.contains(keyword)
        }
        if (!hasSyncKeyword) {
            return false
        }

        val category = notification.category?.lowercase().orEmpty()
        val statusCategory = category == "progress" || category == "status"
        val hasTarget = statusTargets.any { target -> content.contains(target) }

        val tokenCount = content.split(" ").count { it.isNotBlank() }
        val looksLikeConversation = content.contains("?") || tokenCount > 12

        return (statusCategory || hasTarget) && !looksLikeConversation
    }
}
