package com.example.scenic_avatar_guide_app.core.avatar

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import com.example.scenic_avatar_guide_app.core.avatar.animation.EasingType
import com.example.scenic_avatar_guide_app.core.avatar.animation.ExpressionTransitionController
import com.example.scenic_avatar_guide_app.core.avatar.animation.GestureParams
import com.example.scenic_avatar_guide_app.core.avatar.animation.GestureTransitionController
import com.example.scenic_avatar_guide_app.core.avatar.animation.GestureAnimation
import com.example.scenic_avatar_guide_app.core.avatar.animation.GestureAnimationPlayer
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
        private const val TAG = "L2D"  // 改成短名称，避免被 vivo 设备过滤
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

    // 动作过渡控制器
    private val gestureTransitionController = GestureTransitionController()

    // 表情过渡控制器
    private val expressionTransitionController = ExpressionTransitionController()

    // 动作动画播放器（用于点头、摇头等关键帧动画）
    private val gestureAnimationPlayer = GestureAnimationPlayer()

    // 是否启用平滑过渡
    private var enableSmoothTransition = true

    // 动画更新是否激活
    private var animationUpdateActive = false

    // 原生动作是否正在播放（用于避免归零参数覆盖原生动作）
    private var nativeMotionPlaying = false
    private var nativeMotionStartTime: Long = 0
    private var nativeMotionDurationMs: Long = 0

    // 嘴部宽度缩放因子（放大嘴的视觉宽度）
    var mouthWidthScale: Float = 1.0f  // 嘴部宽度偏移，正值让嘴更宽更大

    // 动作精确时序跟踪（用于日志输出）
    private var motionTimingCallTime: Long = 0
    private var motionTimingStartedTime: Long = 0
    private var motionTimingExpectedDuration: Long = 0
    private var motionTimingLabel: String = ""

    override val isInitialized: Boolean get() = _isInitialized
    override val isModelLoaded: Boolean get() = _isModelLoaded && hasSurfaceAttached

    /**
     * 初始化渲染器
     */
    override fun initialize() {
        Log.d(TAG, "=== initialize() START: _isInitialized=$_isInitialized ===")
        if (_isInitialized) {
            Log.d(TAG, "=== initialize() SKIPPED: already initialized ===")
            return
        }

        try {
            // 初始化 JNI 桥接
            val activity = findActivity(context)
            Log.d(TAG, "=== findActivity result: $activity ===")
            activity?.let { JniBridgeJava.SetActivityInstance(it) }
            JniBridgeJava.SetContext(context)

            _isInitialized = true
            Log.i(TAG, "=== initialize() SUCCESS ===")
        } catch (e: Exception) {
            Log.e(TAG, "=== initialize() FAILED ===", e)
        }
    }

    /**
     * 加载模型
     */
    override suspend fun loadModel(modelPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== loadModel() START: path=$modelPath, _isInitialized=$_isInitialized ===")
        if (!_isInitialized) {
            Log.d(TAG, "=== loadModel: calling initialize() first ===")
            initialize()
        }

        try {
            withTimeout(MODEL_LOAD_TIMEOUT) {
                // 1. 验证文件存在且可读
                Log.d(TAG, "=== loadModel: opening asset file ===")
                val fileData = context.assets.open(modelPath)
                val fileSize = fileData.available()
                fileData.close()
                Log.d(TAG, "=== loadModel: file size=$fileSize bytes ===")
                if (fileSize == 0) {
                    throw IllegalStateException("Model file is empty: $modelPath")
                }

                // 2. 通过 JNI 的 LoadFile 验证 C++ 层能否读取
                Log.d(TAG, "=== loadModel: calling JniBridgeJava.LoadFile ===")
                val jniData = JniBridgeJava.LoadFile(modelPath)
                Log.d(TAG, "=== loadModel: JNI data size=${jniData?.size} ===")
                if (jniData == null || jniData.isEmpty()) {
                    throw IllegalStateException("JNI cannot load model: $modelPath")
                }

                currentModelPath = modelPath
                _isModelLoaded = true
                Log.i(TAG, "=== loadModel() SUCCESS: $modelPath ===")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            _isModelLoaded = false
            Log.e(TAG, "=== loadModel() FAILED: $modelPath ===", e)
            Result.failure(e)
        }
    }

    /**
     * 设置口型参数
     */
    override fun setMouth(mouthOpen: Float, mouthForm: Float) {
        if (!_isModelLoaded) return

        currentMouthOpen = mouthOpen.coerceIn(0f, 1f)
        val amplifiedMouthOpen = (currentMouthOpen * 0.85f).coerceAtMost(1.0f)
        setParameter(Live2DParams.MOUTH_OPEN_Y, amplifiedMouthOpen)

        // 应用嘴部宽度缩放：在原有 mouthForm 基础上增加宽度偏移
        // ParamMouthForm 正值让嘴更宽（笑），负值让嘴更窄（嘟嘴）
        val scaledMouthForm = (mouthForm + mouthWidthScale).coerceIn(-1f, 1.5f)
        setParameter(Live2DParams.MOUTH_FORM, scaledMouthForm)
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

        if (enableSmoothTransition) {
            transitionToGesture(gesture)
        } else {
            currentGesture = gesture
            applyGesturePreset(gesture)
        }
    }

    /**
     * 停止动作
     */
    override fun stopMotion() {
        if (!_isModelLoaded) return

        nativeMotionPlaying = false
        gestureAnimationPlayer.stop()

        if (enableSmoothTransition) {
            transitionToGesture(AvatarGesture.IDLE)
        } else {
            currentGesture = AvatarGesture.IDLE
            applyGesturePreset(AvatarGesture.IDLE)
        }
    }

    /**
     * 平滑过渡到指定动作
     */
    fun transitionToGesture(
        gesture: AvatarGesture,
        durationMs: Long = GestureTransitionController.DEFAULT_TRANSITION_MS,
        easing: EasingType = EasingType.EASE_IN_OUT_CUBIC
    ) {
        if (!_isModelLoaded) {
            Log.w(TAG, "transitionToGesture skipped: model not loaded")
            return
        }

        if (gesture == currentGesture && !gestureTransitionController.isTransitioning() && !gestureAnimationPlayer.isPlaying() && !nativeMotionPlaying) {
            return
        }

        nativeMotionPlaying = false
        gestureAnimationPlayer.stop()

        val motionPath = getMotionPathForGesture(gesture)
        Log.d(TAG, "transitionToGesture: $gesture, motionPath=$motionPath")
        if (motionPath != null) {
            playNativeMotion(motionPath)
            currentGesture = gesture
        } else {
            val animation = GestureAnimation.fromGesture(gesture)
            if (animation != null) {
                playGestureAnimation(animation)
                currentGesture = gesture
            } else {
                currentGesture = gesture
                val targetParams = GestureParams.fromGesture(gesture)
                applyGestureParams(targetParams)
                gestureTransitionController.setImmediate(gesture)
            }
        }

        startAnimationUpdate()
    }

    /**
     * 获取动作对应的原生动作文件路径
     */
    private fun getMotionPathForGesture(gesture: AvatarGesture): String? = when (gesture) {
        AvatarGesture.NOD -> "live2d/hiyori/motions/Hiyori_nod.motion3.json"
        AvatarGesture.SHAKE -> "live2d/hiyori/motions/Hiyori_shake.motion3.json"
        AvatarGesture.WAVE -> "live2d/hiyori/motions/Hiyori_wave.motion3.json"
        AvatarGesture.WELCOME_GESTURE -> "live2d/hiyori/motions/Hiyori_welcome.motion3.json"
        AvatarGesture.POINT_LEFT -> "live2d/hiyori/motions/Hiyori_look_left.motion3.json"
        AvatarGesture.POINT_RIGHT -> "live2d/hiyori/motions/Hiyori_look_right.motion3.json"
        else -> null
    }

    /**
     * 播放原生 Live2D 动作
     */
    private fun playNativeMotion(motionPath: String) {
        Log.d(TAG, "playNativeMotion: $motionPath")
        nativeMotionPlaying = true
        nativeMotionStartTime = System.currentTimeMillis()
        // 根据动作文件设置时长
        nativeMotionDurationMs = when {
            motionPath.contains("nod") -> 1200L  // 1.2秒
            motionPath.contains("shake") -> 1200L
            motionPath.contains("wave") -> 1500L
            motionPath.contains("welcome") -> 1800L
            motionPath.contains("look_left") -> 1200L
            motionPath.contains("look_right") -> 1200L
            else -> 1000L // 默认1秒
        }
        // 启动精确时序跟踪
        motionTimingCallTime = System.currentTimeMillis()
        motionTimingStartedTime = 0
        motionTimingExpectedDuration = nativeMotionDurationMs
        motionTimingLabel = when {
            motionPath.contains("nod") -> "NOD"
            motionPath.contains("shake") -> "SHAKE"
            motionPath.contains("wave") -> "WAVE"
            motionPath.contains("welcome") -> "WELCOME"
            motionPath.contains("look_left") -> "LOOK_LEFT"
            motionPath.contains("look_right") -> "LOOK_RIGHT"
            else -> "MOTION"
        }
        runOnRenderThread {
            JniBridgeJava.nativeStartMotionByPath(motionPath, 3) // priority 3 = Force
        }
    }

    /**
     * 播放关键帧动作动画
     */
    private fun playGestureAnimation(animation: GestureAnimation) {
        val fromParams = gestureTransitionController.getCurrentParams()
        gestureAnimationPlayer.play(animation, fromParams)
    }

    /**
     * 设置是否启用平滑过渡
     */
    fun setSmoothTransitionEnabled(enabled: Boolean) {
        enableSmoothTransition = enabled
    }

    /**
     * 启动动画更新循环
     */
    private fun startAnimationUpdate() {
        if (animationUpdateActive) return
        animationUpdateActive = true
        runOnRenderThread {
            updateAnimationLoop()
        }
    }

    /**
     * 动画更新循环
     * 使用帧率限制（约 60fps）避免过于频繁的更新
     */
    private fun updateAnimationLoop() {
        if (!animationUpdateActive || !_isModelLoaded) {
            animationUpdateActive = false
            return
        }

        var needsContinue = false

        // 检查原生动作是否播放完成
        if (nativeMotionPlaying) {
            val elapsed = System.currentTimeMillis() - nativeMotionStartTime
            if (elapsed >= nativeMotionDurationMs) {
                Log.d(TAG, "Native motion completed")
                nativeMotionPlaying = false
                currentGesture = AvatarGesture.IDLE
            } else {
                needsContinue = true
            }
        }

        // 更新关键帧动画（点头、摇头等）- 只有在没有原生动作时才更新
        if (!nativeMotionPlaying) {
            val isAnimationPlaying = gestureAnimationPlayer.isPlaying()
            if (isAnimationPlaying) {
                gestureAnimationPlayer.update()
                val animParams = gestureAnimationPlayer.getCurrentParams()
                applyGestureParams(animParams)
                needsContinue = gestureAnimationPlayer.isPlaying()

                // 动画播放结束，同步过渡控制器的状态
                if (!needsContinue) {
                    gestureTransitionController.setImmediate(AvatarGesture.IDLE)
                    currentGesture = AvatarGesture.IDLE
                }
            }

            // 更新动作过渡（其他动作）- 只有过渡中才更新参数
            val isGestureTransitioning = gestureTransitionController.isTransitioning()
            if (isGestureTransitioning) {
                gestureTransitionController.update()
                if (!isAnimationPlaying) {
                    val params = gestureTransitionController.getCurrentParams()
                    applyGestureParams(params)
                }
                needsContinue = needsContinue || gestureTransitionController.isTransitioning()
            }
        }

        // 更新表情过渡
        if (expressionTransitionController.isTransitioning()) {
            expressionTransitionController.update()
            val (expressionId, intensity) = expressionTransitionController.getCurrentExpression()
            if (expressionId != currentExpression) {
                currentExpression = expressionId
                JniBridgeJava.nativeSetExpression(expressionId)
            }
            needsContinue = needsContinue || expressionTransitionController.isTransitioning()
        }

        // 精确动作时序跟踪：捕获 C++ 层动作真正开始和完成的时刻
        if (motionTimingCallTime > 0) {
            if (motionTimingStartedTime == 0L && !JniBridgeJava.nativeIsMotionFinished()) {
                // C++ 层动作真正开始
                motionTimingStartedTime = System.currentTimeMillis()
            }
            if (motionTimingStartedTime > 0L && JniBridgeJava.nativeIsMotionFinished()) {
                // C++ 层动作真正完成
                val finishedTime = System.currentTimeMillis()
                val callDelay = motionTimingStartedTime - motionTimingCallTime
                val actualDuration = finishedTime - motionTimingStartedTime
                val totalTime = finishedTime - motionTimingCallTime
                val gap = actualDuration - motionTimingExpectedDuration
                Log.i(
                    TAG,
                    "[MotionTiming] ${motionTimingLabel} | " +
                    "调用→完成=${totalTime}ms | " +
                    "动作执行=${actualDuration}ms | " +
                    "调用时延=${callDelay}ms | " +
                    "执行差距=${if (gap >= 0) "+" else ""}${gap}ms (预期=${motionTimingExpectedDuration}ms)"
                )
                motionTimingCallTime = 0
            } else {
                // 仍在跟踪中，强制继续轮询
                needsContinue = true
            }
        }

        // 如果还需要更新，继续循环（帧率由 GLSurfaceView 的渲染循环控制）
        if (needsContinue && animationUpdateActive) {
            runOnRenderThread {
                updateAnimationLoop()
            }
        } else {
            animationUpdateActive = false
        }
    }

    /**
     * 应用动作参数（使用插值后的参数值）
     * 注意：每次应用前先归零所有参数，避免残留
     */
    private fun applyGestureParams(params: com.example.scenic_avatar_guide_app.core.avatar.animation.GestureParams) {
        // 先归零所有参数，确保干净状态
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

        // 然后应用当前参数
        setParameter(Live2DParams.ANGLE_X, params.angleX)
        setParameter(Live2DParams.ANGLE_Y, params.angleY)
        setParameter(Live2DParams.ANGLE_Z, params.angleZ)
        setParameter(Live2DParams.BODY_ANGLE_X, params.bodyAngleX)
        setParameter(Live2DParams.BODY_ANGLE_Y, params.bodyAngleY)
        setParameter(Live2DParams.BODY_ANGLE_Z, params.bodyAngleZ)
        setParameter(Live2DParams.SHOULDER, params.shoulder)
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

        setMouth(state.mouthOpen, state.mouthForm)

        val expressionId = expressionFromEnum(state.expression)
        if (expressionId != currentExpression || state.expressionIntensity != currentExpressionIntensity) {
            currentExpressionIntensity = state.expressionIntensity.coerceIn(0f, 1f)
            if (enableSmoothTransition) {
                expressionTransitionController.transitionTo(expressionId, currentExpressionIntensity, state.expressionTransitionMs)
                startAnimationUpdate()
            } else {
                currentExpression = expressionId
                setExpression(expressionId)
            }
        }

        if (state.gesture != currentGesture) {
            Log.d(TAG, "updateState: gesture changed $currentGesture -> ${state.gesture}")
            if (enableSmoothTransition) {
                transitionToGesture(state.gesture)
            } else {
                if (state.gesture == AvatarGesture.IDLE) {
                    stopMotion()
                } else {
                    playMotion(state.gesture.value, 0, false)
                }
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
            animationUpdateActive = false
            gestureTransitionController.cancelTransition()
            gestureAnimationPlayer.stop()
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
        Log.d(TAG, "=== attachSurfaceView() CALLED: surfaceView=$surfaceView ===")
        surfaceViewRef = WeakReference(surfaceView)
        hasSurfaceAttached = true
        Log.d(TAG, "=== attachSurfaceView: hasSurfaceAttached=$hasSurfaceAttached ===")

        // 只在 Surface 创建完成（C++ CubismFramework 已初始化）后才预加载
        // 否则预加载会被 nativePreloadMotionByPath 中的 IsInitialized() 检查跳过，导致首次播放 cache miss
        if (surfaceView.isSurfaceCreated) {
            Log.d(TAG, "=== attachSurfaceView: surface already created, preloading immediately ===")
            preloadCommonMotions()
        } else {
            Log.d(TAG, "=== attachSurfaceView: surface not ready, registering listener ===")
            surfaceView.onSurfaceCreatedListener = {
                Log.d(TAG, "=== onSurfaceCreatedListener triggered, preloading now ===")
                preloadCommonMotions()
                surfaceView.onSurfaceCreatedListener = null
            }
        }
    }

    /**
     * 预加载常用动作文件
     * 在 Surface 附加后调用，避免首次播放时的延迟
     */
    private fun preloadCommonMotions() {
        Log.d(TAG, "=== preloadCommonMotions() START ===")
        val commonMotions = listOf(
            "live2d/hiyori/motions/Hiyori_nod.motion3.json",
            "live2d/hiyori/motions/Hiyori_shake.motion3.json"
        )
        runOnRenderThread {
            commonMotions.forEach { path ->
                try {
                    JniBridgeJava.nativePreloadMotionByPath(path)
                    Log.d(TAG, "Preloaded motion: $path")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to preload motion: $path", e)
                }
            }
        }
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
     * 参数分层规则（上半身模式，手不可见）：
     * - Expression 层：控制眉毛、眼睛、脸颊、嘴形变形（ParamMouthForm）、头部微姿态（AngleX/Y/Z 的 Add 叠加）
     * - Gesture 层：控制头部功能性动作（AngleX/Y/Z 的覆盖值）、身体角度、肩膀
     *   注意：手在画面外不可见，因此不使用手臂/手部参数（ARM_* / HAND_*），
     *   仅通过头部角度 + 身体旋转 + 肩膀来表达全部语义。
     * - LipSync 层：独占 ParamMouthOpenY（嘴部开合度）
     *
     * 叠加机制：Gesture 通过 setParameter 直接覆盖基础状态，Expression 在 C++ LateUpdate 中以 Add 模式叠加。
     * 因此 Gesture 的头部角度会覆盖 Expression 的基础值，但 Expression 的 Add 偏移仍会生效。
     */
    private fun applyGesturePreset(gesture: AvatarGesture) {
        // 先归零所有参数
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

        // 头部参数范围：-30 到 30
        // 身体参数范围更小，视觉上更敏感，要设更小的值
        when (gesture) {
            AvatarGesture.IDLE -> Unit

            AvatarGesture.NOD -> {
                // 点头：头部明显下低
                setParameter(Live2DParams.ANGLE_Y, 30f)   // 大幅低头
                setParameter(Live2DParams.BODY_ANGLE_Y, 5f) // 身体微前倾
            }

            AvatarGesture.SHAKE -> {
                // 摇头：头部大幅左转 + 大幅左歪
                setParameter(Live2DParams.ANGLE_X, -25f)  // 大幅左转
                setParameter(Live2DParams.ANGLE_Z, -20f)  // 大幅左歪
            }

            AvatarGesture.WAVE -> {
                // 致意：头部右转 + 右歪 + 略仰，亲切感
                setParameter(Live2DParams.ANGLE_X, -20f)  // 右转
                setParameter(Live2DParams.ANGLE_Y, -10f)  // 略仰
                setParameter(Live2DParams.ANGLE_Z, -15f)  // 右歪
            }

            AvatarGesture.POINT_LEFT -> {
                // 看左：头部大幅左转 + 左歪
                setParameter(Live2DParams.ANGLE_X, -28f)  // 大幅左转
                setParameter(Live2DParams.ANGLE_Y, 5f)    // 微低头
                setParameter(Live2DParams.ANGLE_Z, 12f)   // 左歪
            }

            AvatarGesture.POINT_RIGHT -> {
                // 看右：头部大幅右转 + 右歪
                setParameter(Live2DParams.ANGLE_X, 28f)   // 大幅右转
                setParameter(Live2DParams.ANGLE_Y, 5f)    // 微低头
                setParameter(Live2DParams.ANGLE_Z, -12f)  // 右歪
            }

            AvatarGesture.POINT_FORWARD -> {
                // 示意前方：头部前低 + 略右转
                setParameter(Live2DParams.ANGLE_Y, 22f)   // 明显低头
                setParameter(Live2DParams.ANGLE_X, 8f)    // 略右转
                setParameter(Live2DParams.ANGLE_Z, -8f)   // 略右歪
            }

            AvatarGesture.BOW -> {
                // 鞠躬：头部深低 + 身体微前倾
                setParameter(Live2DParams.ANGLE_Y, 28f)   // 深低头
                setParameter(Live2DParams.ANGLE_Z, 8f)    // 右歪
                setParameter(Live2DParams.BODY_ANGLE_Y, 3f) // 身体微前倾
                setParameter(Live2DParams.SHOULDER, 0.5f) // 耸肩
            }

            AvatarGesture.THINKING_POSE -> {
                // 思考：头部仰起 + 大幅右偏 + 右歪
                setParameter(Live2DParams.ANGLE_X, 18f)   // 右偏
                setParameter(Live2DParams.ANGLE_Y, -15f)  // 仰头
                setParameter(Live2DParams.ANGLE_Z, 22f)   // 大幅右歪
            }

            AvatarGesture.GUIDE -> {
                // 引导：头部右转 + 前低
                setParameter(Live2DParams.ANGLE_X, 25f)   // 右转
                setParameter(Live2DParams.ANGLE_Y, 12f)   // 前低
                setParameter(Live2DParams.ANGLE_Z, -10f)  // 右歪
            }

            AvatarGesture.LOOK_UP -> {
                // 仰望：头部大幅后仰 + 右歪
                setParameter(Live2DParams.ANGLE_Y, -28f)  // 大幅仰头
                setParameter(Live2DParams.ANGLE_Z, 12f)   // 右歪
            }

            AvatarGesture.LISTEN -> {
                // 聆听：头部右转 + 前低 + 右歪
                setParameter(Live2DParams.ANGLE_X, 20f)   // 右转
                setParameter(Live2DParams.ANGLE_Y, 18f)   // 前低
                setParameter(Live2DParams.ANGLE_Z, -18f)  // 右歪
            }

            AvatarGesture.WELCOME_GESTURE -> {
                // 欢迎：头部左转 + 前低 + 左歪
                setParameter(Live2DParams.ANGLE_X, -18f)  // 左转
                setParameter(Live2DParams.ANGLE_Y, 15f)   // 前低
                setParameter(Live2DParams.ANGLE_Z, 15f)   // 左歪
            }
        }
    }

    private fun runOnRenderThread(action: () -> Unit) {
        val surfaceView = surfaceViewRef?.get()
        if (surfaceView == null) {
            Log.w(TAG, "runOnRenderThread skipped: surfaceView is null")
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
