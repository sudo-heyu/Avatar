package com.example.scenic_avatar_guide_app.core.avatar.animation

import com.example.scenic_avatar_guide_app.domain.model.AvatarGesture

/**
 * 动作参数配置
 */
data class GestureParams(
    val angleX: Float = 0f,
    val angleY: Float = 0f,
    val angleZ: Float = 0f,
    val bodyAngleX: Float = 0f,
    val bodyAngleY: Float = 0f,
    val bodyAngleZ: Float = 0f,
    val shoulder: Float = 0f
) {
    companion object {
        val IDLE = GestureParams()

        /**
         * 根据 AvatarGesture 获取目标参数
         */
        fun fromGesture(gesture: AvatarGesture): GestureParams = when (gesture) {
            AvatarGesture.IDLE -> IDLE
            AvatarGesture.NOD -> GestureParams(
                angleY = 30f, bodyAngleY = 5f
            )
            AvatarGesture.SHAKE -> GestureParams(
                angleX = -25f, angleZ = -20f
            )
            AvatarGesture.WAVE -> GestureParams(
                angleX = -20f, angleY = -10f, angleZ = -15f
            )
            AvatarGesture.POINT_LEFT -> GestureParams(
                angleX = -28f, angleY = 5f, angleZ = 12f
            )
            AvatarGesture.POINT_RIGHT -> GestureParams(
                angleX = 28f, angleY = 5f, angleZ = -12f
            )
            AvatarGesture.POINT_FORWARD -> GestureParams(
                angleY = 22f, angleX = 8f, angleZ = -8f
            )
            AvatarGesture.BOW -> GestureParams(
                angleY = 28f, angleZ = 8f, bodyAngleY = 3f, shoulder = 0.5f
            )
            AvatarGesture.THINKING_POSE -> GestureParams(
                angleX = 18f, angleY = -15f, angleZ = 22f
            )
            AvatarGesture.GUIDE -> GestureParams(
                angleX = 25f, angleY = 12f, angleZ = -10f
            )
            AvatarGesture.LOOK_UP -> GestureParams(
                angleY = -28f, angleZ = 12f
            )
            AvatarGesture.LISTEN -> GestureParams(
                angleX = 20f, angleY = 18f, angleZ = -18f
            )
            AvatarGesture.WELCOME_GESTURE -> GestureParams(
                angleX = -18f, angleY = 15f, angleZ = 15f
            )
        }
    }

    /**
     * 线性插值
     */
    fun lerp(target: GestureParams, t: Float): GestureParams {
        val clampedT = t.coerceIn(0f, 1f)
        return GestureParams(
            angleX = angleX + (target.angleX - angleX) * clampedT,
            angleY = angleY + (target.angleY - angleY) * clampedT,
            angleZ = angleZ + (target.angleZ - angleZ) * clampedT,
            bodyAngleX = bodyAngleX + (target.bodyAngleX - bodyAngleX) * clampedT,
            bodyAngleY = bodyAngleY + (target.bodyAngleY - bodyAngleY) * clampedT,
            bodyAngleZ = bodyAngleZ + (target.bodyAngleZ - bodyAngleZ) * clampedT,
            shoulder = shoulder + (target.shoulder - shoulder) * clampedT
        )
    }
}

/**
 * 动作过渡状态
 */
data class GestureTransition(
    val fromGesture: AvatarGesture,
    val toGesture: AvatarGesture,
    val fromParams: GestureParams,
    val toParams: GestureParams,
    val durationMs: Long,
    val startTimeMs: Long,
    val easingType: EasingType = EasingType.EASE_IN_OUT_CUBIC,
    val onComplete: (() -> Unit)? = null
)

/**
 * 动作过渡控制器
 * 负责管理动作参数的平滑过渡动画
 */
class GestureTransitionController {

    private var currentParams: GestureParams = GestureParams.IDLE
    private var currentGesture: AvatarGesture = AvatarGesture.IDLE
    private var activeTransition: GestureTransition? = null
    private var transitionProgress: Float = 0f

    /**
     * 获取当前参数值（应在渲染线程调用）
     */
    fun getCurrentParams(): GestureParams = currentParams

    /**
     * 获取当前动作
     */
    fun getCurrentGesture(): AvatarGesture = currentGesture

    /**
     * 是否正在过渡中
     */
    fun isTransitioning(): Boolean = activeTransition != null

