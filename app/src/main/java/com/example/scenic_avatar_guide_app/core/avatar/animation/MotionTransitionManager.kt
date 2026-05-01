package com.example.scenic_avatar_guide_app.core.avatar.animation

import android.util.Log
import com.example.scenic_avatar_guide_app.domain.model.AvatarGesture

private const val TAG = "MotionTransitionMgr"

/**
 * 动作层状态
 */
data class MotionLayer(
    val gesture: AvatarGesture,
    var params: GestureParams,      // 当前参数（可变，用于原生动作同步）
    var weight: Float = 1.0f,      // 当前权重 (0-1)
    var targetWeight: Float = 1.0f, // 目标权重
    var fadeSpeed: Float = 1.0f,   // 淡入淡出速度 (权重/秒)
    var isActive: Boolean = true   // 是否活跃
)

/**
 * 动作过渡管理器
 *
 * 核心职责：
 * 1. 管理多个动作层的混合
 * 2. 处理动作淡入淡出
 * 3. 确保动作之间平滑过渡
 * 4. 提供参数混合输出
 *
 * 过渡策略：
 * - 新动作开始时，旧动作淡出，新动作淡入
 * - 使用交叉淡入淡出 (Cross-fade) 避免突变
 * - 参数混合：weighted average
 */
class MotionTransitionManager {

    // 当前活跃的动作层（最多保留 2 层用于过渡）
    private val layers = mutableListOf<MotionLayer>()

    // 当前最终输出的参数
    private var currentOutputParams: GestureParams = GestureParams.IDLE

    // 当前动作
    private var currentGesture: AvatarGesture = AvatarGesture.IDLE

    // 默认过渡时间（毫秒）
    var defaultTransitionMs: Long = 300L

    // 是否正在过渡中
    private var isTransitioning: Boolean = false

    /**
     * 获取当前混合后的参数
     */
    fun getCurrentParams(): GestureParams = currentOutputParams

    /**
     * 获取当前动作
     */
    fun getCurrentGesture(): AvatarGesture = currentGesture

    /**
     * 是否正在过渡
     */
    fun isInTransition(): Boolean = isTransitioning

    /**
     * 开始新动作（带淡入淡出过渡）
     *
     * @param gesture 目标动作
     * @param targetParams 目标参数
     * @param transitionMs 过渡时间（毫秒）
     */
    fun transitionTo(
        gesture: AvatarGesture,
        targetParams: GestureParams,
        transitionMs: Long = defaultTransitionMs
    ) {
        Log.d(TAG, "transitionTo: $gesture, transitionMs=$transitionMs")

        if (gesture == currentGesture && layers.size == 1 && layers[0].weight >= 0.99f) {
            // 相同动作且已稳定，跳过
            return
        }

        // 计算淡入淡出速度
        val fadeSpeed = if (transitionMs > 0) 1000f / transitionMs else 10f

        // 将所有现有层标记为淡出
        layers.forEach { layer ->
            if (layer.isActive) {
                layer.targetWeight = 0f
                layer.fadeSpeed = fadeSpeed
            }
        }

        // 添加新层（淡入）
        layers.add(MotionLayer(
            gesture = gesture,
            params = targetParams,
            weight = 0f,
            targetWeight = 1f,
            fadeSpeed = fadeSpeed,
            isActive = true
        ))

        currentGesture = gesture
        isTransitioning = true

        // 清理非活跃层
        cleanupInactiveLayers()
    }

    /**
     * 立即设置动作（无过渡）
     */
    fun setImmediate(gesture: AvatarGesture, params: GestureParams) {
        layers.clear()
        layers.add(MotionLayer(
            gesture = gesture,
            params = params,
            weight = 1f,
            targetWeight = 1f,
            isActive = true
        ))
        currentGesture = gesture
        currentOutputParams = params
        isTransitioning = false
    }

    /**
     * 更新动画帧
     * @param deltaTimeMs 帧间隔（毫秒）
     * @return 是否需要继续更新
     */
    fun update(deltaTimeMs: Long = 16): Boolean {
        if (layers.isEmpty()) {
            currentOutputParams = GestureParams.IDLE
            isTransitioning = false
            return false
        }

        var stillTransitioning = false
        var totalWeight = 0f
        var weightedParams = GestureParams()

        layers.forEach { layer ->
            if (!layer.isActive) return@forEach

            // 更新权重
            if (layer.weight != layer.targetWeight) {
                val delta = (layer.fadeSpeed * deltaTimeMs / 1000f)
                layer.weight = if (layer.targetWeight > layer.weight) {
                    (layer.weight + delta).coerceAtMost(layer.targetWeight)
                } else {
                    (layer.weight - delta).coerceAtLeast(layer.targetWeight)
                }
                stillTransitioning = true
            }

            // 检查是否完成淡出
            if (layer.weight < 0.01f && layer.targetWeight == 0f) {
                layer.isActive = false
            }
        }

        // 计算混合参数
        layers.filter { it.isActive && it.weight > 0.01f }.forEach { layer ->
            totalWeight += layer.weight
            weightedParams = GestureParams(
                angleX = weightedParams.angleX + layer.params.angleX * layer.weight,
                angleY = weightedParams.angleY + layer.params.angleY * layer.weight,
                angleZ = weightedParams.angleZ + layer.params.angleZ * layer.weight,
                bodyAngleX = weightedParams.bodyAngleX + layer.params.bodyAngleX * layer.weight,
                bodyAngleY = weightedParams.bodyAngleY + layer.params.bodyAngleY * layer.weight,
                bodyAngleZ = weightedParams.bodyAngleZ + layer.params.bodyAngleZ * layer.weight,
                shoulder = weightedParams.shoulder + layer.params.shoulder * layer.weight,
                eyeBallX = weightedParams.eyeBallX + layer.params.eyeBallX * layer.weight,
                eyeBallY = weightedParams.eyeBallY + layer.params.eyeBallY * layer.weight
            )
        }

        // 归一化
        if (totalWeight > 0.01f) {
            currentOutputParams = GestureParams(
                angleX = weightedParams.angleX / totalWeight,
                angleY = weightedParams.angleY / totalWeight,
                angleZ = weightedParams.angleZ / totalWeight,
                bodyAngleX = weightedParams.bodyAngleX / totalWeight,
                bodyAngleY = weightedParams.bodyAngleY / totalWeight,
                bodyAngleZ = weightedParams.bodyAngleZ / totalWeight,
                shoulder = weightedParams.shoulder / totalWeight,
                eyeBallX = weightedParams.eyeBallX / totalWeight,
                eyeBallY = weightedParams.eyeBallY / totalWeight
            )
        }

        // 清理非活跃层
        cleanupInactiveLayers()

        isTransitioning = stillTransitioning

        return stillTransitioning || layers.size > 1
    }

    /**
     * 从外部更新当前参数（用于原生动作播放时同步状态）
     */
    fun updateCurrentLayerParams(params: GestureParams) {
        if (layers.isNotEmpty()) {
            layers[0].params = params
            currentOutputParams = params
        }
    }

    /**
     * 清理非活跃层
     */
    private fun cleanupInactiveLayers() {
        layers.removeAll { !it.isActive }
    }

    /**
     * 重置到 IDLE
     */
    fun reset() {
        layers.clear()
        currentOutputParams = GestureParams.IDLE
        currentGesture = AvatarGesture.IDLE
        isTransitioning = false
    }
}
