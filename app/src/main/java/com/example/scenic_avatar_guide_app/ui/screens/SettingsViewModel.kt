package com.example.scenic_avatar_guide_app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scenic_avatar_guide_app.core.tts.RemoteTTSController
import com.example.scenic_avatar_guide_app.core.tts.VoiceInfo
import com.example.scenic_avatar_guide_app.core.tts.VoiceStyle
import com.example.scenic_avatar_guide_app.data.local.SettingsDataStore
import com.example.scenic_avatar_guide_app.data.repository.GuideRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URI
import javax.inject.Inject

enum class ConnectionState {
    UNKNOWN, CHECKING, CONNECTED, DISCONNECTED
}

data class ServerEndpointConfig(
    val scheme: String = "http",
    val host: String = "",
    val port: String = ""
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val repository: GuideRepository
) : ViewModel() {

    private val _baseUrl = MutableStateFlow("")
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    private val _deviceId = MutableStateFlow<String?>(null)
    val deviceId: StateFlow<String?> = _deviceId.asStateFlow()

    private val _userId = MutableStateFlow<String?>(null)
    val userId: StateFlow<String?> = _userId.asStateFlow()

    private val _sessionId = MutableStateFlow<String?>(null)
    val sessionId: StateFlow<String?> = _sessionId.asStateFlow()

    private val _serverEndpoint = MutableStateFlow(ServerEndpointConfig())
    val serverEndpoint: StateFlow<ServerEndpointConfig> = _serverEndpoint.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.UNKNOWN)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _isCheckingConnection = MutableStateFlow(false)
    val isCheckingConnection: StateFlow<Boolean> = _isCheckingConnection.asStateFlow()

    private val _currentVoiceId = MutableStateFlow(SettingsDataStore.DEFAULT_VOICE_ID)
    val currentVoiceId: StateFlow<String> = _currentVoiceId.asStateFlow()

    val availableVoices: List<VoiceInfo> = RemoteTTSController.AVAILABLE_VOICES

    init {
        viewModelScope.launch {
            settingsDataStore.baseUrl.collect {
                _baseUrl.value = it
                _serverEndpoint.value = parseBaseUrl(it)
            }
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
        viewModelScope.launch {
            settingsDataStore.voiceId.collect { _currentVoiceId.value = it }
        }
    }

    fun updateBaseUrl(url: String) {
        viewModelScope.launch {
            val current = _baseUrl.value
            settingsDataStore.setBaseUrl(url)
            if (current != url) {
                settingsDataStore.clearSession()
                _statusMessage.value = "服务器地址已更新，已清除旧会话"
            } else {
                _statusMessage.value = "服务器地址已保存"
            }
        }
    }

    fun saveServerEndpoint(scheme: String, host: String, port: String) {
        val normalizedHost = host.trim()
        val normalizedPort = port.trim()
        val safeScheme = if (scheme.lowercase() == "https") "https" else "http"

        if (normalizedHost.isBlank()) {
            _statusMessage.value = "请输入服务器 IP 或域名"
            return
        }

        val portValue = normalizedPort.toIntOrNull()
        if (portValue == null || portValue !in 1..65535) {
            _statusMessage.value = "端口必须在 1 到 65535 之间"
            return
        }

        updateBaseUrl("$safeScheme://$normalizedHost:$portValue/")
    }

    fun clearSession() {
        viewModelScope.launch {
            settingsDataStore.clearSession()
        }
    }

    fun setVoiceId(voiceId: String) {
        viewModelScope.launch {
            settingsDataStore.setVoiceId(voiceId)
            _statusMessage.value = "发音人已切换"
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun checkConnection() {
        viewModelScope.launch {
            _isCheckingConnection.value = true
            _connectionState.value = ConnectionState.CHECKING
            try {
                val result = repository.healthCheck()
                _connectionState.value = if (result is com.example.scenic_avatar_guide_app.core.common.NetworkResult.Success) {
                    _statusMessage.value = "连接成功"
                    ConnectionState.CONNECTED
                } else {
                    _statusMessage.value = "连接失败"
                    ConnectionState.DISCONNECTED
                }
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.DISCONNECTED
                _statusMessage.value = "连接异常：${e.message}"
            }
            _isCheckingConnection.value = false
        }
    }

    private fun parseBaseUrl(url: String): ServerEndpointConfig {
        return runCatching {
            val normalized = if (url.endsWith("/")) url else "$url/"
            val uri = URI(normalized)
            val scheme = uri.scheme?.lowercase().orEmpty().ifBlank { "http" }
            val host = uri.host.orEmpty()
            val port = when {
                uri.port != -1 -> uri.port.toString()
                scheme == "https" -> "443"
                else -> "80"
            }
            ServerEndpointConfig(
                scheme = scheme,
                host = host,
                port = port
            )
        }.getOrDefault(ServerEndpointConfig())
    }
}
