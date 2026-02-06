package com.javohirmx.notifyr.domain.rules

import com.javohirmx.notifyr.data.repository.AppRulesRepository
import com.javohirmx.notifyr.data.repository.KeywordRulesRepository
import com.javohirmx.notifyr.data.repository.TemporaryAppStatusRepository
import com.javohirmx.notifyr.domain.model.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRulesEngine @Inject constructor(
    private val appRulesRepository: AppRulesRepository,
    private val keywordRulesRepository: KeywordRulesRepository,
    private val temporaryAppStatusRepository: TemporaryAppStatusRepository
) {
    
    // Default urgent keywords
    private val defaultUrgentKeywords = listOf(
        "urgent", "asap", "emergency", "important", "critical", "help",
        "meeting", "call me", "deadline", "breaking", "alert", "warning",
        "security", "fraud", "suspicious", "verify", "confirm", "action required",
        "sos", "immediately"
    )
    
    // Default ignore keywords
    private val defaultIgnoreKeywords = listOf(
        "unsubscribe", "promotion", "sale", "discount", "spam", "marketing", "promo",
        "newsletter", "offers"
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
        // Rule hierarchy (highest to lowest priority):
        // 1. Temporary app status (highest priority - checked first)
        // 2. Contact rules (not implemented in simple version)
        // 3. User-defined app rules
        // 4. Keyword rules (can override default app behavior)
        // 5. Default app rules
        // 6. Default handling
        
        // 1. Temporary app status (highest priority)
        val temporaryStatus = evaluateTemporaryStatus(notification)
        if (temporaryStatus != null) {
            return temporaryStatus
        }
        
        // 3. User-defined app rules
        val userAppImportance = evaluateUserAppRules(notification)
        if (userAppImportance != null) {
            return userAppImportance
        }
        
        // 4. Keyword rules (can override default app behavior)
        val keywordImportance = evaluateKeywordRules(notification)
        if (keywordImportance != null) {
            return keywordImportance
        }
        
        // 5. Default app rules
        val defaultAppImportance = evaluateDefaultAppRules(notification)
        if (defaultAppImportance != null) {
            return defaultAppImportance
        }
        
        // 6. Default handling
        return evaluateDefaultRules(notification)
    }
    
    private fun evaluateTemporaryStatus(notification: NotificationData): NotificationImportance? {
        val packageName = notification.packageName
        val temporaryStatus = temporaryAppStatusRepository.getTemporaryStatus(packageName)
        
        if (temporaryStatus != null && !temporaryStatus.isExpired()) {
            return when (temporaryStatus.status) {
                TemporaryStatus.DONT_IGNORE -> NotificationImportance.NORMAL // Let through with normal processing
                TemporaryStatus.IGNORE -> NotificationImportance.IGNORE
                TemporaryStatus.URGENT -> NotificationImportance.URGENT
            }
        }
        
        return null // No active temporary status
    }
    
    private fun evaluateUserAppRules(notification: NotificationData): NotificationImportance? {
        val packageName = notification.packageName
        
        // Check for user-defined app rules first
        val appRule = appRulesRepository.getAppRule(packageName)
        if (appRule != null && appRule.isEnabled) {
            return when (appRule.ruleType) {
                AppRuleType.DONT_INTERCEPT -> null // Should be handled earlier, but return null as fallback
                AppRuleType.ALWAYS_URGENT -> NotificationImportance.URGENT
                AppRuleType.ALWAYS_IGNORE -> NotificationImportance.IGNORE
                AppRuleType.FILTER_KEYWORDS -> null // Continue to keyword evaluation
            }
        }
        
        return null // No user-defined rule found
    }
    
    private fun evaluateDefaultAppRules(notification: NotificationData): NotificationImportance? {
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
        
        return null // No default app rule found
    }
    
    private fun evaluateKeywordRules(notification: NotificationData): NotificationImportance? {
        val content = "${notification.title} ${notification.text}".lowercase()
        
        // Check user-defined keyword rules first
        val keywordRules = keywordRulesRepository.keywordRules.value
        
        // If there are user-defined rules, only use those (don't fall back to defaults)
        if (keywordRules.isNotEmpty()) {
            for (keywordRule in keywordRules) {
                if (!keywordRule.isEnabled) continue
                
                val matches = if (keywordRule.isRegex) {
                    try {
                        val regex = Regex(keywordRule.keyword, RegexOption.IGNORE_CASE)
                        regex.containsMatchIn(content)
                    } catch (e: Exception) {
                        // Invalid regex, fall back to simple contains
                        content.contains(keywordRule.keyword.lowercase())
                    }
                } else {
                    content.contains(keywordRule.keyword.lowercase())
                }
                
                if (matches) {
                    return keywordRule.importance
                }
            }
            return null // No user rule matched
        }
        
        // Fallback to default urgent keywords only if no user rules are defined
        for (keyword in defaultUrgentKeywords) {
            if (content.contains(keyword.lowercase())) {
                return NotificationImportance.URGENT
            }
        }
        
        // Fallback to default ignore keywords
        for (keyword in defaultIgnoreKeywords) {
            if (content.contains(keyword.lowercase())) {
                return NotificationImportance.IGNORE
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
            "org.thoughtcrime.securesms"
        )
        return messagingApps.contains(packageName)
    }
    
    private fun isSystemApp(packageName: String): Boolean {
        return packageName.startsWith("com.android") || 
               packageName.startsWith("com.google.android") ||
               packageName.startsWith("com.samsung.android")
    }
}
