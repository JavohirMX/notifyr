package com.javohirmx.notifyr.domain.model

data class AppRule(
    val packageName: String,
    val appName: String,
    val ruleType: AppRuleType,
    val isEnabled: Boolean = true
)

enum class AppRuleType {
    ALWAYS_URGENT,    // Always mark as urgent
    FILTER_KEYWORDS,  // Apply keyword filtering
    ALWAYS_IGNORE     // Always ignore
}
