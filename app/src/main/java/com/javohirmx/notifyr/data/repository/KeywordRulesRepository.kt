package com.javohirmx.notifyr.data.repository

import com.javohirmx.notifyr.domain.model.KeywordRule
import com.javohirmx.notifyr.domain.model.NotificationImportance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeywordRulesRepository @Inject constructor() {
    
    private val _keywordRules = MutableStateFlow<List<KeywordRule>>(emptyList())
    val keywordRules: StateFlow<List<KeywordRule>> = _keywordRules.asStateFlow()
    
    init {
        initializeDefaultKeywords()
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
    }
    
    fun removeKeywordRule(keyword: String) {
        val currentRules = _keywordRules.value.toMutableList()
        currentRules.removeAll { it.keyword.equals(keyword, ignoreCase = true) }
        _keywordRules.value = currentRules
    }
    
    fun toggleKeywordRule(keyword: String) {
        val currentRules = _keywordRules.value.toMutableList()
        val index = currentRules.indexOfFirst { it.keyword.equals(keyword, ignoreCase = true) }
        
        if (index >= 0) {
            currentRules[index] = currentRules[index].copy(
                isEnabled = !currentRules[index].isEnabled
            )
            _keywordRules.value = currentRules
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
    }
    
    fun resetToDefaults() {
        initializeDefaultKeywords()
    }
    
    fun importKeywords(keywords: List<KeywordRule>) {
        _keywordRules.value = keywords.sortedWith(
            compareBy<KeywordRule> { it.importance.ordinal }
                .thenBy { it.keyword.lowercase() }
        )
    }
    
    fun exportKeywords(): List<KeywordRule> {
        return _keywordRules.value
    }
}
