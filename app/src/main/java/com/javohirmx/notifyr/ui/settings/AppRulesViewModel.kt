package com.javohirmx.notifyr.ui.settings

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javohirmx.notifyr.data.repository.AppRulesRepository
import com.javohirmx.notifyr.domain.model.AppRule
import com.javohirmx.notifyr.domain.model.AppRuleType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class AppRulesUiState(
    val installedApps: List<InstalledApp> = emptyList(),
    val appRules: List<AppRule> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AppRulesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appRulesRepository: AppRulesRepository
) : ViewModel() {
    
    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    
    val uiState: StateFlow<AppRulesUiState> = combine(
        _installedApps,
        appRulesRepository.appRules,
        _isLoading,
        _error
    ) { installedApps, appRules, isLoading, error ->
        AppRulesUiState(
            installedApps = installedApps,
            appRules = appRules.values.toList(),
            isLoading = isLoading,
            error = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppRulesUiState()
    )
    
    
    fun loadInstalledApps() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                val installedApps = withContext(Dispatchers.IO) {
                    getInstalledApps()
                }
                
                _installedApps.value = installedApps
                _isLoading.value = false
            } catch (e: Exception) {
                _error.value = e.message
                _isLoading.value = false
            }
        }
    }
    
    private fun getInstalledApps(): List<InstalledApp> {
        val packageManager = context.packageManager
        val installedPackages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        val currentAppRules = appRulesRepository.appRules.value
        
        return installedPackages
            .filter { appInfo ->
                // Filter out apps that can't launch (no main activity)
                packageManager.getLaunchIntentForPackage(appInfo.packageName) != null ||
                // Or include system apps that might send notifications
                (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            }
            .map { appInfo ->
                val appName = packageManager.getApplicationLabel(appInfo).toString()
                val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val currentRule = currentAppRules[appInfo.packageName]?.ruleType
                
                InstalledApp(
                    packageName = appInfo.packageName,
                    appName = appName,
                    isSystemApp = isSystemApp,
                    currentRule = currentRule
                )
            }
            .sortedWith(compareBy<InstalledApp> { it.currentRule == null }
                .thenBy { it.isSystemApp }
                .thenBy { it.appName.lowercase() })
    }
    
    fun updateAppRule(packageName: String, appName: String, ruleType: AppRuleType?) {
        appRulesRepository.setAppRule(packageName, appName, ruleType)
        
        // Refresh the installed apps list to reflect the change
        viewModelScope.launch {
            val updatedApps = withContext(Dispatchers.IO) {
                getInstalledApps()
            }
            _installedApps.value = updatedApps
        }
    }
    
    private fun getAppName(packageName: String): String {
        return try {
            val packageManager = context.packageManager
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }
    
    fun getAppRule(packageName: String): AppRule? {
        return appRulesRepository.getAppRule(packageName)
    }
    
    fun getAllAppRules(): List<AppRule> {
        return appRulesRepository.getAllAppRules()
    }
}
