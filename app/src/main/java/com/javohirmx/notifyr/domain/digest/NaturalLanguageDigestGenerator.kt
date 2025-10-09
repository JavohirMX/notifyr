package com.javohirmx.notifyr.domain.digest

import com.javohirmx.notifyr.domain.model.*
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NaturalLanguageDigestGenerator @Inject constructor() {
    
    /**
     * Generate a human-readable summary of the digest
     */
    fun generateNaturalLanguageSummary(digest: EnhancedDigest): String {
        return buildString {
            // Opening greeting based on time of day
            append(getGreeting())
            append(", you have ")
            
            when {
                digest.totalCount == 0 -> append("no new updates")
                digest.totalCount == 1 -> append("1 update")
                else -> append("${digest.totalCount} updates")
            }
            append(".\n\n")
            
            // Important items section
            if (digest.needsAttention.isNotEmpty()) {
                append("⚠️ Important:\n")
                digest.needsAttention.take(3).forEach { notif ->
                    append("• ${summarizeNotification(notif)}\n")
                }
                if (digest.needsAttention.size > 3) {
                    append("• and ${digest.needsAttention.size - 3} more important items\n")
                }
                append("\n")
            }
            
            // Conversations section
            if (digest.conversations.isNotEmpty()) {
                append("💬 Messages:\n")
                digest.conversations.take(4).forEach { conv ->
                    append("• ${summarizeConversation(conv)}\n")
                }
                if (digest.conversations.size > 4) {
                    append("• and ${digest.conversations.size - 4} more conversations\n")
                }
                append("\n")
            }
            
            // App updates grouped by category
            val socialUpdates = digest.appGroups.filter { isSocialApp(it.appPackage) }
            val emailUpdates = digest.appGroups.filter { isEmailApp(it.appPackage) }
            val otherUpdates = digest.appGroups.filter { 
                !isSocialApp(it.appPackage) && !isEmailApp(it.appPackage) 
            }
            
            if (socialUpdates.isNotEmpty()) {
                val count = socialUpdates.sumOf { it.notificationCount }
                if (count == 1) {
                    append("📱 1 social media update (can wait)\n")
                } else {
                    append("📱 $count social media updates (can wait)\n")
                }
            }
            
            if (emailUpdates.isNotEmpty()) {
                val count = emailUpdates.sumOf { it.notificationCount }
                if (count == 1) {
                    append("📧 1 new email\n")
                } else {
                    append("📧 $count new emails\n")
                }
            }
            
            if (otherUpdates.isNotEmpty() && otherUpdates.size <= 3) {
                otherUpdates.forEach { group ->
                    append("• ${group.appName}: ${group.notificationCount} update${if (group.notificationCount > 1) "s" else ""}\n")
                }
            } else if (otherUpdates.isNotEmpty()) {
                val count = otherUpdates.sumOf { it.notificationCount }
                append("• $count other updates from ${otherUpdates.size} apps\n")
            }
            
            // Closing
            if (digest.totalCount > 0) {
                append("\nTap to review all updates.")
            }
        }
    }
    
    /**
     * Generate a short one-line summary
     */
    fun generateShortSummary(digest: EnhancedDigest): String {
        return when {
            digest.totalCount == 0 -> "No new notifications"
            digest.needsAttention.isNotEmpty() && digest.conversations.isNotEmpty() -> {
                "${digest.needsAttention.size} important, ${digest.conversations.size} messages"
            }
            digest.needsAttention.isNotEmpty() -> {
                "${digest.needsAttention.size} important notification${if (digest.needsAttention.size > 1) "s" else ""}"
            }
            digest.conversations.isNotEmpty() -> {
                "${digest.conversations.size} new conversation${if (digest.conversations.size > 1) "s" else ""}"
            }
            else -> {
                "${digest.totalCount} updates from ${digest.appGroups.size} apps"
            }
        }
    }
    
    private fun getGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 0..4 -> "Late night"
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..20 -> "Good evening"
            else -> "Good night"
        }
    }
    
    private fun summarizeNotification(notification: NotificationData): String {
        return when {
            notification.title.isNotBlank() && notification.text.isNotBlank() -> {
                "${notification.appName}: ${notification.title}"
            }
            notification.title.isNotBlank() -> {
                "${notification.appName}: ${notification.title}"
            }
            notification.text.isNotBlank() -> {
                "${notification.appName}: ${notification.text.take(50)}${if (notification.text.length > 50) "..." else ""}"
            }
            else -> {
                "${notification.appName} notification"
            }
        }
    }
    
    private fun summarizeConversation(conv: ConversationGroup): String {
        return when {
            conv.messageCount == 1 -> {
                "${conv.sender} sent you a message"
            }
            conv.messageCount in 2..4 -> {
                "${conv.sender} sent ${conv.messageCount} messages"
            }
            conv.messageCount in 5..10 -> {
                "Active chat with ${conv.sender} (${conv.messageCount} messages)"
            }
            else -> {
                "Very active chat with ${conv.sender} (${conv.messageCount} messages)"
            }
        }
    }
    
    private fun isSocialApp(packageName: String): Boolean {
        val socialApps = setOf(
            "com.facebook.katana",
            "com.instagram.android",
            "com.twitter.android",
            "com.snapchat.android",
            "com.tiktok.android",
            "com.linkedin.android",
            "com.reddit.frontpage"
        )
        return socialApps.contains(packageName)
    }
    
    private fun isEmailApp(packageName: String): Boolean {
        val emailApps = setOf(
            "com.google.android.gm",
            "com.google.android.apps.gmail",
            "com.microsoft.office.outlook",
            "com.yahoo.mobile.client.android.mail"
        )
        return emailApps.contains(packageName)
    }
}

