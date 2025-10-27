package com.javohirmx.notifyr.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javohirmx.notifyr.data.repository.AppRulesRepository
import com.javohirmx.notifyr.data.repository.NotificationRepository
import com.javohirmx.notifyr.domain.model.AppRule
import com.javohirmx.notifyr.domain.model.AppRuleType
import com.javohirmx.notifyr.ui.history.AppInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppRulesViewModel @Inject constructor(
    private val appRulesRepository: AppRulesRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(AppRulesUiState())
    val uiState: StateFlow<AppRulesUiState> = _uiState.asStateFlow()
    
    init {
        loadData()
    }
    
    private fun loadData() {
        viewModelScope.launch {
            combine(
                appRulesRepository.appRules,
                notificationRepository.getAllNotifications()
            ) { rules, notifications ->
                // Extract unique apps from notifications
                val apps = notifications
                    .distinctBy { it.packageName }
                    .map { AppInfo(it.packageName, it.appName) }
                    .sortedBy { it.appName }
                
                _uiState.value = _uiState.value.copy(
                    apps = apps,
                    rules = rules,
                    isLoading = false
                )
            }.collect()
        }
    }
    
    fun updateAppRule(
        packageName: String,
        appName: String,
        ruleType: AppRuleType,
        isEnabled: Boolean = true
    ) {
        viewModelScope.launch {
            appRulesRepository.setAppRule(packageName, appName, ruleType, isEnabled)
        }
    }
    
    fun removeAppRule(packageName: String) {
        viewModelScope.launch {
            appRulesRepository.removeAppRule(packageName)
        }
    }
}

data class AppRulesUiState(
    val apps: List<AppInfo> = emptyList(),
    val rules: Map<String, AppRule> = emptyMap(),
    val isLoading: Boolean = true
)
