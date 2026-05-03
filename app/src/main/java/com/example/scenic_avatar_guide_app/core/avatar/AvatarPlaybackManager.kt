package com.example.scenic_avatar_guide_app.core.avatar

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.example.scenic_avatar_guide_app.core.audio.AudioPlayer
import com.example.scenic_avatar_guide_app.core.tts.PhonemeEvent
import com.example.scenic_avatar_guide_app.core.tts.RemoteTTSController
import com.example.scenic_avatar_guide_app.core.tts.StreamingTtsQueue
import com.example.scenic_avatar_guide_app.core.tts.VoiceInfo
import com.example.scenic_avatar_guide_app.core.tts.VoiceStyle
import com.example.scenic_avatar_guide_app.data.repository.GuideRepository
import com.example.scenic_avatar_guide_app.core.avatar.animation.Easing
import com.example.scenic_avatar_guide_app.core.avatar.animation.GestureTransitionController
import com.example.scenic_avatar_guide_app.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "AvatarPlaybackManager"

// 辅助数据类，用于批量返回音频状态
private data class AudioSyncState(
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    val position: Long,
    val duration: Long,
    val eventsEnd: Long
)

/**
 * 数字人播放管理器
 * 协调 TTS、口型同步、表情、动作
 */
class AvatarPlaybackManager(
    private val context: Context,
    private val repository: GuideRepository
) {

    // Avatar 状态
    private val _avatarState = MutableStateFlow(AvatarFullState())
    val avatarState: StateFlow<AvatarFullState> = _avatarState.asStateFlow()

    // 口型状态（独立 StateFlow，避免 60fps 高频更新触发 Compose 重组导致 HWUI 崩溃）
    // 由 AvatarView 直接收集并调用 renderer.setMouth()，完全绕过 Compose State 层
    private val _mouthState = MutableStateFlow(Pair(0f, 0f))
    val mouthState: StateFlow<Pair<Float, Float>> = _mouthState.asStateFlow()

    // 当前发音人
    private val _currentVoice = MutableStateFlow(RemoteTTSController.DEFAULT_VOICE)
    val currentVoice: StateFlow<VoiceInfo> = _currentVoice.asStateFlow()

    // TTS 控制器（远程 Edge-TTS）
    private val ttsController = RemoteTTSController(context, repository)

    // 口型动画驱动器
    private val lipSyncAnimator = LipSyncAnimator()

    // 是否收到过 TTS 片段
    private var receivedTtsSegment = false

    // 是否已通知过第一个片段开始（用于与打字机同步）
    private var notifiedFirstSegment = false

    // 第一个片段开始播放的回调（用于与打字机同步启动）
    var onFirstSegmentStart: (() -> Unit)? = null

    // 流式 TTS 分段播放队列
    private val streamingAudioPlayer = AudioPlayer(context)

    // 口型同步状态保护：防止 segment 切换时的竞态条件
    private val lipSyncLock = Any()
    private val isLipSyncActive = AtomicBoolean(false)
    private val currentLipSyncSegmentId = AtomicReference<String?>(null)

    private val streamingTtsQueue = StreamingTtsQueue(
        audioPlayer = streamingAudioPlayer,
        buildAudioUrl = { repository.buildAudioUrl(it) },
        onSegmentEnqueue = { segment ->
            // segment 入队时预生成口型事件，不等音频开始播放
            // 这样音频一开始播放就能立即同步口型
            Log.d(TAG, "[PRELOAD] segment=${segment.segmentId} 入队，预生成口型事件")
            preloadSegmentLipSync(segment)
        },
        onSegmentStart = { segment ->
            // 使用同步锁保护 segment 切换
            synchronized(lipSyncLock) {
                receivedTtsSegment = true
                isPlaying = true
                cancelWaitingClose()
                if (!notifiedFirstSegment) {
                    notifiedFirstSegment = true
                    onFirstSegmentStart?.invoke()
                }
                _avatarState.update {
                    it.copy(
                        state = AvatarState.SPEAKING,
                        currentText = segment.text
                    )
                }
                currentSegmentId = segment.segmentId
                currentStreamOffsetMs = segment.streamAudioOffsetMs?.toLong() ?: 0L
                currentSegmentText = segment.text ?: ""
            }
            // 在同步锁外启动口型同步，避免死锁
            startAudioSyncedLipSync()
        },
        onSegmentComplete = { segment, actualDurationMs ->
            Log.d(TAG, "segment ${segment.segmentId} 完成，实际时长=${actualDurationMs}ms")
        },
        onWaitingForSegment = {
            // 队列为空但流未结束：保持当前口型状态，仅暂停口型同步循环
            // 不切换到 THINKING 状态，避免 segment 间隙出现明显的口型跳变
            synchronized(lipSyncLock) {
                streamingLipSyncJob?.cancel()
                streamingLipSyncJob = null
                audioPositionSyncJob?.cancel()
                audioPositionSyncJob = null
                currentSegmentEvents.clear()
                currentSegmentId = null
            }
            // 不强制闭嘴，保持当前口型状态，让下一个 segment 开始时平滑过渡
            _avatarState.update {
                it.copy(
                    // 保持 SPEAKING 状态，避免数字人突然停顿
                    currentText = ""
                )
            }
            Log.d(TAG, "[STREAM] 播放队列暂时为空，等待新片段（保持 SPEAKING 状态）")
        },
        onAllComplete = {
            cancelWaitingClose()
            synchronized(lipSyncLock) {
                audioPositionSyncJob?.cancel()
                audioPositionSyncJob = null
                streamingLipSyncJob?.cancel()
                streamingLipSyncJob = null
                isPlaying = false
                currentSegmentEvents.clear()
                currentSegmentId = null
            }
            _mouthState.value = Pair(0f, 0f)
            _avatarState.update {
                it.copy(
                    state = AvatarState.IDLE,
                    gesture = AvatarGesture.IDLE,
                    mouthOpen = 0f,
                    mouthForm = 0f,
                    speakProgress = 0f,
                    currentText = ""
                )
            }
        },
        onError = {
            cancelWaitingClose()
            synchronized(lipSyncLock) {
                audioPositionSyncJob?.cancel()
                audioPositionSyncJob = null
                streamingLipSyncJob?.cancel()
                streamingLipSyncJob = null
                isPlaying = false
                currentSegmentEvents.clear()
                currentSegmentId = null
            }
            _mouthState.value = Pair(0f, 0f)
            _avatarState.update {
                it.copy(
                    state = AvatarState.ERROR,
                    gesture = AvatarGesture.IDLE,
                    mouthOpen = 0f,
                    mouthForm = 0f
                )
            }
        },
        onBufferingStateChanged = { isBuffering ->
            // 缓冲状态由口型同步循环内部处理，这里只记录日志
            if (isBuffering) {
                Log.d(TAG, "[BUFFER] 音频缓冲中")
            } else {
                Log.d(TAG, "[BUFFER] 音频缓冲完成")
            }
        }
    )

    // 作用域
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 当前口型
    private var currentViseme: VisemeType = VisemeType.NEUTRAL

    // 是否正在播放
    private var isPlaying = false

    // 当前播放动作（用于获取 loop 等配置）
    private var currentPlayAction: AvatarPlayAction? = null

    private var motionQueueJob: Job? = null
    private var expressionTimelineJob: Job? = null
    private var waitingCloseJob: Job? = null

    // 流式口型实时更新协程
    private var streamingLipSyncJob: Job? = null

    // 音频播放进度同步：记录当前音频播放位置
    private var audioPositionSyncJob: Job? = null

    // 当前 segment 的口型事件（相对于该 segment 从 0 开始）
    private var currentSegmentEvents = mutableListOf<PhonemeEvent>()

    // 当前 segment 的 ID（用于判断 segment 切换）
    private var currentSegmentId: String? = null

    // 当前 segment 的流式音频偏移（毫秒）
    // 后端说明：marks_time = player_position_ms - stream_audio_offset_ms
    private var currentStreamOffsetMs: Long = 0L

    // 当前 segment 的文本（用于兜底口型生成：预生成缓存为空时从文本重新生成）
    private var currentSegmentText: String = ""

    // 预生成的口型事件缓存（segmentId -> 口型事件列表）
    // 后端优化后 marks 可能为 null，需要在入队时预生成，播放时使用
    private val preloadedLipSyncEvents = mutableMapOf<String, MutableList<PhonemeEvent>>()

    // 乱序重排缓冲区（segmentIndex -> segment）
    // 后端并发合成时各段完成顺序不定，需要按 segmentIndex 排序后再入播放队列
    private val pendingSegments = sortedMapOf<Int, TtsSegmentData>()

    // 下一个期望入队的 segmentIndex（-1 表示不使用 index 排序）
    private var nextExpectedSegmentIndex = 0

    // 帧间平滑：记住上一帧的口型值
    private var lastMouthOpen = 0f
    private var lastMouthForm = 0f

    init {
        // 设置 TTS 回调
        setupTTSCallbacks()

        // 设置口型动画回调：直接更新 _mouthState，绕过 Compose 状态层
        lipSyncAnimator.setOnUpdateListener { open, form ->
            _mouthState.value = Pair(open, form)
        }
    }

    /**
     * 获取可用发音人列表
     */
    fun getAvailableVoices(): List<VoiceInfo> = RemoteTTSController.AVAILABLE_VOICES

    /**
     * 按风格获取推荐发音人
     */
    fun getVoicesByStyle(): Map<VoiceStyle, List<VoiceInfo>> =
        RemoteTTSController.AVAILABLE_VOICES.groupBy { it.style }

    /**
     * 设置发音人
     */
    fun setVoice(voiceId: String) {
        ttsController.setVoice(voiceId)
        _currentVoice.value = RemoteTTSController.AVAILABLE_VOICES.find { it.id == voiceId }
            ?: RemoteTTSController.DEFAULT_VOICE
    }

    /**
     * 设置语速 (0.5 - 2.0)
     */
    fun setSpeed(speed: Float) {
        ttsController.setSpeed(speed)
    }

    /**
     * 设置音调 (0.5 - 2.0)
     */
    fun setPitch(pitch: Float) {
        ttsController.setPitch(pitch)
    }

    /**
     * 设置 TTS 回调
     */
    private fun setupTTSCallbacks() {
        ttsController.onSpeakStart = {
            _avatarState.update { it.copy(state = AvatarState.SPEAKING) }
            isPlaying = true
        }

        ttsController.onSpeakComplete = {
            Log.d(TAG, "[TTS] 非流式播放完成")
            audioPositionSyncJob?.cancel()
            audioPositionSyncJob = null
            val shouldKeepGesture = currentPlayAction?.gestureLoop == true
            _mouthState.value = Pair(0f, 0f)
            _avatarState.update {
                it.copy(
                    state = AvatarState.IDLE,
                    gesture = if (shouldKeepGesture) it.gesture else AvatarGesture.IDLE,
                    gesturePriority = if (shouldKeepGesture) it.gesturePriority else GesturePriority.NORMAL,
                    mouthOpen = 0f,
                    mouthForm = 0f
                )
            }
            isPlaying = false
            currentPlayAction = null
            currentSegmentEvents.clear()
        }

        // 音素事件列表回调 - 使用与流式模式相同的口型同步逻辑
        ttsController.onPhonemeEvents = { events ->
            val eventCount = events.size
            val lastEvent = events.lastOrNull()
            Log.d(TAG, "[TTS] 非流式口型事件: count=$eventCount, 时间范围=0-${lastEvent?.endMs}ms")

            // 使用与流式模式相同的事件处理逻辑
            currentSegmentEvents = LipSyncAnimator.mergeEventsByChar(events).toMutableList()

            // 时间缩放：确保口型事件不超过音频时长（如果有）
            val audioDuration = ttsController.let { controller ->
                // 等音频开始后获取时长，这里先用事件时长
                lastEvent?.endMs ?: 0L
            }

            val eventEnd = currentSegmentEvents.lastOrNull()?.endMs ?: 0L
            Log.d(TAG, "[TTS] 口型事件=${currentSegmentEvents.size}个, 时间范围=0-${eventEnd}ms")

            // 启动与流式模式相同的口型同步逻辑
            startNonStreamingLipSync()
        }
    }

    /**
     * 非流式模式的口型同步（与流式模式使用相同逻辑）
     */
    private fun startNonStreamingLipSync() {
        audioPositionSyncJob?.cancel()

        lastMouthOpen = 0f
        lastMouthForm = 0f

        audioPositionSyncJob = scope.launch {
            var lastAudioPosition = -1L
            var samePositionCount = 0
            var frameCount = 0

            while (isActive && isPlaying) {
                frameCount++
                val frameStart = System.currentTimeMillis()

                val isAudioPlaying = ttsController.isAudioPlaying()
                val audioPos = ttsController.getCurrentPosition()
                val audioDur = ttsController.getEstimatedDuration()
                val eventsEnd = currentSegmentEvents.lastOrNull()?.endMs ?: 0L

                // 每 30 帧输出诊断日志
                if (frameCount % 30 == 0) {
                    Log.d(TAG, "[LIPSYNC-NONSTREAM] frame=$frameCount, audioPos=$audioPos, events=${currentSegmentEvents.size}, eventsEnd=$eventsEnd, isPlaying=$isAudioPlaying, mouthOpen=$lastMouthOpen, mouthForm=$lastMouthForm")
                }

                // 检测停滞
                if (audioPos == lastAudioPosition && audioPos > 0) {
                    samePositionCount++
                } else {
                    samePositionCount = 0
                }
                lastAudioPosition = audioPos

                // 保底：音频已停止
                if (!isAudioPlaying && audioPos > 0) {
                    Log.d(TAG, "[LIPSYNC-NONSTREAM] 保底触发：音频停止")
                    forceCloseMouth()
                    return@launch
                }

                // 保底：进度停滞
                if (samePositionCount >= 3) {
                    Log.d(TAG, "[LIPSYNC-NONSTREAM] 保底触发：进度停滞")
                    forceCloseMouth()
                    return@launch
                }

                // 保底：口型时间轴结束
                if (eventsEnd > 0 && audioPos > eventsEnd + 50) {
                    Log.d(TAG, "[LIPSYNC-NONSTREAM] 保底触发：时间轴结束")
                    forceCloseMouth()
                    return@launch
                }

                // 计算并应用口型（直接更新 _mouthState，绕过 Compose 状态层）
                val (open, form) = calculateLipSync(audioPos)
                _mouthState.value = Pair(open, form)

                // 帧率控制
                val elapsed = System.currentTimeMillis() - frameStart
                if (elapsed < 16) delay(16 - elapsed)
            }
            Log.d(TAG, "[LIPSYNC-NONSTREAM] 循环退出")
            forceCloseMouth()
        }
    }

    /**
     * 播放动作和语音
     */
    fun play(action: AvatarPlayAction) {
        val hasSpeech = !action.text.isNullOrBlank()
        Log.d(TAG, "play: gesture=${action.gesture}, expression=${action.expression}, hasSpeech=$hasSpeech, text=${action.text?.take(20)}, motions=${action.motionQueue.size}")

        if (isPlaying) {
            stop()
        }
        streamingTtsQueue.cancel()
        currentPlayAction = action

        // 计算动作过渡时间：速度越快，过渡时间越短
        val transitionMs = GestureTransitionController.calculateTransitionMs(action.gestureSpeed)

        // 关键修复：同时更新 _mouthState 和 _avatarState，确保渲染器能立即覆盖 Idle 动画的嘴型参数
        // 避免在 updateState() 被调用之前的几帧内，Idle 动画的 ParamMouthForm=1 导致 O 形嘴
        if (hasSpeech) {
            _mouthState.value = Pair(0f, 0f)  // 立即设置闭嘴状态，覆盖 Idle 动画的圆唇
        }
        _avatarState.update {
            it.copy(
                state = if (hasSpeech) AvatarState.SPEAKING else AvatarState.IDLE,
                expression = action.expression,
                expressionIntensity = action.expressionIntensity,
                gesture = action.gesture,
                gesturePriority = action.gesturePriority,
                gestureTransitionMs = transitionMs
            )
        }

        if (action.expressionTimeline.isNotEmpty()) {
            playExpressionTimeline(action.expressionTimeline)
        }

        if (action.motionQueue.isNotEmpty()) {
            playMotionQueue(action.motionQueue)
        }

        action.text?.let { text ->
            if (text.isNotBlank()) {
                ttsController.speak(text)
            }
        }
    }

    /**
     * 播放表情时间轴
     *
     * 每个表情项的 transitionMs 会被写入状态，供渲染层控制过渡动画时长。
     */
    private fun playExpressionTimeline(timeline: List<ExpressionTimelineItem>) {
        expressionTimelineJob?.cancel()
        if (timeline.isEmpty()) return

        expressionTimelineJob = scope.launch {
            val sortedTimeline = timeline.sortedBy { it.startOffsetMs }
            val startTime = SystemClock.elapsedRealtime()

            sortedTimeline.forEach { item ->
                val elapsed = SystemClock.elapsedRealtime() - startTime
                val waitMs = (item.startOffsetMs - elapsed).coerceAtLeast(0)
                if (waitMs > 0) delay(waitMs)

                _avatarState.update {
                    it.copy(
                        expression = item.expression,
                        expressionIntensity = item.intensity,
                        expressionTransitionMs = item.transitionMs
                    )
                }
            }
        }
    }

    /**
     * 启动流式播放会话。后续通过 enqueueSpeechSegment 持续喂入后端返回的 TTS 片段。
     */
    fun startStreaming(
        expression: AvatarExpression = AvatarExpression.THINKING,
        expressionIntensity: Float = 0.7f,
        gesture: AvatarGesture = AvatarGesture.THINKING_POSE
    ) {
        Log.d(TAG, "[STREAMING] 开始流式播放会话")
        stop()
        receivedTtsSegment = false
        notifiedFirstSegment = false
        currentSegmentEvents.clear()
        currentSegmentId = null
        currentStreamOffsetMs = 0L
        currentSegmentText = ""
        preloadedLipSyncEvents.clear()
        pendingSegments.clear()
        nextExpectedSegmentIndex = 0
        streamingTtsQueue.start()
        _mouthState.value = Pair(0f, 0f)
        _avatarState.update {
            it.copy(
                state = AvatarState.THINKING,
                expression = expression,
                expressionIntensity = expressionIntensity,
                gesture = gesture,
                mouthOpen = 0f,
                mouthForm = 0f,
                speakProgress = 0f,
                currentText = ""
            )
        }
    }

    fun updateStreamingAction(
        expression: AvatarExpression,
        expressionIntensity: Float = 0.7f,
        gesture: AvatarGesture = AvatarGesture.IDLE,
        gesturePriority: GesturePriority = GesturePriority.NORMAL,
        gestureLoop: Boolean = false,
        gestureSpeed: Float = 1.0f,
        motionQueue: List<MotionQueueItem> = emptyList()
    ) {
        // 优先级检查：低优先级动作不能打断高优先级动作
        if (gesturePriority.ordinal < _avatarState.value.gesturePriority.ordinal) {
            Log.d(TAG, "updateStreamingAction ignored: $gesturePriority < current ${_avatarState.value.gesturePriority}")
            return
        }

        _avatarState.update {
            it.copy(
                expression = expression,
                expressionIntensity = expressionIntensity,
                gesture = gesture,
                gesturePriority = gesturePriority
            )
        }
        if (motionQueue.isNotEmpty()) {
            playMotionQueue(motionQueue)
        }
    }

    fun enqueueSpeechSegment(segment: TtsSegmentData) {
        val idx = segment.segmentIndex
        if (idx == null) {
            // 旧后端不返回 segmentIndex，按到达顺序直接入队
            streamingTtsQueue.enqueue(segment)
            return
        }

        when {
            idx == nextExpectedSegmentIndex -> {
                streamingTtsQueue.enqueue(segment)
                nextExpectedSegmentIndex++
                flushPendingSegments()
            }
            idx > nextExpectedSegmentIndex -> {
                Log.d(TAG, "[REORDER] 缓存乱序 segment: idx=$idx, nextExpected=$nextExpectedSegmentIndex")
                pendingSegments[idx] = segment
            }
            else -> {
                Log.w(TAG, "[REORDER] 忽略重复 segment: idx=$idx, nextExpected=$nextExpectedSegmentIndex")
            }
        }
    }

    private fun flushPendingSegments() {
        while (pendingSegments.containsKey(nextExpectedSegmentIndex)) {
            val next = pendingSegments.remove(nextExpectedSegmentIndex)!!
            Log.d(TAG, "[REORDER] 释放缓存 segment: idx=$nextExpectedSegmentIndex, segmentId=${next.segmentId}")
            streamingTtsQueue.enqueue(next)
            nextExpectedSegmentIndex++
        }
    }

    fun finishStreamingInput() {
        streamingTtsQueue.finishInput()
    }

    fun playGesture(
        gesture: AvatarGesture,
        expression: AvatarExpression = AvatarExpression.NEUTRAL,
        priority: GesturePriority = GesturePriority.NORMAL,
        loop: Boolean = false,
        speed: Float = 1.0f
    ) {
        // 优先级检查
        if (priority.ordinal < _avatarState.value.gesturePriority.ordinal) {
            Log.d(TAG, "playGesture ignored: $priority < current ${_avatarState.value.gesturePriority}")
            return
        }

        // 计算过渡时间：速度越快，过渡时间越短
        val transitionMs = GestureTransitionController.calculateTransitionMs(speed)

        _avatarState.update {
            it.copy(
                gesture = gesture,
                expression = expression,
                gesturePriority = priority,
                gestureTransitionMs = transitionMs
            )
        }

        // 自动恢复（loop=false 时）
        if (!loop) {
            val autoResetDelay = (2000L / speed.coerceAtLeast(0.1f)).toLong()
            scope.launch {
                delay(autoResetDelay)
                _avatarState.update {
                    it.copy(
                        gesture = AvatarGesture.IDLE,
                        gesturePriority = GesturePriority.NORMAL,
                        gestureTransitionMs = GestureTransitionController.DEFAULT_TRANSITION_MS
                    )
                }
            }
        }
    }

    /**
     * 设置表情
     */
    fun setExpression(expression: AvatarExpression, intensity: Float = 0.7f) {
        _avatarState.update {
            it.copy(
                expression = expression,
                expressionIntensity = intensity
            )
        }
    }

    /**
     * 停止播放
     */
    fun stop() {
        runCatching {
            ttsController.stop()
            streamingTtsQueue.cancel()
            synchronized(lipSyncLock) {
                audioPositionSyncJob?.cancel()
                audioPositionSyncJob = null
                streamingLipSyncJob?.cancel()
                streamingLipSyncJob = null
                isPlaying = false
                currentSegmentEvents.clear()
                currentSegmentId = null
                currentStreamOffsetMs = 0L
                currentSegmentText = ""
                preloadedLipSyncEvents.clear()
                pendingSegments.clear()
                nextExpectedSegmentIndex = 0
            }
            // 重置原子状态
            isLipSyncActive.set(false)
            currentLipSyncSegmentId.set(null)
            motionQueueJob?.cancel()
            motionQueueJob = null
            expressionTimelineJob?.cancel()
            expressionTimelineJob = null
            cancelWaitingClose()
            currentPlayAction = null
            _mouthState.value = Pair(0f, 0f)
            _avatarState.update {
                AvatarFullState()
            }
        }.onFailure {
            Log.w(TAG, "stop failed: ${it.message}")
        }
    }

    private fun cancelWaitingClose() {
        waitingCloseJob?.cancel()
        waitingCloseJob = null
    }

    /**
     * 延迟关闭口型，避免短 gap 造成嘴部顿挫
     */
    private fun scheduleMouthClose(delayMs: Long = 300L) {
        cancelWaitingClose()
        waitingCloseJob = scope.launch {
            delay(delayMs)
            if (!isActive) return@launch
            lipSyncAnimator.stop()
            _mouthState.value = Pair(0f, 0f)
            _avatarState.update {
                it.copy(mouthOpen = 0f, mouthForm = 0f)
            }
        }
    }

    /**
     * 更新口型
     */
    private fun updateMouthFromViseme(viseme: VisemeType) {
        currentViseme = viseme
        _mouthState.value = Pair(viseme.mouthOpen, viseme.mouthForm)
        _avatarState.update {
            it.copy(
                mouthOpen = viseme.mouthOpen,
                mouthForm = viseme.mouthForm
            )
        }
    }

    /**
     * 预生成口型事件（segment 入队时调用）
     *
     * 后端优化后，audio_url 先行推送，duration_ms 和 marks 可能为 null。
     * 在 segment 入队时预生成口型事件，音频一开始播放就能立即同步口型。
     * 如果后续收到实际时长或 marks，会在播放时动态调整。
     */
    private fun preloadSegmentLipSync(segment: TtsSegmentData) {
        val marks = segment.marks
        val segmentDuration = segment.durationMs?.toLong() ?: 0L

        val events = if (!marks.isNullOrEmpty()) {
            val marksEnd = marks.lastOrNull()?.endMs ?: 0
            Log.d(TAG, "[PRELOAD] ${segment.segmentId}: 后端 marks=${marks.size}, " +
                    "marks范围=0-${marksEnd}ms, 音频时长=${segmentDuration}ms")
            ChinesePhonemeEngine.marksToPhonemeEvents(marks)
        } else {
            // 无 marks，使用文本估算
            val estimatedDuration = estimateTextDuration(segment.text)
            Log.d(TAG, "[PRELOAD] ${segment.segmentId}: 无 marks，本地估算=${estimatedDuration}ms, 音频时长=${segmentDuration}ms")
            ChinesePhonemeEngine.textToPhonemeEvents(
                text = segment.text,
                totalDurationMs = segmentDuration.takeIf { it > 0 } ?: estimatedDuration
            )
        }

        if (events.isEmpty()) {
            Log.w(TAG, "[PRELOAD] ${segment.segmentId}: 生成口型事件为空")
            return
        }

        // 按字合并，时间从 0 开始
        val mergedEvents = LipSyncAnimator.mergeEventsByChar(events).toMutableList()

        // 存储到预生成缓存
        preloadedLipSyncEvents[segment.segmentId] = mergedEvents

        val eventEnd = mergedEvents.lastOrNull()?.endMs ?: 0L
        Log.d(TAG, "[PRELOAD] ${segment.segmentId}: 预生成口型事件=${mergedEvents.size}个, " +
                "时间范围=0-${eventEnd}ms")
    }

    /**
     * 更新当前 segment 的口型事件（每个 segment 独立，从 0 开始）
     */
    private fun updateCurrentSegmentLipSync(segment: TtsSegmentData) {
        val marks = segment.marks
        val segmentDuration = segment.durationMs?.toLong() ?: 0L

        val events = if (!marks.isNullOrEmpty()) {
            val marksEnd = marks.lastOrNull()?.endMs ?: 0
            Log.d(TAG, "[SEGMENT] ${segment.segmentId}: 后端 marks=${marks.size}, " +
                    "marks范围=0-${marksEnd}ms, 音频时长=${segmentDuration}ms, " +
                    "差异=${segmentDuration - marksEnd}ms")
            ChinesePhonemeEngine.marksToPhonemeEvents(marks)
        } else {
            val estimatedDuration = estimateTextDuration(segment.text)
            Log.d(TAG, "[SEGMENT] ${segment.segmentId}: 无 marks，本地估算=${estimatedDuration}ms, 音频时长=${segmentDuration}ms")
            ChinesePhonemeEngine.textToPhonemeEvents(
                text = segment.text,
                totalDurationMs = segmentDuration.takeIf { it > 0 } ?: estimatedDuration
            )
        }

        if (events.isEmpty()) {
            Log.w(TAG, "[SEGMENT] ${segment.segmentId}: 生成口型事件为空")
            currentSegmentEvents.clear()
            return
        }

        // 按字合并，时间从 0 开始（不累计偏移）
        currentSegmentEvents = LipSyncAnimator.mergeEventsByChar(events).toMutableList()

        // 后端已保证 marks[-1].end_ms 与 duration_ms 对齐，端侧不再二次拉伸 marks
        val eventStart = currentSegmentEvents.firstOrNull()?.startMs ?: 0L
        val eventEnd = currentSegmentEvents.lastOrNull()?.endMs ?: 0L
        Log.d(TAG, "[SEGMENT] ${segment.segmentId}: 口型事件=${currentSegmentEvents.size}个, " +
                "时间范围=${eventStart}-${eventEnd}ms, 音频时长=${segmentDuration}ms")

        // 启动口型同步
        startAudioSyncedLipSync()
    }

    /**
     * 启动基于音频进度的口型同步协程。
     * 优先使用预生成的口型事件，如果没有则使用当前 segment 的事件。
     */
    private fun startAudioSyncedLipSync() {
        // 获取当前 segment ID，用于后续校验
        val segmentId = synchronized(lipSyncLock) { currentSegmentId }
        if (segmentId == null) {
            Log.w(TAG, "[LIPSYNC] startAudioSyncedLipSync: currentSegmentId is null, skipping")
            return
        }

        // 如果 job 已在运行，取消旧的并重置状态（segment 切换时）
        synchronized(lipSyncLock) {
            if (audioPositionSyncJob?.isActive == true) {
                Log.d(TAG, "[LIPSYNC] 取消旧 job，启动新 segment 口型同步: $segmentId")
                audioPositionSyncJob?.cancel()
            }
        }

        // 从预生成缓存加载口型事件
        synchronized(lipSyncLock) {
            currentSegmentId?.let { segId ->
                preloadedLipSyncEvents[segId]?.let { events ->
                    currentSegmentEvents = events
                    // 使用后清除，避免内存泄漏
                    preloadedLipSyncEvents.remove(segId)
                    Log.d(TAG, "[LIPSYNC] 使用预生成口型事件: segmentId=$segId, events=${events.size}")
                }
            }

            // 如果预生成缓存没有，尝试从 segment 文本重新生成（兜底）
            // 实际触发场景：marks 和 text 同时为空，或 preloadSegmentLipSync 因某种原因未写入缓存
            if (currentSegmentEvents.isEmpty() && currentSegmentText.isNotBlank()) {
                Log.w(TAG, "[LIPSYNC] 预生成缓存为空，使用当前文本兜底生成口型: text='${currentSegmentText.take(20)}'")
                val estDuration = estimateTextDuration(currentSegmentText)
                val fallbackEvents = ChinesePhonemeEngine.textToPhonemeEvents(currentSegmentText, estDuration)
                if (fallbackEvents.isNotEmpty()) {
                    currentSegmentEvents = LipSyncAnimator.mergeEventsByChar(fallbackEvents).toMutableList()
                    Log.d(TAG, "[LIPSYNC] 兜底生成口型事件: ${currentSegmentEvents.size}个")
                } else {
                    Log.w(TAG, "[LIPSYNC] 兜底生成也为空，将等待 marks 动态更新")
                }
            } else if (currentSegmentEvents.isEmpty()) {
                Log.w(TAG, "[LIPSYNC] 预生成缓存为空且无文本，将等待 marks 动态更新")
            }
        }

        lastMouthOpen = 0f
        lastMouthForm = 0f

        // 记录启动时的 segment ID，用于后续校验
        currentLipSyncSegmentId.set(segmentId)
        isLipSyncActive.set(true)

        audioPositionSyncJob = scope.launch {
            var lastAudioPosition = -1L
            var samePositionCount = 0
            var frameCount = 0
            var wasBuffering = false

            while (isActive && isPlaying) {
                // 校验是否仍在处理同一个 segment
                val currentSegId = currentLipSyncSegmentId.get()
                if (currentSegId != segmentId) {
                    Log.d(TAG, "[LIPSYNC] Segment 已切换 ($segmentId -> $currentSegId)，退出循环")
                    break
                }

                frameCount++
                val frameStart = System.currentTimeMillis()

                // 使用同步锁读取音频状态，避免竞态
                val (isAudioPlaying, isBuffering, audioPos, audioDur, eventsEnd) = synchronized(lipSyncLock) {
                    AudioSyncState(
                        isPlaying = streamingAudioPlayer.isActuallyPlaying(),
                        isBuffering = streamingAudioPlayer.isBuffering.value,
                        position = streamingAudioPlayer.getCurrentPosition(),
                        duration = streamingAudioPlayer.getDuration(),
                        eventsEnd = currentSegmentEvents.lastOrNull()?.endMs ?: 0L
                    )
                }

                // 每 30 帧输出诊断日志
                if (frameCount % 30 == 0) {
                    Log.d(TAG, "[LIPSYNC] seg=$segmentId, frame=$frameCount, audioPos=$audioPos, events=${currentSegmentEvents.size}, eventsEnd=$eventsEnd, isPlaying=$isAudioPlaying, isBuffering=$isBuffering, mouthOpen=$lastMouthOpen, mouthForm=$lastMouthForm")
                }

                // 缓冲时暂停口型同步，但不退出循环
                if (isBuffering) {
                    if (!wasBuffering) {
                        Log.d(TAG, "[LIPSYNC] 音频缓冲中，暂停口型同步")
                        wasBuffering = true
                    }
                    // 保持当前口型状态，不更新
                    delay(16)
                    continue
                }
                if (wasBuffering && !isBuffering) {
                    Log.d(TAG, "[LIPSYNC] 音频缓冲完成，恢复口型同步")
                    wasBuffering = false
                    samePositionCount = 0  // 重置停滞计数
                }

                // 检测停滞
                if (audioPos == lastAudioPosition && audioPos > 0) {
                    samePositionCount++
                } else {
                    samePositionCount = 0
                }
                lastAudioPosition = audioPos

                // 保底：音频停止（需连续确认，避免流式 segment 切换瞬间误判）
                // samePositionCount 阈值提高到 10（≈160ms），为 ExoPlayer segment 切换和缓冲留出更多时间
                if (samePositionCount >= 10) {
                    Log.d(TAG, "[LIPSYNC] 保底触发：进度停滞 ${samePositionCount} 帧")
                    forceCloseMouth()
                    break
                }
                // isActuallyPlaying=false 时额外确认：停滞超过 5 帧（≈80ms）才退出，
                // 防止 segment 交接瞬间和缓冲的瞬态 false 触发过早退出
                if (!isAudioPlaying && audioPos > 0 && samePositionCount >= 5) {
                    Log.d(TAG, "[LIPSYNC] 保底触发：音频停止且停滞 ${samePositionCount} 帧")
                    forceCloseMouth()
                    break
                }
                // 注意：故意移除"音频即将结束（audioDur-50）"保底——
                // 流式播放时 getDuration() 返回值不稳定，会导致提前退出截断最后音节

                // 保底：口型时间轴结束
                if (eventsEnd > 0 && audioPos > eventsEnd + 50) {
                    Log.d(TAG, "[LIPSYNC] 保底触发：时间轴结束")
                    forceCloseMouth()
                    break
                }

                // 计算并应用口型（直接更新 _mouthState，绕过 Compose 状态层）
                val (open, form) = calculateLipSync(audioPos)
                _mouthState.value = Pair(open, form)

                // 帧率控制
                val elapsed = System.currentTimeMillis() - frameStart
                if (elapsed < 16) delay(16 - elapsed)
            }
            Log.d(TAG, "[LIPSYNC] 循环退出: segmentId=$segmentId")
            isLipSyncActive.set(false)
            // 只在当前 segment 仍是我们处理的情况下才强制闭嘴
            if (currentLipSyncSegmentId.get() == segmentId) {
                forceCloseMouth()
            }
        }
    }

    /**
     * 根据音频位置计算口型
     * 包含字间过渡处理，遇到 SIL（标点/停顿）强制闭唇
     *
     * 流式播放偏移修正：
     * chunk 流是原始 MP3，最终 audio_url 是裁剪静音后的文件。
     * 后端说明：marks_time = player_position_ms - stream_audio_offset_ms
     */
    private fun calculateLipSync(audioPos: Long): Pair<Float, Float> {
        if (currentSegmentEvents.isEmpty()) return Pair(0f, 0f)

        val effectivePos = (audioPos - currentStreamOffsetMs).coerceAtLeast(0L)

        // 找当前事件（音频位置落在事件时间范围内）
        val currentEvent = currentSegmentEvents.find { effectivePos >= it.startMs && effectivePos < it.endMs }

        if (currentEvent != null) {
            // SIL 事件（标点/停顿）：强制闭唇
            if (currentEvent.viseme == VisemeType.SIL) {
                lastMouthOpen = lerpValue(lastMouthOpen, 0f, 0.5f)
                lastMouthForm = lerpValue(lastMouthForm, 0f, 0.5f)
                return Pair(lastMouthOpen, lastMouthForm)
            }

            // 在某个事件内：计算事件内的进度并应用缓动
            val eventDuration = (currentEvent.endMs - currentEvent.startMs).coerceAtLeast(1)
            val progress = ((effectivePos - currentEvent.startMs).toFloat() / eventDuration).coerceIn(0f, 1f)

            // 在事件内应用轻微的"中间高两端低"缓动，模拟自然说话
            val easedOpen = currentEvent.viseme.mouthOpen * when {
                progress < 0.3f -> 0.7f + progress  // 进入阶段：从70%渐增
                progress > 0.7f -> 0.7f + (1f - progress)  // 退出阶段：渐减到70%
                else -> 1f  // 中间阶段：100%
            }

            val form = currentEvent.viseme.mouthForm

            // 与上一帧平滑过渡
            lastMouthOpen = lerpValue(lastMouthOpen, easedOpen, 0.4f)
            lastMouthForm = lerpValue(lastMouthForm, form, 0.4f)
            return Pair(lastMouthOpen, lastMouthForm)
        }

        // 在两个事件之间：查找前后事件
        val prevEvent = currentSegmentEvents.lastOrNull { it.endMs <= effectivePos }
        val nextEvent = currentSegmentEvents.firstOrNull { it.startMs > effectivePos }

        if (prevEvent != null && nextEvent != null) {
            // 如果前后有 SIL 事件，强制闭唇
            if (prevEvent.viseme == VisemeType.SIL || nextEvent.viseme == VisemeType.SIL) {
                lastMouthOpen = lerpValue(lastMouthOpen, 0f, 0.4f)
                lastMouthForm = lerpValue(lastMouthForm, 0f, 0.4f)
                return Pair(lastMouthOpen, lastMouthForm)
            }

            // 字间过渡：插值 + 保持系数
            val gap = (nextEvent.startMs - prevEvent.endMs).coerceAtLeast(1)
            val t = ((effectivePos - prevEvent.endMs).toFloat() / gap).coerceIn(0f, 1f)

            val prevOpen = prevEvent.viseme.mouthOpen
            val nextOpen = nextEvent.viseme.mouthOpen
            val avgOpen = (prevOpen + nextOpen) / 2f

            // 字间保持系数：让连续开口音之间有闭合过渡
            val holdFactor = when {
                gap >= 150 -> 0.4f   // 长停顿，闭合更多
                gap >= 80 -> 0.5f    // 中等停顿
                avgOpen >= 0.7f -> 0.5f  // 连续高开口音，要有起伏
                avgOpen >= 0.5f -> 0.55f
                else -> 0.65f
            }

            val open = lerpValue(prevOpen, nextOpen, t) * holdFactor
            val form = lerpValue(prevEvent.viseme.mouthForm, nextEvent.viseme.mouthForm, t)

            lastMouthOpen = lerpValue(lastMouthOpen, open, 0.35f)
            lastMouthForm = lerpValue(lastMouthForm, form, 0.35f)
            return Pair(lastMouthOpen, lastMouthForm)
        }

        // 在第一个事件之前
        if (prevEvent == null && nextEvent != null) {
            // 如果下一个是 SIL，保持闭合
            if (nextEvent.viseme == VisemeType.SIL) {
                return Pair(0f, 0f)
            }
            val t = (effectivePos.toFloat() / nextEvent.startMs.coerceAtLeast(1)).coerceIn(0f, 1f)
            val open = nextEvent.viseme.mouthOpen * t
            val form = nextEvent.viseme.mouthForm * t
            lastMouthOpen = lerpValue(lastMouthOpen, open, 0.3f)
            lastMouthForm = lerpValue(lastMouthForm, form, 0.3f)
            return Pair(lastMouthOpen, lastMouthForm)
        }

        // 在最后一个事件之后：逐帧衰减并写回，使嘴巴平滑收敛到 0
        lastMouthOpen *= 0.7f
        lastMouthForm *= 0.7f
        return Pair(lastMouthOpen, lastMouthForm)
    }

    private fun lerpValue(a: Float, b: Float, t: Float): Float {
        return a + (b - a) * t.coerceIn(0f, 1f)
    }

    /**
     * 强制闭合嘴巴
     */
    private fun forceCloseMouth() {
        lastMouthOpen = 0f
        lastMouthForm = 0f
        _mouthState.value = Pair(0f, 0f)
        _avatarState.update { it.copy(mouthOpen = 0f, mouthForm = 0f) }
    }

    private fun estimateTextDuration(text: String): Long {
        return text.sumOf { char ->
            when {
                char in setOf('，', '。', '！', '？', '、', '；', '：', '"', '"') -> 300L
                char in setOf(',', '.', '!', '?', ';', ':', '"', '\'') -> 200L
                char.isWhitespace() -> 100L
                else -> 180L
            }
        }.coerceAtLeast(500L)
    }

    /**
     * 播放动作队列
     *
     * 每个动作的 durationMs 都会被尊重：
     * - 如果 duration 在下一个动作开始之前结束，先平滑过渡到 IDLE，再等待下一个动作
     * - 最后一个动作在 duration 结束后平滑过渡到 IDLE
     * - 动作之间有平滑过渡，过渡时间由 GestureTransitionController.DEFAULT_TRANSITION_MS 控制
     */
    private fun playMotionQueue(queue: List<MotionQueueItem>) {
        motionQueueJob?.cancel()
        if (queue.isEmpty()) return

        val transitionMs = GestureTransitionController.DEFAULT_TRANSITION_MS

        motionQueueJob = scope.launch {
            val sortedQueue = queue.sortedBy { it.startOffsetMs }
            val startTime = SystemClock.elapsedRealtime()

            sortedQueue.forEachIndexed { index, item ->
                val elapsed = SystemClock.elapsedRealtime() - startTime
                val waitMs = (item.startOffsetMs - elapsed).coerceAtLeast(0)
                if (waitMs > 0) delay(waitMs)

                val gesture = AvatarGesture.fromValue(item.type)
                Log.d(TAG, "motion[$index]: $gesture, transitionMs=$transitionMs")
                // 使用平滑过渡更新动作
                _avatarState.update {
                    it.copy(
                        gesture = gesture,
                        gestureTransitionMs = transitionMs
                    )
                }

                if (item.durationMs > 0) {
                    val nextItem = sortedQueue.getOrNull(index + 1)
                    val nextStartMs = nextItem?.startOffsetMs ?: Long.MAX_VALUE
                    val now = SystemClock.elapsedRealtime() - startTime
                    val timeUntilNext = (nextStartMs - now).coerceAtLeast(0)
                    val holdMs = item.durationMs.coerceAtMost(timeUntilNext)

                    if (holdMs > 0) delay(holdMs)

                    // duration 结束后，如果距离下一个动作还有时间，平滑过渡到 IDLE 并等待
                    if (nextItem != null && item.durationMs < timeUntilNext) {
                        // 平滑过渡到 IDLE
                        _avatarState.update {
                            it.copy(
                                gesture = AvatarGesture.IDLE,
                                gestureTransitionMs = transitionMs
                            )
                        }
                        val remainingWait = timeUntilNext - item.durationMs
                        if (remainingWait > 0) delay(remainingWait)
                    } else if (nextItem == null) {
                        // 最后一个动作，duration 结束后平滑过渡到 IDLE
                        val remaining = item.durationMs - holdMs
                        if (remaining > 0) delay(remaining)
                        _avatarState.update {
                            it.copy(
                                gesture = AvatarGesture.IDLE,
                                gestureTransitionMs = transitionMs
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * 释放资源
     *
     * 优化释放顺序以降低 Android 15 Scudo + Binder Parcel 崩溃率：
     * 1. 先停止播放活动
     * 2. 取消所有协程（防止后台访问）
     * 3. 释放 TTS 控制器
     * 4. 释放流式队列（会触发 audioPlayer.release）
     */
    fun release() {
        runCatching {
            stop()
            scope.cancel()
            ttsController.release()
            streamingTtsQueue.release()
        }.onFailure {
            Log.w(TAG, "release failed: ${it.message}")
        }
    }
}

/**
 * 表情时间轴项
 */
data class ExpressionTimelineItem(
    val expression: AvatarExpression,
    val startOffsetMs: Long,
    val intensity: Float = 0.7f,
    val transitionMs: Long = 200
)

/**
 * 数字人播放动作
 */
data class AvatarPlayAction(
    val text: String? = null,
    val expression: AvatarExpression = AvatarExpression.NEUTRAL,
    val expressionIntensity: Float = 0.7f,
    val gesture: AvatarGesture = AvatarGesture.IDLE,
    val gesturePriority: GesturePriority = GesturePriority.NORMAL,
    val gestureLoop: Boolean = false,
    val gestureSpeed: Float = 1.0f,
    val motionQueue: List<MotionQueueItem> = emptyList(),
    /**
     * 表情变化时间轴
     * 按startOffsetMs排序，播放过程中动态切换表情
     */
    val expressionTimeline: List<ExpressionTimelineItem> = emptyList()
)
