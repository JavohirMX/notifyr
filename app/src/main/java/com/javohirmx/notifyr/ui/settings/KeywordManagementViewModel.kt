package com.javohirmx.notifyr.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javohirmx.notifyr.data.repository.KeywordRulesRepository
import com.javohirmx.notifyr.domain.model.KeywordRule
import com.javohirmx.notifyr.domain.model.NotificationImportance
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class KeywordManagementUiState(
    val allKeywords: List<KeywordRule> = emptyList(),
    val urgentKeywords: List<KeywordRule> = emptyList(),
    val ignoreKeywords: List<KeywordRule> = emptyList(),
    val editingKeyword: KeywordRule? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class KeywordManagementViewModel @Inject constructor(
    private val keywordRulesRepository: KeywordRulesRepository
) : ViewModel() {
    
    private val _editingKeyword = MutableStateFlow<KeywordRule?>(null)
    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    
    val uiState: StateFlow<KeywordManagementUiState> = combine(
        keywordRulesRepository.keywordRules,
        _editingKeyword,
        _isLoading,
        _error
    ) { keywordRules, editingKeyword, isLoading, error ->
        KeywordManagementUiState(
            allKeywords = keywordRules,
            urgentKeywords = keywordRules.filter { it.importance == NotificationImportance.URGENT },
            ignoreKeywords = keywordRules.filter { it.importance == NotificationImportance.IGNORE },
            editingKeyword = editingKeyword,
            isLoading = isLoading,
            error = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = KeywordManagementUiState()
    )
    
    fun addKeyword(keyword: String, importance: NotificationImportance, isRegex: Boolean) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                keywordRulesRepository.addKeywordRule(keyword, importance, isRegex)
                
                _isLoading.value = false
            } catch (e: Exception) {
                _error.value = e.message
                _isLoading.value = false
            }
        }
    }
    
    fun deleteKeyword(keyword: String) {
        viewModelScope.launch {
            try {
                keywordRulesRepository.removeKeywordRule(keyword)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }
    
    fun toggleKeyword(keyword: String) {
        viewModelScope.launch {
            try {
                keywordRulesRepository.toggleKeywordRule(keyword)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }
    
    fun editKeyword(keyword: KeywordRule) {
        _editingKeyword.value = keyword
    }
    
    fun cancelEdit() {
        _editingKeyword.value = null
    }
    
    fun updateKeyword(
        oldKeyword: String,
        newKeyword: String,
        importance: NotificationImportance,
        isRegex: Boolean
    ) {
        viewModelScope.launch {
            try {
                keywordRulesRepository.updateKeywordRule(oldKeyword, newKeyword, importance, isRegex)
                _editingKeyword.value = null
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }
    
    fun clearError() {
        _error.value = null
    }
    
    fun resetToDefaults() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                keywordRulesRepository.resetToDefaults()
                _isLoading.value = false
            } catch (e: Exception) {
                _error.value = e.message
                _isLoading.value = false
            }
        }
    }
    
    fun clearAllKeywords() {
        viewModelScope.launch {
            try {
                keywordRulesRepository.clearAllKeywords()
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }
}
