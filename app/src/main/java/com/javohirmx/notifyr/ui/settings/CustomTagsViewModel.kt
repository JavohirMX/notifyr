package com.javohirmx.notifyr.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javohirmx.notifyr.data.repository.CustomTagRepository
import com.javohirmx.notifyr.data.database.CustomTagEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CustomTagsUiState(
    val allTags: List<CustomTagEntity> = emptyList(),
    val editingTag: CustomTagEntity? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class CustomTagsViewModel @Inject constructor(
    private val customTagRepository: CustomTagRepository
) : ViewModel() {
    
    private val _editingTag = MutableStateFlow<CustomTagEntity?>(null)
    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    
    val uiState: StateFlow<CustomTagsUiState> = combine(
        customTagRepository.getAllTags(),
        _editingTag,
        _isLoading,
        _error
    ) { tags, editingTag, isLoading, error ->
        CustomTagsUiState(
            allTags = tags,
            editingTag = editingTag,
            isLoading = isLoading,
            error = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CustomTagsUiState()
    )
    
    fun addTag(name: String, color: String? = null) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                customTagRepository.insertTag(name.trim(), color)
                
                _isLoading.value = false
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to add tag"
                _isLoading.value = false
            }
        }
    }
    
    fun deleteTag(tag: CustomTagEntity) {
        viewModelScope.launch {
            try {
                customTagRepository.deleteTag(tag)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to delete tag"
            }
        }
    }
    
    fun editTag(tag: CustomTagEntity) {
        _editingTag.value = tag
    }
    
    fun cancelEdit() {
        _editingTag.value = null
    }
    
    fun updateTag(tag: CustomTagEntity, newName: String, newColor: String? = null) {
        viewModelScope.launch {
            try {
                val updatedTag = tag.copy(name = newName.trim(), color = newColor)
                customTagRepository.updateTag(updatedTag)
                _editingTag.value = null
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to update tag"
            }
        }
    }
    
    fun clearError() {
        _error.value = null
    }
}

