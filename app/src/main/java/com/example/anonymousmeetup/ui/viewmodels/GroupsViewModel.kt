package com.example.anonymousmeetup.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.anonymousmeetup.data.local.EncounterStore
import com.example.anonymousmeetup.data.model.Group
import com.example.anonymousmeetup.data.preferences.UserPreferences
import com.example.anonymousmeetup.data.repository.GroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GroupsViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val encounterStore: EncounterStore,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _groups = MutableStateFlow<List<Group>>(emptyList())
    val groups: StateFlow<List<Group>> = _groups

    private val _searchResults = MutableStateFlow<List<Group>>(emptyList())
    val searchResults: StateFlow<List<Group>> = _searchResults

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _joinSuccess = MutableStateFlow<String?>(null)
    val joinSuccess: StateFlow<String?> = _joinSuccess

    private val _filter = MutableStateFlow("Мои")
    val filter: StateFlow<String> = _filter

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isSearchLoading = MutableStateFlow(false)
    val isSearchLoading: StateFlow<Boolean> = _isSearchLoading

    private val _nearbyGroupHashes = MutableStateFlow<Set<String>>(emptySet())
    val nearbyGroupHashes: StateFlow<Set<String>> = _nearbyGroupHashes

    val isLocationTrackingEnabled = userPreferences.isLocationTrackingEnabled

    init {
        loadGroups()
        observeNearbyGroups()
    }

    private fun observeNearbyGroups() {
        viewModelScope.launch {
            encounterStore.observeEncounters().collect { encounters ->
                val now = System.currentTimeMillis()
                val recent = encounters
                    .filter { now - it.happenedAt <= 6 * 60 * 60 * 1000 }
                    .mapNotNull { it.groupHash }
                    .toSet()
                _nearbyGroupHashes.value = recent
            }
        }
    }

    fun loadGroups() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                groupRepository.getLocalJoinedGroups().collect { groups ->
                    _groups.value = groups
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _error.value = e.message
                _isLoading.value = false
            }
        }
    }

    fun setFilter(filter: String) {
        _filter.value = filter
    }

    fun searchGroups(query: String) {
        viewModelScope.launch {
            _isSearchLoading.value = true
            try {
                groupRepository.searchGroups(query).collect { groups ->
                    _searchResults.value = groups
                    _isSearchLoading.value = false
                }
            } catch (e: Exception) {
                _error.value = e.message
                _isSearchLoading.value = false
            }
        }
    }

    fun createGroup(name: String, description: String, category: String, isPrivate: Boolean) {
        viewModelScope.launch {
            try {
                groupRepository.createGroup(name, description, category, isPrivate)
                loadGroups()
            } catch (e: Exception) {
                _error.value = "Ошибка создания группы: ${e.message}"
            }
        }
    }

    fun setLocationTrackingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                userPreferences.setLocationTrackingEnabled(enabled)
            } catch (e: Exception) {
                _error.value = "Ошибка изменения статуса отслеживания: ${e.message}"
            }
        }
    }

    fun joinGroup(group: Group) {
        viewModelScope.launch {
            try {
                groupRepository.joinGroupLocally(group)
                _error.value = null
                _joinSuccess.value = group.id
                loadGroups()
            } catch (e: Exception) {
                _error.value = "Ошибка присоединения к группе: ${e.message}"
            }
        }
    }

    fun clearJoinSuccess() {
        _joinSuccess.value = null
    }

    fun leaveGroup(groupId: String) {
        viewModelScope.launch {
            try {
                groupRepository.leaveGroupLocally(groupId)
                _error.value = null
                loadGroups()
            } catch (e: Exception) {
                _error.value = "Ошибка выхода из группы: ${e.message}"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
