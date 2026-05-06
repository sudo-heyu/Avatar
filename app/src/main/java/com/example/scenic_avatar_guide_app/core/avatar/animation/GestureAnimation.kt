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
         * 自然的摇头：左右摆动，带轻微歪头（幅度已调低，更克制自然）
         */
        val SHAKE = GestureAnimation(
            keyframes = listOf(
                // 初始状态
                GestureKeyframe(0, GestureParams.IDLE),
                // 向左
                GestureKeyframe(200, GestureParams(angleX = -12f, angleZ = 3f)),
                // 向右
                GestureKeyframe(250, GestureParams(angleX = 12f, angleZ = -3f)),
                // 再向左
                GestureKeyframe(250, GestureParams(angleX = -10f, angleZ = 2.5f)),
                // 再向右
                GestureKeyframe(220, GestureParams(angleX = 8f, angleZ = -2f)),
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
                GestureKeyframe(180, GestureParams(angleX = -15f, angleZ = 5f)),
                GestureKeyframe(220, GestureParams(angleX = 15f, angleZ = -5f)),
                GestureKeyframe(220, GestureParams(angleX = -14f, angleZ = 4.5f)),
                GestureKeyframe(200, GestureParams(angleX = 12f, angleZ = -4f)),
                GestureKeyframe(180, GestureParams(angleX = -8f, angleZ = 2.5f)),
                GestureKeyframe(200, GestureParams.IDLE)
            ),
            loopCount = 1
        )

        /**
         * 挥手致意（欢迎场景）
         * 自然的侧首致意：头部向左倾斜，轻微下低，然后回正
         * 配合身体微转，表达欢迎姿态
         */
        val WAVE = GestureAnimation(
            keyframes = listOf(
                // 初始状态
                GestureKeyframe(0, GestureParams.IDLE),
                // 开始侧首（头向左倾斜）
                GestureKeyframe(180, GestureParams(angleX = -12f, angleZ = -8f)),
                // 加深致意（微低头 + 身体前倾）
                GestureKeyframe(220, GestureParams(angleX = -15f, angleY = 8f, angleZ = -12f, bodyAngleY = 2f)),
                // 保持姿态
                GestureKeyframe(200, GestureParams(angleX = -14f, angleY = 6f, angleZ = -10f, bodyAngleY = 2f)),
                // 开始回正
                GestureKeyframe(180, GestureParams(angleX = -8f, angleZ = -5f)),
                // 完全回正
                GestureKeyframe(200, GestureParams.IDLE)
            ),
            loopCount = 1
        )

        /**
         * 鞠躬/欠身（感谢、道歉场景）
         * 庄重的鞠躬：身体前倾 + 头部下低，节奏较慢
         * 表达敬意或歉意
         */
        val BOW = GestureAnimation(
            keyframes = listOf(
                // 初始状态
                GestureKeyframe(0, GestureParams.IDLE),
                // 开始前倾（预备）
                GestureKeyframe(200, GestureParams(angleY = 12f, bodyAngleY = 2f)),
                // 鞠躬（头部下低 + 身体前倾 + 肩膀微耸）
                GestureKeyframe(350, GestureParams(angleY = 28f, angleZ = 5f, bodyAngleY = 6f, shoulder = 0.5f)),
                // 保持鞠躬姿态
                GestureKeyframe(300, GestureParams(angleY = 26f, angleZ = 4f, bodyAngleY = 5f, shoulder = 0.4f)),
                // 开始起身
                GestureKeyframe(250, GestureParams(angleY = 15f, bodyAngleY = 3f, shoulder = 0.2f)),
                // 完全回正
                GestureKeyframe(200, GestureParams.IDLE)
            ),
            loopCount = 1
        )

        /**
         * 向左示意（引导游客向左）
         * 头部转向左侧 + 眼球跟随 + 身体微转
         * 表达"请往这边看"
         */
        val POINT_LEFT = GestureAnimation(
            keyframes = listOf(
                // 初始状态
                GestureKeyframe(0, GestureParams.IDLE),
                // 开始转向左侧
                GestureKeyframe(180, GestureParams(angleX = -18f, angleZ = 8f, eyeBallX = -0.6f)),
                // 加深转向（眼球完全向左）
                GestureKeyframe(220, GestureParams(angleX = -26f, angleY = 3f, angleZ = 12f, bodyAngleX = -3f, eyeBallX = -1.0f)),
                // 保持示意姿态
                GestureKeyframe(300, GestureParams(angleX = -24f, angleZ = 11f, bodyAngleX = -2f, eyeBallX = -0.9f)),
                // 开始回正
                GestureKeyframe(200, GestureParams(angleX = -12f, angleZ = 5f, eyeBallX = -0.4f)),
                // 完全回正
                GestureKeyframe(180, GestureParams.IDLE)
            ),
            loopCount = 1
        )

        /**
         * 向右示意（引导游客向右）
         * 头部转向右侧 + 眼球跟随 + 身体微转
         * 与 POINT_LEFT 镜像对称
         */
        val POINT_RIGHT = GestureAnimation(
            keyframes = listOf(
                // 初始状态
                GestureKeyframe(0, GestureParams.IDLE),
                // 开始转向右侧
                GestureKeyframe(180, GestureParams(angleX = 18f, angleZ = -8f, eyeBallX = 0.6f)),
                // 加深转向（眼球完全向右）
                GestureKeyframe(220, GestureParams(angleX = 26f, angleY = 3f, angleZ = -12f, bodyAngleX = 3f, eyeBallX = 1.0f)),
                // 保持示意姿态
                GestureKeyframe(300, GestureParams(angleX = 24f, angleZ = -11f, bodyAngleX = 2f, eyeBallX = 0.9f)),
                // 开始回正
                GestureKeyframe(200, GestureParams(angleX = 12f, angleZ = -5f, eyeBallX = 0.4f)),
                // 完全回正
                GestureKeyframe(180, GestureParams.IDLE)
            ),
            loopCount = 1
        )

        /**
         * 向前示意/颔首（引导游客向前或指向某物）
         * 头部微前倾 + 眼球向下，表达"请看这里"
         */
        val POINT_FORWARD = GestureAnimation(
            keyframes = listOf(
                // 初始状态
                GestureKeyframe(0, GestureParams.IDLE),
                // 开始前倾
                GestureKeyframe(150, GestureParams(angleY = 15f, angleX = 5f, angleZ = -4f)),
                // 颔首示意（眼球向下看）
                GestureKeyframe(200, GestureParams(angleY = 22f, angleX = 8f, angleZ = -6f, eyeBallY = 0.3f)),
                // 保持示意
                GestureKeyframe(250, GestureParams(angleY = 20f, angleX = 6f, angleZ = -5f, eyeBallY = 0.25f)),
                // 开始回正
                GestureKeyframe(180, GestureParams(angleY = 10f, angleX = 3f)),
                // 完全回正
                GestureKeyframe(150, GestureParams.IDLE)
            ),
            loopCount = 1
        )

        /**
         * 思考姿态（沉思场景）
         * 头部侧偏 + 微仰，表达思考状态
         * 这是一个姿态性动作，保持时间较长
         */
        val THINKING_POSE = GestureAnimation(
            keyframes = listOf(
                // 初始状态
                GestureKeyframe(0, GestureParams.IDLE),
                // 开始偏转
                GestureKeyframe(200, GestureParams(angleX = 12f, angleZ = 10f)),
                // 进入思考姿态（侧偏 + 微仰）
                GestureKeyframe(300, GestureParams(angleX = 18f, angleY = -10f, angleZ = 18f, eyeBallX = 0.3f)),
                // 保持思考（轻微晃动模拟自然感）
                GestureKeyframe(400, GestureParams(angleX = 16f, angleY = -8f, angleZ = 20f)),
                // 继续保持
                GestureKeyframe(350, GestureParams(angleX = 19f, angleY = -11f, angleZ = 17f)),
                // 开始回正
                GestureKeyframe(250, GestureParams(angleX = 10f, angleY = -5f, angleZ = 8f)),
                // 完全回正
                GestureKeyframe(200, GestureParams.IDLE)
            ),
            loopCount = 1
        )

        /**
         * 引导姿态（路线指引场景）
         * 侧身 + 头部跟随，引导游客前往某方向
         * 表达"请跟我来"
         */
        val GUIDE = GestureAnimation(
            keyframes = listOf(
                // 初始状态
                GestureKeyframe(0, GestureParams.IDLE),
                // 开始侧转
                GestureKeyframe(180, GestureParams(angleX = 15f, angleZ = -6f, bodyAngleX = 3f)),
                // 引导姿态（侧身 + 头部跟随 + 眼球引导）
                GestureKeyframe(250, GestureParams(angleX = 22f, angleY = 8f, angleZ = -8f, bodyAngleX = 5f, bodyAngleY = 3f, eyeBallX = 0.5f, eyeBallY = 0.2f)),
                // 保持引导
                GestureKeyframe(350, GestureParams(angleX = 20f, angleY = 6f, angleZ = -7f, bodyAngleX = 4f, eyeBallX = 0.4f)),
                // 开始回正
                GestureKeyframe(220, GestureParams(angleX = 10f, angleZ = -3f, bodyAngleX = 2f, eyeBallX = 0.2f)),
                // 完全回正
                GestureKeyframe(200, GestureParams.IDLE)
            ),
            loopCount = 1
        )

        /**
         * 仰望姿态（观看高大景物场景）
         * 头部上仰 + 眼球向上，表达惊叹和敬畏
         * 用于观看大佛、高塔等场景
         */
        val LOOK_UP = GestureAnimation(
            keyframes = listOf(
                // 初始状态
                GestureKeyframe(0, GestureParams.IDLE),
                // 开始抬头
                GestureKeyframe(200, GestureParams(angleY = -20f, eyeBallY = 0.5f)),
                // 仰望姿态（头部上仰 + 身体微后仰 + 眼球向上）
                GestureKeyframe(280, GestureParams(angleY = -32f, angleZ = 8f, bodyAngleY = -8f, eyeBallY = 0.9f)),
                // 保持仰望（表达敬畏）
                GestureKeyframe(350, GestureParams(angleY = -30f, angleZ = 7f, bodyAngleY = -7f, eyeBallY = 0.85f)),
                // 轻微点头（表示震撼）
                GestureKeyframe(200, GestureParams(angleY = -25f, angleZ = 6f, bodyAngleY = -5f, eyeBallY = 0.7f)),
                // 开始回正
                GestureKeyframe(220, GestureParams(angleY = -12f, eyeBallY = 0.3f)),
                // 完全回正
                GestureKeyframe(180, GestureParams.IDLE)
            ),
            loopCount = 1
        )

        /**
         * 聆听姿态（倾听用户说话场景）
         * 头部侧偏前倾，表达专注聆听
         * 表达"我在认真听您说"
         */
        val LISTEN = GestureAnimation(
            keyframes = listOf(
                // 初始状态
                GestureKeyframe(0, GestureParams.IDLE),
                // 开始侧转
                GestureKeyframe(150, GestureParams(angleX = 12f, angleZ = -8f)),
                // 聆听姿态（侧偏 + 前倾 + 眼球专注）
                GestureKeyframe(200, GestureParams(angleX = 18f, angleY = 12f, angleZ = -14f, eyeBallX = -0.3f, eyeBallY = 0.15f)),
                // 保持聆听（轻微晃动表示在听）
                GestureKeyframe(350, GestureParams(angleX = 16f, angleY = 11f, angleZ = -12f)),
                // 继续保持
                GestureKeyframe(300, GestureParams(angleX = 19f, angleY = 13f, angleZ = -15f)),
                // 开始回正
                GestureKeyframe(200, GestureParams(angleX = 8f, angleY = 5f, angleZ = -6f)),
                // 完全回正
                GestureKeyframe(150, GestureParams.IDLE)
            ),
            loopCount = 1
        )

        /**
         * 欢迎手势（热情欢迎场景）
         * 更热情的致意，配合点头
         * 用于初次见面或重要欢迎场合
         */
        val WELCOME_GESTURE = GestureAnimation(
            keyframes = listOf(
                // 初始状态
                GestureKeyframe(0, GestureParams.IDLE),
                // 开始致意
                GestureKeyframe(160, GestureParams(angleX = -10f, angleZ = -8f, bodyAngleY = 2f)),
                // 加深欢迎姿态
                GestureKeyframe(200, GestureParams(angleX = -16f, angleY = 10f, angleZ = -14f, bodyAngleY = 4f)),
                // 配合点头（第一次）
                GestureKeyframe(180, GestureParams(angleX = -14f, angleY = 18f, angleZ = -12f, bodyAngleY = 3f)),
                // 抬头
                GestureKeyframe(160, GestureParams(angleX = -12f, angleY = 8f, angleZ = -10f)),
                // 再次点头（第二次，更轻）
                GestureKeyframe(180, GestureParams(angleX = -10f, angleY = 14f, angleZ = -8f)),
                // 开始回正
                GestureKeyframe(200, GestureParams(angleX = -6f, angleZ = -4f)),
                // 完全回正
                GestureKeyframe(180, GestureParams.IDLE)
            ),
            loopCount = 1
        )

        /**
         * 根据动作类型获取动画
         */
        fun fromGesture(gesture: AvatarGesture): GestureAnimation? = when (gesture) {
            AvatarGesture.NOD -> NOD
            AvatarGesture.SHAKE -> SHAKE
            AvatarGesture.WAVE -> WAVE
            AvatarGesture.BOW -> BOW
            AvatarGesture.POINT_LEFT -> POINT_LEFT
            AvatarGesture.POINT_RIGHT -> POINT_RIGHT
            AvatarGesture.POINT_FORWARD -> POINT_FORWARD
            AvatarGesture.THINKING_POSE -> THINKING_POSE
            AvatarGesture.GUIDE -> GUIDE
            AvatarGesture.LOOK_UP -> LOOK_UP
            AvatarGesture.LISTEN -> LISTEN
            AvatarGesture.WELCOME_GESTURE -> WELCOME_GESTURE
            AvatarGesture.IDLE -> null
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
