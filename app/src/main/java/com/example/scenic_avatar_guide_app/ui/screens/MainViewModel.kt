package com.example.scenic_avatar_guide_app.ui.screens

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scenic_avatar_guide_app.data.repository.GuideRepository
import com.example.scenic_avatar_guide_app.data.local.SettingsDataStore
import com.example.scenic_avatar_guide_app.domain.model.AvatarAction
import com.example.scenic_avatar_guide_app.domain.model.AvatarExpression
import com.example.scenic_avatar_guide_app.domain.model.AvatarGesture
import com.example.scenic_avatar_guide_app.domain.model.ChatResponseData
import com.example.scenic_avatar_guide_app.domain.model.ChatMessage
import com.example.scenic_avatar_guide_app.domain.model.EmotionToExpression
import com.example.scenic_avatar_guide_app.domain.model.IntentToGesture
import com.example.scenic_avatar_guide_app.domain.model.RouteData
import com.example.scenic_avatar_guide_app.domain.model.AvatarState
import com.example.scenic_avatar_guide_app.domain.model.AvatarFullState
import com.example.scenic_avatar_guide_app.domain.model.SourceInfo
import com.example.scenic_avatar_guide_app.core.avatar.AvatarPlaybackManager
import com.example.scenic_avatar_guide_app.core.avatar.AvatarPlayAction
import com.example.scenic_avatar_guide_app.core.tts.VoiceInfo
import com.example.scenic_avatar_guide_app.core.tts.VoiceStyle
import com.example.scenic_avatar_guide_app.data.local.ScenicDataSource
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
    private val application: Application,
    private val repository: GuideRepository,
    private val settingsDataStore: SettingsDataStore,
    private val scenicDataSource: ScenicDataSource
) : ViewModel() {

    val scenicAreas = scenicDataSource.loadScenicAreas()

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
    private val _avatarState = MutableStateFlow<AvatarState>(AvatarState.IDLE)
    val avatarState: StateFlow<AvatarState> = _avatarState.asStateFlow()

    // 数字人完整状态（用于 Live2D）
    private val _avatarFullState = MutableStateFlow(AvatarFullState())
    val avatarFullState: StateFlow<AvatarFullState> = _avatarFullState.asStateFlow()

    // 语音输入模式
    private val _voiceInputMode = MutableStateFlow(false)
    val voiceInputMode: StateFlow<Boolean> = _voiceInputMode.asStateFlow()

    // 录音状态
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    // 音量级别 (0-1)
    private val _volumeLevel = MutableStateFlow(0f)
    val volumeLevel: StateFlow<Float> = _volumeLevel.asStateFlow()

    // 测试面板是否显示
    private val _showTestPanel = MutableStateFlow(true)
    val showTestPanel: StateFlow<Boolean> = _showTestPanel.asStateFlow()

    // 待发送图片 URI
    private val _pendingImageUri = MutableStateFlow<String?>(null)
    val pendingImageUri: StateFlow<String?> = _pendingImageUri.asStateFlow()

    // 是否显示景区景点选择对话框
    private val _showScenicSelection = MutableStateFlow(false)
    val showScenicSelection: StateFlow<Boolean> = _showScenicSelection.asStateFlow()

    // 当前发音人（默认 Edge-TTS 晓晓）
    private val _currentVoice = MutableStateFlow(VoiceInfo(
        id = "zh-CN-XiaoxiaoNeural",
        displayName = "晓晓",
        description = "亲和女声",
        gender = com.example.scenic_avatar_guide_app.core.tts.Gender.FEMALE,
        style = VoiceStyle.FRIENDLY
    ))
    val currentVoice: StateFlow<VoiceInfo> = _currentVoice.asStateFlow()

    // 数字人播放管理器
    private val playbackManager = AvatarPlaybackManager(application, repository)

    // 会话ID
    private var sessionId: String? = null

    init {
        viewModelScope.launch {
            val scenicId = settingsDataStore.scenicId.first()
            val spotId = settingsDataStore.spotId.first()
            if (scenicId == null || spotId == null) {
                _showScenicSelection.value = true
            } else {
                initSession()
            }
        }
        observeAvatarState()
        observeVoiceChanges()
        observeSessionChanges()
        observeVoiceSettings()
    }

    fun onScenicSpotSelected(scenicId: String, spotId: String) {
        viewModelScope.launch {
            settingsDataStore.setScenicId(scenicId)
            settingsDataStore.setSpotId(spotId)
            _showScenicSelection.value = false
            initSession()
        }
    }

    fun dismissScenicSelection() {
        _showScenicSelection.value = false
    }

    /**
     * 观察数字人状态变化
     */
    private fun observeAvatarState() {
        viewModelScope.launch {
            playbackManager.avatarState.collect { state ->
                _avatarFullState.value = state
                _avatarState.value = state.state
            }
        }
    }

    /**
     * 观察发音人变化
     */
    private fun observeVoiceChanges() {
        viewModelScope.launch {
            playbackManager.currentVoice.collect { voice ->
                _currentVoice.value = voice
            }
        }
    }

    private fun observeSessionChanges() {
        viewModelScope.launch {
            settingsDataStore.sessionId.collect { latestSessionId ->
                sessionId = latestSessionId
            }
        }
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
        val pendingImage = _pendingImageUri.value
        if ((text.isBlank() && pendingImage == null) || _isLoading.value) return

        addMessage(
            content = text,
            isUser = true,
            pendingImageUri = pendingImage
        )
        _inputText.value = ""
        _pendingImageUri.value = null
        sendMessageToBackend(text, pendingImage)
    }

    private fun sendMessageToBackend(text: String, pendingImageUri: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            _avatarState.value = AvatarState.THINKING

            if (sessionId.isNullOrBlank()) {
                createNewSession()
            }

            var imageUrl: String? = null
            if (pendingImageUri != null) {
                val uri = android.net.Uri.parse(pendingImageUri)
                repository.uploadImage(uri, application).fold(
                    onSuccess = { imageUrl = it },
                    onFailure = {
                        _isLoading.value = false
                        _avatarState.value = AvatarState.IDLE
                        addMessage("图片上传失败，请重试", isUser = false)
                        return@launch
                    }
                )
            }

            repository.sendTextMessage(
                sessionId = sessionId ?: "",
                message = text,
                mode = when (_currentMode.value) {
                    InteractionMode.Chat -> "chat"
                    InteractionMode.Route -> "route"
                },
                imageUrl = imageUrl
            ).fold(
                onSuccess = { response ->
                    _avatarState.value = AvatarState.SPEAKING
                    addMessage(
                        content = response.replyText,
                        isUser = false,
                        sources = response.sources,
                        avatarAction = response.avatarAction,
                        routeData = response.routeData
                    )

                    playbackManager.play(buildAvatarPlayAction(response))
                },
                onFailure = {
                    _avatarState.value = AvatarState.IDLE
                    addMessage("抱歉，服务暂时不可用，请稍后再试。", isUser = false)
                }
            )
            _isLoading.value = false
        }
    }

    private fun addMessage(
        content: String,
        isUser: Boolean,
        sources: List<SourceInfo> = emptyList(),
        avatarAction: AvatarAction? = null,
        routeData: RouteData? = null,
        pendingImageUri: String? = null,
        imageUrl: String? = null
    ) {
        val currentList = _messages.value.toMutableList()
        currentList.add(ChatMessage(
            id = UUID.randomUUID().toString(),
            content = content,
            isUser = isUser,
            timestamp = System.currentTimeMillis(),
            sources = sources,
            avatarAction = avatarAction,
            routeData = routeData,
            pendingImageUri = pendingImageUri,
            imageUrl = imageUrl
        ))
        _messages.value = currentList
    }

    fun setPendingImage(uri: String) {
        _pendingImageUri.value = uri
    }

    fun clearPendingImage() {
        _pendingImageUri.value = null
    }

    private fun buildAvatarPlayAction(response: ChatResponseData): AvatarPlayAction {
        val action = response.avatarAction
        val expression = resolveExpression(response)
        val gesture = resolveGesture(response)

        return AvatarPlayAction(
            text = response.replyText,
            expression = expression,
            expressionIntensity = action?.expression?.intensity ?: 0.7f,
            gesture = gesture,
            motionQueue = action?.motionQueue ?: emptyList()
        )
    }

    private fun resolveExpression(response: ChatResponseData): AvatarExpression {
        val explicit = response.avatarAction?.expression?.type
        if (!explicit.isNullOrBlank()) {
            return AvatarExpression.fromValue(explicit)
        }
        return EmotionToExpression.map(response.effectiveEmotion)
    }

    private fun resolveGesture(response: ChatResponseData): AvatarGesture {
        val explicit = response.avatarAction?.gesture?.type
        if (!explicit.isNullOrBlank()) {
            return AvatarGesture.fromValue(explicit)
        }
        return IntentToGesture.map(response.effectiveIntent)
    }

    // ==================== 数字人测试功能 ====================

    /**
     * 播放测试动作
     */
    fun playTestAction(action: AvatarPlayAction) {
        playbackManager.play(action)
    }

    /**
     * 停止播放
     */
    fun stopPlayback() {
        playbackManager.stop()
    }

    /**
     * 切换测试面板显示
     */
    fun toggleTestPanel() {
        _showTestPanel.value = !_showTestPanel.value
    }

    /**
     * 获取可用发音人列表
     */
    fun getAvailableVoices(): List<VoiceInfo> = playbackManager.getAvailableVoices()

    /**
     * 按风格获取发音人
     */
    fun getVoicesByStyle(): Map<VoiceStyle, List<VoiceInfo>> = playbackManager.getVoicesByStyle()

    /**
     * 监听设置中发音人变化，实时同步到播放管理器
     */
    private fun observeVoiceSettings() {
        viewModelScope.launch {
            settingsDataStore.voiceId.collect { voiceId ->
                playbackManager.setVoice(voiceId)
            }
        }
    }

    /**
     * 设置发音人（供 UI 直接调用，同时更新 DataStore）
     */
    fun setVoice(voiceId: String) {
        viewModelScope.launch {
            settingsDataStore.setVoiceId(voiceId)
        }
    }

    /**
     * 设置语速
     */
    fun setSpeed(speed: Float) {
        playbackManager.setSpeed(speed)
    }

    // ==================== 语音输入功能 ====================

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
        viewModelScope.launch {
            kotlinx.coroutines.delay(300)
            _voiceInputMode.value = false
        }
    }

    fun onSpeechError(error: String) {
        _isRecording.value = false
        _volumeLevel.value = 0f
        if (error.contains("权限") || error.contains("permission", ignoreCase = true)) {
        } else if (error.isNotEmpty() && error != "没有识别到语音" && error != "没有识别到语音内容") {
            addMessage("语音识别失败：$error", isUser = false)
        }
        viewModelScope.launch {
            kotlinx.coroutines.delay(500)
            _voiceInputMode.value = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        playbackManager.release()
    }
}
