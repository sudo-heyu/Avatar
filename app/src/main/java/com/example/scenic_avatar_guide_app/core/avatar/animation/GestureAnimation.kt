package com.example.scenic_avatar_guide_app.core.avatar.animation

import com.example.scenic_avatar_guide_app.domain.model.AvatarGesture

/**
 * 动作关键帧
 * @param durationMs 从上一帧到这一帧的持续时间
 * @param params 目标参数值
 * @param easing 缓动类型
 */
data class GestureKeyframe(
    val durationMs: Long,
    val params: GestureParams,
    val easing: EasingType = EasingType.EASE_IN_OUT_CUBIC
)

/**
 * 动作动画定义
 * @param keyframes 关键帧列表（第一帧的 durationMs 表示初始状态延迟）
 * @param loopCount 循环次数（1 = 播放一次，-1 = 无限循环）
 */
data class GestureAnimation(
    val keyframes: List<GestureKeyframe>,
    val loopCount: Int = 1
) {
    companion object {
        /**
         * 点头动画
         * 自然的点头：两次下低，带身体前倾
         */
        val NOD = GestureAnimation(
            keyframes = listOf(
                // 初始状态
                GestureKeyframe(0, GestureParams.IDLE),
                // 下低（主要动作）
                GestureKeyframe(200, GestureParams(angleY = 30f, bodyAngleY = 5f)),
                // 回升
                GestureKeyframe(160, GestureParams(angleY = 10f, bodyAngleY = 2f)),
                // 再次下低（第二次点头）
                GestureKeyframe(200, GestureParams(angleY = 28f, bodyAngleY = 4f)),
                // 回到中间
                GestureKeyframe(240, GestureParams.IDLE)
            ),
            loopCount = 1
        )

        /**
         * 摇头动画
         * 自然的摇头：左右摆动，带轻微歪头
         */
        val SHAKE = GestureAnimation(
            keyframes = listOf(
                // 初始状态
                GestureKeyframe(0, GestureParams.IDLE),
                // 向左
                GestureKeyframe(200, GestureParams(angleX = -18f, angleZ = 5f)),
                // 向右
                GestureKeyframe(250, GestureParams(angleX = 18f, angleZ = -5f)),
                // 再向左
                GestureKeyframe(250, GestureParams(angleX = -15f, angleZ = 4f)),
                // 再向右
                GestureKeyframe(220, GestureParams(angleX = 12f, angleZ = -3f)),
                // 回到中间
                GestureKeyframe(180, GestureParams.IDLE)
            ),
            loopCount = 1
        )

        /**
         * 轻微点头（表示理解/认同）
         */
        val NOD_LIGHT = GestureAnimation(
            keyframes = listOf(
                GestureKeyframe(0, GestureParams.IDLE),
                GestureKeyframe(150, GestureParams(angleY = 12f, angleZ = 2f)),
                GestureKeyframe(180, GestureParams.IDLE)
            ),
            loopCount = 1
        )

        /**
         * 强烈摇头（表示否定/拒绝）
         */
        val SHAKE_STRONG = GestureAnimation(
            keyframes = listOf(
                GestureKeyframe(0, GestureParams.IDLE),
                GestureKeyframe(180, GestureParams(angleX = -22f, angleZ = 8f)),
                GestureKeyframe(220, GestureParams(angleX = 22f, angleZ = -8f)),
                GestureKeyframe(220, GestureParams(angleX = -20f, angleZ = 7f)),
                GestureKeyframe(200, GestureParams(angleX = 18f, angleZ = -6f)),
                GestureKeyframe(180, GestureParams(angleX = -12f, angleZ = 4f)),
                GestureKeyframe(200, GestureParams.IDLE)
            ),
            loopCount = 1
        )

        /**
         * 根据动作类型获取动画
         */
        fun fromGesture(gesture: AvatarGesture): GestureAnimation? = when (gesture) {
            AvatarGesture.NOD -> NOD
            AvatarGesture.SHAKE -> SHAKE
            else -> null
        }
    }

    /**
     * 获取总时长
     */
    fun getTotalDurationMs(): Long {
        return keyframes.sumOf { it.durationMs }
    }
}

/**
 * 动作动画播放器
 * 负责播放关键帧动画
 */
class GestureAnimationPlayer {

    private var currentAnimation: GestureAnimation? = null
    private var currentKeyframeIndex: Int = 0
    private var keyframeStartTime: Long = 0
    private var currentLoop: Int = 0
    private var isPlaying: Boolean = false
    private var currentParams: GestureParams = GestureParams.IDLE

    // 动画开始时的参数（用于第一帧插值）
    private var startParams: GestureParams = GestureParams.IDLE

    /**
     * 开始播放动画
     */
    fun play(animation: GestureAnimation, fromParams: GestureParams = GestureParams.IDLE) {
        currentAnimation = animation
        currentKeyframeIndex = 0
        keyframeStartTime = System.currentTimeMillis()
        currentLoop = 0
        isPlaying = true
        startParams = fromParams
        currentParams = fromParams
    }

    /**
     * 停止播放
     */
    fun stop() {
        isPlaying = false
        currentAnimation = null
    }

    /**
     * 是否正在播放
     */
    fun isPlaying(): Boolean = isPlaying

    /**
     * 获取当前参数
     */
    fun getCurrentParams(): GestureParams = currentParams

    /**
     * 更新动画帧
     * @return 是否需要继续更新
     */
    fun update(): Boolean {
        val animation = currentAnimation ?: return false
        if (!isPlaying) return false

        val now = System.currentTimeMillis()
        val keyframes = animation.keyframes
        val currentKeyframe = keyframes[currentKeyframeIndex]

        // 计算当前关键帧的进度
        val elapsed = now - keyframeStartTime
        val duration = currentKeyframe.durationMs

        if (duration <= 0) {
            // 第一帧（延迟为0），立即进入下一帧
            currentParams = currentKeyframe.params
            advanceKeyframe()
            return isPlaying
        }

        val progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
        val easedProgress = currentKeyframe.easing.apply(progress)

        // 计算插值参数
        val prevParams = if (currentKeyframeIndex == 0) startParams else keyframes[currentKeyframeIndex - 1].params
        currentParams = prevParams.lerp(currentKeyframe.params, easedProgress)

        // 检查是否需要进入下一帧
        if (progress >= 1f) {
            currentParams = currentKeyframe.params
            advanceKeyframe()
        }

        return isPlaying
    }

    private fun advanceKeyframe() {
        val animation = currentAnimation ?: return
        val keyframes = animation.keyframes

        if (currentKeyframeIndex < keyframes.size - 1) {
            // 进入下一帧
            currentKeyframeIndex++
            keyframeStartTime = System.currentTimeMillis()
        } else {
            // 当前循环结束
            if (animation.loopCount < 0 || currentLoop < animation.loopCount - 1) {
                // 继续循环
                currentLoop++
                currentKeyframeIndex = 0
                keyframeStartTime = System.currentTimeMillis()
            } else {
                // 动画结束
                isPlaying = false
            }
        }
    }
}
