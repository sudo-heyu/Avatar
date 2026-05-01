package com.example.scenic_avatar_guide_app.core.avatar

import android.util.Log
import com.example.scenic_avatar_guide_app.core.audio.AudioAmplitudeAnalyzer
import com.example.scenic_avatar_guide_app.core.avatar.animation.Easing
import com.example.scenic_avatar_guide_app.core.avatar.animation.EasingType
import com.example.scenic_avatar_guide_app.core.tts.PhonemeEvent
import com.example.scenic_avatar_guide_app.domain.model.VisemeType
import kotlinx.coroutines.*

private const val TAG = "LipSyncAnimator"

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
 * - 音频进度同步：支持外部时间源，实现与音频播放精确同步
 */
class LipSyncAnimator {

    companion object {
        /**
         * 按字（charIndex）合并音素事件 - 增强版
         *
         * 保留声母过渡阶段，而不是完全丢弃：
         * - 声母阶段：20-30% 的字时长，mouthOpen 取声母值
         * - 韵母阶段：70-80% 的字时长，mouthOpen 取韵母值
         * - 闭唇声母（b,p,m）特殊处理：保留完整的闭嘴->张开动作
         *
         * 这能避免连续开口音时嘴巴一直大张的问题。
         */
        fun mergeEventsByChar(events: List<PhonemeEvent>): List<PhonemeEvent> {
            if (events.size <= 1) return events

            return events.groupBy { it.charIndex }
                .toSortedMap()
                .values
                .flatMap { group ->
                    if (group.size == 1) {
                        listOf(group.first())
                    } else if (group.any { it.viseme in listOf(VisemeType.BP, VisemeType.ZC, VisemeType.JQ, VisemeType.ZH) }) {
                        // 含闭唇/半闭唇声母的字保留原样，确保闭嘴动作不被吞掉
                        // 包括：b,p,m (BP), z,c,s (ZC), j,q,x (JQ), zh,ch,sh,r (ZH)
                        group.sortedBy { it.startMs }
                    } else {
                        // 获取声母和韵母
                        val sorted = group.sortedBy { it.startMs }
                        val initial = sorted.firstOrNull()
                        val finalEvents = sorted.drop(1)

                        if (finalEvents.isEmpty() || initial == null) {
                            // 只有声母或没有事件
                            listOf(sorted.first())
                        } else {
                            val charStartMs = sorted.minOf { it.startMs }
                            val charEndMs = sorted.maxOf { it.endMs }
                            val charDuration = charEndMs - charStartMs

                            // 声母时长占比：根据声母类型决定
                            val initialRatio = ChineseVisemeMapper.getInitialDurationRatio(initial.phoneme)
                            val initialDuration = (charDuration * initialRatio).toLong()

                            // 韵母代表（取 mouthOpen 最大的，通常是主元音）
                            val finalRep = finalEvents
                                .filter { it.viseme != VisemeType.SIL }
                                .maxByOrNull { it.viseme.mouthOpen }
                                ?: finalEvents.first()

                            if (initialDuration > 30 && initial.viseme != VisemeType.SIL) {
                                // 保留声母过渡阶段（非闭唇声母）
                                listOf(
                                    // 声母阶段
                                    PhonemeEvent(
                                        phoneme = initial.phoneme,
                                        startMs = charStartMs,
                                        endMs = charStartMs + initialDuration,
                                        viseme = initial.viseme,
                                        charIndex = initial.charIndex
                                    ),
                                    // 韵母阶段（扩展到字结束）
                                    PhonemeEvent(
                                        phoneme = finalRep.phoneme,
                                        startMs = charStartMs + initialDuration,
                                        endMs = charEndMs,
                                        viseme = finalRep.viseme,
                                        charIndex = finalRep.charIndex
                                    )
                                )
                            } else {
                                // 声母时长太短或无声母，只保留韵母
                                listOf(
                                    PhonemeEvent(
                                        phoneme = finalRep.phoneme,
                                        startMs = charStartMs,
                                        endMs = charEndMs,
                                        viseme = finalRep.viseme,
                                        charIndex = finalRep.charIndex
                                    )
                                )
                            }
                        }
                    }
                }
        }
    }

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

    // 外部时间源（用于音频同步）
    private var externalTimeSource: (() -> Long)? = null

