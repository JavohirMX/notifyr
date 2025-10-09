package com.javohirmx.notifyr.domain.digest

import com.javohirmx.notifyr.domain.model.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DigestGenerator @Inject constructor() {
    
    /**
     * Generate an enhanced digest from a list of notifications
     */
    fun generateDigest(
        notifications: List<NotificationData>,
        timeRangeMinutes: Int = 240 // Default 4 hours
    ): EnhancedDigest {
        if (notifications.isEmpty()) {
            return EnhancedDigest(
                timeRange = formatTimeRange(timeRangeMinutes),
                totalCount = 0,
                needsAttention = emptyList(),
                conversations = emptyList(),
                appGroups = emptyList(),
                summary = DigestSummary(
                    totalNotifications = 0,
                    conversationCount = 0,
                    appCount = 0,
                    needsAttentionCount = 0,
                    summaryText = "No new notifications",
                    topApps = emptyList(),
                    topSenders = emptyList()
                )
            )
        }
        
        // Filter out what needs attention
        val needsAttention = notifications.filter { it.tags.needsAttention() }
        
        // Group by conversation
        val conversations = groupByConversation(notifications)
        
        // Group by app
        val appGroups = groupByApp(notifications)
        
        // Generate summary
        val summary = generateSummary(
            notifications = notifications,
            conversations = conversations,
            appGroups = appGroups,
            needsAttention = needsAttention
        )
        
        return EnhancedDigest(
            timeRange = formatTimeRange(timeRangeMinutes),
            totalCount = notifications.size,
            needsAttention = needsAttention,
            conversations = conversations,
            appGroups = appGroups,
            summary = summary
        )
    }
    
    private fun groupByConversation(notifications: List<NotificationData>): List<ConversationGroup> {
        // Group by conversation ID
        val grouped = notifications
            .filter { it.conversationId != null }
            .groupBy { it.conversationId }
        
        return grouped.mapNotNull { (conversationId, items) ->
            if (conversationId == null) return@mapNotNull null
            
            val latest = items.maxByOrNull { it.timestamp } ?: return@mapNotNull null
            val sender = items.firstOrNull()?.sender ?: latest.appName
            
            ConversationGroup(
                sender = sender,
                appName = latest.appName,
                appPackage = latest.packageName,
                messageCount = items.size,
                latestMessage = latest.text,
                latestTimestamp = latest.timestamp,
                notifications = items.sortedByDescending { it.timestamp }
            )
        }.sortedByDescending { it.latestTimestamp }
    }
    
    private fun groupByApp(notifications: List<NotificationData>): List<AppGroup> {
        val grouped = notifications.groupBy { it.packageName }
        
        return grouped.map { (packageName, items) ->
            val latest = items.maxByOrNull { it.timestamp }!!
            val categories = items.mapNotNull { it.category }.toSet()
            
            AppGroup(
                appName = latest.appName,
                appPackage = packageName,
                notificationCount = items.size,
                categories = categories,
                latestTimestamp = latest.timestamp,
                notifications = items.sortedByDescending { it.timestamp }
            )
        }.sortedByDescending { it.latestTimestamp }
    }
    
    private fun generateSummary(
        notifications: List<NotificationData>,
        conversations: List<ConversationGroup>,
        appGroups: List<AppGroup>,
        needsAttention: List<NotificationData>
    ): DigestSummary {
        // Get top apps by count
        val topApps = appGroups
            .sortedByDescending { it.notificationCount }
            .take(3)
            .map { it.appName to it.notificationCount }
        
        // Get top senders
        val topSenders = conversations
            .sortedByDescending { it.messageCount }
            .take(3)
            .map { it.sender to it.messageCount }
        
        // Generate summary text
        val summaryText = buildSummaryText(
            totalCount = notifications.size,
            conversationCount = conversations.size,
            appCount = appGroups.size,
            needsAttentionCount = needsAttention.size,
            topApps = topApps,
            topSenders = topSenders
        )
        
        return DigestSummary(
            totalNotifications = notifications.size,
            conversationCount = conversations.size,
            appCount = appGroups.size,
            needsAttentionCount = needsAttention.size,
            summaryText = summaryText,
            topApps = topApps,
            topSenders = topSenders
        )
    }
    
    private fun buildSummaryText(
        totalCount: Int,
        conversationCount: Int,
        appCount: Int,
        needsAttentionCount: Int,
        topApps: List<Pair<String, Int>>,
        topSenders: List<Pair<String, Int>>
    ): String {
        val parts = mutableListOf<String>()
        
        // Priority summary
        if (needsAttentionCount > 0) {
            parts.add("$needsAttentionCount ${if (needsAttentionCount == 1) "notification needs" else "notifications need"} attention")
        }
        
        // Conversation summary
        if (conversationCount > 0) {
            val sender = topSenders.firstOrNull()
            if (sender != null && sender.second > 1) {
                parts.add("${sender.first} sent ${sender.second} messages")
            } else if (conversationCount == 1) {
                parts.add("1 conversation")
            } else {
                parts.add("$conversationCount conversations")
            }
        }
        
        // App summary
        if (appCount > 0) {
            val topApp = topApps.firstOrNull()
            if (topApp != null && conversationCount == 0) {
                parts.add("${topApp.second} from ${topApp.first}")
            }
        }
        
        return when {
            parts.isEmpty() -> "$totalCount notifications"
            parts.size == 1 -> parts[0]
            parts.size == 2 -> "${parts[0]}, ${parts[1]}"
            else -> "${parts[0]}, ${parts[1]}, and more"
        }
    }
    
    private fun formatTimeRange(minutes: Int): String {
        return when {
            minutes < 60 -> "Last $minutes minutes"
            minutes < 120 -> "Last hour"
            minutes < 1440 -> "Last ${minutes / 60} hours"
            else -> "Last ${minutes / 1440} days"
        }
    }
}

