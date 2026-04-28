package com.example.scenic_avatar_guide_app.core.avatar

import android.content.Context
import com.example.scenic_avatar_guide_app.core.tts.PhonemeEvent
import com.example.scenic_avatar_guide_app.core.tts.RemoteTTSController
import com.example.scenic_avatar_guide_app.core.tts.VoiceInfo
import com.example.scenic_avatar_guide_app.core.tts.VoiceStyle
import com.example.scenic_avatar_guide_app.data.repository.GuideRepository
import com.example.scenic_avatar_guide_app.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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

    // TTS 控制器（远程 Edge-TTS）
    private val ttsController = RemoteTTSController(context, repository)

    // 口型动画驱动器
    private val lipSyncAnimator = LipSyncAnimator()

    // 作用域
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 当前口型
    private var currentViseme: VisemeType = VisemeType.NEUTRAL

    // 是否正在播放
    private var isPlaying = false

    init {
        // 设置 TTS 回调
        setupTTSCallbacks()

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
            lipSyncAnimator.stop()
            _avatarState.update {
                it.copy(
                    state = AvatarState.IDLE,
                    mouthOpen = 0f,
                    mouthForm = 0f
                )
            }
            isPlaying = false
        }

        // 音素事件列表回调 - 驱动高精度口型动画
        ttsController.onPhonemeEvents = { events ->
            lipSyncAnimator.start(events, scope)
        }

        // 单个音素回调已由 LipSyncAnimator 的平滑动画接管，不再直接更新状态
        // 避免双重竞争导致口型跳动
    }

    /**
     * 播放动作和语音
     */
    fun play(action: AvatarPlayAction) {
        if (isPlaying) {
            stop()
        }

        // 设置表情
        _avatarState.update {
            it.copy(
                state = AvatarState.SPEAKING,
                expression = action.expression,
                expressionIntensity = action.expressionIntensity,
                gesture = action.gesture
            )
        }

        // 播放动作队列
        if (action.motionQueue.isNotEmpty()) {
            playMotionQueue(action.motionQueue)
        }

        // 播放 TTS
        action.text?.let { text ->
            ttsController.speak(text)
        }
    }

    /**
     * 播放简单动作（无语音）
     */
    fun playGesture(gesture: AvatarGesture, expression: AvatarExpression = AvatarExpression.NEUTRAL) {
        _avatarState.update {
            it.copy(
                gesture = gesture,
                expression = expression
            )
        }

        // 自动恢复
        scope.launch {
            delay(2000)
            _avatarState.update {
                it.copy(gesture = AvatarGesture.IDLE)
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
        isPlaying = false
        _avatarState.update {
            AvatarFullState()
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
     * 播放动作队列
     */
    private fun playMotionQueue(queue: List<MotionQueueItem>) {
        scope.launch {
            queue.sortedBy { it.startOffsetMs }.forEach { item ->
                delay(item.startOffsetMs)

                val gesture = AvatarGesture.fromValue(item.type)
                _avatarState.update { it.copy(gesture = gesture) }

                if (item.durationMs > 0) {
                    delay(item.durationMs)
                    _avatarState.update { it.copy(gesture = AvatarGesture.IDLE) }
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
        scope.cancel()
    }
}

/**
 * 数字人播放动作
 */
data class AvatarPlayAction(
    val text: String? = null,
    val expression: AvatarExpression = AvatarExpression.NEUTRAL,
    val expressionIntensity: Float = 0.7f,
    val gesture: AvatarGesture = AvatarGesture.IDLE,
    val motionQueue: List<MotionQueueItem> = emptyList()
)
