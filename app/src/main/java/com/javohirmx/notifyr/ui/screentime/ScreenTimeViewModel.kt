package com.javohirmx.notifyr.ui.screentime

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.javohirmx.notifyr.data.repository.ScreenTimeRepository
import com.javohirmx.notifyr.domain.model.DailyScreenTime
import com.javohirmx.notifyr.domain.model.ScreenTimeRange
import com.javohirmx.notifyr.domain.model.UsageSession
import com.javohirmx.notifyr.domain.usecase.GetScreenTimeUseCase
import com.javohirmx.notifyr.service.ScreenTimeCollectorService
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
    private val screenTimeRepository: ScreenTimeRepository,
    private val screenTimeCollectorService: ScreenTimeCollectorService
) : AndroidViewModel(application) {
    
    private val _uiState = MutableStateFlow(ScreenTimeUiState())
    val uiState: StateFlow<ScreenTimeUiState> = _uiState.asStateFlow()
    
    // Cache sessions by date to avoid reloading
    private val sessionsCache = mutableMapOf<Long, List<UsageSession>>()
    
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
                // Trigger immediate collection if permission is granted and it's TODAY range
                // This ensures we have the latest data
                if (PermissionUtils.isUsageStatsPermissionGranted(getApplication()) && range == ScreenTimeRange.TODAY) {
                    screenTimeCollectorService.triggerImmediateCollection()
                    // Wait a bit for collection to complete
                    kotlinx.coroutines.delay(500)
                }
                
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
        // Clear cache on refresh
        sessionsCache.clear()
        
        // Trigger immediate collection on refresh
        if (PermissionUtils.isUsageStatsPermissionGranted(getApplication())) {
            screenTimeCollectorService.triggerImmediateCollection()
        }
        
        loadScreenTime(_uiState.value.selectedRange)
    }
    
    suspend fun loadSessionsForDate(date: Long): List<UsageSession> {
        // Return cached sessions if available
        sessionsCache[date]?.let { return it }
        
        return try {
            val sessions = screenTimeRepository.getSessionsByDate(date)
            // Cache the sessions
            sessionsCache[date] = sessions
            sessions
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun clearSessionsCache() {
        sessionsCache.clear()
    }
}

data class ScreenTimeUiState(
    val isLoading: Boolean = false,
    val hasUsageStatsPermission: Boolean = false,
    val selectedRange: ScreenTimeRange = ScreenTimeRange.TODAY,
    val dailyScreenTime: List<DailyScreenTime> = emptyList(),
    val error: String? = null
)