    // 外部播放状态源（用于判断是否应该停止动画）
    private var externalIsPlaying: (() -> Boolean)? = null

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
     * 设置外部时间源（用于音频同步）
     *
     * @param timeSource 返回当前音频播放位置（毫秒）的函数
     * @param isPlayingSource 返回当前是否正在播放的函数
     */
    fun setExternalTimeSource(
        timeSource: () -> Long,
        isPlayingSource: () -> Boolean
    ) {
        externalTimeSource = timeSource
        externalIsPlaying = isPlayingSource
    }

    /**
     * 清除外部时间源，使用系统时间
     */
    fun clearExternalTimeSource() {
        externalTimeSource = null
        externalIsPlaying = null
    }

    /**
     * 获取当前时间（毫秒）
     * 如果设置了外部时间源，使用外部时间；否则使用系统时间
     */
    private fun getCurrentTime(): Long {
        return externalTimeSource?.invoke() ?: System.currentTimeMillis()
    }

    /**
     * 启动口型动画
     *
     * @param events 按时间排序的音素事件列表
     * @param scope 协程作用域（通常使用 AvatarPlaybackManager 的 scope）
     * @param useAudioSync 是否使用音频同步模式（基于外部时间源）
     */
    fun start(events: List<PhonemeEvent>, scope: CoroutineScope, useAudioSync: Boolean = false) {
        stop()

        if (events.isEmpty()) {
            onUpdate?.invoke(0f, 0f)
            return
        }

        // 按字合并，消除字内声母导致的快速闭合抖动
        val mergedEvents = mergeEventsByChar(events)

        // 音频同步模式：使用外部时间源（音频进度）驱动口型
        if (useAudioSync && externalTimeSource != null) {
            startAudioSynced(mergedEvents, scope)
            return
        }

        // 传统模式：使用系统时间驱动
        job = scope.launch {
            val startTime = System.currentTimeMillis()
            var lastOpen = 0f
            var lastForm = 0f

            mergedEvents.forEachIndexed { index, event ->
                if (!isActive) return@launch

                // 检查外部播放状态
                if (externalIsPlaying?.invoke() == false) {
                    // 音频已停止，立即闭合
                    animateTransitionWithEasing(lastOpen, lastForm, 0f, 0f, 100, isOpening = false)
                    onUpdate?.invoke(0f, 0f)
                    return@launch
                }

                // 等待到该音素的开始时间
                val elapsed = System.currentTimeMillis() - startTime
                val waitTime = event.startMs - elapsed
                if (waitTime > 0) delay(waitTime)

                val duration = (event.endMs - event.startMs).coerceAtLeast(60)

                // 增强协同发音：根据音素类型差异化处理
                val result = animateWithCoarticulation(
                    events = mergedEvents,
                    index = index,
                    lastOpen = lastOpen,
                    lastForm = lastForm,
                    duration = duration
                )

                // 字间自然衰减：向中性微张靠拢，避免连续开口字时嘴一直大张
                val isLast = index == mergedEvents.lastIndex
                lastOpen = when {
                    event.viseme == VisemeType.SIL -> 0f
                    isLast -> result.first
                    else -> result.first + (0.12f - result.first) * 0.25f
                }
                lastForm = result.second
            }

            // 全部结束，嘴巴自然闭合
            animateTransitionWithEasing(lastOpen, lastForm, 0f, 0f, 150, isOpening = false)
            onUpdate?.invoke(0f, 0f)
        }
    }

