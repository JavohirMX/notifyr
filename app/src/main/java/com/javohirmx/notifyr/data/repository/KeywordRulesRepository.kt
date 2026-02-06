package com.javohirmx.notifyr.data.repository

import androidx.datastore.core.DataStore
import com.javohirmx.notifyr.data.datastore.AppSettings
import com.javohirmx.notifyr.domain.model.KeywordRule
import com.javohirmx.notifyr.domain.model.NotificationImportance
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
class KeywordRulesRepository @Inject constructor(
    private val dataStore: DataStore<AppSettings>
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val _keywordRules = MutableStateFlow<List<KeywordRule>>(emptyList())
    val keywordRules: StateFlow<List<KeywordRule>> = _keywordRules.asStateFlow()
    
    init {
        // Start with in-memory defaults immediately for deterministic behavior,
        // then try to load any persisted rules from DataStore to override them.
        initializeDefaultKeywords()
        scope.launch { loadKeywordRules() }
    }
    
    private suspend fun loadKeywordRules() {
        try {
            val settings = dataStore.data.first()
            val json = settings.keywordRulesJson
            
            if (json.isNotEmpty() && json != "[]") {
                // We have previously persisted keywords – merge them with whatever
                // is currently in memory so we don't clobber rules that may have
                // been added before this load completes (important for tests and
                // for any early in-memory mutations).
                val persistedRules = Json.decodeFromString<List<KeywordRule>>(json)
                val currentRules = _keywordRules.value
                
                // Persisted rules take precedence by keyword (case-insensitive),
                // but we keep any additional in-memory rules that don't exist
                // in the persisted set.
                val persistedByKey = persistedRules.associateBy { it.keyword.lowercase() }
                val merged = buildList {
                    addAll(persistedRules)
                    currentRules.forEach { rule ->
                        val key = rule.keyword.lowercase()
                        if (!persistedByKey.containsKey(key)) {
                            add(rule)
                        }
                    }
                }
                
                _keywordRules.value = merged.sortedWith(
                    compareBy<KeywordRule> { it.importance.ordinal }
                        .thenBy { it.keyword.lowercase() }
                )
            } else {
                // Only fall back to defaults if we truly have nothing loaded yet.
                if (_keywordRules.value.isEmpty()) {
                    initializeDefaultKeywords()
                }
            }
        } catch (e: Exception) {
            // On error, only initialize defaults if nothing is in memory yet.
            if (_keywordRules.value.isEmpty()) {
                initializeDefaultKeywords()
            }
        }
    }
    
    private suspend fun saveKeywordRules() {
        try {
            dataStore.updateData { currentSettings ->
                val rulesJson = Json.encodeToString(_keywordRules.value)
                currentSettings.copy(keywordRulesJson = rulesJson)
            }
        } catch (e: Exception) {
            // Log error but don't crash
            android.util.Log.e("KeywordRulesRepository", "Failed to save keyword rules", e)
        }
    }
    
    private fun initializeDefaultKeywords() {
        val defaultUrgentKeywords = listOf(
            "urgent", "asap", "emergency", "important", "critical", "help",
            "meeting", "call me", "deadline", "breaking", "alert", "warning",
            "security", "fraud", "suspicious", "verify", "confirm", "action required",
            "immediate", "now", "quickly", "hurry", "rush", "priority"
        )
        
        val defaultIgnoreKeywords = listOf(
            "unsubscribe", "promotion", "sale", "discount", "offer", "deal",
            "marketing", "newsletter", "spam", "advertisement", "promo"
        )
        
        val initialRules = mutableListOf<KeywordRule>()
        
        defaultUrgentKeywords.forEach { keyword ->
            initialRules.add(
                KeywordRule(
                    keyword = keyword,
                    importance = NotificationImportance.URGENT,
                    isEnabled = true,
                    isRegex = false
                )
            )
        }
        
        defaultIgnoreKeywords.forEach { keyword ->
            initialRules.add(
                KeywordRule(
                    keyword = keyword,
                    importance = NotificationImportance.IGNORE,
                    isEnabled = true,
                    isRegex = false
                )
            )
        }
        
        _keywordRules.value = initialRules
    }
    
    fun addKeywordRule(keyword: String, importance: NotificationImportance, isRegex: Boolean = false) {
        val currentRules = _keywordRules.value.toMutableList()
        
        // Check if keyword already exists
        val existingIndex = currentRules.indexOfFirst { it.keyword.equals(keyword, ignoreCase = true) }
        
        if (existingIndex >= 0) {
            // Update existing rule
            currentRules[existingIndex] = currentRules[existingIndex].copy(
                importance = importance,
                isRegex = isRegex,
                isEnabled = true
            )
        } else {
            // Add new rule
            currentRules.add(
                KeywordRule(
                    keyword = keyword,
                    importance = importance,
                    isEnabled = true,
                    isRegex = isRegex
                )
            )
        }
        
        _keywordRules.value = currentRules.sortedWith(
            compareBy<KeywordRule> { it.importance.ordinal }
                .thenBy { it.keyword.lowercase() }
        )
        
        scope.launch {
            saveKeywordRules()
        }
    }
    
    fun removeKeywordRule(keyword: String) {
        val currentRules = _keywordRules.value.toMutableList()
        currentRules.removeAll { it.keyword.equals(keyword, ignoreCase = true) }
        _keywordRules.value = currentRules
        
        scope.launch {
            saveKeywordRules()
        }
    }
    
    fun toggleKeywordRule(keyword: String) {
        val currentRules = _keywordRules.value.toMutableList()
        val index = currentRules.indexOfFirst { it.keyword.equals(keyword, ignoreCase = true) }
        
        if (index >= 0) {
            currentRules[index] = currentRules[index].copy(
                isEnabled = !currentRules[index].isEnabled
            )
            _keywordRules.value = currentRules
            
            scope.launch {
                saveKeywordRules()
            }
        }
    }
    
    fun updateKeywordRule(oldKeyword: String, newKeyword: String, importance: NotificationImportance, isRegex: Boolean) {
        val currentRules = _keywordRules.value.toMutableList()
        val index = currentRules.indexOfFirst { it.keyword.equals(oldKeyword, ignoreCase = true) }
        
        if (index >= 0) {
            currentRules[index] = KeywordRule(
                keyword = newKeyword,
                importance = importance,
                isEnabled = currentRules[index].isEnabled,
                isRegex = isRegex
            )
            _keywordRules.value = currentRules.sortedWith(
                compareBy<KeywordRule> { it.importance.ordinal }
                    .thenBy { it.keyword.lowercase() }
            )
            
            scope.launch {
                saveKeywordRules()
            }
        }
    }
    
    fun getKeywordsByImportance(importance: NotificationImportance): List<KeywordRule> {
        return _keywordRules.value.filter { it.importance == importance && it.isEnabled }
    }
    
    fun getAllKeywords(): List<KeywordRule> {
        return _keywordRules.value
    }
    
    fun clearAllKeywords() {
        _keywordRules.value = emptyList()
        
        scope.launch {
            saveKeywordRules()
        }
    }
    
    fun resetToDefaults() {
        initializeDefaultKeywords()
        scope.launch {
            saveKeywordRules()
        }
    }
    
    fun importKeywords(keywords: List<KeywordRule>) {
        _keywordRules.value = keywords.sortedWith(
            compareBy<KeywordRule> { it.importance.ordinal }
                .thenBy { it.keyword.lowercase() }
        )
        
        scope.launch {
            saveKeywordRules()
        }
    }
    
    fun exportKeywords(): List<KeywordRule> {
        return _keywordRules.value
    }
}
