package com.javohirmx.notifyr.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javohirmx.notifyr.data.repository.AppRulesRepository
import com.javohirmx.notifyr.data.repository.NotificationRepository
import com.javohirmx.notifyr.data.repository.TemporaryAppStatusRepository
import com.javohirmx.notifyr.domain.model.AppRule
import com.javohirmx.notifyr.domain.model.AppRuleType
import com.javohirmx.notifyr.domain.model.TemporaryAppStatus
import com.javohirmx.notifyr.domain.model.TemporaryStatus
import com.javohirmx.notifyr.ui.history.AppInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppRulesViewModel @Inject constructor(
    private val appRulesRepository: AppRulesRepository,
    private val notificationRepository: NotificationRepository,
    private val temporaryAppStatusRepository: TemporaryAppStatusRepository
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
                notificationRepository.getAllNotifications(),
                temporaryAppStatusRepository.activeStatuses
            ) { rules, notifications, tempStatuses ->
                // Extract unique apps from notifications
                val apps = notifications
                    .distinctBy { it.packageName }
                    .map { AppInfo(it.packageName, it.appName) }
                    .sortedBy { it.appName }
                
                _uiState.value = _uiState.value.copy(
                    apps = apps,
                    rules = rules,
                    temporaryStatuses = tempStatuses,
                    isLoading = false
                )
            }.collect()
        }
    }
    
    fun updateAppRule(
        packageName: String,
        appName: String,
        ruleType: AppRuleType,
        isEnabled: Boolean = true,
        syncStatusPhrases: List<String>? = null
    ) {
        viewModelScope.launch {
            appRulesRepository.setAppRule(
                packageName = packageName,
                appName = appName,
                ruleType = ruleType,
                isEnabled = isEnabled,
                syncStatusPhrases = syncStatusPhrases
            )
        }
    }
    
    fun removeAppRule(packageName: String) {
        viewModelScope.launch {
            appRulesRepository.removeAppRule(packageName)
        }
    }
    
    fun setTemporaryStatus(
        packageName: String,
        appName: String,
        status: TemporaryStatus,
        durationMinutes: Int
    ) {
        temporaryAppStatusRepository.setTemporaryStatus(
            packageName,
            appName,
            status,
            durationMinutes
        )
    }
    
    fun removeTemporaryStatus(packageName: String) {
        temporaryAppStatusRepository.removeTemporaryStatus(packageName)
    }
}

data class AppRulesUiState(
    val apps: List<AppInfo> = emptyList(),
    val rules: Map<String, AppRule> = emptyMap(),
    val temporaryStatuses: Map<String, TemporaryAppStatus> = emptyMap(),
    val isLoading: Boolean = true
)
