package com.javohirmx.notifyr.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javohirmx.notifyr.data.repository.ConversationMuteRepository
import com.javohirmx.notifyr.domain.model.MutedConversation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MutedConversationsViewModel @Inject constructor(
    private val conversationMuteRepository: ConversationMuteRepository
) : ViewModel() {

    val mutedConversations: StateFlow<List<MutedConversation>> =
        conversationMuteRepository.mutedConversations
            .map { mutes ->
                mutes.values.sortedWith(
                    compareBy({ it.appName.lowercase() }, { it.label.lowercase() })
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    fun unmute(conversation: MutedConversation) {
        viewModelScope.launch {
            conversationMuteRepository.unmute(
                conversation.packageName,
                conversation.conversationId
            )
        }
    }
}
