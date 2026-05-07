package com.example.scenic_avatar_guide_app.ui.screens

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scenic_avatar_guide_app.data.repository.AuthRepository
import com.example.scenic_avatar_guide_app.data.repository.GuideRepository
import com.example.scenic_avatar_guide_app.data.repository.SessionRepository
import com.example.scenic_avatar_guide_app.data.repository.toChatMessage
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

import android.util.Log

private const val TAG = "MainViewModel"
private const val MAX_MESSAGES = 100
private const val MAX_STREAM_CONTINUATION_ATTEMPTS = 3

/**
 * 打字机效果控制器
 * 负责平滑地逐字显示文本
 *
 * 设计要点：
 * 1. 等待首个 TTS 片段开始播放后再显示文字，实现音画同步
 * 2. 根据 TTS 播放进度动态调整显示速度
 * 3. 控制更新频率防止 Compose 渲染崩溃
 */
class TypewriterController(private val scope: kotlinx.coroutines.CoroutineScope) {
    // Phase 1 修复: Mutex 保护并发访问
    private val mutex = Mutex()

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

    // 打字速度：每次从缓冲区取出的字符数。提高取出数量、降低 UI 提交频率，
    // 避免长回复期间频繁触发 Compose 文本重绘。
    private val normalCharsPerTick = 8
    private val completeCharsPerTick = 40
    private val longTextCharsPerTick = 64

    // 打字间隔（毫秒）- 根据接收速度动态调整
    private var currentIntervalMs = 80L

    // 最小间隔（快速模式）
    private val minIntervalMs = 50L

    // 最大间隔（慢速模式）
    private val maxIntervalMs = 110L

    // 批量更新阈值
    private var batchChars = 0

    // 最小 UI 更新间隔（毫秒）- 控制 Compose Text 重绘频率，同时避免回复显得断续。
    private val minUpdateIntervalMs = 180L
    private val longTextUpdateIntervalMs = 320L

    // 上次更新时间
    private var lastUpdateTime = 0L

    // 上次接收文本时间
    private var lastAppendTime = 0L

    // TTS 同步等待超时（毫秒）- 如果 TTS 在此时间内未就绪，直接开始显示文字
    private val ttsSyncTimeoutMs = 1500L

    // 是否收到过 TTS 就绪信号
    private var receivedTtsReady = false

    // 文本更新回调
    var onTextUpdate: ((messageId: String, text: String) -> Unit)? = null

