package com.javohirmx.notifyr.ui.ml

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javohirmx.notifyr.domain.ml.HybridNotificationClassifier
import com.javohirmx.notifyr.domain.ml.MLModelStats
import com.javohirmx.notifyr.domain.ml.MLTrainingDataManager
import com.javohirmx.notifyr.domain.ml.TrainingDataStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MLSettingsUiState(
    val isMLEnabled: Boolean = true,
    val modelStats: MLModelStats = MLModelStats(0, 0L, 0f, false),
    val trainingDataStats: TrainingDataStats = TrainingDataStats(0, 0, 0, 0, emptyList(), null, null),
    val isTraining: Boolean = false,
    val trainingProgress: String = "",
    val error: String? = null
)

@HiltViewModel
class MLSettingsViewModel @Inject constructor(
    private val hybridClassifier: HybridNotificationClassifier,
    private val trainingDataManager: MLTrainingDataManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(MLSettingsUiState())
    val uiState: StateFlow<MLSettingsUiState> = _uiState.asStateFlow()
    
    init {
        loadSettings()
    }
    
    fun loadSettings() {
        viewModelScope.launch {
            try {
                val modelStats = hybridClassifier.getMLStats()
                val trainingStats = trainingDataManager.getTrainingDataStats()
                val isEnabled = hybridClassifier.isMLEnabled()
                
                _uiState.value = _uiState.value.copy(
                    isMLEnabled = isEnabled,
                    modelStats = modelStats,
                    trainingDataStats = trainingStats,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Failed to load settings"
                )
            }
        }
    }
    
    fun toggleMLEnabled(enabled: Boolean) {
        hybridClassifier.setMLEnabled(enabled)
        _uiState.value = _uiState.value.copy(isMLEnabled = enabled)
    }
    
    fun trainModel() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isTraining = true,
                    trainingProgress = "Training model..."
                )
                
                hybridClassifier.trainModel(epochs = 10)
                
                // Refresh stats
                val modelStats = hybridClassifier.getMLStats()
                
                _uiState.value = _uiState.value.copy(
                    isTraining = false,
                    trainingProgress = "Training complete!",
                    modelStats = modelStats
                )
                
                // Clear progress after a delay
                kotlinx.coroutines.delay(2000)
                _uiState.value = _uiState.value.copy(trainingProgress = "")
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isTraining = false,
                    trainingProgress = "",
                    error = "Training failed: ${e.message}"
                )
            }
        }
    }
    
    fun resetModel() {
        viewModelScope.launch {
            try {
                hybridClassifier.resetMLModel()
                loadSettings()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Reset failed: ${e.message}"
                )
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

