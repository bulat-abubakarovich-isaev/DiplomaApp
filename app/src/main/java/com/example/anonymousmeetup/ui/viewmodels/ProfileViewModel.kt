package com.example.anonymousmeetup.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.anonymousmeetup.data.preferences.UserPreferences
import com.example.anonymousmeetup.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    val nickname = userPreferences.nickname
    val isLocationTrackingEnabled = userPreferences.isLocationTrackingEnabled
    val notificationsEnabled = userPreferences.notificationsEnabled
    val secureBackupEnabled = userPreferences.secureBackupEnabled

    private val _publicKey = MutableStateFlow<String?>(null)
    val publicKey: StateFlow<String?> = _publicKey

    private val _keyDate = MutableStateFlow<String?>(null)
    val keyDate: StateFlow<String?> = _keyDate

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _info = MutableStateFlow<String?>(null)
    val info: StateFlow<String?> = _info

    private val _backupPayload = MutableStateFlow<String?>(null)
    val backupPayload: StateFlow<String?> = _backupPayload

    init {
        viewModelScope.launch {
            try {
                val keyInfo = userRepository.ensureDailyKeys()
                _publicKey.value = keyInfo.publicKey
                _keyDate.value = keyInfo.keyDate
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun setLocationTrackingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                userPreferences.setLocationTrackingEnabled(enabled)
            } catch (e: Exception) {
                _error.value = "Ошибка изменения статуса: ${e.message}"
            }
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                userPreferences.setNotificationsEnabled(enabled)
            } catch (e: Exception) {
                _error.value = "Ошибка изменения уведомлений: ${e.message}"
            }
        }
    }

    fun setSecureBackupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                userPreferences.setSecureBackupEnabled(enabled)
            } catch (e: Exception) {
                _error.value = "Ошибка изменения secure backup: ${e.message}"
            }
        }
    }

    fun logout() {
        userRepository.logout()
        viewModelScope.launch {
            userPreferences.clearUserData()
        }
    }

    fun updateNickname(newNickname: String) {
        viewModelScope.launch {
            try {
                userRepository.updateLogin(newNickname)
                _info.value = "Ник обновлён"
            } catch (e: Exception) {
                _error.value = "Не удалось обновить ник: ${e.message}"
            }
        }
    }

    fun exportBackup(password: String) {
        viewModelScope.launch {
            runCatching {
                userRepository.exportBackup(password)
            }.onSuccess { backup ->
                _backupPayload.value = backup
                _info.value = "Backup создан"
            }.onFailure {
                _error.value = "Ошибка экспорта: ${it.message}"
            }
        }
    }

    fun importBackup(data: String, password: String) {
        viewModelScope.launch {
            runCatching {
                userRepository.importBackup(data, password)
            }.onSuccess {
                val keyInfo = userRepository.ensureDailyKeys()
                _publicKey.value = keyInfo.publicKey
                _keyDate.value = keyInfo.keyDate
                _info.value = "Backup успешно импортирован"
            }.onFailure {
                _error.value = "Ошибка импорта: ${it.message}"
            }
        }
    }

    fun clearInfo() {
        _info.value = null
    }

    fun clearError() {
        _error.value = null
    }

    fun consumeBackupPayload() {
        _backupPayload.value = null
    }
}
