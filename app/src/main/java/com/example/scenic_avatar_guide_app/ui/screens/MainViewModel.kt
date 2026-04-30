package com.example.scenic_avatar_guide_app.ui.screens

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scenic_avatar_guide_app.data.repository.GuideRepository
import com.example.scenic_avatar_guide_app.data.local.SettingsDataStore
import com.example.scenic_avatar_guide_app.domain.model.AvatarAction
import com.example.scenic_avatar_guide_app.domain.model.AvatarExpression
import com.example.scenic_avatar_guide_app.domain.model.AvatarGesture
import com.example.scenic_avatar_guide_app.domain.model.ChatStreamEvent
import com.example.scenic_avatar_guide_app.domain.model.ChatMessage
import com.example.scenic_avatar_guide_app.domain.model.EmotionToExpression
import com.example.scenic_avatar_guide_app.domain.model.IntentToGesture
import com.example.scenic_avatar_guide_app.domain.model.RouteData
import com.example.scenic_avatar_guide_app.domain.model.AvatarState
import com.example.scenic_avatar_guide_app.domain.model.AvatarFullState
import com.example.scenic_avatar_guide_app.domain.model.ResponseMetadata
import com.example.scenic_avatar_guide_app.domain.model.SourceInfo
import com.example.scenic_avatar_guide_app.core.avatar.AvatarPlaybackManager
import com.example.scenic_avatar_guide_app.core.avatar.AvatarPlayAction
import com.example.scenic_avatar_guide_app.core.tts.VoiceInfo
import com.example.scenic_avatar_guide_app.core.tts.VoiceStyle
import com.example.scenic_avatar_guide_app.data.local.ScenicDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

import android.util.Log

private const val TAG = "MainViewModel"

/**
 * 打字机效果控制器
 * 负责平滑地逐字显示文本
 * 支持与 TTS 同步启动：等待第一个音频片段准备好后才开始显示
 */
class TypewriterController(private val scope: kotlinx.coroutines.CoroutineScope) {
    // 待显示的文本缓冲区
    private val pendingText = StringBuilder()

    // 当前消息 ID
    private var currentMessageId: String? = null

    // 已显示的文本
    private var displayedText = StringBuilder()

    // 打字机任务
    private var typewriterJob: Job? = null

    // 是否已收到全部文本
    private var isComplete = false

    // 是否允许开始显示（等待 TTS 同步信号）
    private var canStartDisplay = false

    // 基础打字间隔（毫秒）- 正常速度
    private val baseIntervalMs = 180L

    // 最大速度间隔（毫秒）- 消息全部获取后使用
    private val maxSpeedIntervalMs = 50L

    // 当前间隔
    private var currentIntervalMs = baseIntervalMs

    // 上次接收新文本的时间
    private var lastReceiveTime = 0L

    // 批量更新：累积字符数后统一回调
    private var batchChars = 0

    // 文本更新回调
    var onTextUpdate: ((messageId: String, text: String) -> Unit)? = null

    /**
     * 开始新的打字机会话
     * @param messageId 消息 ID
     * @param waitForSync 是否等待 TTS 同步信号（默认 true）
     */
    fun start(messageId: String, waitForSync: Boolean = true) {
        stop()
        currentMessageId = messageId
        pendingText.clear()
        displayedText.clear()
        batchChars = 0
        isComplete = false
        canStartDisplay = !waitForSync
        currentIntervalMs = baseIntervalMs
        lastReceiveTime = System.currentTimeMillis()

        typewriterJob = scope.launch {
            // 等待 TTS 同步信号
            while (!canStartDisplay && isActive) {
                delay(16)
            }

            while (isActive) {
                if (pendingText.isNotEmpty()) {
                    // 取出字符显示
                    val char = pendingText[0]
                    pendingText.deleteCharAt(0)
                    displayedText.append(char)
                    batchChars++

                    // 根据状态调整速度
                    currentIntervalMs = if (isComplete) {
                        // 消息已全部获取，使用最大速度
                        maxSpeedIntervalMs
                    } else {
                        // 消息还在接收中，使用正常速度
                        baseIntervalMs
                    }

                    // 批量 flush：每累积 2 个字符统一回调
                    if (batchChars >= 2) {
                        currentMessageId?.let { id ->
                            onTextUpdate?.invoke(id, displayedText.toString())
                        }
                        batchChars = 0
                    }
                } else if (isComplete) {
                    // 消息完成且已显示完毕
                    if (batchChars > 0) {
                        currentMessageId?.let { id ->
                            onTextUpdate?.invoke(id, displayedText.toString())
                        }
                        batchChars = 0
                    }
                    return@launch
                }

                delay(currentIntervalMs)
            }
        }
    }

