package com.example.anonymousmeetup.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.anonymousmeetup.data.model.ChatSession
import com.example.anonymousmeetup.data.model.LocationPayload
import com.example.anonymousmeetup.data.model.PrivateMessageUiModel
import com.example.anonymousmeetup.data.preferences.UserPreferences
import com.example.anonymousmeetup.data.repository.PrivateChatRepository
import com.example.anonymousmeetup.services.MapService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PrivateChatViewModel @Inject constructor(
    private val privateChatRepository: PrivateChatRepository,
    private val mapService: MapService,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _session = MutableStateFlow<ChatSession?>(null)
    val session: StateFlow<ChatSession?> = _session

    private val _messages = MutableStateFlow<List<PrivateMessageUiModel>>(emptyList())
    val messages: StateFlow<List<PrivateMessageUiModel>> = _messages

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _info = MutableStateFlow<String?>(null)
    val info: StateFlow<String?> = _info

    private var sessionJob: Job? = null
    private var messagesJob: Job? = null

    fun bindSession(sessionId: String) {
        sessionJob?.cancel()
        sessionJob = viewModelScope.launch {
            privateChatRepository.observeSession(sessionId).collect { session ->
                _session.value = session
                if (session == null) {
                    _messages.value = emptyList()
                } else {
                    subscribeToHashes(session)
                }
            }
        }
    }

    fun sendText(text: String) {
        val session = _session.value ?: return
        viewModelScope.launch {
            runCatching {
                privateChatRepository.sendPrivateText(session.currentChatHash, text)
            }.onFailure {
                _error.value = "Send failed: ${it.message}"
            }
        }
    }

    fun sendLocation() {
        val session = _session.value ?: return
        viewModelScope.launch {
            runCatching {
                val location = mapService.getCurrentLocation()
                val payload = LocationPayload(
                    displayName = userPreferences.getNickname() ?: "Anonymous",
                    latitude = location.latitude,
                    longitude = location.longitude,
                    sentAt = System.currentTimeMillis()
                )
                privateChatRepository.sendLocation(session.currentChatHash, payload)
            }.onFailure {
                _error.value = "Failed to send location: ${it.message}"
            }
        }
    }

    fun rotatePrivacy() {
        val session = _session.value ?: return
        viewModelScope.launch {
            privateChatRepository.rotateChatHash(session.sessionId)
                .onSuccess {
                    _info.value = "Channel rotated"
                }
                .onFailure {
                    _error.value = "Rotation failed: ${it.message}"
                }
        }
    }

    fun clearInfo() {
        _info.value = null
    }

    fun clearError() {
        _error.value = null
    }

    private fun subscribeToHashes(session: ChatSession) {
        messagesJob?.cancel()
        val hashes = (listOf(session.currentChatHash) + session.previousChatHashes).distinct()
        if (hashes.isEmpty()) {
            _messages.value = emptyList()
            return
        }

        messagesJob = viewModelScope.launch {
            val flows = hashes.map { chatHash ->
                privateChatRepository.observePrivateMessages(chatHash)
            }

            if (flows.size == 1) {
                flows.first().collect { items ->
                    _messages.value = items
                        .distinctBy { it.id }
                        .sortedBy { it.timestamp }
                }
                return@launch
            }

            combine(flows) { groups ->
                groups.flatMap { it }
                    .distinctBy { it.id }
                    .sortedBy { it.timestamp }
            }.collect { merged ->
                _messages.value = merged
            }
        }
    }
}
