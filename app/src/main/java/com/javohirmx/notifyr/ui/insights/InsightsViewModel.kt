package com.javohirmx.notifyr.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javohirmx.notifyr.domain.model.InsightTimeRange
import com.javohirmx.notifyr.domain.model.NotificationInsights
import com.javohirmx.notifyr.domain.usecase.GenerateInsightsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InsightsUiState(
    val insights: NotificationInsights? = null,
    val selectedTimeRange: InsightTimeRange = InsightTimeRange.WEEK,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val generateInsightsUseCase: GenerateInsightsUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(InsightsUiState(isLoading = true))
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()
    
    init {
        loadInsights(InsightTimeRange.WEEK)
    }
    
    fun loadInsights(timeRange: InsightTimeRange) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                selectedTimeRange = timeRange,
                error = null
            )
            
            try {
                val insights = generateInsightsUseCase(timeRange.getDurationMillis())
                _uiState.value = _uiState.value.copy(
                    insights = insights,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load insights"
                )
            }
        }
    }
    
    fun refreshInsights() {
        loadInsights(_uiState.value.selectedTimeRange)
    }
}

