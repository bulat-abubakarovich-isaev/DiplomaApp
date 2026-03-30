package com.example.anonymousmeetup.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.anonymousmeetup.data.model.PrivateMessageUiModel
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
class MapViewModel @Inject constructor(
    private val privateChatRepository: PrivateChatRepository,
    val mapService: MapService
) : ViewModel() {

    private val _markers = MutableStateFlow<List<PeerMarker>>(emptyList())
    val markers: StateFlow<List<PeerMarker>> = _markers

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var observeJob: Job? = null
    private var markersJob: Job? = null

    init {
        observePrivateLocations()
    }

    private fun observePrivateLocations() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            privateChatRepository.observeSessions().collect { sessions ->
                val active = sessions.filter { it.isActive }
                if (active.isEmpty()) {
                    markersJob?.cancel()
                    _markers.value = emptyList()
                    return@collect
                }
                subscribeToSessionMessages(active)
            }
        }
    }

    private fun subscribeToSessionMessages(active: List<com.example.anonymousmeetup.data.model.ChatSession>) {
        markersJob?.cancel()
        markersJob = viewModelScope.launch {
            val flows = active.map { session ->
                privateChatRepository.observePrivateMessages(session.currentChatHash)
            }

            if (flows.size == 1) {
                flows.first().collect { list ->
                    _markers.value = extractMarkers(active, listOf(list))
                }
                return@launch
            }

            combine(flows) { arrays ->
                arrays.map { it.toList() }
            }.collect { grouped ->
                _markers.value = extractMarkers(active, grouped)
            }
        }
    }

    private fun extractMarkers(
        sessions: List<com.example.anonymousmeetup.data.model.ChatSession>,
        groupedMessages: List<List<PrivateMessageUiModel>>
    ): List<PeerMarker> {
        val markers = mutableListOf<PeerMarker>()

        sessions.forEachIndexed { index, session ->
            val latestLocation = groupedMessages
                .getOrNull(index)
                .orEmpty()
                .filter { it.type == "LOCATION" && it.location != null }
                .maxByOrNull { it.timestamp }
                ?.location

            if (latestLocation != null) {
                markers.add(
                    PeerMarker(
                        sessionId = session.sessionId,
                        alias = session.peerDisplayName,
                        latitude = latestLocation.latitude,
                        longitude = latestLocation.longitude,
                        timestamp = latestLocation.sentAt
                    )
                )
            }
        }

        return markers.sortedByDescending { it.timestamp }
    }

    override fun onCleared() {
        super.onCleared()
        markersJob?.cancel()
        mapService.onStop()
    }

    data class PeerMarker(
        val sessionId: String,
        val alias: String,
        val latitude: Double,
        val longitude: Double,
        val timestamp: Long
    )
}
