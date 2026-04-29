package com.example.scenic_avatar_guide_app.core.avatar

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import com.example.scenic_avatar_guide_app.domain.model.AvatarExpression
import com.example.scenic_avatar_guide_app.domain.model.AvatarGesture
import com.example.scenic_avatar_guide_app.domain.model.AvatarFullState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.lang.ref.WeakReference

/**
 * Live2D 渲染器实现
 * 封装 Live2D 模型加载、参数控制、动作播放等功能
 */
class Live2DRendererImpl(
    private val context: Context
) : Live2DRenderer {

    companion object {
        private const val TAG = "Live2DRendererImpl"
        private const val MODEL_LOAD_TIMEOUT = 10000L // 10秒超时
    }

    private var _isInitialized = false
    private var _isModelLoaded = false
    private var currentModelPath: String? = null

    // 当前参数缓存
    private var currentMouthOpen = 0f
    private var currentExpression: String? = null
    private var currentExpressionIntensity = 0.7f
    private var currentGesture: AvatarGesture = AvatarGesture.IDLE
    private var surfaceViewRef: WeakReference<Live2DGLSurfaceView>? = null
    private var hasSurfaceAttached = false

    override val isInitialized: Boolean get() = _isInitialized
    override val isModelLoaded: Boolean get() = _isModelLoaded && hasSurfaceAttached

    /**
     * 初始化渲染器
     */
    override fun initialize() {
        if (_isInitialized) return

        try {
            // 初始化 JNI 桥接
            findActivity(context)?.let { JniBridgeJava.SetActivityInstance(it) }
            JniBridgeJava.SetContext(context)

            _isInitialized = true
            Log.i(TAG, "Live2D renderer initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Live2D renderer", e)
        }
    }

    /**
     * 加载模型
     */
    override suspend fun loadModel(modelPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!_isInitialized) {
            initialize()
        }

        try {
            withTimeout(MODEL_LOAD_TIMEOUT) {
                // 1. 验证文件存在且可读
                val fileData = context.assets.open(modelPath)
                val fileSize = fileData.available()
                fileData.close()
                if (fileSize == 0) {
                    throw IllegalStateException("Model file is empty: $modelPath")
                }

                // 2. 通过 JNI 的 LoadFile 验证 C++ 层能否读取
                val jniData = JniBridgeJava.LoadFile(modelPath)
                if (jniData == null || jniData.isEmpty()) {
                    throw IllegalStateException("JNI cannot load model: $modelPath")
                }

                currentModelPath = modelPath
                _isModelLoaded = true
                Log.i(TAG, "Model validated: $modelPath (${fileSize} bytes)")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            _isModelLoaded = false
            Log.e(TAG, "Failed to load model: $modelPath", e)
            Result.failure(e)
        }
    }

    /**
     * 设置口型参数
     */
    override fun setMouth(mouthOpen: Float, mouthForm: Float) {
        if (!_isModelLoaded) return

        currentMouthOpen = mouthOpen.coerceIn(0f, 1f)
        val amplifiedMouthOpen = (currentMouthOpen * 1.3f).coerceAtMost(1.5f)
        setParameter(Live2DParams.MOUTH_OPEN_Y, amplifiedMouthOpen)
    }

    /**
     * 设置表情
     */
    override fun setExpression(expressionId: String) {
        if (!_isModelLoaded) return

        currentExpression = expressionId
        runOnRenderThread {
            JniBridgeJava.nativeSetExpression(expressionId)
        }
    }

    /**
     * 播放动作
     */
    override fun playMotion(group: String, index: Int, loop: Boolean) {
        if (!_isModelLoaded) return
        val gesture = AvatarGesture.fromValue(group)
        currentGesture = gesture
        applyGesturePreset(gesture)
    }

    /**
     * 停止动作
     */
    override fun stopMotion() {
        if (!_isModelLoaded) return
        currentGesture = AvatarGesture.IDLE
        applyGesturePreset(AvatarGesture.IDLE)
    }

    /**
     * 设置参数值
     */
    override fun setParameter(paramId: String, value: Float, weight: Float) {
        if (!_isModelLoaded) return
        runOnRenderThread {
            JniBridgeJava.nativeSetParameter(paramId, value, weight)
        }
    }

    /**
     * 更新状态
     */
    override fun updateState(state: AvatarFullState) {
        if (!_isModelLoaded) return

        // 更新口型
        setMouth(state.mouthOpen, state.mouthForm)

        // 更新表情
        val expressionId = expressionFromEnum(state.expression)
        if (expressionId != currentExpression || state.expressionIntensity != currentExpressionIntensity) {
            currentExpressionIntensity = state.expressionIntensity.coerceIn(0f, 1f)
            setExpression(expressionId)
        }

        // 根据状态触发动作
        if (state.gesture != currentGesture) {
            if (state.gesture == AvatarGesture.IDLE) {
                stopMotion()
            } else {
                playMotion(state.gesture.value, 0, false)
            }
        }
    }

    /**
     * 设置是否只显示上半身
     */
    override fun setUpperBodyMode(enabled: Boolean) {
        runOnRenderThread {
            JniBridgeJava.nativeSetUpperBodyMode(enabled)
        }
    }

    /**
     * 释放资源
     */
    override fun release() {
        if (!_isInitialized) return

        try {
            JniBridgeJava.nativeOnStop()
            JniBridgeJava.nativeOnDestroy()
            _isInitialized = false
            _isModelLoaded = false
            currentModelPath = null
            surfaceViewRef = null
            Log.i(TAG, "Live2D renderer released")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release Live2D renderer", e)
        }
    }

    fun attachSurfaceView(surfaceView: Live2DGLSurfaceView) {
        surfaceViewRef = WeakReference(surfaceView)
        hasSurfaceAttached = true
        Log.d(TAG, "Surface attached, model ready: $_isModelLoaded")
    }

    /**
     * 表情枚举转 ID
     */
    private fun expressionFromEnum(expression: AvatarExpression): String {
        return expression.value
    }

    /**
     * 应用动作预设（Gesture 层）
     *
     * 参数分层规则（上半身模式）：
     * - Expression 层：控制眉毛、眼睛、脸颊、嘴形变形（ParamMouthForm）、头部微姿态（AngleX/Y/Z 的 Add 叠加）
     * - Gesture 层：控制头部功能性动作（AngleX/Y/Z 的覆盖值）、身体角度、肩膀、手臂、手部
     * - LipSync 层：独占 ParamMouthOpenY（嘴部开合度）
     *
     * 叠加机制：Gesture 通过 setParameter 直接覆盖基础状态，Expression 在 C++ LateUpdate 中以 Add 模式叠加。
     * 因此 Gesture 的头部角度会覆盖 Expression 的基础值，但 Expression 的 Add 偏移仍会生效。
     */
    private fun applyGesturePreset(gesture: AvatarGesture) {
        // 先归零所有 Gesture 层参数，避免前一个动作残留
        setParameter(Live2DParams.ANGLE_X, 0f)
        setParameter(Live2DParams.ANGLE_Y, 0f)
        setParameter(Live2DParams.ANGLE_Z, 0f)
        setParameter(Live2DParams.BODY_ANGLE_X, 0f)
        setParameter(Live2DParams.BODY_ANGLE_Y, 0f)
        setParameter(Live2DParams.BODY_ANGLE_Z, 0f)
        setParameter(Live2DParams.SHOULDER, 0f)
        setParameter(Live2DParams.ARM_LA, 0f)
        setParameter(Live2DParams.ARM_RA, 0f)
        setParameter(Live2DParams.ARM_LB, 0f)
        setParameter(Live2DParams.ARM_RB, 0f)
        setParameter(Live2DParams.HAND_L, 0f)
        setParameter(Live2DParams.HAND_R, 0f)
        setParameter(Live2DParams.HAND_LB, 0f)
        setParameter(Live2DParams.HAND_RB, 0f)

        when (gesture) {
            AvatarGesture.IDLE -> Unit

            AvatarGesture.NOD -> {
                // 小幅点头，避免像磕头；肩膀微抬配合
                setParameter(Live2DParams.ANGLE_Y, -10f)
                setParameter(Live2DParams.SHOULDER, 0.1f)
            }

            AvatarGesture.SHAKE -> {
                // 轻微偏头表示否定，不做大幅度左右摇
                setParameter(Live2DParams.ANGLE_X, 10f)
                setParameter(Live2DParams.SHOULDER, -0.05f)
            }

            AvatarGesture.WAVE -> {
                // 小幅挥手：不歪头（Expression 已负责情绪歪头），身体微侧，单臂抬起不过肩
                setParameter(Live2DParams.BODY_ANGLE_X, 6f)
                setParameter(Live2DParams.ARM_LA, 0.35f)
                setParameter(Live2DParams.HAND_L, 0.3f)
            }

            AvatarGesture.POINT_LEFT -> {
                // 左侧指引：头部微转，身体配合，手臂平伸不出界
                setParameter(Live2DParams.ANGLE_X, -12f)
                setParameter(Live2DParams.BODY_ANGLE_X, -8f)
                setParameter(Live2DParams.ARM_LA, 0.3f)
                setParameter(Live2DParams.HAND_L, 0.25f)
            }

            AvatarGesture.POINT_RIGHT -> {
                // 右侧指引：对称设计
                setParameter(Live2DParams.ANGLE_X, 12f)
                setParameter(Live2DParams.BODY_ANGLE_X, 8f)
                setParameter(Live2DParams.ARM_RA, 0.3f)
                setParameter(Live2DParams.HAND_R, 0.25f)
            }

            AvatarGesture.POINT_FORWARD -> {
                // 前方提示：双手轻微前伸，头部微前倾
                setParameter(Live2DParams.ANGLE_Z, -5f)
                setParameter(Live2DParams.ARM_LA, 0.18f)
                setParameter(Live2DParams.ARM_RA, 0.18f)
                setParameter(Live2DParams.HAND_L, 0.12f)
                setParameter(Live2DParams.HAND_R, 0.12f)
            }

            AvatarGesture.BOW -> {
                // 上半身模式下的"欠身致意"：不额外低头（Expression 已负责歉意低头），
                // 用肩膀微怂 + 手臂内收表现收敛姿态
                setParameter(Live2DParams.SHOULDER, 0.25f)
                setParameter(Live2DParams.ARM_LB, 0.15f)
                setParameter(Live2DParams.ARM_RB, 0.15f)
            }

            AvatarGesture.THINKING_POSE -> {
                // 手触下巴思考：内敛姿态，手臂幅度适中不出界
                setParameter(Live2DParams.ANGLE_X, -6f)
                setParameter(Live2DParams.ANGLE_Y, 6f)
                setParameter(Live2DParams.ARM_LB, 0.4f)
                setParameter(Live2DParams.HAND_LB, 0.3f)
            }

            AvatarGesture.GUIDE -> {
                // 导览手势：平伸手掌引导，幅度适中
                setParameter(Live2DParams.ANGLE_X, 8f)
                setParameter(Live2DParams.BODY_ANGLE_X, 5f)
                setParameter(Live2DParams.ARM_RA, 0.35f)
                setParameter(Live2DParams.HAND_R, 0.28f)
            }
        }
    }

    private fun runOnRenderThread(action: () -> Unit) {
        val surfaceView = surfaceViewRef?.get()
        if (surfaceView == null) {
            return
        }
        surfaceView.runOnRenderThread(action)
    }

    private fun findActivity(context: Context): Activity? {
        var current = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }
}