    /**
     * 开始过渡到新动作
     * @param targetGesture 目标动作
     * @param durationMs 过渡时长（毫秒）
     * @param easing 缓动类型
     * @param onComplete 过渡完成回调
     */
    fun transitionTo(
        targetGesture: AvatarGesture,
        durationMs: Long = DEFAULT_TRANSITION_MS,
        easing: EasingType = EasingType.EASE_IN_OUT_CUBIC,
        onComplete: (() -> Unit)? = null
    ) {
        val now = System.currentTimeMillis()

        // 如果正在过渡，从当前插值位置继续
        val fromParams = currentParams

        // 如果目标相同，跳过
        if (targetGesture == currentGesture && activeTransition == null) {
            onComplete?.invoke()
            return
        }

        val targetParams = GestureParams.fromGesture(targetGesture)

        activeTransition = GestureTransition(
            fromGesture = currentGesture,
            toGesture = targetGesture,
            fromParams = fromParams,
            toParams = targetParams,
            durationMs = durationMs,
            startTimeMs = now,
            easingType = easing,
            onComplete = onComplete
        )

        currentGesture = targetGesture
        transitionProgress = 0f

        // 注意：currentParams 保持为 fromParams，让 update() 方法进行插值
        // 但为了让调用者能立即获取目标参数，提供一个方法获取目标参数
    }

    /**
     * 获取目标参数（过渡中的目标姿态）
     * 用于立即应用目标姿态，而不是等待过渡完成
     */
    fun getTargetParams(): GestureParams {
        return activeTransition?.toParams ?: currentParams
    }

    /**
     * 更新动画帧
     * @return 是否需要继续更新（true = 过渡中）
     */
    fun update(): Boolean {
        val transition = activeTransition ?: return false

        val elapsed = System.currentTimeMillis() - transition.startTimeMs
        transitionProgress = (elapsed.toFloat() / transition.durationMs).coerceIn(0f, 1f)

        // 应用缓动
        val easedProgress = transition.easingType.apply(transitionProgress)

        // 插值计算当前参数
        currentParams = transition.fromParams.lerp(transition.toParams, easedProgress)

        // 检查是否完成
        if (transitionProgress >= 1f) {
            currentParams = transition.toParams
            activeTransition = null
            transition.onComplete?.invoke()
            return false
        }

        return true
    }

    /**
     * 立即设置动作（无过渡）
     */
    fun setImmediate(gesture: AvatarGesture) {
        activeTransition = null
        currentGesture = gesture
        currentParams = GestureParams.fromGesture(gesture)
    }

    /**
     * 取消当前过渡
     */
    fun cancelTransition() {
        activeTransition?.onComplete?.invoke()
        activeTransition = null
    }

    companion object {
        const val DEFAULT_TRANSITION_MS = 300L
        const val FAST_TRANSITION_MS = 150L
        const val SLOW_TRANSITION_MS = 500L
    }
}

/**
 * 表情过渡控制器
 */
class ExpressionTransitionController {

    private var currentExpressionId: String = "neutral"
    private var currentIntensity: Float = 0.7f
    private var targetExpressionId: String = "neutral"
    private var targetIntensity: Float = 0.7f
    private var transitionProgress: Float = 1f
    private var transitionStartTime: Long = 0
    private var transitionDurationMs: Long = 0

    /**
     * 获取当前表情
     */
    fun getCurrentExpression(): Pair<String, Float> = Pair(currentExpressionId, currentIntensity)

    /**
     * 是否正在过渡
     */
    fun isTransitioning(): Boolean = transitionProgress < 1f

    /**
     * 开始过渡到新表情
     */
    fun transitionTo(
        expressionId: String,
        intensity: Float = 0.7f,
        durationMs: Long = 200
    ) {
        if (expressionId == currentExpressionId && intensity == currentIntensity && !isTransitioning()) {
            return
        }

        targetExpressionId = expressionId
        targetIntensity = intensity
        transitionProgress = 0f
        transitionStartTime = System.currentTimeMillis()
        transitionDurationMs = durationMs
    }

    /**
     * 更新动画帧
     * @return 是否需要继续更新
     */
    fun update(): Boolean {
        if (transitionProgress >= 1f) return false

        val elapsed = System.currentTimeMillis() - transitionStartTime
        transitionProgress = (elapsed.toFloat() / transitionDurationMs).coerceIn(0f, 1f)

        // 使用平滑缓动
        val easedProgress = EasingType.EASE_IN_OUT_CUBIC.apply(transitionProgress)

        // 插值强度
        currentIntensity = currentIntensity + (targetIntensity - currentIntensity) * easedProgress

        // 表情切换（表情ID不插值，但可以平滑过渡强度）
        if (transitionProgress >= 0.5f && currentExpressionId != targetExpressionId) {
            currentExpressionId = targetExpressionId
        }

        if (transitionProgress >= 1f) {
            currentExpressionId = targetExpressionId
            currentIntensity = targetIntensity
            return false
        }

        return true
    }

    /**
     * 立即设置表情
     */
    fun setImmediate(expressionId: String, intensity: Float = 0.7f) {
        currentExpressionId = expressionId
        currentIntensity = intensity
        targetExpressionId = expressionId
        targetIntensity = intensity
        transitionProgress = 1f
    }
}
