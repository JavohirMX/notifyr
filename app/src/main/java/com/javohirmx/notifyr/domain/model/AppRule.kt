package com.javohirmx.notifyr.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AppRule(
    val packageName: String,
    val appName: String,
    val ruleType: AppRuleType,
    val isEnabled: Boolean = true
)

@Serializable
enum class AppRuleType {
    DONT_INTERCEPT,   // Don't intercept - let original notification through unchanged
    ALWAYS_URGENT,    // Always mark as urgent
    FILTER_KEYWORDS,  // Apply keyword filtering
    ALWAYS_IGNORE     // Always ignore
}

/**
 * Extension property to get display text for each rule type
 */
val AppRuleType.displayName: String
    get() = when (this) {
        AppRuleType.DONT_INTERCEPT -> "Don't Intercept"
        AppRuleType.ALWAYS_URGENT -> "Always Urgent"
        AppRuleType.FILTER_KEYWORDS -> "Filter by Keywords"
        AppRuleType.ALWAYS_IGNORE -> "Always Ignore"
    }

/**
 * Extension property to get description for each rule type
 */
val AppRuleType.description: String
    get() = when (this) {
        AppRuleType.DONT_INTERCEPT -> "Let notifications through without any modification"
        AppRuleType.ALWAYS_URGENT -> "Always show notifications from this app immediately"
        AppRuleType.FILTER_KEYWORDS -> "Apply keyword filtering to classify notifications"
        AppRuleType.ALWAYS_IGNORE -> "Silently archive all notifications from this app"
    }
