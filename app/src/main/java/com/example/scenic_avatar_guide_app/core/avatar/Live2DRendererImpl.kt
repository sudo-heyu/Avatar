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
    private var currentMouthForm = 0f
    private var currentExpression: String? = null
    private var currentExpressionIntensity = 0.7f
    private var currentGesture: AvatarGesture = AvatarGesture.IDLE
    private var surfaceViewRef: WeakReference<Live2DGLSurfaceView>? = null

    override val isInitialized: Boolean get() = _isInitialized
    override val isModelLoaded: Boolean get() = _isModelLoaded

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
                context.assets.open(modelPath).close()
                currentModelPath = modelPath
                _isModelLoaded = true
                Log.i(TAG, "Model loaded: $modelPath")
            }
            Result.success(Unit)
        } catch (e: Exception) {
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
        currentMouthForm = mouthForm.coerceIn(-1f, 1f)

        // 通过参数设置口型
        // 实际的 Live2D 参数设置由 C++ 层处理
        setParameter(Live2DParams.MOUTH_OPEN_Y, currentMouthOpen)
        setParameter(Live2DParams.MOUTH_FORM, currentMouthForm)
    }

    /**
     * 设置表情
     */
    override fun setExpression(expressionId: String) {
        if (!_isModelLoaded) return

        currentExpression = expressionId
        currentExpressionIntensity = currentExpressionIntensity.coerceIn(0f, 1f)
        applyExpressionPreset(expressionId, currentExpressionIntensity)
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
    }

    /**
     * 表情枚举转 ID
     */
    private fun expressionFromEnum(expression: AvatarExpression): String {
        return expression.value
    }

    private fun applyExpressionPreset(expressionId: String, intensity: Float) {
        val safeIntensity = intensity.coerceIn(0f, 1f)

        setParameter(Live2DParams.EYE_L_OPEN, 1.0f)
        setParameter(Live2DParams.EYE_R_OPEN, 1.0f)
        setParameter(Live2DParams.BROW_L_Y, 0f)
        setParameter(Live2DParams.BROW_R_Y, 0f)
        setParameter(Live2DParams.BROW_L_ANGLE, 0f)
        setParameter(Live2DParams.BROW_R_ANGLE, 0f)
        setParameter(Live2DParams.EYE_BALL_X, 0f)
        setParameter(Live2DParams.EYE_BALL_Y, 0f)

        when (AvatarExpression.fromValue(expressionId)) {
            AvatarExpression.NEUTRAL -> Unit
            AvatarExpression.HAPPY -> {
                setParameter(Live2DParams.BROW_L_Y, 0.25f * safeIntensity)
                setParameter(Live2DParams.BROW_R_Y, 0.25f * safeIntensity)
                setParameter(Live2DParams.BROW_L_ANGLE, -0.2f * safeIntensity)
                setParameter(Live2DParams.BROW_R_ANGLE, 0.2f * safeIntensity)
                setParameter(Live2DParams.EYE_L_OPEN, 0.85f)
                setParameter(Live2DParams.EYE_R_OPEN, 0.85f)
            }
            AvatarExpression.THINKING -> {
                setParameter(Live2DParams.BROW_L_ANGLE, -0.35f * safeIntensity)
                setParameter(Live2DParams.BROW_R_ANGLE, 0.15f * safeIntensity)
                setParameter(Live2DParams.BROW_L_Y, -0.1f * safeIntensity)
                setParameter(Live2DParams.EYE_BALL_X, -0.25f * safeIntensity)
            }
            AvatarExpression.SURPRISED -> {
                setParameter(Live2DParams.BROW_L_Y, 0.45f * safeIntensity)
                setParameter(Live2DParams.BROW_R_Y, 0.45f * safeIntensity)
                setParameter(Live2DParams.EYE_L_OPEN, 1.2f)
                setParameter(Live2DParams.EYE_R_OPEN, 1.2f)
            }
            AvatarExpression.EXCITED -> {
                setParameter(Live2DParams.BROW_L_Y, 0.35f * safeIntensity)
                setParameter(Live2DParams.BROW_R_Y, 0.35f * safeIntensity)
                setParameter(Live2DParams.EYE_L_OPEN, 1.05f)
                setParameter(Live2DParams.EYE_R_OPEN, 1.05f)
                setParameter(Live2DParams.ANGLE_Z, -6f * safeIntensity)
            }
            AvatarExpression.CONCERNED -> {
                setParameter(Live2DParams.BROW_L_Y, -0.15f * safeIntensity)
                setParameter(Live2DParams.BROW_R_Y, -0.15f * safeIntensity)
                setParameter(Live2DParams.BROW_L_ANGLE, 0.35f * safeIntensity)
                setParameter(Live2DParams.BROW_R_ANGLE, -0.35f * safeIntensity)
                setParameter(Live2DParams.EYE_L_OPEN, 0.8f)
                setParameter(Live2DParams.EYE_R_OPEN, 0.8f)
            }
            AvatarExpression.APologetic -> {
                setParameter(Live2DParams.BROW_L_Y, -0.2f * safeIntensity)
                setParameter(Live2DParams.BROW_R_Y, -0.2f * safeIntensity)
                setParameter(Live2DParams.EYE_L_OPEN, 0.75f)
                setParameter(Live2DParams.EYE_R_OPEN, 0.75f)
                setParameter(Live2DParams.ANGLE_Y, -8f * safeIntensity)
            }
            AvatarExpression.WELCOMING -> {
                setParameter(Live2DParams.BROW_L_Y, 0.2f * safeIntensity)
                setParameter(Live2DParams.BROW_R_Y, 0.2f * safeIntensity)
                setParameter(Live2DParams.ANGLE_Z, -4f * safeIntensity)
                setParameter(Live2DParams.BODY_ANGLE_X, 4f * safeIntensity)
            }
        }
    }

    private fun applyGesturePreset(gesture: AvatarGesture) {
        setParameter(Live2DParams.ANGLE_X, 0f)
        setParameter(Live2DParams.ANGLE_Y, 0f)
        setParameter(Live2DParams.ANGLE_Z, 0f)
        setParameter(Live2DParams.BODY_ANGLE_X, 0f)

        when (gesture) {
            AvatarGesture.IDLE -> Unit
            AvatarGesture.NOD -> setParameter(Live2DParams.ANGLE_Y, -18f)
            AvatarGesture.SHAKE -> setParameter(Live2DParams.ANGLE_X, 18f)
            AvatarGesture.WAVE -> {
                setParameter(Live2DParams.ANGLE_Z, -14f)
                setParameter(Live2DParams.BODY_ANGLE_X, 10f)
            }
            AvatarGesture.POINT_LEFT -> {
                setParameter(Live2DParams.ANGLE_X, -20f)
                setParameter(Live2DParams.BODY_ANGLE_X, -12f)
            }
            AvatarGesture.POINT_RIGHT -> {
                setParameter(Live2DParams.ANGLE_X, 20f)
                setParameter(Live2DParams.BODY_ANGLE_X, 12f)
            }
            AvatarGesture.POINT_FORWARD -> setParameter(Live2DParams.ANGLE_Z, -8f)
            AvatarGesture.BOW -> setParameter(Live2DParams.ANGLE_Y, -26f)
            AvatarGesture.THINKING_POSE -> {
                setParameter(Live2DParams.ANGLE_X, -10f)
                setParameter(Live2DParams.ANGLE_Y, 10f)
            }
            AvatarGesture.GUIDE -> {
                setParameter(Live2DParams.ANGLE_X, 12f)
                setParameter(Live2DParams.BODY_ANGLE_X, 8f)
            }
        }
    }

    private fun runOnRenderThread(action: () -> Unit) {
        surfaceViewRef?.get()?.runOnRenderThread(action) ?: action()
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
