package com.example.anonymousmeetup.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.anonymousmeetup.data.model.GroupMessage
import com.example.anonymousmeetup.data.preferences.UserPreferences
import com.example.anonymousmeetup.data.repository.GroupChatRepository
import com.example.anonymousmeetup.data.repository.GroupRepository
import com.example.anonymousmeetup.data.repository.PrivateChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val groupChatRepository: GroupChatRepository,
    private val groupRepository: GroupRepository,
    private val privateChatRepository: PrivateChatRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _messages = MutableStateFlow<List<UiMessage>>(emptyList())
    val messages: StateFlow<List<UiMessage>> = _messages

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _groupHash = MutableStateFlow<String?>(null)
    val groupHash: StateFlow<String?> = _groupHash

    val currentLogin: Flow<String?> = userPreferences.nickname

    fun loadMessages(groupId: String) {
        viewModelScope.launch {
            try {
                val group = groupRepository.getGroup(groupId)
                _groupHash.value = group.groupHash
                groupChatRepository.observeGroupMessages(group.groupHash).collect { messages ->
                    _messages.value = mapToUiMessages(group.groupHash, messages)
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun sendMessage(groupId: String, text: String) {
        viewModelScope.launch {
            try {
                val group = groupRepository.getGroup(groupId)
                groupChatRepository.sendGroupMessage(group.groupHash, text)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun startPrivateChat(targetAlias: String, targetPublicKey: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val result = privateChatRepository.initiateHandshake("${targetAlias}::${targetPublicKey}")
            result
                .onSuccess { sessionId ->
                    _error.value = null
                    onResult(sessionId)
                }
                .onFailure { throwable ->
                    _error.value = "Ошибка создания приватного канала: ${throwable.message}"
                    onResult(null)
                }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun setError(message: String) {
        _error.value = message
    }

    private fun mapToUiMessages(groupHash: String, messages: List<GroupMessage>): List<UiMessage> {
        return messages.mapNotNull { message ->
            val text = groupChatRepository.decryptGroupMessage(groupHash, message) ?: return@mapNotNull null
            UiMessage(
                id = message.id,
                senderAlias = message.senderAlias,
                text = text,
                timestamp = message.timestamp,
                isMine = false
            )
        }
    }

    data class UiMessage(
        val id: String,
        val senderAlias: String,
        val text: String,
        val timestamp: Long,
        val isMine: Boolean
    )
}
