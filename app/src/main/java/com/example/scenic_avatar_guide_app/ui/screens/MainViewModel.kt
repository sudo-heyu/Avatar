package com.example.scenic_avatar_guide_app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scenic_avatar_guide_app.data.repository.GuideRepository
import com.example.scenic_avatar_guide_app.data.local.SettingsDataStore
import com.example.scenic_avatar_guide_app.domain.model.ChatMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: GuideRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    // 消息列表
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    // 输入文本
    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 当前交互模式
    private val _currentMode = MutableStateFlow(InteractionMode.Chat)
    val currentMode: StateFlow<InteractionMode> = _currentMode.asStateFlow()

    // 数字人状态
    private val _avatarState = MutableStateFlow(AvatarState.Idle)
    val avatarState: StateFlow<AvatarState> = _avatarState.asStateFlow()

    // 语音输入模式
    private val _voiceInputMode = MutableStateFlow(false)
    val voiceInputMode: StateFlow<Boolean> = _voiceInputMode.asStateFlow()

    // 录音状态
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    // 音量级别 (0-1)
    private val _volumeLevel = MutableStateFlow(0f)
    val volumeLevel: StateFlow<Float> = _volumeLevel.asStateFlow()

    // 会话ID
    private var sessionId: String? = null

    init {
        initSession()
    }

    private fun initSession() {
        viewModelScope.launch {
            sessionId = settingsDataStore.sessionId.first()
            if (sessionId.isNullOrBlank()) {
                createNewSession()
            }
            if (_messages.value.isEmpty()) {
                addMessage("您好！我是景灵智导，很高兴为您服务。请问有什么可以帮助您？", isUser = false)
            }
        }
    }

    private suspend fun createNewSession() {
        val deviceId = settingsDataStore.deviceId.first() ?: UUID.randomUUID().toString().also {
            settingsDataStore.setDeviceId(it)
        }
        repository.createSession(deviceId).fold(
            onSuccess = { response ->
                sessionId = response.sessionId
                settingsDataStore.setSessionId(response.sessionId)
            },
            onFailure = {
                sessionId = UUID.randomUUID().toString()
            }
        )
    }

    fun updateInputText(text: String) {
        _inputText.value = text
    }

    fun switchMode(mode: InteractionMode) {
        _currentMode.value = mode
    }

    fun sendMessage() {
        val text = _inputText.value.trim()
        if (text.isBlank() || _isLoading.value) return
        addMessage(text, isUser = true)
        _inputText.value = ""
        sendMessageToBackend(text)
    }

    private fun sendMessageToBackend(text: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _avatarState.value = AvatarState.Thinking

            if (sessionId.isNullOrBlank()) {
                createNewSession()
            }

            repository.sendTextMessage(
                sessionId = sessionId ?: "",
                message = text,
                mode = when (_currentMode.value) {
                    InteractionMode.Chat -> "chat"
                    InteractionMode.QA -> "qa"
                    InteractionMode.Route -> "route"
                }
            ).fold(
                onSuccess = { response ->
                    _avatarState.value = AvatarState.Speaking
                    addMessage(response.replyText, isUser = false)
                    kotlinx.coroutines.delay(1000)
                    _avatarState.value = AvatarState.Idle
                },
                onFailure = {
                    _avatarState.value = AvatarState.Idle
                    addMessage("抱歉，服务暂时不可用，请稍后再试。", isUser = false)
                }
            )
            _isLoading.value = false
        }
    }

    private fun addMessage(content: String, isUser: Boolean) {
        val currentList = _messages.value.toMutableList()
        currentList.add(ChatMessage(
            id = UUID.randomUUID().toString(),
            content = content,
            isUser = isUser,
            timestamp = System.currentTimeMillis()
        ))
        _messages.value = currentList
    }

    // 语音输入模式
    fun enterVoiceInputMode() {
        _voiceInputMode.value = true
    }

    fun exitVoiceInputMode() {
        _voiceInputMode.value = false
        _isRecording.value = false
        _volumeLevel.value = 0f
    }

    fun startRecording() {
        _isRecording.value = true
    }

    fun stopRecording() {
        _isRecording.value = false
        _volumeLevel.value = 0f
    }

    fun updateVolumeLevel(level: Float) {
        _volumeLevel.value = level.coerceIn(0f, 1f)
    }

    fun onSpeechRecognized(text: String) {
        _isRecording.value = false
        _volumeLevel.value = 0f
        if (text.isNotBlank()) {
            addMessage(text, isUser = true)
            sendMessageToBackend(text)
        }
        // 延迟退出语音模式，让用户看到结果
        viewModelScope.launch {
            kotlinx.coroutines.delay(300)
            _voiceInputMode.value = false
        }
    }

    fun onSpeechError(error: String) {
        _isRecording.value = false
        _volumeLevel.value = 0f
        if (error.contains("权限") || error.contains("permission", ignoreCase = true)) {
            // 不显示权限错误消息，让UI处理权限请求
        } else if (error.isNotEmpty() && error != "没有识别到语音" && error != "没有识别到语音内容") {
            addMessage("语音识别失败：$error", isUser = false)
        }
        // 延迟退出语音模式
        viewModelScope.launch {
            kotlinx.coroutines.delay(500)
            _voiceInputMode.value = false
        }
    }

    fun startCameraInput() {
        addMessage("拍照识景功能开发中，敬请期待。", isUser = false)
    }
}
