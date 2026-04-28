package com.example.scenic_avatar_guide_app.core.avatar

import com.example.scenic_avatar_guide_app.core.tts.PhonemeEvent
import kotlinx.coroutines.*

/**
 * 口型动画驱动器
 * 负责按时间轴平滑推进口型状态，支持协同发音（Coarticulation）
 */
class LipSyncAnimator {

    private var job: Job? = null
    private var onUpdate: ((mouthOpen: Float, mouthForm: Float) -> Unit)? = null

    fun setOnUpdateListener(listener: (mouthOpen: Float, mouthForm: Float) -> Unit) {
        onUpdate = listener
    }

    /**
     * 启动口型动画
     *
     * @param events 按时间排序的音素事件列表
     * @param scope 协程作用域（通常使用 AvatarPlaybackManager 的 scope）
     */
    fun start(events: List<PhonemeEvent>, scope: CoroutineScope) {
        stop()

        if (events.isEmpty()) {
            onUpdate?.invoke(0f, 0f)
            return
        }

        job = scope.launch {
            val startTime = System.currentTimeMillis()
            var lastOpen = 0f
            var lastForm = 0f

            events.forEachIndexed { index, event ->
                if (!isActive) return@launch

                // 等待到该音素的开始时间
                val elapsed = System.currentTimeMillis() - startTime
                val waitTime = event.startMs - elapsed
                if (waitTime > 0) delay(waitTime)

                // 协同发音：混合当前音素与前后相邻音素
                val prevViseme = events.getOrNull(index - 1)?.viseme
                val nextViseme = events.getOrNull(index + 1)?.viseme

                val targetOpen = blendMouth(
                    prevViseme?.mouthOpen,
                    event.viseme.mouthOpen,
                    nextViseme?.mouthOpen
                )
                val targetForm = blendMouth(
                    prevViseme?.mouthForm,
                    event.viseme.mouthForm,
                    nextViseme?.mouthForm
                )

                // 在音素持续时间内平滑过渡
                val duration = (event.endMs - event.startMs).coerceAtLeast(40)
                animateTransition(
                    fromOpen = lastOpen,
                    fromForm = lastForm,
                    toOpen = targetOpen,
                    toForm = targetForm,
                    durationMs = duration
                )

                lastOpen = targetOpen
                lastForm = targetForm
            }

            // 全部结束，嘴巴自然闭合
            animateTransition(lastOpen, lastForm, 0f, 0f, 120)
            onUpdate?.invoke(0f, 0f)
        }
    }

    /**
     * 立即停止口型动画并重置嘴巴状态
     */
    fun stop() {
        job?.cancel()
        job = null
        onUpdate?.invoke(0f, 0f)
    }

    /**
     * 协同发音混合：加权平均前后音素
     *
     * 权重分配：前一音素 25%，当前 55%，后一音素 20%
     * 这使得口型过渡更自然，避免机械跳变
     */
    private fun blendMouth(prev: Float?, current: Float, next: Float?): Float {
        val p = prev ?: current
        val n = next ?: current
        return p * 0.25f + current * 0.55f + n * 0.2f
    }

    /**
     * 在指定时长内从当前口型线性过渡到目标口型
     *
     * 使用 20ms 步进，约 50fps，兼顾流畅度与性能
     */
    private suspend fun animateTransition(
        fromOpen: Float,
        fromForm: Float,
        toOpen: Float,
        toForm: Float,
        durationMs: Long
    ) {
        val steps = (durationMs / 20).toInt().coerceAtLeast(1)
        val stepDuration = durationMs / steps

        repeat(steps) { step ->
            if (job?.isActive != true) return
            val progress = (step + 1).toFloat() / steps
            val open = lerp(fromOpen, toOpen, progress)
            val form = lerp(fromForm, toForm, progress)
            onUpdate?.invoke(open, form)
            delay(stepDuration)
        }
    }

    private fun lerp(a: Float, b: Float, t: Float): Float {
        return a + (b - a) * t.coerceIn(0f, 1f)
    }
}
