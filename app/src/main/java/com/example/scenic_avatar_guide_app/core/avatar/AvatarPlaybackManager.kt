package com.example.scenic_avatar_guide_app.core.avatar

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.example.scenic_avatar_guide_app.core.audio.AudioPlayer
import com.example.scenic_avatar_guide_app.core.tts.PhonemeEvent
import com.example.scenic_avatar_guide_app.core.tts.RemoteTTSController
import com.example.scenic_avatar_guide_app.core.tts.StreamingTtsQueue
import com.example.scenic_avatar_guide_app.core.tts.SystemTTSController
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

private const val TAG = "AvatarPlaybackManager"

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

    // 当前发音人
    private val _currentVoice = MutableStateFlow(RemoteTTSController.DEFAULT_VOICE)
    val currentVoice: StateFlow<VoiceInfo> = _currentVoice.asStateFlow()

    // TTS 控制器（远程 Edge-TTS，用于非流式播放）
    private val ttsController = RemoteTTSController(context, repository)

    // 系统 TTS 控制器（降级兜底）
    private val systemTtsController = SystemTTSController(context)

    // 口型动画驱动器
    private val lipSyncAnimator = LipSyncAnimator()

    // 流式 TTS 累计文本（用于降级时播放）
    private var streamingText = StringBuilder()

    // 是否收到过 TTS 片段
    private var receivedTtsSegment = false

    // 流式 TTS 分段播放队列
    private val streamingAudioPlayer = AudioPlayer(context)
    private val streamingTtsQueue = StreamingTtsQueue(
        audioPlayer = streamingAudioPlayer,
        buildAudioUrl = { repository.buildAudioUrl(it) },
        onSegmentStart = { segment ->
            receivedTtsSegment = true
            isPlaying = true
            cancelWaitingClose()
            _avatarState.update {
                it.copy(
                    state = AvatarState.SPEAKING,
                    currentText = segment.text
                )
            }
            // 更新当前 segment 的口型事件（不再累计，每个 segment 独立）
            currentSegmentId = segment.segmentId
            updateCurrentSegmentLipSync(segment)
        },
        onSegmentComplete = { segment, actualDurationMs ->
            Log.d(TAG, "segment ${segment.segmentId} 完成，实际时长=${actualDurationMs}ms")
        },
        onWaitingForSegment = {
            // 短 gap 内保持当前状态，不闭合嘴巴
        },
        onAllComplete = {
            cancelWaitingClose()
            audioPositionSyncJob?.cancel()
            audioPositionSyncJob = null
            streamingLipSyncJob?.cancel()
            streamingLipSyncJob = null
            isPlaying = false
            currentSegmentEvents.clear()
            currentSegmentId = null
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
            audioPositionSyncJob?.cancel()
            audioPositionSyncJob = null
            streamingLipSyncJob?.cancel()
            streamingLipSyncJob = null
            isPlaying = false
            currentSegmentEvents.clear()
            currentSegmentId = null
            _avatarState.update {
                it.copy(
                    state = AvatarState.ERROR,
                    gesture = AvatarGesture.IDLE,
                    mouthOpen = 0f,
                    mouthForm = 0f
                )
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

    // 帧间平滑：记住上一帧的口型值
    private var lastMouthOpen = 0f
    private var lastMouthForm = 0f

    init {
        // 设置 TTS 回调
        setupTTSCallbacks()

        // 设置系统 TTS 回调（降级方案）
        setupSystemTTSCallbacks()

        // 设置口型动画回调
        lipSyncAnimator.setOnUpdateListener { open, form ->
            _avatarState.update {
                it.copy(mouthOpen = open, mouthForm = form)
            }
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
        systemTtsController.setVoice(voiceId)
        _currentVoice.value = RemoteTTSController.AVAILABLE_VOICES.find { it.id == voiceId }
            ?: RemoteTTSController.DEFAULT_VOICE
    }

    /**
     * 设置语速 (0.5 - 2.0)
     */
    fun setSpeed(speed: Float) {
        ttsController.setSpeed(speed)
        systemTtsController.setSpeed(speed)
    }

    /**
     * 设置音调 (0.5 - 2.0)
     */
    fun setPitch(pitch: Float) {
        ttsController.setPitch(pitch)
        systemTtsController.setPitch(pitch)
    }

    /**
     * 设置 TTS 回调
     */
    private fun setupTTSCallbacks() {
        ttsController.onSpeakStart = {
            _avatarState.update { it.copy(state = AvatarState.SPEAKING) }
            isPlaying = true

            // 设置外部时间源（音频进度）用于口型同步
            lipSyncAnimator.setExternalTimeSource(
                timeSource = { ttsController.getCurrentPosition() },
                isPlayingSource = { ttsController.isAudioPlaying() }
            )
        }

        ttsController.onSpeakComplete = {
            Log.d(TAG, "[TTS] 非流式播放完成")
            lipSyncAnimator.stop()
            lipSyncAnimator.clearExternalTimeSource()
            val shouldKeepGesture = currentPlayAction?.gestureLoop == true
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
        }

        // 音素事件列表回调 - 驱动高精度口型动画
        // 使用音频同步模式
        ttsController.onPhonemeEvents = { events ->
            val eventCount = events.size
            val lastEvent = events.lastOrNull()
            Log.d(TAG, "[TTS] 非流式口型事件: count=$eventCount, 时间范围=0-${lastEvent?.endMs}ms")
            lipSyncAnimator.start(events, scope, useAudioSync = true)
        }

        // 单个音素回调已由 LipSyncAnimator 的平滑动画接管，不再直接更新状态
        // 避免双重竞争导致口型跳动
    }

    /**
     * 设置系统 TTS 回调（降级方案）
     */
    private fun setupSystemTTSCallbacks() {
        systemTtsController.onSpeakStart = {
            _avatarState.update { it.copy(state = AvatarState.SPEAKING) }
            // 系统 TTS 无法获取精确的播放进度，清除外部时间源
            lipSyncAnimator.clearExternalTimeSource()
        }

        systemTtsController.onSpeakComplete = {
            lipSyncAnimator.stop()
            val shouldKeepGesture = currentPlayAction?.gestureLoop == true
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
        streamingText.clear()
        currentPlayAction = action

        // 计算动作过渡时间：速度越快，过渡时间越短
        val transitionMs = GestureTransitionController.calculateTransitionMs(action.gestureSpeed)

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
        streamingText.clear()
        receivedTtsSegment = false
        currentSegmentEvents.clear()
        currentSegmentId = null
        streamingTtsQueue.start()
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

    /**
     * 累计流式文本（用于降级时播放）
     */
    fun appendStreamingText(text: String) {
        streamingText.append(text)
    }

    fun enqueueSpeechSegment(segment: TtsSegmentData) {
        streamingTtsQueue.enqueue(segment)
    }

    fun finishStreamingInput() {
        streamingTtsQueue.finishInput()

        // 如果没有收到 TTS 片段，但有文本，使用系统 TTS 降级播放
        if (!receivedTtsSegment && streamingText.isNotEmpty()) {
            Log.d(TAG, "未收到 TTS 片段，降级到系统 TTS 播放")
            scope.launch {
                delay(300) // 稍微延迟，等待状态稳定
                playWithSystemTTS(streamingText.toString())
            }
        }
    }

    /**
     * 使用系统 TTS 播放（降级方案）
     */
    private fun playWithSystemTTS(text: String) {
        isPlaying = true
        _avatarState.update {
            it.copy(
                state = AvatarState.SPEAKING,
                currentText = text
            )
        }

        // 生成口型事件
        val events = ChinesePhonemeEngine.textToPhonemeEvents(
            text = text,
            totalDurationMs = estimateTextDuration(text)
        )
        lipSyncAnimator.start(events, scope)

        systemTtsController.speak(text)
    }

    /**
     * 播放简单动作（无语音）
     *
     * @param priority 动作优先级，低于当前优先级时会被忽略
     * @param loop 是否循环保持，为 true 时不自动恢复 IDLE
     * @param speed 动作速度倍率，影响自动恢复时长和过渡速度
     */
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
        ttsController.stop()
        systemTtsController.stop()
        streamingTtsQueue.cancel()
        audioPositionSyncJob?.cancel()
        audioPositionSyncJob = null
        streamingLipSyncJob?.cancel()
        streamingLipSyncJob = null
        motionQueueJob?.cancel()
        motionQueueJob = null
        expressionTimelineJob?.cancel()
        expressionTimelineJob = null
        cancelWaitingClose()
        isPlaying = false
        currentPlayAction = null
        currentSegmentEvents.clear()
        currentSegmentId = null
        _avatarState.update {
            AvatarFullState()
        }
    }

    /**
     * 取消延迟闭嘴任务
     */
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
        _avatarState.update {
            it.copy(
                mouthOpen = viseme.mouthOpen,
                mouthForm = viseme.mouthForm
            )
        }
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

        // 关键修复：确保最后一个口型事件的结束时间不超过音频时长
        // 这可以避免"音频播完但嘴还在动"的问题
        if (segmentDuration > 0 && currentSegmentEvents.isNotEmpty()) {
            val lastEvent = currentSegmentEvents.last()
            if (lastEvent.endMs > segmentDuration) {
                // 按比例压缩所有事件的时间，使其匹配音频时长
                val scale = segmentDuration.toFloat() / lastEvent.endMs
                currentSegmentEvents = currentSegmentEvents.mapIndexed { index, event ->
                    PhonemeEvent(
                        phoneme = event.phoneme,
                        startMs = (event.startMs * scale).toLong(),
                        endMs = (event.endMs * scale).toLong(),
                        viseme = event.viseme,
                        charIndex = event.charIndex
                    )
                }.toMutableList()
                Log.d(TAG, "[SEGMENT] ${segment.segmentId}: 时间缩放 scale=$scale, 原 end=${lastEvent.endMs}ms -> 新 end=${segmentDuration}ms")
            }
        }

        val eventStart = currentSegmentEvents.firstOrNull()?.startMs ?: 0L
        val eventEnd = currentSegmentEvents.lastOrNull()?.endMs ?: 0L
        Log.d(TAG, "[SEGMENT] ${segment.segmentId}: 口型事件=${currentSegmentEvents.size}个, " +
                "时间范围=${eventStart}-${eventEnd}ms, 音频时长=${segmentDuration}ms")

        // 启动口型同步
        startAudioSyncedLipSync()
    }

    /**
     * 启动基于音频进度的口型同步协程。
     */
    private fun startAudioSyncedLipSync() {
        if (audioPositionSyncJob?.isActive == true) return

        lastMouthOpen = 0f
        lastMouthForm = 0f

        audioPositionSyncJob = scope.launch {
            var lastAudioPosition = -1L
            var samePositionCount = 0

            while (isActive && isPlaying) {
                val frameStart = System.currentTimeMillis()

                val isAudioPlaying = streamingAudioPlayer.isActuallyPlaying()
                val audioPos = streamingAudioPlayer.getCurrentPosition()
                val audioDur = streamingAudioPlayer.getDuration()
                val eventsEnd = currentSegmentEvents.lastOrNull()?.endMs ?: 0L

                // 检测停滞
                if (audioPos == lastAudioPosition && audioPos > 0) {
                    samePositionCount++
                } else {
                    samePositionCount = 0
                }
                lastAudioPosition = audioPos

                // 保底：音频停止或停滞
                if ((!isAudioPlaying && audioPos > 0) || samePositionCount >= 3) {
                    forceCloseMouth()
                    return@launch
                }

                // 保底：音频即将结束
                if (audioDur > 0 && audioPos >= audioDur - 50) {
                    forceCloseMouth()
                    return@launch
                }

                // 保底：口型时间轴结束
                if (eventsEnd > 0 && audioPos > eventsEnd) {
                    forceCloseMouth()
                    return@launch
                }

                // 计算并应用口型
                val (open, form) = calculateLipSync(audioPos)
                _avatarState.update { it.copy(mouthOpen = open, mouthForm = form) }

                // 帧率控制
                val elapsed = System.currentTimeMillis() - frameStart
                if (elapsed < 16) delay(16 - elapsed)
            }
            forceCloseMouth()
        }
    }

    /**
     * 根据音频位置计算口型
     */
    private fun calculateLipSync(audioPos: Long): Pair<Float, Float> {
        if (currentSegmentEvents.isEmpty()) return Pair(0f, 0f)

        // 找当前事件（音频位置落在事件时间范围内）
        val currentEvent = currentSegmentEvents.find { audioPos >= it.startMs && audioPos < it.endMs }

        if (currentEvent != null) {
            // 在某个事件内：直接使用该事件的口型
            val open = currentEvent.viseme.mouthOpen
            val form = currentEvent.viseme.mouthForm

            // 与上一帧平滑过渡
            lastMouthOpen = lerpValue(lastMouthOpen, open, 0.35f)
            lastMouthForm = lerpValue(lastMouthForm, form, 0.35f)
            return Pair(lastMouthOpen, lastMouthForm)
        }

        // 在两个事件之间：查找前后事件
        val prevEvent = currentSegmentEvents.lastOrNull { it.endMs <= audioPos }
        val nextEvent = currentSegmentEvents.firstOrNull { it.startMs > audioPos }

        if (prevEvent != null && nextEvent != null) {
            // 字间过渡：插值
            val gap = (nextEvent.startMs - prevEvent.endMs).coerceAtLeast(1)
            val t = ((audioPos - prevEvent.endMs).toFloat() / gap).coerceIn(0f, 1f)

            val open = lerpValue(prevEvent.viseme.mouthOpen, nextEvent.viseme.mouthOpen, t)
            val form = lerpValue(prevEvent.viseme.mouthForm, nextEvent.viseme.mouthForm, t)

            lastMouthOpen = lerpValue(lastMouthOpen, open, 0.25f)
            lastMouthForm = lerpValue(lastMouthForm, form, 0.25f)
            return Pair(lastMouthOpen, lastMouthForm)
        }

        // 在第一个事件之前
        if (prevEvent == null && nextEvent != null) {
            val t = (audioPos.toFloat() / nextEvent.startMs.coerceAtLeast(1)).coerceIn(0f, 1f)
            val open = nextEvent.viseme.mouthOpen * t
            val form = nextEvent.viseme.mouthForm * t
            lastMouthOpen = lerpValue(lastMouthOpen, open, 0.3f)
            lastMouthForm = lerpValue(lastMouthForm, form, 0.3f)
            return Pair(lastMouthOpen, lastMouthForm)
        }

        // 在最后一个事件之后
        return Pair(lastMouthOpen * 0.7f, lastMouthForm * 0.7f)
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
     */
    fun release() {
        stop()
        ttsController.release()
        systemTtsController.release()
        streamingTtsQueue.release()
        streamingAudioPlayer.release()
        scope.cancel()
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
