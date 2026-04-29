package com.example.scenic_avatar_guide_app.core.avatar

import com.example.scenic_avatar_guide_app.core.audio.AudioAmplitudeAnalyzer
import com.example.scenic_avatar_guide_app.core.avatar.animation.Easing
import com.example.scenic_avatar_guide_app.core.avatar.animation.EasingType
import com.example.scenic_avatar_guide_app.core.tts.PhonemeEvent
import com.example.scenic_avatar_guide_app.domain.model.VisemeType
import kotlinx.coroutines.*

/**
 * 口型动画驱动器
 * 负责按时间轴平滑推进口型状态，支持协同发音（Coarticulation）
 *
 * 优化特性：
 * - 缓动曲线：开口快、闭合慢，模拟真实说话
 * - 协同发音：前后音素加权混合
 * - 增强协同发音：基于音素类型差异化处理（爆破音闭气、元音滑动等）
 * - 平滑过渡：帧级插值避免跳跃
 * - 情绪影响：根据情绪强度调整口型幅度
 * - 音频振幅校正：实时振幅辅助口型同步（P4优化）
 */
class LipSyncAnimator {

    private var job: Job? = null
    private var onUpdate: ((mouthOpen: Float, mouthForm: Float) -> Unit)? = null

    // 默认缓动类型
    private var defaultEasingType: EasingType = EasingType.EASE_IN_OUT_CUBIC

    // 情绪乘数（从 ChinesePhonemeEngine 获取）
    private val emotionMultiplier: Float
        get() = ChinesePhonemeEngine.emotionMultiplier

    // 音频振幅分析器（可选，P4优化）
    private var amplitudeAnalyzer: AudioAmplitudeAnalyzer? = null

    // 是否启用振幅校正
    private var enableAmplitudeCorrection: Boolean = false

    fun setOnUpdateListener(listener: (mouthOpen: Float, mouthForm: Float) -> Unit) {
        onUpdate = listener
    }

    /**
     * 设置默认缓动曲线类型
     */
    fun setDefaultEasing(easingType: EasingType) {
        defaultEasingType = easingType
    }

    /**
     * 设置音频振幅分析器（P4优化）
     *
     * @param analyzer 振幅分析器实例
     * @param enabled 是否启用振幅校正
     */
    fun setAmplitudeAnalyzer(analyzer: AudioAmplitudeAnalyzer?, enabled: Boolean = true) {
        amplitudeAnalyzer = analyzer
        enableAmplitudeCorrection = enabled && analyzer != null
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

                val duration = (event.endMs - event.startMs).coerceAtLeast(40)

                // 增强协同发音：根据音素类型差异化处理
                val result = animateWithCoarticulation(
                    events = events,
                    index = index,
                    lastOpen = lastOpen,
                    lastForm = lastForm,
                    duration = duration
                )

                // 大开口音素结束时保留基础开度，避免频繁完全闭合
                val endOpenFloor = if (event.viseme.mouthOpen >= 0.7f) 0.35f else 0f
                lastOpen = result.first.coerceAtLeast(endOpenFloor)
                lastForm = result.second
            }

            // 全部结束，嘴巴自然闭合
            animateTransitionWithEasing(lastOpen, lastForm, 0f, 0f, 150, isOpening = false)
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
     * 增强协同发音动画（P3优化）
     *
     * 根据音素类型差异化处理：
     * - 爆破音(BP,DT,GK)：前期闭气准备，后期快速释放
     * - 元音(A,O,E,I,U,V)：平滑滑动过渡
     * - 摩擦音(F,JQ,ZC,ZH)：稳定保持
     * - 其他：标准加权混合
     */
    private suspend fun animateWithCoarticulation(
        events: List<PhonemeEvent>,
        index: Int,
        lastOpen: Float,
        lastForm: Float,
        duration: Long
    ): Pair<Float, Float> {
        val event = events[index]
        val viseme = event.viseme
        val prevViseme = events.getOrNull(index - 1)?.viseme
        val nextViseme = events.getOrNull(index + 1)?.viseme

        return when (viseme) {
            // 爆破音：前期闭气，后期快速释放
            VisemeType.BP, VisemeType.DT, VisemeType.GK -> {
                animatePlosive(viseme, prevViseme, nextViseme, lastOpen, lastForm, duration)
            }
            // 元音：平滑滑动
            VisemeType.A, VisemeType.O, VisemeType.E,
            VisemeType.I, VisemeType.U, VisemeType.V, VisemeType.UA -> {
                animateVowel(viseme, prevViseme, nextViseme, lastOpen, lastForm, duration)
            }
            // 其他：标准处理
            else -> {
                animateStandard(viseme, prevViseme, nextViseme, lastOpen, lastForm, duration)
            }
        }
    }

