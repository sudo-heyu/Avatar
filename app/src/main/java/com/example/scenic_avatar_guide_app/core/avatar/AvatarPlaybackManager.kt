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
            startSegmentLipSync(segment)
        },
        onSegmentComplete = {
            // 不立即停止唇形动画，让 LipSyncAnimator 自然结束或保持当前口型。
            // 若下一段很快开始，嘴部不会闪闭再张开，从而消除视觉顿挫感。
        },
        onWaitingForSegment = {
            // 延迟关闭口型：短 gap 内不闭嘴，避免嘴部频繁开合
            scheduleMouthClose()
        },
        onAllComplete = {
            cancelWaitingClose()
            lipSyncAnimator.stop()
            isPlaying = false
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
            lipSyncAnimator.stop()
            isPlaying = false
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
        }

        ttsController.onSpeakComplete = {
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

        // 音素事件列表回调 - 驱动高精度口型动画
        ttsController.onPhonemeEvents = { events ->
            lipSyncAnimator.start(events, scope)
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
        Log.d(TAG, "play: gesture=${action.gesture}, expression=${action.expression}, motions=${action.motionQueue.size}, priority=${action.gesturePriority}, loop=${action.gestureLoop}, speed=${action.gestureSpeed}")

        if (isPlaying) {
            stop()
        }
        streamingTtsQueue.cancel()
        streamingText.clear()
        currentPlayAction = action

        val hasSpeech = !action.text.isNullOrBlank()

        _avatarState.update {
            it.copy(
                state = if (hasSpeech) AvatarState.SPEAKING else AvatarState.IDLE,
                expression = action.expression,
                expressionIntensity = action.expressionIntensity,
                gesture = action.gesture,
                gesturePriority = action.gesturePriority
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
        stop()
        streamingText.clear()
        receivedTtsSegment = false
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

        _avatarState.update {
            it.copy(
                gesture = gesture,
                expression = expression,
                gesturePriority = priority
            )
        }

        // 自动恢复（loop=false 时）
        if (!loop) {
            val autoResetDelay = (2000L / speed.coerceAtLeast(0.1f)).toLong()
            scope.launch {
                delay(autoResetDelay)
                _avatarState.update {
                    it.copy(gesture = AvatarGesture.IDLE, gesturePriority = GesturePriority.NORMAL)
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
        motionQueueJob?.cancel()
        motionQueueJob = null
        expressionTimelineJob?.cancel()
        expressionTimelineJob = null
        cancelWaitingClose()
        isPlaying = false
        currentPlayAction = null
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

    private fun startSegmentLipSync(segment: TtsSegmentData) {
        val marks = segment.marks
        val events = if (!marks.isNullOrEmpty()) {
            ChinesePhonemeEngine.marksToPhonemeEvents(marks)
        } else {
            ChinesePhonemeEngine.textToPhonemeEvents(
                text = segment.text,
                totalDurationMs = segment.durationMs?.toLong() ?: estimateTextDuration(segment.text)
            )
        }
        lipSyncAnimator.start(events, scope)
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
     * - 如果 duration 在下一个动作开始之前结束，先恢复 IDLE，再等待下一个动作
     * - 最后一个动作在 duration 结束后恢复 IDLE
     */
    private fun playMotionQueue(queue: List<MotionQueueItem>) {
        motionQueueJob?.cancel()
        if (queue.isEmpty()) return

        motionQueueJob = scope.launch {
            val sortedQueue = queue.sortedBy { it.startOffsetMs }
            val startTime = SystemClock.elapsedRealtime()

            sortedQueue.forEachIndexed { index, item ->
                val elapsed = SystemClock.elapsedRealtime() - startTime
                val waitMs = (item.startOffsetMs - elapsed).coerceAtLeast(0)
                if (waitMs > 0) delay(waitMs)

                val gesture = AvatarGesture.fromValue(item.type)
                Log.d(TAG, "motion[$index]: $gesture")
                _avatarState.update { it.copy(gesture = gesture) }

                if (item.durationMs > 0) {
                    val nextItem = sortedQueue.getOrNull(index + 1)
                    val nextStartMs = nextItem?.startOffsetMs ?: Long.MAX_VALUE
                    val now = SystemClock.elapsedRealtime() - startTime
                    val timeUntilNext = (nextStartMs - now).coerceAtLeast(0)
                    val holdMs = item.durationMs.coerceAtMost(timeUntilNext)

                    if (holdMs > 0) delay(holdMs)

                    // duration 结束后，如果距离下一个动作还有时间，恢复 IDLE 并等待
                    if (nextItem != null && item.durationMs < timeUntilNext) {
                        _avatarState.update { it.copy(gesture = AvatarGesture.IDLE) }
                        val remainingWait = timeUntilNext - item.durationMs
                        if (remainingWait > 0) delay(remainingWait)
                    } else if (nextItem == null) {
                        // 最后一个动作，duration 结束后恢复 IDLE
                        val remaining = item.durationMs - holdMs
                        if (remaining > 0) delay(remaining)
                        _avatarState.update { it.copy(gesture = AvatarGesture.IDLE) }
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
