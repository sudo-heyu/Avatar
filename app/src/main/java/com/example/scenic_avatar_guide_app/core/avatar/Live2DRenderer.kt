package com.example.scenic_avatar_guide_app.core.avatar

import com.example.scenic_avatar_guide_app.domain.model.AvatarFullState

/**
 * Live2D 参数常量
 * 基于 Cubism SDK 标准参数命名
 */
object Live2DParams {
    // 口型参数
    const val MOUTH_OPEN_Y = "ParamMouthOpenY"
    const val MOUTH_FORM = "ParamMouthForm"

    // 眼睛参数
    const val EYE_L_OPEN = "ParamEyeLOpen"
    const val EYE_R_OPEN = "ParamEyeROpen"
    const val EYE_BALL_X = "ParamEyeBallX"
    const val EYE_BALL_Y = "ParamEyeBallY"

    // 眉毛参数
    const val BROW_L_Y = "ParamBrowLY"
    const val BROW_R_Y = "ParamBrowRY"
    const val BROW_L_ANGLE = "ParamBrowLAngle"
    const val BROW_R_ANGLE = "ParamBrowRAngle"

    // 头部参数
    const val ANGLE_X = "ParamAngleX"
    const val ANGLE_Y = "ParamAngleY"
    const val ANGLE_Z = "ParamAngleZ"

    // 身体参数
    const val BODY_ANGLE_X = "ParamBodyAngleX"
    const val BODY_ANGLE_Y = "ParamBodyAngleY"
    const val BREATH = "ParamBreath"
}

/**
 * Live2D 渲染器配置
 */
data class Live2DRenderConfig(
    val modelPath: String = "live2d/hiyori/Hiyori.model3.json",
    val modelScale: Float = 1.0f,
    val modelX: Float = 0.0f,
    val modelY: Float = 0.0f
)

/**
 * Live2D 渲染器接口
 */
interface Live2DRenderer {
    /**
     * 是否已初始化
     */
    val isInitialized: Boolean

    /**
     * 当前模型是否已加载
     */
    val isModelLoaded: Boolean

    /**
     * 初始化渲染器
     */
    fun initialize()

    /**
     * 加载模型
     * @param modelPath 模型配置文件路径（相对于 assets）
     */
    suspend fun loadModel(modelPath: String): Result<Unit>

    /**
     * 设置口型参数
     * @param mouthOpen 开合度 (0-1)
     * @param mouthForm 嘴型 (-1=扁嘴, 0=中性, 1=圆嘴)
     */
    fun setMouth(mouthOpen: Float, mouthForm: Float)

    /**
     * 设置表情
     * @param expressionId 表情 ID
     */
    fun setExpression(expressionId: String)

    /**
     * 播放动作
     * @param group 动作组名
     * @param index 动作索引
     * @param loop 是否循环
     */
    fun playMotion(group: String, index: Int, loop: Boolean = false)

    /**
     * 停止动作
     */
    fun stopMotion()

    /**
     * 设置参数值
     * @param paramId 参数 ID
     * @param value 参数值
     * @param weight 权重 (0-1)
     */
    fun setParameter(paramId: String, value: Float, weight: Float = 1.0f)

    /**
     * 更新状态
     * @param state 完整状态
     */
    fun updateState(state: AvatarFullState)

    /**
     * 设置是否只显示上半身
     */
    fun setUpperBodyMode(enabled: Boolean)

    /**
     * 释放资源
     */
    fun release()
}
