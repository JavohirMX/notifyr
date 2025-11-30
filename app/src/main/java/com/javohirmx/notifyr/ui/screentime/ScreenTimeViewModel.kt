package com.javohirmx.notifyr.ui.screentime

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.javohirmx.notifyr.data.repository.ScreenTimeRepository
import com.javohirmx.notifyr.domain.model.DailyScreenTime
import com.javohirmx.notifyr.domain.model.ScreenTimeRange
import com.javohirmx.notifyr.domain.model.UsageSession
import com.javohirmx.notifyr.domain.usecase.GetScreenTimeUseCase
import com.javohirmx.notifyr.utils.PermissionUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScreenTimeViewModel @Inject constructor(
    application: Application,
    private val getScreenTimeUseCase: GetScreenTimeUseCase,
    private val screenTimeRepository: ScreenTimeRepository
) : AndroidViewModel(application) {
    
    private val _uiState = MutableStateFlow(ScreenTimeUiState())
    val uiState: StateFlow<ScreenTimeUiState> = _uiState.asStateFlow()
    
    init {
        checkPermission()
        loadScreenTime(ScreenTimeRange.TODAY)
    }
    
    fun checkPermission() {
        val hasPermission = PermissionUtils.isUsageStatsPermissionGranted(getApplication())
        _uiState.value = _uiState.value.copy(
            hasUsageStatsPermission = hasPermission
        )
    }
    
    fun loadScreenTime(range: ScreenTimeRange) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                selectedRange = range,
                error = null
            )
            
            try {
                val dailyScreenTime = getScreenTimeUseCase(range)
                _uiState.value = _uiState.value.copy(
                    dailyScreenTime = dailyScreenTime,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load screen time"
                )
            }
        }
    }
    
    fun changeRange(range: ScreenTimeRange) {
        if (_uiState.value.selectedRange != range) {
            loadScreenTime(range)
        }
    }
    
    fun requestPermission() {
        PermissionUtils.openUsageStatsSettings(getApplication())
    }
    
    fun refresh() {
        loadScreenTime(_uiState.value.selectedRange)
    }
    
    suspend fun loadSessionsForDate(date: Long): List<UsageSession> {
        return try {
            screenTimeRepository.getSessionsByDate(date)
        } catch (e: Exception) {
            emptyList()
        }
    }
}

data class ScreenTimeUiState(
    val isLoading: Boolean = false,
    val hasUsageStatsPermission: Boolean = false,
    val selectedRange: ScreenTimeRange = ScreenTimeRange.TODAY,
    val dailyScreenTime: List<DailyScreenTime> = emptyList(),
    val error: String? = null
)

