package com.example.scenic_avatar_guide_app.core.avatar.animation

import com.example.scenic_avatar_guide_app.domain.model.AvatarGesture

/**
 * 动作关键帧
 * @param durationMs 从上一帧到这一帧的持续时间
 * @param params 目标参数值
 * @param easing 缓动类型（默认使用更平滑的三次缓动）
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
         *
         * 缓动策略：
         * - 启动帧：EASE_OUT（平滑启动，无停顿感）
         * - 动作帧：EASE_IN_OUT（保持流畅）
         * - 结束帧：EASE_IN（平滑减速到静止）
         */
        val NOD = GestureAnimation(
            keyframes = listOf(
                // 初始状态 -> 预备（平滑启动）
                GestureKeyframe(200, GestureParams(angleY = -5f, bodyAngleY = -1f), EasingType.EASE_OUT_CUBIC),
                // 预备 -> 下低（流畅过渡）
                GestureKeyframe(300, GestureParams(angleY = 25f, bodyAngleY = 4f), EasingType.EASE_IN_OUT_CUBIC),
                // 下低 -> 回升
                GestureKeyframe(280, GestureParams(angleY = 8f, bodyAngleY = 1f), EasingType.EASE_IN_OUT_CUBIC),
                // 回升 -> 再次下低
                GestureKeyframe(300, GestureParams(angleY = 20f, bodyAngleY = 3f), EasingType.EASE_IN_OUT_CUBIC),
                // 再次下低 -> 回升
                GestureKeyframe(250, GestureParams(angleY = 5f, bodyAngleY = 1f), EasingType.EASE_OUT_CUBIC),
                // 回升 -> 完全回正（平滑减速）
                GestureKeyframe(350, GestureParams.IDLE, EasingType.EASE_IN_CUBIC)
            ),
            loopCount = 1
        )

        /**
         * 摇头动画
         * 自然的摇头：左右摆动，带轻微歪头
         *
         * 缓动策略：
         * - 启动帧：EASE_OUT（平滑启动）
         * - 中间摆动：EASE_IN_OUT（保持流畅）
         * - 结束帧：EASE_IN（平滑减速）
         */
        val SHAKE = GestureAnimation(
            keyframes = listOf(
                // 初始 -> 预备（平滑启动）
                GestureKeyframe(200, GestureParams(angleX = 5f), EasingType.EASE_OUT_CUBIC),
                // 预备 -> 向左
                GestureKeyframe(350, GestureParams(angleX = -15f, angleZ = 4f), EasingType.EASE_IN_OUT_CUBIC),
                // 向左 -> 经过中间
                GestureKeyframe(250, GestureParams(angleX = 0f, angleZ = 0f), EasingType.EASE_IN_OUT_CUBIC),
                // 中间 -> 向右
                GestureKeyframe(350, GestureParams(angleX = 15f, angleZ = -4f), EasingType.EASE_IN_OUT_CUBIC),
                // 向右 -> 经过中间
                GestureKeyframe(250, GestureParams(angleX = 0f, angleZ = 0f), EasingType.EASE_OUT_CUBIC),
                // 中间 -> 轻微向左
                GestureKeyframe(250, GestureParams(angleX = -8f, angleZ = 2f), EasingType.EASE_IN_OUT_CUBIC),
                // 向左 -> 完全回正（平滑减速）
                GestureKeyframe(300, GestureParams.IDLE, EasingType.EASE_IN_CUBIC)
            ),
            loopCount = 1
        )

        /**
         * 轻微点头（表示理解/认同）
         */
        val NOD_LIGHT = GestureAnimation(
            keyframes = listOf(
                GestureKeyframe(250, GestureParams(angleY = -3f), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(300, GestureParams(angleY = 12f, angleZ = 2f), EasingType.EASE_IN_OUT_CUBIC),
                GestureKeyframe(250, GestureParams(angleY = 3f), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(300, GestureParams.IDLE, EasingType.EASE_IN_CUBIC)
            ),
            loopCount = 1
        )

        /**
         * 强烈摇头（表示否定/拒绝）
         */
        val SHAKE_STRONG = GestureAnimation(
            keyframes = listOf(
                GestureKeyframe(250, GestureParams(angleX = 8f), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(300, GestureParams(angleX = -18f, angleZ = 6f), EasingType.EASE_IN_OUT_CUBIC),
                GestureKeyframe(250, GestureParams(angleX = 5f), EasingType.EASE_IN_OUT_CUBIC),
                GestureKeyframe(300, GestureParams(angleX = 16f, angleZ = -5f), EasingType.EASE_IN_OUT_CUBIC),
                GestureKeyframe(250, GestureParams(angleX = -5f), EasingType.EASE_IN_OUT_CUBIC),
                GestureKeyframe(300, GestureParams(angleX = 12f, angleZ = -3f), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(250, GestureParams(angleX = -4f), EasingType.EASE_IN_OUT_CUBIC),
                GestureKeyframe(350, GestureParams.IDLE, EasingType.EASE_IN_CUBIC)
            ),
            loopCount = 1
        )

        /**
         * 挥手致意（欢迎场景）
         * 自然的侧首致意：头部向左倾斜，轻微下低，然后回正
         */
        val WAVE = GestureAnimation(
            keyframes = listOf(
                GestureKeyframe(350, GestureParams(angleX = -10f, angleZ = -6f), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(400, GestureParams(angleX = -14f, angleY = 10f, angleZ = -10f, bodyAngleY = 3f), EasingType.EASE_IN_OUT_CUBIC),
                GestureKeyframe(350, GestureParams(angleX = -12f, angleY = 8f, angleZ = -9f, bodyAngleY = 2f), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(350, GestureParams(angleX = -6f, angleY = 3f, angleZ = -4f), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(350, GestureParams.IDLE, EasingType.EASE_IN_CUBIC)
            ),
            loopCount = 1
        )

        /**
         * 鞠躬/欠身（感谢、道歉场景）
         * 庄重的鞠躬：身体前倾 + 头部下低，节奏较慢
         */
        val BOW = GestureAnimation(
            keyframes = listOf(
                GestureKeyframe(350, GestureParams(angleY = 10f, bodyAngleY = 2f), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(500, GestureParams(angleY = 25f, angleZ = 4f, bodyAngleY = 8f, shoulder = 0.5f), EasingType.EASE_IN_OUT_CUBIC),
                GestureKeyframe(450, GestureParams(angleY = 23f, angleZ = 3f, bodyAngleY = 7f, shoulder = 0.4f), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(400, GestureParams(angleY = 12f, bodyAngleY = 4f, shoulder = 0.2f), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(350, GestureParams.IDLE, EasingType.EASE_IN_CUBIC)
            ),
            loopCount = 1
        )

        /**
         * 向左示意（引导游客向左）
         * 头部转向左侧 + 眼球跟随 + 身体微转
         */
        val POINT_LEFT = GestureAnimation(
            keyframes = listOf(
                GestureKeyframe(350, GestureParams(angleX = -15f, angleZ = 6f, eyeBallX = -0.4f), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(400, GestureParams(angleX = -22f, angleY = 2f, angleZ = 10f, bodyAngleX = -2f, eyeBallX = -0.8f), EasingType.EASE_IN_OUT_CUBIC),
                GestureKeyframe(450, GestureParams(angleX = -20f, angleZ = 9f, bodyAngleX = -1f, eyeBallX = -0.7f), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(350, GestureParams(angleX = -10f, angleZ = 4f, eyeBallX = -0.3f), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(350, GestureParams.IDLE, EasingType.EASE_IN_CUBIC)
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
                GestureKeyframe(350, GestureParams(angleX = 15f, angleZ = -6f, eyeBallX = 0.4f), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(400, GestureParams(angleX = 22f, angleY = 2f, angleZ = -10f, bodyAngleX = 2f, eyeBallX = 0.8f), EasingType.EASE_IN_OUT_CUBIC),
                GestureKeyframe(450, GestureParams(angleX = 20f, angleZ = -9f, bodyAngleX = 1f, eyeBallX = 0.7f), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(350, GestureParams(angleX = 10f, angleZ = -4f, eyeBallX = 0.3f), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(350, GestureParams.IDLE, EasingType.EASE_IN_CUBIC)
            ),
            loopCount = 1
        )

        /**
         * 向前示意/颔首（引导游客向前或指向某物）
         * 头部微前倾 + 眼球向下，表达"请看这里"
         */
        val POINT_FORWARD = GestureAnimation(
            keyframes = listOf(
                GestureKeyframe(300, GestureParams(angleY = -5f, angleZ = 3f), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(350, GestureParams(angleY = 12f, angleX = 4f, angleZ = -5f), EasingType.EASE_IN_OUT_CUBIC),
                GestureKeyframe(400, GestureParams(angleY = 22f, angleX = 8f, angleZ = -8f, eyeBallY = 0.4f), EasingType.EASE_IN_OUT_CUBIC),
                GestureKeyframe(450, GestureParams(angleY = 20f, angleX = 6f, angleZ = -7f, eyeBallY = 0.35f), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(350, GestureParams(angleY = 10f, angleX = 3f, angleZ = -3f, eyeBallY = 0.15f), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(350, GestureParams.IDLE, EasingType.EASE_IN_CUBIC)
            ),
            loopCount = 1
        )

        /**
         * 思考姿态（沉思场景）
         * 头部侧偏 + 微仰，表达思考状态
         */
        val THINKING_POSE = GestureAnimation(
            keyframes = listOf(
                GestureKeyframe(350, GestureParams(angleX = 6f, angleZ = 5f), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(400, GestureParams(angleX = 12f, angleY = -6f, angleZ = 12f), EasingType.EASE_IN_OUT_CUBIC),
                GestureKeyframe(450, GestureParams(angleX = 18f, angleY = -12f, angleZ = 20f, eyeBallX = 0.35f), EasingType.EASE_IN_OUT_CUBIC),
                GestureKeyframe(550, GestureParams(angleX = 16f, angleY = -10f, angleZ = 22f, eyeBallX = 0.3f), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(500, GestureParams(angleX = 19f, angleY = -13f, angleZ = 18f, eyeBallX = 0.4f), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(400, GestureParams(angleX = 10f, angleY = -6f, angleZ = 10f, eyeBallX = 0.15f), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(350, GestureParams.IDLE, EasingType.EASE_IN_CUBIC)
            ),
            loopCount = 1
        )

        /**
         * 引导姿态（路线指引场景）
         * 侧身 + 头部跟随，引导游客前往某方向
         */
        val GUIDE = GestureAnimation(
            keyframes = listOf(
                GestureKeyframe(350, GestureParams(angleX = 8f, angleZ = -4f), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(400, GestureParams(angleX = 15f, angleZ = -7f, bodyAngleX = 3f), EasingType.EASE_IN_OUT_CUBIC),
                GestureKeyframe(450, GestureParams(angleX = 22f, angleY = 10f, angleZ = -10f, bodyAngleX = 5f, bodyAngleY = 4f, eyeBallX = 0.55f, eyeBallY = 0.25f), EasingType.EASE_IN_OUT_CUBIC),
                GestureKeyframe(500, GestureParams(angleX = 20f, angleY = 8f, angleZ = -9f, bodyAngleX = 4f, eyeBallX = 0.45f, eyeBallY = 0.2f), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(400, GestureParams(angleX = 10f, angleZ = -4f, bodyAngleX = 2f, eyeBallX = 0.2f), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(350, GestureParams.IDLE, EasingType.EASE_IN_CUBIC)
            ),
            loopCount = 1
        )

        /**
         * 仰望姿态（观看高大景物场景）
         * 头部上仰 + 眼球向上，表达惊叹和敬畏
         */
        val LOOK_UP = GestureAnimation(
            keyframes = listOf(
                GestureKeyframe(350, GestureParams(angleY = 5f), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(400, GestureParams(angleY = -18f, eyeBallY = 0.45f), EasingType.EASE_IN_OUT_CUBIC),
                GestureKeyframe(450, GestureParams(angleY = -35f, angleZ = 10f, bodyAngleY = -10f, eyeBallY = 0.95f), EasingType.EASE_IN_OUT_CUBIC),
                GestureKeyframe(550, GestureParams(angleY = -32f, angleZ = 8f, bodyAngleY = -8f, eyeBallY = 0.9f), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(400, GestureParams(angleY = -28f, angleZ = 7f, bodyAngleY = -6f, eyeBallY = 0.75f), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(400, GestureParams(angleY = -15f, eyeBallY = 0.35f), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(350, GestureParams.IDLE, EasingType.EASE_IN_CUBIC)
            ),
            loopCount = 1
        )

        /**
         * 聆听姿态（倾听用户说话场景）
         * 头部侧偏前倾，表达专注聆听
         */
        val LISTEN = GestureAnimation(
            keyframes = listOf(
                GestureKeyframe(350, GestureParams(angleX = 6f, angleZ = -5f), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(400, GestureParams(angleX = 14f, angleZ = -10f), EasingType.EASE_IN_OUT_CUBIC),
                GestureKeyframe(450, GestureParams(angleX = 18f, angleY = 14f, angleZ = -16f, eyeBallX = -0.35f, eyeBallY = 0.18f), EasingType.EASE_IN_OUT_CUBIC),
                GestureKeyframe(550, GestureParams(angleX = 16f, angleY = 12f, angleZ = -14f), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(500, GestureParams(angleX = 19f, angleY = 15f, angleZ = -17f, eyeBallX = -0.3f), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(400, GestureParams(angleX = 8f, angleY = 6f, angleZ = -7f, eyeBallX = -0.15f), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(350, GestureParams.IDLE, EasingType.EASE_IN_CUBIC)
            ),
            loopCount = 1
        )

        /**
         * 欢迎手势（热情欢迎场景）
         * 更热情的致意，配合点头
         */
        val WELCOME_GESTURE = GestureAnimation(
            keyframes = listOf(
                GestureKeyframe(350, GestureParams(angleY = 5f, bodyAngleY = 1f), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(400, GestureParams(angleX = -10f, angleZ = -8f, bodyAngleY = 3f), EasingType.EASE_IN_OUT_CUBIC),
                GestureKeyframe(450, GestureParams(angleX = -16f, angleY = 12f, angleZ = -14f, bodyAngleY = 5f), EasingType.EASE_IN_OUT_CUBIC),
                GestureKeyframe(400, GestureParams(angleX = -14f, angleY = 20f, angleZ = -12f, bodyAngleY = 4f), EasingType.EASE_IN_OUT_CUBIC),
                GestureKeyframe(350, GestureParams(angleX = -12f, angleY = 10f, angleZ = -10f), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(400, GestureParams(angleX = -10f, angleY = 16f, angleZ = -9f), EasingType.EASE_IN_OUT_CUBIC),
                GestureKeyframe(350, GestureParams(angleX = -5f, angleZ = -4f), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(350, GestureParams.IDLE, EasingType.EASE_IN_CUBIC)
            ),
            loopCount = 1
        )

        // ==================== 待机动画系列 ====================
        // 参考 Hiyori 模型的官方 Idle 动作设计
        // 多个变体随机播放，增加自然感
        // 待机动画使用柔和的缓动曲线

        /**
         * 待机动画 1：左顾右盼
         * 头部左右转动，带身体跟随
         */
        val IDLE_1 = GestureAnimation(
            keyframes = listOf(
                GestureKeyframe(500, GestureParams(angleX = 5f, angleZ = -3f, breath = 0.3f), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(1000, GestureParams(
                    angleX = -18f, angleY = 0f, angleZ = 8f,
                    bodyAngleX = 2f, bodyAngleY = 0f, bodyAngleZ = 3f,
                    eyeBallX = -0.31f, eyeBallY = 0.21f,
                    breath = 0.5f
                ), EasingType.EASE_IN_OUT_CUBIC),
                GestureKeyframe(400, GestureParams(
                    angleX = -18f, angleY = 0f, angleZ = 8f,
                    bodyAngleX = 2f, bodyAngleY = 0f, bodyAngleZ = 3f,
                    eyeBallX = -0.31f, eyeBallY = 0.21f,
                    eyeOpen = 0f,
                    breath = 0.8f
                ), EasingType.EASE_IN_OUT_CUBIC),
                GestureKeyframe(300, GestureParams(
                    angleX = -18f, angleY = 0f, angleZ = 8f,
                    bodyAngleX = 2f, bodyAngleY = 0f, bodyAngleZ = 3f,
                    eyeBallX = -0.31f, eyeBallY = 0.21f,
                    eyeOpen = 1f,
                    breath = 1f
                ), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(600, GestureParams(
                    angleX = 0f, angleZ = 0f,
                    bodyAngleX = 0f,
                    eyeBallX = 0f, eyeBallY = 0.15f,
                    breath = 0.6f
                ), EasingType.EASE_IN_OUT_CUBIC),
                GestureKeyframe(1000, GestureParams(
                    angleX = 18f, angleY = 0f, angleZ = -8f,
                    bodyAngleX = -2f, bodyAngleY = 0f, bodyAngleZ = -3f,
                    eyeBallX = 0.31f, eyeBallY = 0.21f,
                    breath = 0.5f
                ), EasingType.EASE_IN_OUT_CUBIC),
                GestureKeyframe(800, GestureParams(
                    angleX = 0f, angleY = 0f, angleZ = 0f,
                    bodyAngleX = 0f, bodyAngleY = 0f, bodyAngleZ = 0f,
                    eyeBallX = 0f, eyeBallY = 0.1f,
                    breath = 0f
                ), EasingType.EASE_IN_CUBIC)
            ),
            loopCount = 1
        )

        /**
         * 待机动画 2：点头微笑
         * 轻微点头，眼睛微笑
         */
        val IDLE_2 = GestureAnimation(
            keyframes = listOf(
                GestureKeyframe(500, GestureParams(angleY = -6f, bodyAngleY = -2f, breath = 0.3f), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(800, GestureParams(
                    angleX = 0f, angleY = -10f, angleZ = 0f,
                    bodyAngleX = 0f, bodyAngleY = -3f,
                    eyeBallX = 0f, eyeBallY = 0.1f,
                    eyeSmile = 0.2f,
                    breath = 0.5f
                ), EasingType.EASE_IN_OUT_CUBIC),
                GestureKeyframe(600, GestureParams(
                    angleX = 0f, angleY = 15f, angleZ = 0f,
                    bodyAngleX = 0f, bodyAngleY = 4f,
                    eyeBallX = 0f, eyeBallY = 0.2f,
                    eyeSmile = 0.5f,
                    breath = 0.8f
                ), EasingType.EASE_IN_OUT_CUBIC),
                GestureKeyframe(500, GestureParams(
                    angleX = 0f, angleY = 10f, angleZ = 0f,
                    bodyAngleX = 0f, bodyAngleY = 3f,
                    eyeBallX = 0f, eyeBallY = 0.15f,
                    eyeOpen = 0.3f,
                    eyeSmile = 0.7f,
                    breath = 1f
                ), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(700, GestureParams(
                    angleX = 0f, angleY = 3f, angleZ = 0f,
                    bodyAngleX = 0f, bodyAngleY = 1f,
                    eyeBallX = 0f, eyeBallY = 0.1f,
                    eyeSmile = 0.3f,
                    breath = 0.5f
                ), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(600, GestureParams.IDLE, EasingType.EASE_IN_CUBIC)
            ),
            loopCount = 1
        )

        /**
         * 待机动画 3：歪头思考
         * 头部歪斜，眼球移动
         */
        val IDLE_3 = GestureAnimation(
            keyframes = listOf(
                GestureKeyframe(500, GestureParams(angleX = -5f, angleZ = 5f, breath = 0.3f), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(1000, GestureParams(
                    angleX = 10f, angleY = -5f, angleZ = 15f,
                    bodyAngleX = -1f, bodyAngleY = 0f, bodyAngleZ = -4f,
                    eyeBallX = 0.2f, eyeBallY = 0.38f,
                    browY = 0.1f,
                    breath = 0.5f
                ), EasingType.EASE_IN_OUT_CUBIC),
                GestureKeyframe(400, GestureParams(
                    angleX = 10f, angleY = -5f, angleZ = 15f,
                    bodyAngleX = -1f, bodyAngleY = 0f, bodyAngleZ = -4f,
                    eyeBallX = 0.2f, eyeBallY = 0.38f,
                    eyeOpen = 0f,
                    browY = 0.1f,
                    breath = 0.8f
                ), EasingType.EASE_IN_OUT_CUBIC),
                GestureKeyframe(350, GestureParams(
                    angleX = 10f, angleY = -5f, angleZ = 15f,
                    bodyAngleX = -1f, bodyAngleY = 0f, bodyAngleZ = -4f,
                    eyeBallX = 0.2f, eyeBallY = 0.38f,
                    eyeOpen = 1f,
                    browY = 0.1f,
                    breath = 1f
                ), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(700, GestureParams(
                    angleX = 0f, angleZ = 0f,
                    bodyAngleX = 0f,
                    eyeBallX = 0f, eyeBallY = 0.2f,
                    browY = 0f,
                    breath = 0.6f
                ), EasingType.EASE_IN_OUT_CUBIC),
                GestureKeyframe(1000, GestureParams(
                    angleX = -10f, angleY = -5f, angleZ = -15f,
                    bodyAngleX = 1f, bodyAngleY = 0f, bodyAngleZ = 4f,
                    eyeBallX = -0.2f, eyeBallY = 0.38f,
                    browY = 0f,
                    breath = 0.5f
                ), EasingType.EASE_IN_OUT_CUBIC),
                GestureKeyframe(800, GestureParams(
                    angleX = 0f, angleY = 0f, angleZ = 0f,
                    bodyAngleX = 0f, bodyAngleY = 0f, bodyAngleZ = 0f,
                    eyeBallX = 0f, eyeBallY = 0.1f,
                    breath = 0f
                ), EasingType.EASE_IN_CUBIC)
            ),
            loopCount = 1
        )

        /**
         * 待机动画 4：深呼吸
         * 身体起伏，肩膀运动
         */
        val IDLE_4 = GestureAnimation(
            keyframes = listOf(
                GestureKeyframe(500, GestureParams(angleY = 3f, breath = 0.2f), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(900, GestureParams(
                    angleX = 0f, angleY = -5f, angleZ = 0f,
                    bodyAngleX = 0f, bodyAngleY = -3f, bodyAngleZ = 0f,
                    shoulder = -0.3f,
                    breath = 0.6f
                ), EasingType.EASE_IN_OUT_CUBIC),
                GestureKeyframe(800, GestureParams(
                    angleX = 0f, angleY = -10f, angleZ = 0f,
                    bodyAngleX = 0f, bodyAngleY = -6f, bodyAngleZ = 0f,
                    shoulder = -0.6f,
                    breath = 1f
                ), EasingType.EASE_IN_OUT_CUBIC),
                GestureKeyframe(700, GestureParams(
                    angleX = 0f, angleY = -10f, angleZ = 0f,
                    bodyAngleX = 0f, bodyAngleY = -6f, bodyAngleZ = 0f,
                    shoulder = -0.6f,
                    breath = 1f
                ), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(900, GestureParams(
                    angleX = 0f, angleY = 0f, angleZ = 0f,
                    bodyAngleX = 0f, bodyAngleY = 0f, bodyAngleZ = 0f,
                    shoulder = 0f,
                    breath = 0.4f
                ), EasingType.EASE_IN_OUT_CUBIC),
                GestureKeyframe(800, GestureParams(
                    angleX = 0f, angleY = 5f, angleZ = 0f,
                    bodyAngleX = 0f, bodyAngleY = 3f, bodyAngleZ = 0f,
                    shoulder = 0.3f,
                    breath = 0f
                ), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(700, GestureParams.IDLE, EasingType.EASE_IN_CUBIC)
            ),
            loopCount = 1
        )

        /**
         * 待机动画 5：轻微晃动
         * 非常轻微的动作，作为过渡
         */
        val IDLE_5 = GestureAnimation(
            keyframes = listOf(
                GestureKeyframe(600, GestureParams(breath = 0.3f), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(1400, GestureParams(
                    angleX = -5f, angleY = 0f, angleZ = 3f,
                    bodyAngleX = 1f, bodyAngleY = 0f,
                    eyeBallX = -0.1f, eyeBallY = 0.05f,
                    breath = 0.6f
                ), EasingType.EASE_IN_OUT_CUBIC),
                GestureKeyframe(700, GestureParams(
                    angleX = -2f, angleZ = 1f,
                    eyeBallX = -0.05f,
                    breath = 0.4f
                ), EasingType.EASE_OUT_CUBIC),
                GestureKeyframe(900, GestureParams(
                    angleX = 0f, angleY = 0f, angleZ = 0f,
                    bodyAngleX = 0f, bodyAngleY = 0f,
                    eyeBallX = 0f, eyeBallY = 0.05f,
                    breath = 0f
                ), EasingType.EASE_IN_CUBIC)
            ),
            loopCount = 1
        )

        /**
         * 所有待机动画列表
         */
        val IDLE_ANIMATIONS = listOf(IDLE_1, IDLE_2, IDLE_3, IDLE_4, IDLE_5)

        /**
         * 随机获取一个待机动画
         */
        fun randomIdle(): GestureAnimation = IDLE_ANIMATIONS.random()

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
            AvatarGesture.IDLE -> randomIdle()  // 随机选择待机动画
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
