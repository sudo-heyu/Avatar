package com.example.scenic_avatar_guide_app.core.avatar

import com.example.scenic_avatar_guide_app.domain.model.AvatarFullState

/**
 * Live2D 参数常量
 * 基于 Cubism SDK 标准参数命名
 */
object Live2DParams {
    // ==================== 口型参数（LipSync 独占）====================
    const val MOUTH_OPEN_Y = "ParamMouthOpenY"

    // ==================== 表情参数（Expression 独占）====================
    // 面部：眉毛、眼睛、眼珠、脸颊
    const val EYE_L_OPEN = "ParamEyeLOpen"
    const val EYE_R_OPEN = "ParamEyeROpen"
    const val EYE_L_SMILE = "ParamEyeLSmile"
    const val EYE_R_SMILE = "ParamEyeRSmile"

    // 眉毛
    const val BROW_L_Y = "ParamBrowLY"
    const val BROW_R_Y = "ParamBrowRY"
    const val BROW_L_ANGLE = "ParamBrowLAngle"
    const val BROW_R_ANGLE = "ParamBrowRAngle"

    // 嘴部变形（Expression 管嘴形，LipSync 管开合）
    const val MOUTH_FORM = "ParamMouthForm"

    // 头部微姿态（Expression 情绪性偏移 + Gesture 功能性动作叠加）
    const val ANGLE_X = "ParamAngleX"
    const val ANGLE_Y = "ParamAngleY"
    const val ANGLE_Z = "ParamAngleZ"

    // ==================== 动作参数（Gesture 独占）====================
    // 身体旋转
    const val BODY_ANGLE_X = "ParamBodyAngleX"
    const val BODY_ANGLE_Y = "ParamBodyAngleY"
    const val BODY_ANGLE_Z = "ParamBodyAngleZ"

    // 肩膀
    const val SHOULDER = "ParamShoulder"

    // 下半身/腿部
    const val LEG = "ParamLeg"

    // 眼球方向
    const val EYE_BALL_X = "ParamEyeBallX"
    const val EYE_BALL_Y = "ParamEyeBallY"

    // 呼吸
    const val BREATH = "ParamBreath"

    // 手臂（当前未使用：手在画面外不可见，抬臂无意义）
    const val ARM_LA = "ParamArmLA"
    const val ARM_RA = "ParamArmRA"
    const val ARM_LB = "ParamArmLB"
    const val ARM_RB = "ParamArmRB"

    // 手部（当前未使用：手在画面外不可见）
    const val HAND_L = "ParamHandL"
    const val HAND_R = "ParamHandR"
    const val HAND_LB = "ParamHandLB"
    const val HAND_RB = "ParamHandRB"
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
     * @param mouthForm 嘴型状态，Live2D 实模由 Expression 层控制 ParamMouthForm
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
     * 设置数字人显示模式
     */
    fun setDisplayMode(mode: AvatarDisplayMode)

    /**
     * 重置说话状态
     * 同步清除 speakingMouthOverride 和 isSpeaking 标志，并闭嘴
     * 用于 stop() 时直接重置渲染器状态，不依赖 StateFlow 异步链
     */
    fun resetSpeakingState()

    /**
     * 释放资源
     */
    fun release()
}
