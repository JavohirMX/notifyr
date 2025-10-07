package com.javohirmx.notifyr.ui.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javohirmx.notifyr.utils.PermissionUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val isNotificationListenerEnabled: Boolean = false,
    val isOnboardingCompleted: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()
    
    private val sharedPreferences = context.getSharedPreferences("notifyr_prefs", Context.MODE_PRIVATE)
    
    init {
        checkPermissions()
        checkOnboardingStatus()
    }
    
    fun checkPermissions() {
        viewModelScope.launch {
            val isEnabled = PermissionUtils.isNotificationListenerEnabled(context)
            _uiState.value = _uiState.value.copy(
                isNotificationListenerEnabled = isEnabled
            )
        }
    }
    
    private fun checkOnboardingStatus() {
        val isCompleted = sharedPreferences.getBoolean("onboarding_completed", false)
        _uiState.value = _uiState.value.copy(
            isOnboardingCompleted = isCompleted
        )
    }
    
    fun completeOnboarding() {
        sharedPreferences.edit()
            .putBoolean("onboarding_completed", true)
            .apply()
        
        _uiState.value = _uiState.value.copy(
            isOnboardingCompleted = true
        )
    }
    
    fun isOnboardingCompleted(): Boolean {
        return sharedPreferences.getBoolean("onboarding_completed", false)
    }
}
