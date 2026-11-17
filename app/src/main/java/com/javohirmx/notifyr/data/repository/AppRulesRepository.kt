package com.javohirmx.notifyr.data.repository

import androidx.datastore.core.DataStore
import com.javohirmx.notifyr.data.datastore.AppSettings
import com.javohirmx.notifyr.domain.model.AppRule
import com.javohirmx.notifyr.domain.model.AppRuleType
import com.javohirmx.notifyr.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRulesRepository @Inject constructor(
    private val dataStore: DataStore<AppSettings>,
    @ApplicationScope private val appScope: CoroutineScope
) {
    
    /**
     * Secondary constructor used in unit tests or non-Hilt contexts.
     * Falls back to its own application-level scope when one is not provided.
     */
    constructor(dataStore: DataStore<AppSettings>) : this(
        dataStore,
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    )
    
    
    private val _appRules = MutableStateFlow<Map<String, AppRule>>(emptyMap())
    val appRules: StateFlow<Map<String, AppRule>> = _appRules.asStateFlow()

    /**
     * Guards [_appRules] against the initial asynchronous load overwriting local mutations.
     *
     * Defaults are seeded synchronously in [init], so the load task must not re-seed them when
     * the user (or a test) has already cleared or changed the rules while it was still reading.
     */
    private val stateLock = Any()
    private var hasLocalChanges = false
    
    init {
        // Start with in-memory defaults immediately for deterministic behavior,
        // then try to load any persisted rules from DataStore to override/merge.
        initializeDefaultRules()
        appScope.launch {
            loadAppRules()
        }
    }
    
    private suspend fun loadAppRules() {
        try {
            val settings = dataStore.data.first()
            val json = settings.appRulesJson

            synchronized(stateLock) {
                // A local mutation wins over whatever was on disk when this load started.
                if (hasLocalChanges) return

                if (json.isNotEmpty() && json != "[]") {
                    val persistedList = Json.decodeFromString<List<AppRule>>(json)
                    val persistedMap = persistedList.associateBy { it.packageName }
                    val currentMap = _appRules.value

                    // Persisted rules take precedence by packageName, but we keep any
                    // additional in-memory defaults that don't exist in the persisted set.
                    val merged = mutableMapOf<String, AppRule>()
                    merged.putAll(persistedMap)
                    currentMap.values.forEach { rule ->
                        if (!merged.containsKey(rule.packageName)) {
                            merged[rule.packageName] = rule
                        }
                    }

                    _appRules.value = merged
                }
                // An empty persisted value keeps the defaults seeded in init. Re-seeding here
                // used to resurrect defaults after a clear that happened before this load.
            }
        } catch (e: Exception) {
            // Keep whatever is already in memory; defaults were seeded synchronously in init.
        }
    }
    
    private suspend fun saveAppRules() {
        try {
            dataStore.updateData { currentSettings ->
                val rulesJson = Json.encodeToString(_appRules.value.values.toList())
                currentSettings.copy(appRulesJson = rulesJson)
            }
        } catch (e: Exception) {
            // Log error but don't crash
            android.util.Log.e("AppRulesRepository", "Failed to save app rules", e)
        }
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
    
    fun setAppRule(
        packageName: String,
        appName: String,
        ruleType: AppRuleType?,
        isEnabled: Boolean = true,
        syncStatusPhrases: List<String>? = null
    ) {
        synchronized(stateLock) {
            val currentRules = _appRules.value.toMutableMap()
            val existingRule = currentRules[packageName]

            if (ruleType == null) {
                currentRules.remove(packageName)
            } else {
                currentRules[packageName] = AppRule(
                    packageName = packageName,
                    appName = appName,
                    ruleType = ruleType,
                    isEnabled = isEnabled,
                    syncStatusPhrases = syncStatusPhrases ?: existingRule?.syncStatusPhrases ?: emptyList()
                )
            }

            if (currentRules != _appRules.value) {
                hasLocalChanges = true
                _appRules.value = currentRules
            }
        }

        // Persist changes
        appScope.launch {
            saveAppRules()
        }
    }
    
    fun removeAppRule(packageName: String) {
        synchronized(stateLock) {
            val currentRules = _appRules.value.toMutableMap()
            if (currentRules.remove(packageName) != null) {
                hasLocalChanges = true
                _appRules.value = currentRules
            }
        }

        appScope.launch {
            saveAppRules()
        }
    }
    
    fun clearAllRules() {
        synchronized(stateLock) {
            hasLocalChanges = true
            _appRules.value = emptyMap()
        }

        appScope.launch {
            saveAppRules()
        }
    }
    
    fun resetToDefaults() {
        synchronized(stateLock) {
            hasLocalChanges = true
            initializeDefaultRules()
        }

        appScope.launch {
            saveAppRules()
        }
    }
    
    fun exportAppRules(): List<AppRule> {
        return _appRules.value.values.toList()
    }
    
    fun importAppRules(appRules: List<AppRule>) {
        synchronized(stateLock) {
            hasLocalChanges = true
            _appRules.value = appRules.associateBy { it.packageName }
        }

        appScope.launch {
            saveAppRules()
        }
    }
}
