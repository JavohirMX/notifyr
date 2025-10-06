package com.javohirmx.notifyr.domain.rules

import com.javohirmx.notifyr.domain.model.*

class NotificationRulesEngine {
    
    // Default urgent keywords
    private val defaultUrgentKeywords = listOf(
        "urgent", "asap", "emergency", "important", "critical", "help",
        "meeting", "call me", "deadline", "breaking", "alert", "warning",
        "security", "fraud", "suspicious", "verify", "confirm", "action required"
    )
    
    // Default banking/financial app packages (always urgent)
    private val defaultBankingApps = listOf(
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
    
    // Default social media apps (usually ignore)
    private val defaultSocialMediaApps = listOf(
        "com.facebook.katana",
        "com.instagram.android",
        "com.twitter.android",
        "com.snapchat.android",
        "com.tiktok.android",
        "com.linkedin.android",
        "com.reddit.frontpage"
    )
    
    fun classifyNotification(notification: NotificationData): NotificationData {
        val importance = evaluateNotificationImportance(notification)
        return notification.copy(importance = importance)
    }
    
    private fun evaluateNotificationImportance(notification: NotificationData): NotificationImportance {
        // Rule hierarchy as per guidelines:
        // 1. Contact rules (not implemented in simple version)
        // 2. App rules
        // 3. Keyword rules
        // 4. Default handling
        
        // 2. App rules
        val appImportance = evaluateAppRules(notification)
        if (appImportance != null) {
            return appImportance
        }
        
        // 3. Keyword rules
        val keywordImportance = evaluateKeywordRules(notification)
        if (keywordImportance != null) {
            return keywordImportance
        }
        
        // 4. Default handling
        return evaluateDefaultRules(notification)
    }
    
    private fun evaluateAppRules(notification: NotificationData): NotificationImportance? {
        val packageName = notification.packageName
        
        // Banking apps are always urgent
        if (defaultBankingApps.contains(packageName)) {
            return NotificationImportance.URGENT
        }
        
        // Social media apps are usually ignored
        if (defaultSocialMediaApps.contains(packageName)) {
            return NotificationImportance.IGNORE
        }
        
        // Messaging apps get keyword filtering (return null to continue evaluation)
        if (isMessagingApp(packageName)) {
            return null // Continue to keyword evaluation
        }
        
        return null // No specific app rule found
    }
    
    private fun evaluateKeywordRules(notification: NotificationData): NotificationImportance? {
        val content = "${notification.title} ${notification.text}".lowercase()
        
        // Check for urgent keywords
        for (keyword in defaultUrgentKeywords) {
            if (content.contains(keyword.lowercase())) {
                return NotificationImportance.URGENT
            }
        }
        
        return null // No keyword match found
    }
    
    private fun evaluateDefaultRules(notification: NotificationData): NotificationImportance {
        // Default behavior based on notification category and app type
        return when {
            notification.category == "call" -> NotificationImportance.URGENT
            notification.category == "msg" -> NotificationImportance.NORMAL
            notification.category == "email" -> NotificationImportance.NORMAL
            notification.category == "social" -> NotificationImportance.IGNORE
            notification.category == "promo" -> NotificationImportance.IGNORE
            isSystemApp(notification.packageName) -> NotificationImportance.NORMAL
            else -> NotificationImportance.NORMAL // Default as per requirements
        }
    }
    
    private fun isMessagingApp(packageName: String): Boolean {
        val messagingApps = listOf(
            "com.whatsapp",
            "com.telegram.messenger",
            "com.discord",
            "com.slack",
            "com.microsoft.teams",
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.facebook.orca", // Messenger
            "org.signal.messenger"
        )
        return messagingApps.contains(packageName)
    }
    
    private fun isSystemApp(packageName: String): Boolean {
        return packageName.startsWith("com.android") || 
               packageName.startsWith("com.google.android") ||
               packageName.startsWith("com.samsung.android")
    }
}