    /**
     * 爆破音动画
     *
     * 特点：前期闭气准备（嘴巴接近闭合），后期快速释放到目标口型
     * 时间分配：40% 闭气准备 + 60% 释放
     */
    private suspend fun animatePlosive(
        current: VisemeType,
        prev: VisemeType?,
        next: VisemeType?,
        lastOpen: Float,
        lastForm: Float,
        duration: Long
    ): Pair<Float, Float> {
        // 闭气准备阶段（40%）
        val holdDuration = (duration * 0.4f).toLong().coerceAtLeast(20)
        // 释放阶段（60%）
        val releaseDuration = duration - holdDuration

        // 闭气阶段：嘴巴接近闭合
        val holdOpen = applyAllCorrections(0.05f)
        val holdForm = current.mouthForm * 0.5f

        animateTransitionWithEasing(lastOpen, lastForm, holdOpen, holdForm, holdDuration, isOpening = false)

        // 释放阶段：混合前后音素，快速到达目标
        val targetOpen = applyAllCorrections(
            blendMouthAdvanced(prev, current, next, 0.7f)
        )
        val targetForm = blendMouth(prev?.mouthForm, current.mouthForm, next?.mouthForm)

        animateTransitionWithEasing(holdOpen, holdForm, targetOpen, targetForm, releaseDuration, isOpening = true)

        return Pair(targetOpen, targetForm)
    }

    /**
     * 元音动画
     *
     * 特点：平滑滑动过渡，前后音素权重随时间变化
     */
    private suspend fun animateVowel(
        current: VisemeType,
        prev: VisemeType?,
        next: VisemeType?,
        lastOpen: Float,
        lastForm: Float,
        duration: Long
    ): Pair<Float, Float> {
        // 元音起始：混合前一音素
        val startOpen = applyAllCorrections(
            blendMouthAdvanced(prev, current, null, 0.0f)
        )
        val startForm = blendMouth(prev?.mouthForm, current.mouthForm, null)

        // 元音中间：当前音素为主
        val midOpen = applyAllCorrections(current.mouthOpen)
        val midForm = current.mouthForm

        // 元音结束：混合后一音素
        val endOpen = applyAllCorrections(
            blendMouthAdvanced(null, current, next, 1.0f)
        )
        val endForm = blendMouth(null, current.mouthForm, next?.mouthForm)

        // 分三段动画：进入 → 保持 → 滑出
        val enterDuration = (duration * 0.3f).toLong()
        val holdDuration = (duration * 0.4f).toLong()
        val exitDuration = duration - enterDuration - holdDuration

        // 进入阶段
        if (enterDuration > 0) {
            animateTransitionWithEasing(lastOpen, lastForm, startOpen, startForm, enterDuration, isOpening = true)
        }

        // 保持阶段
        if (holdDuration > 0) {
            animateTransitionWithEasing(startOpen, startForm, midOpen, midForm, holdDuration, isOpening = true)
        }

        // 滑出阶段
        if (exitDuration > 0) {
            animateTransitionWithEasing(midOpen, midForm, endOpen, endForm, exitDuration, isOpening = endOpen > midOpen)
        }

        return Pair(endOpen, endForm)
    }

    /**
     * 标准动画
     *
     * 使用加权混合，前后音素各占20%，当前占60%
     */
    private suspend fun animateStandard(
        current: VisemeType,
        prev: VisemeType?,
        next: VisemeType?,
        lastOpen: Float,
        lastForm: Float,
        duration: Long
    ): Pair<Float, Float> {
        val targetOpen = applyAllCorrections(
            blendMouth(prev?.mouthOpen, current.mouthOpen, next?.mouthOpen)
        )
        val targetForm = blendMouth(prev?.mouthForm, current.mouthForm, next?.mouthForm)

        val isOpening = targetOpen > lastOpen
        animateTransitionWithEasing(lastOpen, lastForm, targetOpen, targetForm, duration, isOpening)

        return Pair(targetOpen, targetForm)
    }

