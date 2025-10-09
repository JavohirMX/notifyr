package com.javohirmx.notifyr.domain.rules

import com.javohirmx.notifyr.data.repository.AppRulesRepository
import com.javohirmx.notifyr.data.repository.KeywordRulesRepository
import com.javohirmx.notifyr.domain.model.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enhanced rules engine that assigns smart tags to notifications
 * This works alongside the existing NotificationRulesEngine for backward compatibility
 */
@Singleton
class EnhancedNotificationRulesEngine @Inject constructor(
    private val appRulesRepository: AppRulesRepository,
    private val keywordRulesRepository: KeywordRulesRepository
) {
    
    // Default category mappings
    private val financialApps = setOf(
        "com.chase.sig.android",
        "com.bankofamerica.digitalwallet",
        "com.wellsfargo.mobile.android",
        "com.usbank.mobilebanking",
        "com.citi.citimobile",
        "com.paypal.android.p2pmobile",
        "com.venmo",
        "com.coinbase.android",
        "com.robinhood.android"
    )
    
    private val socialApps = setOf(
        "com.facebook.katana",
        "com.instagram.android",
        "com.twitter.android",
        "com.snapchat.android",
        "com.tiktok.android",
        "com.linkedin.android",
        "com.reddit.frontpage"
    )
    
    private val messagingApps = setOf(
        "com.whatsapp",
        "com.telegram.messenger",
        "com.discord",
        "com.slack",
        "com.microsoft.teams",
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.facebook.orca",
        "org.signalapp.signal"
    )
    
    private val workApps = setOf(
        "com.slack",
        "com.microsoft.teams",
        "com.microsoft.office.outlook",
        "com.google.android.apps.gmail",
        "com.asana.app",
        "com.trello",
        "com.atlassian.jira"
    )
    
    private val shoppingApps = setOf(
        "com.amazon.mShop.android.shopping",
        "com.ebay.mobile",
        "com.etsy.android",
        "com.walmart.android",
        "com.shopify.mobile",
        "com.target.ui",
        "in.amazon.mShop.android.shopping"
    )
    
    private val entertainmentApps = setOf(
        "com.netflix.mediaclient",
        "com.spotify.music",
        "com.google.android.youtube",
        "com.hulu.plus",
        "com.disney.disneyplus"
    )
    
    /**
     * Classify notification and assign smart tags
     */
    fun classifyNotificationWithTags(notification: NotificationData): NotificationData {
        val contexts = determineContexts(notification)
        val priority = determinePriority(notification, contexts)
        val timeSensitivity = determineTimeSensitivity(notification, priority)
        val actionType = determineActionType(notification)
        
        val tags = NotificationTags(
            priority = priority,
            contexts = contexts,
            timeSensitivity = timeSensitivity,
            actionType = actionType
        )
        
        // Extract sender for messaging apps
        val sender = extractSender(notification)
        val conversationId = generateConversationId(notification, sender)
        
        return notification.copy(
            tags = tags,
            sender = sender,
            conversationId = conversationId
        )
    }
    
    private fun determineContexts(notification: NotificationData): Set<NotificationContext> {
        val contexts = mutableSetOf<NotificationContext>()
        val packageName = notification.packageName
        
        // Check app categories
        when {
            financialApps.contains(packageName) -> {
                contexts.add(NotificationContext.FINANCIAL)
            }
            socialApps.contains(packageName) -> {
                contexts.add(NotificationContext.SOCIAL)
            }
            messagingApps.contains(packageName) -> {
                contexts.add(NotificationContext.PERSONAL)
            }
            workApps.contains(packageName) -> {
                contexts.add(NotificationContext.WORK)
            }
            shoppingApps.contains(packageName) -> {
                contexts.add(NotificationContext.SHOPPING)
            }
            entertainmentApps.contains(packageName) -> {
                contexts.add(NotificationContext.ENTERTAINMENT)
            }
        }
        
        // Check content for additional context
        val content = "${notification.title} ${notification.text}".lowercase()
        
        if (content.contains("delivery") || content.contains("shipped") || content.contains("order")) {
            contexts.add(NotificationContext.SHOPPING)
        }
        
        if (content.contains("meeting") || content.contains("calendar") || content.contains("deadline")) {
            contexts.add(NotificationContext.WORK)
        }
        
        // Default to personal if no other context
        if (contexts.isEmpty()) {
            contexts.add(NotificationContext.PERSONAL)
        }
        
        return contexts
    }
    
    private fun determinePriority(notification: NotificationData, contexts: Set<NotificationContext>): Priority {
        val content = "${notification.title} ${notification.text}".lowercase()
        
        // Critical keywords
        val criticalKeywords = listOf(
            "fraud", "security alert", "unauthorized", "emergency", 
            "critical", "suspended", "locked", "blocked"
        )
        
        if (criticalKeywords.any { content.contains(it) }) {
            return Priority.CRITICAL
        }
        
        // Financial notifications are usually important
        if (contexts.contains(NotificationContext.FINANCIAL)) {
            return Priority.IMPORTANT
        }
        
        // Check for urgent keywords
        val urgentKeywords = listOf(
            "urgent", "asap", "important", "deadline", "now",
            "meeting", "call", "verify", "confirm"
        )
        
        if (urgentKeywords.any { content.contains(it) }) {
            return Priority.IMPORTANT
        }
        
        // Social media is usually low priority
        if (contexts.contains(NotificationContext.SOCIAL) && 
            !contexts.contains(NotificationContext.WORK)) {
            return Priority.LOW
        }
        
        // Check user-defined app rules
        val appRule = appRulesRepository.getAppRule(notification.packageName)
        if (appRule != null) {
            return when (appRule.ruleType) {
                AppRuleType.ALWAYS_URGENT -> Priority.IMPORTANT
                AppRuleType.ALWAYS_IGNORE -> Priority.LOW
                AppRuleType.FILTER_KEYWORDS -> Priority.NORMAL
            }
        }
        
        return Priority.NORMAL
    }
    
    private fun determineTimeSensitivity(notification: NotificationData, priority: Priority): TimeSensitivity {
        if (priority == Priority.CRITICAL) {
            return TimeSensitivity.IMMEDIATE
        }
        
        val content = "${notification.title} ${notification.text}".lowercase()
        
        // Time-sensitive keywords
        val immediateKeywords = listOf(
            "now", "urgent", "asap", "emergency", "call",
            "meeting in", "starting", "live"
        )
        
        if (immediateKeywords.any { content.contains(it) }) {
            return TimeSensitivity.IMMEDIATE
        }
        
        val soonKeywords = listOf(
            "today", "tonight", "soon", "reminder", "upcoming"
        )
        
        if (soonKeywords.any { content.contains(it) }) {
            return TimeSensitivity.SOON
        }
        
        // Default based on priority
        return when (priority) {
            Priority.CRITICAL -> TimeSensitivity.IMMEDIATE
            Priority.IMPORTANT -> TimeSensitivity.SOON
            Priority.NORMAL -> TimeSensitivity.LATER
            Priority.LOW -> TimeSensitivity.WHENEVER
        }
    }
    
    private fun determineActionType(notification: NotificationData): ActionType {
        val content = "${notification.title} ${notification.text}".lowercase()
        
        // Check for questions or requests
        val needsResponseKeywords = listOf(
            "?", "reply", "respond", "confirm", "verify", "approve",
            "accept", "decline", "review", "action required"
        )
        
        if (needsResponseKeywords.any { content.contains(it) }) {
            return ActionType.NEEDS_RESPONSE
        }
        
        // Check for transactional content
        val transactionalKeywords = listOf(
            "receipt", "confirmation", "order placed", "payment", 
            "invoice", "shipped", "delivered"
        )
        
        if (transactionalKeywords.any { content.contains(it) }) {
            return ActionType.TRANSACTIONAL
        }
        
        // Check if it's a conversation
        if (messagingApps.contains(notification.packageName)) {
            return ActionType.CONVERSATIONAL
        }
        
        // Check for automated messages
        val automatedKeywords = listOf(
            "automated", "no-reply", "do not reply", "notification",
            "alert", "update", "reminder"
        )
        
        if (automatedKeywords.any { content.contains(it) }) {
            return ActionType.AUTOMATED
        }
        
        return ActionType.FYI
    }
    
    private fun extractSender(notification: NotificationData): String? {
        // Try to extract sender from title for messaging apps
        if (messagingApps.contains(notification.packageName)) {
            // For many messaging apps, sender is in the title
            val title = notification.title
            
            // Remove app name from title if present
            val cleanTitle = title.replace(notification.appName, "").trim()
            
            // If title looks like a name (not too long, doesn't have typical notification text)
            if (cleanTitle.isNotEmpty() && 
                cleanTitle.length < 50 && 
                !cleanTitle.contains(":") &&
                !cleanTitle.lowercase().contains("notification")) {
                return cleanTitle
            }
        }
        
        return null
    }
    
    private fun generateConversationId(notification: NotificationData, sender: String?): String? {
        // For messaging apps, group by app + sender
        if (messagingApps.contains(notification.packageName) && sender != null) {
            return "${notification.packageName}:${sender}"
        }
        
        // For other apps, group by package name
        return notification.packageName
    }
}