    /**
     * 音频同步模式：基于外部时间源（音频进度）驱动口型
     *
     * 超前补偿：让口型比音频进度提前触发，确保视觉和听觉同步
     */
    private fun startAudioSynced(events: List<PhonemeEvent>, scope: CoroutineScope) {
        Log.d(TAG, "[AUDIOSYNC] 启动音频同步模式, events=${events.size}, lastEventEnd=${events.lastOrNull()?.endMs}")

        job = scope.launch {
            var lastOpen = 0f
            var lastForm = 0f
            var lastPosition = -1L
            var samePositionCount = 0
            var frameCount = 0
            val lastEventEnd = events.lastOrNull()?.endMs ?: 0L

            // 超前补偿（毫秒）
            val leadMs = 30L

            // 闭合提前量：音频结束前多少毫秒开始闭合嘴巴
            val closeLeadMs = 80L

            while (isActive) {
                frameCount++

                // 检查播放状态
                val isPlaying = externalIsPlaying?.invoke() ?: true
                val currentPosition = externalTimeSource?.invoke() ?: 0L

                // 应用超前补偿
                val lipPosition = (currentPosition + leadMs).coerceAtLeast(0L)

                // 每10帧输出诊断日志
                if (frameCount % 10 == 0) {
                    val currentEvent = events.find { lipPosition in it.startMs..it.endMs }
                    val eventInfo = currentEvent?.let { "${it.phoneme}:${it.startMs}-${it.endMs}" } ?: "none"
                    Log.d(TAG, "[AUDIOSYNC] audioPos=$currentPosition, lipPos=$lipPosition, event=$eventInfo, isPlaying=$isPlaying")
                }

                // 保底机制1：音频位置停滞检测
                if (currentPosition == lastPosition && currentPosition > 0) {
                    samePositionCount++
                    if (samePositionCount >= 3) {
                        Log.d(TAG, "[AUDIOSYNC] 音频停滞，强制闭合")
                        onUpdate?.invoke(0f, 0f)
                        return@launch
                    }
                } else {
                    samePositionCount = 0
                }
                lastPosition = currentPosition

                // 保底机制2：音频已停止（最可靠的停止信号）
                if (!isPlaying && currentPosition > 0) {
                    Log.d(TAG, "[AUDIOSYNC] 音频停止，强制闭合")
                    onUpdate?.invoke(0f, 0f)
                    return@launch
                }

                // 保底机制3：口型时间轴已结束（立即退出，不继续循环）
                if (lipPosition > lastEventEnd + 30) {
                    Log.d(TAG, "[AUDIOSYNC] 时间轴结束，强制闭合 lipPos=$lipPosition, lastEnd=$lastEventEnd")
                    onUpdate?.invoke(0f, 0f)
                    return@launch
                }

                // 查找当前应该的口型事件（使用超前位置）
                val currentEvent = events.find { lipPosition in it.startMs..it.endMs }

                if (currentEvent != null) {
                    // SIL 事件（标点/停顿）：强制闭唇
                    if (currentEvent.viseme == VisemeType.SIL) {
                        lastOpen = lerp(lastOpen, 0f, 0.5f)
                        lastForm = lerp(lastForm, 0f, 0.5f)
                        onUpdate?.invoke(lastOpen, lastForm)
                    } else {
                        val currentIndex = events.indexOf(currentEvent)
                        val progress = (lipPosition - currentEvent.startMs).toFloat() /
                            (currentEvent.endMs - currentEvent.startMs).coerceAtLeast(1)
                        val prevViseme = events.getOrNull(currentIndex - 1)?.viseme
                        val nextViseme = events.getOrNull(currentIndex + 1)?.viseme

                        val targetOpen = applyAllCorrections(
                            blendMouth(prevViseme?.mouthOpen, currentEvent.viseme.mouthOpen, nextViseme?.mouthOpen)
                        )
                        val targetForm = blendMouth(prevViseme?.mouthForm, currentEvent.viseme.mouthForm, nextViseme?.mouthForm)

                        val easedProgress = Easing.easeInOutCubic(progress.coerceIn(0f, 1f))
                        val fromOpen = lastOpen
                        val fromForm = lastForm

                        lastOpen = lerp(fromOpen, targetOpen, easedProgress)
                        lastForm = lerp(fromForm, targetForm, easedProgress)
                        onUpdate?.invoke(lastOpen, lastForm)
                    }
                } else {
                    // 不在任何事件中：检查是否在两事件之间
                    val prevEvent = events.lastOrNull { it.endMs < lipPosition }
                    val nextEvent = events.firstOrNull { it.startMs > lipPosition }

                    if (prevEvent != null && nextEvent != null) {
                        // 如果前后有 SIL 事件，强制闭唇
                        if (prevEvent.viseme == VisemeType.SIL || nextEvent.viseme == VisemeType.SIL) {
                            lastOpen = lerp(lastOpen, 0f, 0.4f)
                            lastForm = lerp(lastForm, 0f, 0.4f)
                            onUpdate?.invoke(lastOpen, lastForm)
                        } else {
                            // 字间过渡：动态保持系数，让连续开口音有起伏
                            val gap = (nextEvent.startMs - prevEvent.endMs).coerceAtLeast(1)
                            val intoGap = lipPosition - prevEvent.endMs
                            val t = (intoGap.toFloat() / gap).coerceIn(0f, 1f)

                            val prevOpen = prevEvent.viseme.mouthOpen
                            val nextOpen = nextEvent.viseme.mouthOpen
                            val avgOpen = (prevOpen + nextOpen) / 2f

                            // 语速快（gap 小）时的保持系数
                            val holdFactor = when {
                                gap >= 150 -> 0.4f   // 长停顿，闭合更多
                                gap >= 80 -> 0.5f    // 中等停顿
                                avgOpen >= 0.7f -> 0.5f  // 连续高开口音，要有起伏
                                avgOpen >= 0.5f -> 0.55f
                                else -> 0.65f
                            }

                            lastOpen = lerp(prevOpen, nextOpen, t) * holdFactor
                            lastForm = lerp(prevEvent.viseme.mouthForm, nextEvent.viseme.mouthForm, t)
                            onUpdate?.invoke(lastOpen, lastForm)
                        }
                    } else if (prevEvent != null) {
                        // 在所有事件之后，快速闭合（最多50ms）
                        val afterEnd = lipPosition - prevEvent.endMs
                        if (afterEnd < 50) {
                            val t = (afterEnd / 50f).coerceIn(0f, 1f)
                            lastOpen = lerp(lastOpen, 0f, t)
                            lastForm = lerp(lastForm, 0f, t)
                            onUpdate?.invoke(lastOpen, lastForm)
                        } else {
                            onUpdate?.invoke(0f, 0f)
                            return@launch
                        }
                    } else {
                        // 在所有事件之前，保持闭合
                        onUpdate?.invoke(0f, 0f)
                    }
                }

                delay(16) // ~60fps
            }
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
     * 按字（charIndex）合并音素事件
     * 委托给 companion object 中的增强版实现
     */
    private fun mergeEventsByChar(events: List<PhonemeEvent>): List<PhonemeEvent> {
        return Companion.mergeEventsByChar(events)
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
     * 时间分配：55% 闭气准备 + 45% 释放
     */
    private suspend fun animatePlosive(
        current: VisemeType,
        prev: VisemeType?,
        next: VisemeType?,
        lastOpen: Float,
        lastForm: Float,
        duration: Long
    ): Pair<Float, Float> {
        // 闭气准备阶段（55%，让闭嘴姿态更明显）
        val holdDuration = (duration * 0.55f).toLong().coerceAtLeast(30)
        // 释放阶段（45%）
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
     * 权重分配：
     * - 正常情况：前一音素 20%，当前 60%，后一音素 20%
     * - 连续大开口音（>=0.8）：增强前后权重到 25%，让过渡更明显
     *
     * 这样连续开口音时，前后音素的影响能让嘴巴有起伏变化
     */
    private fun blendMouth(prev: Float?, current: Float, next: Float?): Float {
        val p = prev ?: current
        val n = next ?: current

        // 计算是否是连续大开口音（当前和前后都是大开口）
        val isCurrentHighOpen = current >= 0.8f
        val isPrevHighOpen = (prev ?: 0f) >= 0.7f
        val isNextHighOpen = (next ?: 0f) >= 0.7f
        val isConsecutiveHighOpen = isCurrentHighOpen && (isPrevHighOpen || isNextHighOpen)

        return when {
            isConsecutiveHighOpen -> {
                // 连续大开口音：增强前后权重，让过渡更明显
                val prevWeight = if (isPrevHighOpen) 0.25f else 0.15f
                val nextWeight = if (isNextHighOpen) 0.25f else 0.15f
                val currentWeight = 1f - prevWeight - nextWeight
                p * prevWeight + current * currentWeight + n * nextWeight
            }
            isCurrentHighOpen -> {
                // 单独大开口音：正常权重
                0.15f * p + 0.7f * current + 0.15f * n
            }
            else -> {
                // 正常情况：标准权重
                0.2f * p + 0.6f * current + 0.2f * n
            }
        }
    }

    /**
     * 增强协同发音混合
     *
     * @param phase 当前音素内的播放进度 0.0-1.0
     * - 0.0：刚进入，前一音素影响大
     * - 0.5：中间，当前音素为主
     * - 1.0：即将退出，后一音素影响大
     *
     * 连续大开口音时增强前后权重，让过渡更明显
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

        // 检测是否是连续大开口音
        val isCurrentHighOpen = currentOpen >= 0.8f
        val isPrevHighOpen = (prev?.mouthOpen ?: 0f) >= 0.7f
        val isNextHighOpen = (next?.mouthOpen ?: 0f) >= 0.7f
        val isConsecutiveHighOpen = isCurrentHighOpen && (isPrevHighOpen || isNextHighOpen)

        // 动态权重：根据相位调整前后音素的影响
        // 连续大开口音时增强前后权重，让过渡更明显
        val maxNeighborWeight = when {
            isConsecutiveHighOpen -> 0.35f  // 连续大开口：增强过渡
            isCurrentHighOpen -> 0.2f       // 单独大开口：正常过渡
            else -> 0.3f                    // 其他情况
        }
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
