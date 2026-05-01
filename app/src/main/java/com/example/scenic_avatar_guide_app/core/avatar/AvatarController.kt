package com.example.scenic_avatar_guide_app.core.avatar

import android.content.Context
import com.example.scenic_avatar_guide_app.domain.model.*
import com.example.scenic_avatar_guide_app.core.tts.PhonemeEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 数字人控制器
 * 管理数字人状态、动画、口型同步
 */
class AvatarController(private val context: Context) {

    // 当前状态
    private val _state = MutableStateFlow(AvatarFullState())
    val state: StateFlow<AvatarFullState> = _state.asStateFlow()

    // 控制器作用域
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 口型动画 Job
    private var lipSyncJob: Job? = null

    // 动作队列 Job
    private var motionQueueJob: Job? = null

    /**
     * 设置口型
     */
    fun setMouth(mouthOpen: Float, mouthForm: Float = 0f) {
        _state.update {
            it.copy(
                mouthOpen = mouthOpen.coerceIn(0f, 1f),
                mouthForm = mouthForm.coerceIn(-1f, 1f)
            )
        }
    }

    /**
     * 设置表情
     */
    fun setExpression(
        expression: AvatarExpression,
        intensity: Float = 0.7f,
        transitionMs: Long = 200
    ) {
        _state.update {
            it.copy(
                expression = expression,
                expressionIntensity = intensity
            )
        }
    }

    /**
     * 触发动作
     */
    fun triggerGesture(
        gesture: AvatarGesture,
        priority: GesturePriority = GesturePriority.NORMAL
    ) {
        // 检查优先级
        val currentPriority = _state.value.gesturePriority
        if (priority.ordinal < currentPriority.ordinal) {
            return // 低优先级动作不打断高优先级
        }

        _state.update {
            it.copy(
                gesture = gesture,
                gesturePriority = priority
            )
        }
    }

    /**
     * 设置状态
     */
    fun setState(state: AvatarState) {
        _state.update { it.copy(state = state) }
    }

    /**
     * 设置播放进度
     */
    fun setSpeakProgress(progress: Float) {
        _state.update { it.copy(speakProgress = progress.coerceIn(0f, 1f)) }
    }

    /**
     * 播放口型同步动画
     * @param phonemes 音素事件列表
     */
    fun playLipSync(phonemes: List<PhonemeEvent>) {
        lipSyncJob?.cancel()

        setState(AvatarState.SPEAKING)

        lipSyncJob = scope.launch {
            val startTime = System.currentTimeMillis()

            for (phoneme in phonemes) {
                // 等待到该音素的开始时间
                val elapsed = System.currentTimeMillis() - startTime
                val waitTime = phoneme.startMs - elapsed
                if (waitTime > 0) {
                    delay(waitTime)
                }

                // 映射口型
                animateToViseme(phoneme.viseme, phoneme.endMs - phoneme.startMs)
            }

            // 结束后恢复
            delay(100)
            setMouth(0f, 0f)
            setState(AvatarState.IDLE)
        }
    }

    /**
     * 简化的口型播放（无音素数据时使用）
     * 根据文本生成模拟口型
     */
    fun playSimpleLipSync(text: String, duration: Long) {
        lipSyncJob?.cancel()

        setState(AvatarState.SPEAKING)

        // 根据文本长度估算音节数
        val syllableCount = text.length.coerceAtLeast(1)
        val syllableDuration = duration / syllableCount

        lipSyncJob = scope.launch {
            text.forEach { char ->
                // 根据字符生成口型
                val viseme = getVisemeForChar(char)

                // 开嘴
                animateToViseme(viseme, (syllableDuration * 0.3f).toLong())
                delay((syllableDuration * 0.6f).toLong())

                // 闭嘴
                animateToViseme(VisemeType.NEUTRAL, (syllableDuration * 0.1f).toLong())
            }

            setState(AvatarState.IDLE)
        }
    }

    /**
     * 播放动作队列
     */
    fun playMotionQueue(queue: List<MotionQueueItem>) {
        motionQueueJob?.cancel()

        motionQueueJob = scope.launch {
            queue.sortedBy { it.startOffsetMs }.forEach { item ->
                // 等待到开始时间
                delay(item.startOffsetMs)

                // 触发动作
                val gesture = AvatarGesture.fromValue(item.type)
                triggerGesture(gesture, GesturePriority.NORMAL)

                // 如果指定了持续时间，等待后恢复
                if (item.durationMs > 0) {
                    delay(item.durationMs)
                    triggerGesture(AvatarGesture.IDLE, GesturePriority.NORMAL)
                }
            }
        }
    }

    /**
     * 停止口型动画
     */
    fun stopLipSync() {
        lipSyncJob?.cancel()
        lipSyncJob = null
        setMouth(0f, 0f)
    }

    /**
     * 重置状态
     */
    fun reset() {
        lipSyncJob?.cancel()
        motionQueueJob?.cancel()
        _state.value = AvatarFullState()
    }

    /**
     * 动画过渡到目标口型
     */
    private suspend fun animateToViseme(target: VisemeType, durationMs: Long) {
        val startOpen = _state.value.mouthOpen
        val startForm = _state.value.mouthForm
        val targetOpen = target.mouthOpen
        val targetForm = target.mouthForm

        val steps = (durationMs / 16).toInt().coerceAtLeast(1)
        val stepDuration = durationMs / steps

        repeat(steps) { i ->
            val progress = easeInOutQuad(i.toFloat() / steps)
            setMouth(
                startOpen + (targetOpen - startOpen) * progress,
                startForm + (targetForm - startForm) * progress
            )
            delay(stepDuration)
        }

        setMouth(targetOpen, targetForm)
    }

    /**
     * 根据字符获取口型
     */
    private fun getVisemeForChar(char: Char): VisemeType {
        return when {
            char in "aeo" -> VisemeType.OPEN
            char in "iuü" -> VisemeType.WIDE
            char in "bpmw" -> VisemeType.CLOSED
            char in "dtnl" -> VisemeType.SLIGHT
            char in "gkh" -> VisemeType.HALF
            char in "zcs" -> VisemeType.SLIGHT
            char in "jqx" -> VisemeType.WIDE
            char in "r" -> VisemeType.SLIGHT
            else -> VisemeType.NEUTRAL
        }
    }

    /**
     * 缓动函数
     */
    private fun easeInOutQuad(t: Float): Float {
        return if (t < 0.5f) 2 * t * t else 1 - (-2 * t + 2) * (-2 * t + 2) / 2
    }

    /**
     * 释放资源
     */
    fun release() {
        runCatching {
            lipSyncJob?.cancel()
            motionQueueJob?.cancel()
            scope.cancel()
        }.onFailure {
            android.util.Log.w("AvatarController", "release failed: ${it.message}")
        }
    }
}
