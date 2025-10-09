package com.javohirmx.notifyr.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javohirmx.notifyr.data.service.DataExportImportService
import com.javohirmx.notifyr.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DataManagementViewModel @Inject constructor(
    private val dataExportImportService: DataExportImportService
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(DataManagementUiState())
    val uiState: StateFlow<DataManagementUiState> = _uiState.asStateFlow()
    
    fun exportData(
        context: Context,
        exportType: ExportType,
        includeSettings: Boolean = true
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true, exportError = null)
            
            val result = dataExportImportService.exportData(context, exportType, includeSettings)
            
            _uiState.value = _uiState.value.copy(
                isExporting = false,
                lastExportResult = result,
                exportError = if (!result.success) result.error else null
            )
        }
    }
    
    fun exportDataToUri(
        context: Context,
        uri: Uri,
        exportType: ExportType,
        includeSettings: Boolean = true
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true, exportError = null)
            
            val result = dataExportImportService.exportDataToUri(context, uri, exportType, includeSettings)
            
            _uiState.value = _uiState.value.copy(
                isExporting = false,
                lastExportResult = result,
                exportError = if (!result.success) result.error else null
            )
        }
    }
    
    fun importDataFromUri(
        context: Context,
        uri: Uri,
        replaceExisting: Boolean = false
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isImporting = true, importError = null)
            
            val result = dataExportImportService.importDataFromUri(context, uri, replaceExisting)
            
            _uiState.value = _uiState.value.copy(
                isImporting = false,
                lastImportResult = result,
                importError = if (!result.success) result.error else null
            )
        }
    }
    
    fun validateImportFile(context: Context, uri: Uri): Boolean {
        return dataExportImportService.validateExportFile(context, uri)
    }
    
    fun clearExportResult() {
        _uiState.value = _uiState.value.copy(
            lastExportResult = null,
            exportError = null
        )
    }
    
    fun clearImportResult() {
        _uiState.value = _uiState.value.copy(
            lastImportResult = null,
            importError = null
        )
    }
}

data class DataManagementUiState(
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val lastExportResult: ExportResult? = null,
    val lastImportResult: ImportResult? = null,
    val exportError: String? = null,
    val importError: String? = null
)
