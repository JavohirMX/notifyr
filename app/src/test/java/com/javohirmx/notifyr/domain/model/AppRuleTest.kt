package com.javohirmx.notifyr.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppRuleTest {
    
    @Test
    fun `AppRuleType displayName returns correct values`() {
        assertThat(AppRuleType.DONT_INTERCEPT.displayName).isEqualTo("Don't Intercept")
        assertThat(AppRuleType.ALWAYS_URGENT.displayName).isEqualTo("Always Urgent")
        assertThat(AppRuleType.FILTER_KEYWORDS.displayName).isEqualTo("Filter by Keywords")
        assertThat(AppRuleType.ALWAYS_IGNORE.displayName).isEqualTo("Always Ignore")
        assertThat(AppRuleType.ALWAYS_DROP_SYNC_STATUS.displayName).isEqualTo("Always Drop Sync Status")
        assertThat(AppRuleType.NEVER_DROP_SYNC_STATUS.displayName).isEqualTo("Never Drop Sync Status")
    }
    
    @Test
    fun `AppRuleType description returns correct values`() {
        assertThat(AppRuleType.DONT_INTERCEPT.description).isEqualTo("Let notifications through without any modification")
        assertThat(AppRuleType.ALWAYS_URGENT.description).isEqualTo("Always show notifications from this app immediately")
        assertThat(AppRuleType.FILTER_KEYWORDS.description).isEqualTo("Apply keyword filtering to classify notifications")
        assertThat(AppRuleType.ALWAYS_IGNORE.description).isEqualTo("Silently archive all notifications from this app")
        assertThat(AppRuleType.ALWAYS_DROP_SYNC_STATUS.description).isEqualTo("Always drop sync and background status notifications from this app")
        assertThat(AppRuleType.NEVER_DROP_SYNC_STATUS.description).isEqualTo("Never auto-drop sync and background status notifications from this app")
    }
    
    @Test
    fun `AppRule is enabled by default`() {
        val rule = AppRule(
            packageName = "com.test",
            appName = "Test",
            ruleType = AppRuleType.ALWAYS_URGENT
        )
        
        assertThat(rule.isEnabled).isTrue()
    }
    
    @Test
    fun `AppRule can be disabled`() {
        val rule = AppRule(
            packageName = "com.test",
            appName = "Test",
            ruleType = AppRuleType.ALWAYS_URGENT,
            isEnabled = false
        )
        
        assertThat(rule.isEnabled).isFalse()
    }
}