    /**
     * 通知 TTS 已准备好，可以开始显示文字
     * 用于与数字人说话同步启动
     */
    fun notifyTtsReady() {
        canStartDisplay = true
    }

    /**
     * 添加待显示文本
     */
    fun append(text: String) {
        pendingText.append(text)
        lastReceiveTime = System.currentTimeMillis()
    }

    /**
     * 标记消息已全部获取，切换到最大速度完成剩余显示
     */
    fun finish() {
        isComplete = true
    }

    /**
     * 立即显示所有剩余文本（用于取消等场景）
     */
    fun flush() {
        currentMessageId?.let { id ->
            val allText = displayedText.toString() + pendingText.toString()
            onTextUpdate?.invoke(id, allText)
            displayedText.clear()
            displayedText.append(allText)
            pendingText.clear()
            batchChars = 0
        }
    }

    /**
     * 停止打字机
     */
    fun stop() {
        typewriterJob?.cancel()
        typewriterJob = null
        currentMessageId = null
        pendingText.clear()
        displayedText.clear()
        batchChars = 0
        isComplete = false
    }
}

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

    // 活跃对话中（流式响应 + 数字人播放期间），用于输入区显示 STOP 按钮
    private val _isConversationActive = MutableStateFlow(false)
    val isConversationActive: StateFlow<Boolean> = _isConversationActive.asStateFlow()

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
    private val playbackManager = AvatarPlaybackManager(application, repository).apply {
        // 设置第一个 TTS 片段开始播放的回调，用于与打字机同步启动
        onFirstSegmentStart = {
            typewriterController.notifyTtsReady()
        }
    }

    // 会话ID
    private var sessionId: String? = null

    // 当前助手消息 ID，用于中止请求
    private var currentAssistantMessageId: String? = null

    private var currentStreamJob: Job? = null

    // 打字机效果控制器
    private val typewriterController = TypewriterController(viewModelScope).apply {
        onTextUpdate = { messageId, text ->
            updateAssistantMessageContent(messageId, text)
        }
    }

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
            var lastGesture = _avatarFullState.value.gesture
            var lastExpression = _avatarFullState.value.expression
            var lastState = _avatarFullState.value.state
            playbackManager.avatarState.collect { state ->
                if (state.gesture != lastGesture || state.expression != lastExpression) {
                    Log.d(TAG, "observeAvatarState: gesture=${state.gesture}, expression=${state.expression}")
                    lastGesture = state.gesture
                    lastExpression = state.expression
                }
                if (lastState != AvatarState.IDLE && state.state == AvatarState.IDLE) {
                    _isConversationActive.value = false
                    currentAssistantMessageId = null
                }
                lastState = state.state
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
        Log.d(TAG, "sendMessageToBackend: text=$text, pendingImageUri=$pendingImageUri")
        cancelCurrentStream()
        currentStreamJob = viewModelScope.launch {
            Log.d(TAG, "开始流式请求流程")
            _isLoading.value = true
            _isConversationActive.value = true
            _avatarState.value = AvatarState.THINKING
            playbackManager.startStreaming()

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
                        playbackManager.stop()
                        addMessage("图片上传失败，请重试", isUser = false)
                        return@launch
                    }
                )
            }

            val assistantMessageId = addMessage(content = "", isUser = false, isLoading = true)
            currentAssistantMessageId = assistantMessageId

            // 启动打字机效果
            typewriterController.start(assistantMessageId)

            val mode = when (_currentMode.value) {
                InteractionMode.Chat -> "chat"
                InteractionMode.Route -> "route"
            }

            var receivedAnyEvent = false
            var receivedText = false
            var latestAvatarAction: AvatarAction? = null
            var latestMetadata: ResponseMetadata? = null

            try {
                repository.sendTextMessageStream(
                    sessionId = sessionId ?: "",
                    message = text,
                    mode = mode,
                    imageUrl = imageUrl
                ).collect { event ->
                    val hadAnyEvent = receivedAnyEvent
                    receivedAnyEvent = true
                    Log.d(TAG, "收到流式事件: ${event::class.simpleName}")
                    when (event) {
                        is ChatStreamEvent.MessageStart -> {
                            Log.d(TAG, "MessageStart: messageId=${event.messageId}, sessionId=${event.sessionId}")
                            event.sessionId?.let { sessionId = it }
                        }
                        is ChatStreamEvent.TextDelta -> {
                            Log.v(TAG, "TextDelta: ${event.delta.take(20)}...")
                            receivedText = true
                            // 使用打字机效果，添加到缓冲区
                            typewriterController.append(event.delta)
                        }
                        is ChatStreamEvent.TtsSegment -> {
                            Log.d(TAG, "[LATENCY] TtsSegment 收到: segmentId=${event.segment.segmentId}, time=${System.currentTimeMillis()}, audioUrl=${event.segment.audioUrl}, durationMs=${event.segment.durationMs}")
                            playbackManager.enqueueSpeechSegment(event.segment)
                        }
                        is ChatStreamEvent.AvatarActionDelta -> {
                            Log.d(TAG, "AvatarActionDelta: expression=${event.action.expression?.type}")
                            latestAvatarAction = event.action
                            updateAssistantMessage(
                                id = assistantMessageId,
                                avatarAction = event.action
                            )
                            val gestureData = event.action.gesture
                            playbackManager.updateStreamingAction(
                                expression = resolveExpression(event.action, latestMetadata),
                                expressionIntensity = event.action.expression?.intensity ?: 0.7f,
                                gesture = resolveGesture(event.action, latestMetadata),
                                gesturePriority = com.example.scenic_avatar_guide_app.domain.model.GesturePriority.fromValue(gestureData?.priority),
                                gestureLoop = gestureData?.loop ?: false,
                                gestureSpeed = gestureData?.speed ?: 1.0f,
                                motionQueue = event.action.motionQueue ?: emptyList()
                            )
                        }
                        is ChatStreamEvent.SourcesDelta -> {
                            Log.d(TAG, "SourcesDelta: ${event.sources.size} sources")
                            updateAssistantMessage(assistantMessageId, sources = event.sources)
                        }
                        is ChatStreamEvent.RouteDataDelta -> {
                            Log.d(TAG, "RouteDataDelta: ${event.routeData.title}")
                            updateAssistantMessage(assistantMessageId, routeData = event.routeData)
                        }
                        is ChatStreamEvent.MetadataDelta -> {
                            Log.d(TAG, "MetadataDelta: intent=${event.metadata.intent}")
                            latestMetadata = event.metadata
                            latestAvatarAction?.let { action ->
                                val gestureData = action.gesture
                                playbackManager.updateStreamingAction(
                                    expression = resolveExpression(action, event.metadata),
                                    expressionIntensity = action.expression?.intensity ?: 0.7f,
                                    gesture = resolveGesture(action, event.metadata),
                                    gesturePriority = com.example.scenic_avatar_guide_app.domain.model.GesturePriority.fromValue(gestureData?.priority),
                                    gestureLoop = gestureData?.loop ?: false,
                                    gestureSpeed = gestureData?.speed ?: 1.0f,
                                    motionQueue = action.motionQueue ?: emptyList()
                                )
                            }
                        }
                        ChatStreamEvent.Done -> {
                            Log.d(TAG, "Done")
                            _isLoading.value = false
                            // 保底：如果没有收到 TTS 片段，也让打字机开始
                            typewriterController.notifyTtsReady()
                            // 标记消息完成，以最大速度显示剩余文本
                            typewriterController.finish()
                            updateAssistantMessage(assistantMessageId, isLoading = false)
                            playbackManager.finishStreamingInput()
                        }
                        is ChatStreamEvent.Error -> {
                            Log.e(TAG, "Error: code=${event.code}, message=${event.message}")
                            typewriterController.notifyTtsReady()
                            _isLoading.value = false
                            typewriterController.flush()
                            playbackManager.stop()
                            updateAssistantMessage(
                                id = assistantMessageId,
                                content = currentMessageContent(assistantMessageId)
                                    .ifBlank { "抱歉，服务暂时不可用，请稍后再试。" },
                                isLoading = false,
                                isError = true
                            )
                        }
                    }
                }
                if (_isLoading.value) {
                    _isLoading.value = false
                    // 保底：如果没有收到 TTS 片段，也让打字机开始
                    typewriterController.notifyTtsReady()
                    typewriterController.finish()
                    updateAssistantMessage(assistantMessageId, isLoading = false)
                    playbackManager.finishStreamingInput()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "流式请求异常", e)
                _isLoading.value = false
                typewriterController.notifyTtsReady()
                typewriterController.flush()
                playbackManager.stop()
                updateAssistantMessage(
                    id = assistantMessageId,
                    content = currentMessageContent(assistantMessageId)
                        .ifBlank { "抱歉，服务暂时不可用，请稍后再试。" },
                    isLoading = false,
                    isError = true
                )
            }
        }
    }

    private fun cancelCurrentStream() {
        currentStreamJob?.cancel()
        currentStreamJob = null
        _isLoading.value = false
        typewriterController.stop()
        playbackManager.stop()
    }

    private fun addMessage(
        content: String,
        isUser: Boolean,
        isLoading: Boolean = false,
        isError: Boolean = false,
        sources: List<SourceInfo> = emptyList(),
        avatarAction: AvatarAction? = null,
        routeData: RouteData? = null,
        pendingImageUri: String? = null,
        imageUrl: String? = null
    ): String {
        val id = UUID.randomUUID().toString()
        val currentList = _messages.value.toMutableList()
        currentList.add(ChatMessage(
            id = id,
            content = content,
            isUser = isUser,
            timestamp = System.currentTimeMillis(),
            isLoading = isLoading,
            isError = isError,
            sources = sources,
            avatarAction = avatarAction,
            routeData = routeData,
            pendingImageUri = pendingImageUri,
            imageUrl = imageUrl
        ))
        _messages.value = currentList
        return id
    }

    private fun appendAssistantDelta(id: String, delta: String) {
        val currentList = _messages.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index == -1) return

        val current = currentList[index]
        currentList[index] = current.copy(
            content = current.content + delta,
            isLoading = true,
            isError = false
        )
        _messages.value = currentList
    }

    /**
     * 更新助手消息内容（由打字机效果调用）
     */
    private fun updateAssistantMessageContent(id: String, content: String) {
        val currentList = _messages.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index == -1) return

        val current = currentList[index]
        currentList[index] = current.copy(
            content = content,
            isLoading = true
        )
        _messages.value = currentList
    }

    private fun updateAssistantMessage(
        id: String,
        content: String? = null,
        isLoading: Boolean? = null,
        isError: Boolean? = null,
        sources: List<SourceInfo>? = null,
        avatarAction: AvatarAction? = null,
        routeData: RouteData? = null
    ) {
        val currentList = _messages.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index == -1) return

        val current = currentList[index]
        currentList[index] = current.copy(
            content = content ?: current.content,
            isLoading = isLoading ?: current.isLoading,
            isError = isError ?: current.isError,
            sources = sources ?: current.sources,
            avatarAction = avatarAction ?: current.avatarAction,
            routeData = routeData ?: current.routeData
        )
        _messages.value = currentList
    }

    private fun currentMessageContent(id: String): String {
        return _messages.value.firstOrNull { it.id == id }?.content.orEmpty()
    }

    fun setPendingImage(uri: String) {
        _pendingImageUri.value = uri
    }

    fun clearPendingImage() {
        _pendingImageUri.value = null
    }

    private fun resolveExpression(
        action: AvatarAction?,
        metadata: ResponseMetadata?
    ): AvatarExpression {
        val explicit = action?.expression?.type
        if (!explicit.isNullOrBlank()) {
            return AvatarExpression.fromValue(explicit)
        }
        return EmotionToExpression.map(metadata?.emotion)
    }

    private fun resolveGesture(
        action: AvatarAction?,
        metadata: ResponseMetadata?
    ): AvatarGesture {
        val explicit = action?.gesture?.type
        if (!explicit.isNullOrBlank()) {
            return AvatarGesture.fromValue(explicit)
        }
        return IntentToGesture.map(metadata?.intent)
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
        cancelCurrentStream()
    }

    /**
     * 中止当前对话
     */
    fun abortConversation() {
        if (!_isConversationActive.value) return

        val sessionToAbort = sessionId
        val messageToAbort = currentAssistantMessageId

        cancelCurrentStream()

        messageToAbort?.let { msgId ->
            val currentList = _messages.value.toMutableList()
            val index = currentList.indexOfFirst { it.id == msgId }
            if (index != -1) {
                val current = currentList[index]
                currentList[index] = current.copy(
                    content = current.content.ifBlank { "消息已中断。" },
                    isLoading = false,
                    isError = false
                )
                _messages.value = currentList
            }
        }

        if (sessionToAbort != null && messageToAbort != null) {
            viewModelScope.launch {
                repository.abortChat(sessionToAbort, messageToAbort)
            }
        }

        _isConversationActive.value = false
        currentAssistantMessageId = null

        viewModelScope.launch {
            createNewSession()
        }
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
        currentStreamJob?.cancel()
        playbackManager.release()
    }
}
