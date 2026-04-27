package com.example.scenic_avatar_guide_app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scenic_avatar_guide_app.data.local.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _baseUrl = MutableStateFlow("")
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    private val _deviceId = MutableStateFlow<String?>(null)
    val deviceId: StateFlow<String?> = _deviceId.asStateFlow()

    private val _userId = MutableStateFlow<String?>(null)
    val userId: StateFlow<String?> = _userId.asStateFlow()

    private val _sessionId = MutableStateFlow<String?>(null)
    val sessionId: StateFlow<String?> = _sessionId.asStateFlow()

    init {
        viewModelScope.launch {
            settingsDataStore.baseUrl.collect { _baseUrl.value = it }
        }
        viewModelScope.launch {
            settingsDataStore.deviceId.collect { _deviceId.value = it }
        }
        viewModelScope.launch {
            settingsDataStore.userId.collect { _userId.value = it }
        }
        viewModelScope.launch {
            settingsDataStore.sessionId.collect { _sessionId.value = it }
        }
    }

    fun updateBaseUrl(url: String) {
        viewModelScope.launch {
            settingsDataStore.setBaseUrl(url)
        }
    }

    fun clearSession() {
        viewModelScope.launch {
            settingsDataStore.clearSession()
        }
    }
}