    /**
     * 开始新的打字机会话
     * @param messageId 消息 ID
     * @param waitForSync 是否等待 TTS 同步信号（默认 true）
     */
    suspend fun start(messageId: String, waitForSync: Boolean = true) {
        mutex.withLock {
            stop()
            currentMessageId = messageId
            pendingText.clear()
            displayedText.clear()
            batchChars = 0
            isComplete = false
            canStartDisplay = !waitForSync
            receivedTtsReady = false
            currentIntervalMs = 80L
            lastUpdateTime = System.currentTimeMillis()
            lastAppendTime = System.currentTimeMillis()
        }

        typewriterJob = scope.launch {
            // 等待 TTS 同步信号（首个音频片段开始播放）
            // 有超时保护：如果 TTS 在 1.5 秒内未就绪，直接开始显示文字
            if (waitForSync) {
                Log.d("TypewriterController", "等待 TTS 同步信号...")
                val startTime = System.currentTimeMillis()
                while (!canStartDisplay && isActive) {
                    val elapsed = System.currentTimeMillis() - startTime
                    if (elapsed >= ttsSyncTimeoutMs) {
                        Log.d("TypewriterController", "TTS 同步等待超时，直接开始显示文字")
                        break
                    }
                    delay(16)
                }
                if (canStartDisplay) {
                    Log.d("TypewriterController", "TTS 已就绪，开始显示文字")
                }
            }

            while (isActive) {
                var emitId: String? = null
                var emitText: String? = null
                var shouldFinish = false
                val delayMs = mutex.withLock {
                    if (pendingText.isNotEmpty()) {
                        val now = System.currentTimeMillis()
                        val totalBufferedLength = displayedText.length + pendingText.length
                        val timeSinceAppend = now - lastAppendTime
                        currentIntervalMs = when {
                            totalBufferedLength > 1600 -> minIntervalMs
                            isComplete -> minIntervalMs
                            timeSinceAppend < 120 -> maxIntervalMs
                            timeSinceAppend < 400 -> 80L
                            else -> minIntervalMs
                        }

                        val charsToTake = minOf(
                            when {
                                totalBufferedLength > 1600 -> longTextCharsPerTick
                                isComplete -> completeCharsPerTick
                                else -> normalCharsPerTick
                            },
                            pendingText.length
                        )
                        val nextChunk = pendingText.substring(0, charsToTake)
                        pendingText.delete(0, charsToTake)
                        displayedText.append(nextChunk)
                        batchChars += nextChunk.length

                        val timeSinceLastUpdate = now - lastUpdateTime
                        val minBatchChars = when {
                            displayedText.length > 1600 -> 120
                            displayedText.length > 900 -> 64
                            else -> 24
                        }
                        val minInterval = when {
                            displayedText.length > 900 -> longTextUpdateIntervalMs
                            else -> minUpdateIntervalMs
                        }
                        if (batchChars >= minBatchChars || timeSinceLastUpdate >= minInterval) {
                            emitId = currentMessageId
                            emitText = displayedText.toString()
                            batchChars = 0
                            lastUpdateTime = now
                        }
                    } else if (isComplete) {
                        if (batchChars > 0) {
                            emitId = currentMessageId
                            emitText = displayedText.toString()
                            batchChars = 0
                        }
                        shouldFinish = true
                    }
                    currentIntervalMs
                }

                if (emitId != null && emitText != null) {
                    onTextUpdate?.invoke(emitId!!, emitText!!)
                }
                if (shouldFinish) {
                    return@launch
                }

                delay(delayMs)
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
    suspend fun append(text: String) = mutex.withLock {
        pendingText.append(text)
        lastAppendTime = System.currentTimeMillis()
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
    suspend fun flush() {
        var emitId: String? = null
        var emitText: String? = null
        mutex.withLock {
            currentMessageId?.let { id ->
                val allText = displayedText.toString() + pendingText.toString()
                emitId = id
                emitText = allText
                displayedText.clear()
                displayedText.append(allText)
                pendingText.clear()
                batchChars = 0
            }
        }
        if (emitId != null && emitText != null) {
            onTextUpdate?.invoke(emitId!!, emitText!!)
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
    private val sessionRepository: SessionRepository,
    private val authRepository: AuthRepository,
    private val settingsDataStore: SettingsDataStore,
    private val scenicDataSource: ScenicDataSource
) : ViewModel() {

    val scenicAreas = scenicDataSource.loadScenicAreas()

    // 认证状态
    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private val _authUsername = MutableStateFlow<String?>(null)
    val authUsername: StateFlow<String?> = _authUsername.asStateFlow()

    private val _showAuthDialog = MutableStateFlow(false)
    val showAuthDialog: StateFlow<Boolean> = _showAuthDialog.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _isAuthLoading = MutableStateFlow(false)
    val isAuthLoading: StateFlow<Boolean> = _isAuthLoading.asStateFlow()

    // 会话列表需要刷新的事件计数器（供 MainScreen 监听以触发侧边栏刷新）
    private val _sessionListNeedsRefresh = MutableStateFlow(0)
    val sessionListNeedsRefresh: StateFlow<Int> = _sessionListNeedsRefresh.asStateFlow()

    // 匹配后端误漏的 XML/结构标签（含中文尖括号变体），防止显示给用户
    // 同时匹配行尾不完整标签，避免打字机效果中途闪现半截标签
    private val displayTagRegex = Regex("""[<〈][^>]*>|[<〈][^>]*$""", RegexOption.MULTILINE)

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

    /**
     * 设置渲染器状态重置回调
     * 当 AvatarView 中的 Live2DRenderer 就绪时调用，用于解决 StateFlow 合并跳过 IDLE 问题
     */
    fun setResetSpeakingStateCallback(callback: () -> Unit) {
        playbackManager.onResetSpeakingState = callback
    }

    // 口型状态（直接暴露，绕过 Compose 状态层，避免高频更新触发重组）
    val mouthState: StateFlow<Pair<Float, Float>> = playbackManager.mouthState

    // 会话ID
    private var sessionId: String? = null

    // 标记：当前是否是从应用启动直接进入的欢迎界面（未通过侧边栏切换会话）
    // 用于判断是否需要在发送消息时创建新会话
    private var isFreshStart: Boolean = true

    // 当前助手消息 ID，用于中止请求
    private var currentAssistantMessageId: String? = null
    private var currentBackendMessageId: String? = null

    private var currentStreamJob: Job? = null

    // 打字机效果控制器
    private val typewriterController = TypewriterController(viewModelScope).apply {
        onTextUpdate = { messageId, text ->
            updateAssistantMessageContent(messageId, text)
        }
    }

    init {
        ensureUserId()

        // 持续监听认证状态变化，确保多 ViewModel 间状态同步
        viewModelScope.launch {
            settingsDataStore.isAuthenticated.collect { _isAuthenticated.value = it }
        }
        viewModelScope.launch {
            settingsDataStore.authUsername.collect { _authUsername.value = it }
        }

        viewModelScope.launch {
            val scenicId = settingsDataStore.scenicId.first()
            val spotId = settingsDataStore.spotId.first()
            if (scenicId == null || spotId == null) {
                _showScenicSelection.value = true
            } else {
                initSession()
            }

            // 未认证用户延迟弹出登录提示，已认证用户不弹
            val authenticated = settingsDataStore.isAuthenticated.first()
            if (!authenticated) {
                delay(800)
                if (!_showScenicSelection.value) {
                    _showAuthDialog.value = true
                }
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

    fun showScenicSelectionDialog() {
        _showScenicSelection.value = true
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
            playbackManager.avatarState.collect { state ->
                if (state.gesture != lastGesture || state.expression != lastExpression) {
                    Log.d(TAG, "observeAvatarState: gesture=${state.gesture}, expression=${state.expression}")
                    lastGesture = state.gesture
                    lastExpression = state.expression
                }
                // 当状态变为 IDLE 且当前有活跃对话时，重置状态
                // 不依赖 lastState 追踪，避免状态变化过快导致 Flow 合并后丢失中间状态
                val streamStillRunning = currentStreamJob?.isActive == true || _isLoading.value
                if (state.state == AvatarState.IDLE && _isConversationActive.value && !streamStillRunning) {
                    Log.d(TAG, "observeAvatarState: 播放完成，重置 isConversationActive")
                    _isConversationActive.value = false
                    currentAssistantMessageId = null
                } else if (state.state == AvatarState.IDLE && _isConversationActive.value) {
                    Log.d(TAG, "observeAvatarState: 忽略流式回复中的临时 IDLE")
                }
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
            val storedSessionId = settingsDataStore.sessionId.first()

            val validSessionId = if (!storedSessionId.isNullOrBlank()) {
                validateSession(storedSessionId)
            } else null

            sessionId = validSessionId
            if (validSessionId != null) {
                settingsDataStore.setSessionId(validSessionId)
            } else {
                settingsDataStore.clearSession()
            }

            if (_messages.value.isEmpty()) {
                addMessage("您好！我是景灵智导，很高兴为您服务。请问有什么可以帮助您？", isUser = false)
            }
        }
    }

    private fun ensureUserId() {
        viewModelScope.launch {
            val userId = settingsDataStore.userId.first()
            if (userId == null) {
                val newUserId = "guest_${java.util.UUID.randomUUID().toString().replace("-", "").take(16)}"
                settingsDataStore.setUserId(newUserId)
            }
        }
    }

    // ==================== 认证 ====================

    fun showAuthDialog() {
        _showAuthDialog.value = true
    }

    fun dismissAuthDialog() {
        _showAuthDialog.value = false
        _authError.value = null
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            authRepository.login(username, password).fold(
                onSuccess = { userData ->
                    _isAuthenticated.value = true
                    _authUsername.value = userData.username
                    _showAuthDialog.value = false
                    _authError.value = null
                    // 认证后重建会话，使用新的 user_id
                    settingsDataStore.clearSession()
                    startNewSession()
                },
                onFailure = { error ->
                    _authError.value = error.message ?: "登录失败，请重试"
                }
            )
            _isAuthLoading.value = false
        }
    }

    fun register(username: String, password: String) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            authRepository.register(username, password).fold(
                onSuccess = { userData ->
                    _isAuthenticated.value = true
                    _authUsername.value = userData.username
                    _showAuthDialog.value = false
                    _authError.value = null
                    // 认证后重建会话
                    settingsDataStore.clearSession()
                    startNewSession()
                },
                onFailure = { error ->
                    _authError.value = error.message ?: "注册失败，请重试"
                }
            )
            _isAuthLoading.value = false
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _isAuthenticated.value = false
            _authUsername.value = null
            _authError.value = null
            // 重建匿名会话
            settingsDataStore.clearSession()
            startNewSession()
        }
    }

    /**
     * 验证本地存储的 sessionId 在后端是否真实有效。
     * 若 session 存在但消息为空（用户未实际发送过消息），同样视为无效并丢弃，
     * 避免侧边栏积累"新对话"之类的空历史记录。
     */
    private suspend fun validateSession(storedSessionId: String): String? {
        return sessionRepository.getSessionDetail(storedSessionId).fold(
            onSuccess = { detail ->
                if (detail.messages.isNotEmpty()) {
                    Log.d(TAG, "validateSession: $storedSessionId is valid with ${detail.messages.size} messages")
                    storedSessionId
                } else {
                    Log.d(TAG, "validateSession: $storedSessionId is empty, discarding")
                    settingsDataStore.clearSession()
                    null
                }
            },
            onFailure = { error ->
                Log.w(TAG, "validateSession: $storedSessionId invalid, clearing. Cause: ${error.message}")
                settingsDataStore.clearSession()
                null
            }
        )
    }

    private suspend fun createNewSession(): Result<String> {
        val deviceId = settingsDataStore.deviceId.first() ?: UUID.randomUUID().toString().also {
            settingsDataStore.setDeviceId(it)
        }
        return repository.createSession(deviceId).fold(
            onSuccess = { response ->
                sessionId = response.sessionId
                settingsDataStore.setSessionId(response.sessionId)
                _sessionListNeedsRefresh.value++
                Result.success(response.sessionId)
            },
            onFailure = { error ->
                Log.e(TAG, "createNewSession failed", error)
                Result.failure(error)
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

            // 判断是否需要在欢迎界面创建新会话：
            // 1. sessionId 为空 → 创建新会话
            // 2. isFreshStart=true → 用户在当前界面尚未发送过消息，创建新会话
            //    这处理了用户刚进入应用直接发送消息的场景
            val shouldCreateNewSession = sessionId.isNullOrBlank() || isFreshStart

            if (shouldCreateNewSession) {
                Log.d(TAG, "sendMessageToBackend: 创建新会话 (sessionId=$sessionId, isFreshStart=$isFreshStart)")
                val newSessionId = createNewSession().getOrNull()
                if (newSessionId == null) {
                    _isLoading.value = false
                    _isConversationActive.value = false
                    _avatarState.value = AvatarState.IDLE
                    playbackManager.stop()
                    addMessage("会话创建失败，请检查网络后重试", isUser = false, isError = true)
                    return@launch
                }
                sessionId = newSessionId
                // 成功创建会话后，标记不再是 fresh start
                isFreshStart = false
            }

            var imageUrl: String? = null
            if (pendingImageUri != null) {
                val uri = android.net.Uri.parse(pendingImageUri)
                repository.uploadImage(uri, application).fold(
                    onSuccess = { imageUrl = it },
                    onFailure = {
                        _isLoading.value = false
                        _isConversationActive.value = false
                        _avatarState.value = AvatarState.IDLE
                        playbackManager.stop()
                        addMessage("图片上传失败，请重试", isUser = false)
                        return@launch
                    }
                )
            }

            val assistantMessageId = addMessage(content = "", isUser = false, isLoading = true)
            currentAssistantMessageId = assistantMessageId
            currentBackendMessageId = null

            // 启动打字机效果，等待第一个音频片段开始播放后再同步显示
            // 这样文字显示和数字人说话会同步开始
            typewriterController.start(assistantMessageId, waitForSync = true)

            val mode = when (_currentMode.value) {
                InteractionMode.Chat -> "chat"
                InteractionMode.Route -> "route"
            }

            var receivedAnyEvent = false
            var receivedText = false
            var receivedDone = false
            var latestAvatarAction: AvatarAction? = null
            var latestMetadata: ResponseMetadata? = null
            // 记录 segment 预告和失败状态。真实播放只消费 tts_segment_ready。
            val segmentIndexById = mutableMapOf<String, Int>()
            val failedTtsSegmentIds = mutableSetOf<String>()
            val failedTtsSegmentIndexes = mutableSetOf<Int>()

            try {
                var continuationAttempts = 0
                var requestMessage = text
                var requestImageUrl = imageUrl

                while (isActive && _isLoading.value) {
                    var prematureStreamEnd = false
                    repository.sendTextMessageStream(
                        sessionId = sessionId ?: "",
                        message = requestMessage,
                        mode = mode,
                        imageUrl = requestImageUrl
                    ).collect { event ->
                    val hadAnyEvent = receivedAnyEvent
                    receivedAnyEvent = true
                    Log.d(TAG, "收到流式事件: ${event::class.simpleName}")
                    when (event) {
                        is ChatStreamEvent.MessageStart -> {
                            Log.d(TAG, "MessageStart: messageId=${event.messageId}, sessionId=${event.sessionId}")
                            event.sessionId?.let { sessionId = it }
                            event.messageId?.let { currentBackendMessageId = it }
                        }
                        is ChatStreamEvent.TextDelta -> {
                            Log.v(TAG, "TextDelta: ${event.delta.take(20)}...")
                            receivedText = true
                            // 使用打字机效果，添加到缓冲区
                            typewriterController.append(event.delta)
                        }
                        is ChatStreamEvent.TtsSegment -> {
                            val seg = event.segment
                            seg.segmentIndex?.let { segmentIndexById[seg.segmentId] = it }
                            Log.d(
                                TAG,
                                "[LATENCY] TtsSegment 预通知: segmentId=${seg.segmentId}, " +
                                    "idx=${seg.segmentIndex}, audioUrl=${seg.audioUrl}"
                            )
                        }
                        is ChatStreamEvent.TtsSegmentReady -> {
                            val seg = event.segment
                            val readyIndex = seg.segmentIndex ?: segmentIndexById[seg.segmentId]
                            val failedById = seg.segmentId in failedTtsSegmentIds
                            val failedByIndex = readyIndex?.let { it in failedTtsSegmentIndexes } == true
                            if (failedById || failedByIndex) {
                                Log.w(TAG, "[LATENCY] TtsSegmentReady 跳过失败片段: segmentId=${seg.segmentId}, idx=$readyIndex")
                            } else {
                                Log.d(TAG, "[LATENCY] TtsSegmentReady 播放: segmentId=${seg.segmentId}, audioUrl=${seg.audioUrl}, durationMs=${seg.durationMs}")
                                playbackManager.enqueueSpeechSegment(seg)
                            }
                        }
                        is ChatStreamEvent.TtsAudioError -> {
                            val error = event.error
                            val segmentId = error.segmentId
                            val segmentIndex = error.segmentIndex ?: segmentId?.let { segmentIndexById[it] }
                            segmentId?.let { failedTtsSegmentIds.add(it) }
                            segmentIndex?.let { failedTtsSegmentIndexes.add(it) }
                            Log.w(
                                TAG,
                                "TtsAudioError: segmentId=$segmentId, idx=$segmentIndex, " +
                                    "code=${error.code}, message=${error.message ?: error.reason ?: error.error}"
                            )
                            typewriterController.notifyTtsReady()
                            playbackManager.skipSpeechSegment(segmentId, segmentIndex)
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
                            receivedDone = true
                            _isLoading.value = false
                            // 保底：如果没有收到 TTS 片段，也让打字机开始
                            typewriterController.notifyTtsReady()
                            // 标记消息完成，以最大速度显示剩余文本
                            typewriterController.finish()
                            updateAssistantMessage(
                                id = assistantMessageId,
                                isLoading = false,
                                backendMessageId = currentBackendMessageId
                            )
                            playbackManager.finishStreamingInput()
                            // 对话完成后通知侧边栏刷新，使 firstUserMessage 及时更新
                            _sessionListNeedsRefresh.value++
                        }
                        ChatStreamEvent.PrematurelyEnded -> {
                            Log.w(TAG, "流式响应提前结束，准备自动续写")
                            prematureStreamEnd = true
                            typewriterController.notifyTtsReady()
                            typewriterController.flush()
                        }
                        is ChatStreamEvent.Aborted -> {
                            Log.d(TAG, "Aborted: messageId=${event.messageId}, sessionId=${event.sessionId}, reason=${event.reason}")
                            typewriterController.notifyTtsReady()
                            typewriterController.flush()
                            _isLoading.value = false
                            _isConversationActive.value = false
                            playbackManager.stop()
                            updateAssistantMessage(
                                id = assistantMessageId,
                                content = currentMessageContent(assistantMessageId).ifBlank { "消息已中断。" },
                                isLoading = false,
                                isError = false
                            )
                        }
                        is ChatStreamEvent.Error -> {
                            Log.e(TAG, "Error: code=${event.code}, message=${event.message}")
                            typewriterController.notifyTtsReady()
                            _isLoading.value = false
                            _isConversationActive.value = false
                            typewriterController.flush()
                            playbackManager.stop()
                            val errorDetail = event.message ?: "未知错误"
                            updateAssistantMessage(
                                id = assistantMessageId,
                                content = currentMessageContent(assistantMessageId)
                                    .ifBlank { "[服务错误] $errorDetail" },
                                isLoading = false,
                                isError = true
                            )
                        }
                    }
                }
                    if (receivedDone || !_isLoading.value) {
                        break
                    }
                    if (prematureStreamEnd && receivedText && continuationAttempts < MAX_STREAM_CONTINUATION_ATTEMPTS) {
                        continuationAttempts += 1
                        requestMessage = buildContinuationPrompt(currentMessageContent(assistantMessageId))
                        requestImageUrl = null
                        Log.w(TAG, "第 $continuationAttempts 次自动续写流式回复")
                        continue
                    }
                    break
                }
                if (_isLoading.value) {
                    Log.w(
                        TAG,
                        "流式响应未收到 Done 就结束: receivedAnyEvent=$receivedAnyEvent, " +
                            "receivedText=$receivedText, receivedDone=$receivedDone, " +
                            "continuationAttempts=$continuationAttempts"
                    )
                    _isLoading.value = false
                    _isConversationActive.value = false
                    // 保底：如果没有收到 TTS 片段，也让打字机开始
                    typewriterController.notifyTtsReady()
                    typewriterController.flush()
                    playbackManager.stop()
                    updateAssistantMessage(
                        id = assistantMessageId,
                        content = currentMessageContent(assistantMessageId)
                            .ifBlank { "回复连接中断，请检查网络或服务器后重试。" },
                        isLoading = false,
                        isError = true
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "流式请求异常: ${e::class.simpleName}: ${e.message}", e)
                _isLoading.value = false
                _isConversationActive.value = false
                typewriterController.notifyTtsReady()
                typewriterController.flush()
                playbackManager.stop()
                val errorDetail = "[${e::class.simpleName}] ${e.message ?: "连接异常"}"
                updateAssistantMessage(
                    id = assistantMessageId,
                    content = currentMessageContent(assistantMessageId)
                        .ifBlank { errorDetail },
                    isLoading = false,
                    isError = true
                )
            }
        }
    }

    private fun buildContinuationPrompt(currentAnswer: String): String {
        val tail = currentAnswer
            .takeLast(160)
            .replace("\n", " ")
            .trim()
        return if (tail.isBlank()) {
            "上一条回复在生成过程中断了。请直接继续完成上一条回复，不要重复已经说过的内容，不要重新开头。"
        } else {
            "上一条回复在以下内容后中断：\"$tail\"。请从中断处继续完成上一条回复，不要重复已经说过的内容，不要重新开头。"
        }
    }

    private fun cancelCurrentStream() {
        currentStreamJob?.cancel()
        currentStreamJob = null
        currentBackendMessageId = null
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
        // N4: 防止长会话 OOM，保留最新 MAX_MESSAGES 条
        if (currentList.size > MAX_MESSAGES) {
            currentList.subList(0, currentList.size - MAX_MESSAGES).clear()
        }
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
     * 使用同步块保护，防止 Compose 渲染时并发修改
     */
    private fun updateAssistantMessageContent(id: String, content: String) {
        val currentList = _messages.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index == -1) return

        // 过滤后端误漏的标签，防止显示给用户
        val cleanContent = content.replace(displayTagRegex, "")

        val current = currentList[index]
        // 只有内容真正变化时才更新，避免不必要的重组
        if (current.content == cleanContent) return

        // 不覆盖 isLoading，保持当前值（由 Done/Error/Aborted 事件控制）
        currentList[index] = current.copy(
            content = cleanContent
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
        routeData: RouteData? = null,
        backendMessageId: String? = null
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
            routeData = routeData ?: current.routeData,
            backendMessageId = backendMessageId ?: current.backendMessageId
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
        val localMessageId = currentAssistantMessageId
        val messageToAbort = currentBackendMessageId ?: localMessageId

        cancelCurrentStream()

        localMessageId?.let { msgId ->
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
        currentBackendMessageId = null

        viewModelScope.launch {
            val newSessionId = createNewSession().getOrNull()
            if (newSessionId == null) {
                addMessage("新会话创建失败，请检查网络后重试", isUser = false, isError = true)
            }
        }
    }

    /**
     * 切换测试面板显示
     */
    fun toggleTestPanel() {
        _showTestPanel.value = !_showTestPanel.value
    }

    // ==================== 会话切换 ====================

    /**
     * 切换到指定会话，从后端加载历史消息
     */
    fun switchToSession(sessionId: String) {
        cancelCurrentStream()
        this.sessionId = sessionId
        // 用户主动切换会话，标记不再是 fresh start
        isFreshStart = false

        viewModelScope.launch {
            settingsDataStore.setSessionId(sessionId)
            _isLoading.value = true
            _messages.value = emptyList()

            sessionRepository.getSessionDetail(sessionId).fold(
                onSuccess = { detail ->
                    _messages.value = detail.messages.map { it.toChatMessage() }
                },
                onFailure = { e ->
                    Log.e(TAG, "switchToSession failed", e)
                    addMessage("无法加载历史会话，请重试", isUser = false, isError = true)
                }
            )
            _isLoading.value = false
        }
    }

    /**
     * 创建全新会话（会立即向后端请求创建，适用于登出/登录后重建）
     */
    fun startNewSession() {
        cancelCurrentStream()
        _messages.value = emptyList()
        _isConversationActive.value = false
        currentAssistantMessageId = null
        currentBackendMessageId = null

        viewModelScope.launch {
            val newSessionId = createNewSession().getOrNull()
            if (newSessionId == null) {
                addMessage("会话创建失败，请检查网络后重试", isUser = false, isError = true)
                return@launch
            }
            addMessage("您好！我是景灵智导，很高兴为您服务。请问有什么可以帮助您？", isUser = false)
        }
    }

    /**
     * 开始新的空对话（不立即创建后端会话，仅在用户发送第一条消息后创建）
     * 用于用户手动点击"新建对话"
     */
    fun startEmptyChat() {
        cancelCurrentStream()
        _messages.value = emptyList()
        _isConversationActive.value = false
        currentAssistantMessageId = null
        currentBackendMessageId = null
        sessionId = null
        // 用户点击新建对话，标记为 fresh start，期望下次发送消息时创建新会话
        isFreshStart = true
        _sessionListNeedsRefresh.value++
        viewModelScope.launch {
            settingsDataStore.clearSession()
            addMessage("您好！我是景灵智导，很高兴为您服务。请问有什么可以帮助您？", isUser = false)
        }
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

    // ==================== 满意度反馈功能 ====================

    private val _showFeedbackDialog = MutableStateFlow(false)
    val showFeedbackDialog: StateFlow<Boolean> = _showFeedbackDialog.asStateFlow()

    private val _feedbackMessageId = MutableStateFlow<String?>(null)
    val feedbackMessageId: StateFlow<String?> = _feedbackMessageId.asStateFlow()

    private val _isSubmittingFeedback = MutableStateFlow(false)
    val isSubmittingFeedback: StateFlow<Boolean> = _isSubmittingFeedback.asStateFlow()

    private val _feedbackResult = MutableStateFlow<FeedbackResult?>(null)
    val feedbackResult: StateFlow<FeedbackResult?> = _feedbackResult.asStateFlow()

    data class FeedbackResult(
        val success: Boolean,
        val message: String
    )

    fun showFeedbackDialog(messageId: String) {
        _feedbackMessageId.value = messageId
        _showFeedbackDialog.value = true
    }

    fun dismissFeedbackDialog() {
        _showFeedbackDialog.value = false
        _feedbackMessageId.value = null
    }

    fun submitFeedback(rating: Int, isComplaint: Boolean, comment: String?) {
        val messageId = _feedbackMessageId.value ?: return
        val message = _messages.value.find { it.id == messageId } ?: return

        viewModelScope.launch {
            _isSubmittingFeedback.value = true
            
            val result = repository.submitFeedback(
                rating = rating,
                messageId = message.backendMessageId,
                isComplaint = isComplaint,
                comment = comment
            )

            _isSubmittingFeedback.value = false

            result.fold(
                onSuccess = {
                    updateMessageFeedbackStatus(messageId, true)
                    _feedbackResult.value = FeedbackResult(true, "感谢您的反馈！")
                    dismissFeedbackDialog()
                    Log.d(TAG, "Feedback submitted successfully: ${it.feedbackId}")
                },
                onFailure = { error ->
                    _feedbackResult.value = FeedbackResult(false, "提交失败：${error.message}")
                    Log.e(TAG, "Feedback submission failed", error)
                }
            )
        }
    }

    fun clearFeedbackResult() {
        _feedbackResult.value = null
    }

    private fun updateMessageFeedbackStatus(messageId: String, hasFeedback: Boolean) {
        val currentList = _messages.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == messageId }
        if (index != -1) {
            val current = currentList[index]
            currentList[index] = current.copy(hasFeedback = hasFeedback)
            _messages.value = currentList
        }
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
        runCatching {
            currentStreamJob?.cancel()
            playbackManager.release()
        }.onFailure {
            Log.w(TAG, "onCleared release failed: ${it.message}")
        }
    }
}