    /**
     * 协同发音混合：加权平均前后音素
     *
     * 权重分配：前一音素 20%，当前 60%，后一音素 20%
     *
     * 大开口音素（>=0.7）降低前后权重，避免结束位置过低导致频繁张合
     */
    private fun blendMouth(prev: Float?, current: Float, next: Float?): Float {
        val p = prev ?: current
        val n = next ?: current
        val isHighOpen = current >= 0.7f
        val prevWeight = if (isHighOpen) 0.1f else 0.2f
        val nextWeight = if (isHighOpen) 0.1f else 0.2f
        val currentWeight = 1f - prevWeight - nextWeight
        return p * prevWeight + current * currentWeight + n * nextWeight
    }

    /**
     * 增强协同发音混合
     *
     * @param phase 当前音素内的播放进度 0.0-1.0
     * - 0.0：刚进入，前一音素影响大
     * - 0.5：中间，当前音素为主
     * - 1.0：即将退出，后一音素影响大
     *
     * 大开口音素（>=0.7）降低前后权重上限，结束位置保留更多开度
     */
    private fun blendMouthAdvanced(
        prev: VisemeType?,
        current: VisemeType,
        next: VisemeType?,
        phase: Float
    ): Float {
        val currentOpen = current.mouthOpen
        val prevOpen = prev?.mouthOpen ?: currentOpen
        val nextOpen = next?.mouthOpen ?: currentOpen

        // 动态权重：根据相位调整前后音素的影响
        // 大开口音素降低前后音素权重，避免结束时过度闭合
        val maxNeighborWeight = if (currentOpen >= 0.7f) 0.15f else 0.3f
        val prevWeight = (maxNeighborWeight * (1f - phase)).coerceIn(0f, maxNeighborWeight)
        val nextWeight = (maxNeighborWeight * phase).coerceIn(0f, maxNeighborWeight)
        val currentWeight = 1f - prevWeight - nextWeight

        return prevOpen * prevWeight + currentOpen * currentWeight + nextOpen * nextWeight
    }

    /**
     * 应用情绪影响（P2优化）
     */
    private fun applyEmotion(mouthOpen: Float): Float {
        return (mouthOpen * emotionMultiplier).coerceIn(0f, 1f)
    }

    /**
     * 应用音频振幅校正（P4优化）
     *
     * 当启用振幅校正时，使用实时音频振幅辅助口型同步：
     * - 有声段：振幅放大口型幅度
     * - 无声段：快速衰减
     *
     * @param mouthOpen 基础口型开度
     * @return 校正后的口型开度
     */
    private fun applyAmplitudeCorrection(mouthOpen: Float): Float {
        val analyzer = amplitudeAnalyzer ?: return mouthOpen
        if (!enableAmplitudeCorrection) return mouthOpen

        return analyzer.applyAmplitudeCorrection(mouthOpen)
    }

    /**
     * 综合应用所有校正：情绪 + 振幅
     */
    private fun applyAllCorrections(mouthOpen: Float): Float {
        // 先应用情绪影响
        val withEmotion = applyEmotion(mouthOpen)
        // 再应用振幅校正
        return applyAmplitudeCorrection(withEmotion)
    }

    /**
     * 带缓动曲线的过渡动画
     */
    private suspend fun animateTransitionWithEasing(
        fromOpen: Float,
        fromForm: Float,
        toOpen: Float,
        toForm: Float,
        durationMs: Long,
        isOpening: Boolean
    ) {
        val steps = (durationMs / 16).toInt().coerceAtLeast(1)
        val stepDuration = durationMs / steps

        repeat(steps) { step ->
            if (job?.isActive != true) return
            val rawProgress = (step + 1).toFloat() / steps
            val easedProgress = Easing.speakTransition(rawProgress, isOpening)

            val open = lerp(fromOpen, toOpen, easedProgress)
            val form = lerp(fromForm, toForm, easedProgress)
            onUpdate?.invoke(open, form)
            delay(stepDuration)
        }
    }

    private fun lerp(a: Float, b: Float, t: Float): Float {
        return a + (b - a) * t.coerceIn(0f, 1f)
    }
}
