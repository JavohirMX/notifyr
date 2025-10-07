package com.javohirmx.notifyr.data.repository

import com.javohirmx.notifyr.domain.model.AppRule
import com.javohirmx.notifyr.domain.model.AppRuleType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRulesRepository @Inject constructor() {
    
    private val _appRules = MutableStateFlow<Map<String, AppRule>>(emptyMap())
    val appRules: StateFlow<Map<String, AppRule>> = _appRules.asStateFlow()
    
    init {
        initializeDefaultRules()
    }
    
    private fun initializeDefaultRules() {
        val defaultRules = mutableMapOf<String, AppRule>()
        
        // Banking apps - always urgent
        val bankingApps = listOf(
            "com.chase.sig.android" to "Chase Mobile",
            "com.bankofamerica.digitalwallet" to "Bank of America",
            "com.wellsfargo.mobile.android" to "Wells Fargo Mobile",
            "com.usbank.mobilebanking" to "U.S. Bank",
            "com.citi.citimobile" to "Citi Mobile",
            "com.paypal.android.p2pmobile" to "PayPal",
            "com.venmo" to "Venmo",
            "com.coinbase.android" to "Coinbase",
            "com.robinhood.android" to "Robinhood"
        )
        
        // Social media apps - always ignore
        val socialMediaApps = listOf(
            "com.facebook.katana" to "Facebook",
            "com.instagram.android" to "Instagram",
            "com.twitter.android" to "Twitter",
            "com.snapchat.android" to "Snapchat",
            "com.tiktok.android" to "TikTok",
            "com.linkedin.android" to "LinkedIn",
            "com.reddit.frontpage" to "Reddit"
        )
        
        // Messaging apps - filter by keywords
        val messagingApps = listOf(
            "com.whatsapp" to "WhatsApp",
            "com.telegram.messenger" to "Telegram",
            "com.discord" to "Discord",
            "com.slack" to "Slack",
            "com.microsoft.teams" to "Microsoft Teams",
            "com.google.android.apps.messaging" to "Messages",
            "com.samsung.android.messaging" to "Samsung Messages",
            "com.facebook.orca" to "Messenger",
            "org.signal.messenger" to "Signal"
        )
        
        bankingApps.forEach { (packageName, appName) ->
            defaultRules[packageName] = AppRule(
                packageName = packageName,
                appName = appName,
                ruleType = AppRuleType.ALWAYS_URGENT
            )
        }
        
        socialMediaApps.forEach { (packageName, appName) ->
            defaultRules[packageName] = AppRule(
                packageName = packageName,
                appName = appName,
                ruleType = AppRuleType.ALWAYS_IGNORE
            )
        }
        
        messagingApps.forEach { (packageName, appName) ->
            defaultRules[packageName] = AppRule(
                packageName = packageName,
                appName = appName,
                ruleType = AppRuleType.FILTER_KEYWORDS
            )
        }
        
        _appRules.value = defaultRules
    }
    
    fun getAppRule(packageName: String): AppRule? {
        return _appRules.value[packageName]
    }
    
    fun getAllAppRules(): List<AppRule> {
        return _appRules.value.values.toList()
    }
    
    fun setAppRule(packageName: String, appName: String, ruleType: AppRuleType?) {
        val currentRules = _appRules.value.toMutableMap()
        
        if (ruleType == null) {
            currentRules.remove(packageName)
        } else {
            currentRules[packageName] = AppRule(
                packageName = packageName,
                appName = appName,
                ruleType = ruleType
            )
        }
        
        _appRules.value = currentRules
    }
    
    fun removeAppRule(packageName: String) {
        val currentRules = _appRules.value.toMutableMap()
        currentRules.remove(packageName)
        _appRules.value = currentRules
    }
    
    fun clearAllRules() {
        _appRules.value = emptyMap()
    }
    
    fun resetToDefaults() {
        initializeDefaultRules()
    }
}
