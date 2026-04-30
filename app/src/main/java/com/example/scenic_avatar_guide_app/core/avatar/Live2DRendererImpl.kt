package com.example.scenic_avatar_guide_app.core.avatar

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.scenic_avatar_guide_app.core.avatar.animation.ExpressionTransitionController
import com.example.scenic_avatar_guide_app.core.avatar.animation.GestureParams
import com.example.scenic_avatar_guide_app.core.avatar.animation.GestureAnimation
import com.example.scenic_avatar_guide_app.core.avatar.animation.GestureAnimationPlayer
import com.example.scenic_avatar_guide_app.core.avatar.animation.MotionTransitionManager
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
 *
 * 动作过渡机制 v2.1：
 * - 使用 MotionTransitionManager 管理动作层混合
 * - 支持动作间的交叉淡入淡出 (Cross-fade)
 * - 原生动作播放期间记录最后参数，结束后平滑过渡
 */
class Live2DRendererImpl(
    private val context: Context
) : Live2DRenderer {

    companion object {
        private const val TAG = "L2D"
        private const val MODEL_LOAD_TIMEOUT = 10000L
        private const val DEFAULT_TRANSITION_MS = 300L
        private const val ANIMATION_TICK_MS = 16L // ~60fps
    }

    // 主线程 Handler（用于后备动画更新）
    private val mainHandler = Handler(Looper.getMainLooper())

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

    // 动作过渡管理器
    private val motionTransitionManager = MotionTransitionManager()

    // 表情过渡控制器
    private val expressionTransitionController = ExpressionTransitionController()

    // 动作动画播放器（用于点头、摇头等关键帧动画）
    private val gestureAnimationPlayer = GestureAnimationPlayer()

    // 是否启用平滑过渡
    private var enableSmoothTransition = true

    // 动画更新是否激活
    private var animationUpdateActive = false

    // 上次动画循环执行时间（用于检测卡住）
    private var lastLoopTime: Long = 0

    // 原生动作状态
    private var nativeMotionPlaying = false
    private var nativeMotionStartTime: Long = 0
    private var nativeMotionDurationMs: Long = 0

    // SDK 报告的动作完成状态（由渲染线程更新）
    @Volatile
    private var sdkMotionFinished = false

    // 待执行的动作（用于动作队列）
    private var pendingGestureAfterNative: AvatarGesture? = null
    private var pendingTransitionMsAfterNative: Long = DEFAULT_TRANSITION_MS

    // 嘴部宽度缩放因子（偏移值，0=原始，正值=更大更圆，负值=更扁）
    var mouthWidthScale: Float = 0.3f

    // 说话时强制覆盖 SDK 动画的嘴部参数（解决 Idle 动画与口型同步竞争）
    @Volatile
    private var speakingMouthOverride = false
    private var overrideMouthOpenY = 0f
    private var overrideMouthForm = 0f

    // 上一帧时间
    private var lastFrameTime: Long = 0

    override val isInitialized: Boolean get() = _isInitialized
    override val isModelLoaded: Boolean get() = _isModelLoaded && hasSurfaceAttached

    override fun initialize() {
        Log.d(TAG, "=== initialize() START: _isInitialized=$_isInitialized ===")
        if (_isInitialized) {
            Log.d(TAG, "=== initialize() SKIPPED: already initialized ===")
            return
        }

        try {
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

    override suspend fun loadModel(modelPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== loadModel() START: path=$modelPath ===")
        if (!_isInitialized) {
            initialize()
        }

        try {
            withTimeout(MODEL_LOAD_TIMEOUT) {
                val fileData = context.assets.open(modelPath)
                val fileSize = fileData.available()
                fileData.close()
                if (fileSize == 0) {
                    throw IllegalStateException("Model file is empty: $modelPath")
                }

                val jniData = JniBridgeJava.LoadFile(modelPath)
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

    override fun setMouth(mouthOpen: Float, mouthForm: Float) {
        if (!_isModelLoaded) return

        currentMouthOpen = mouthOpen.coerceIn(0f, 1f)
        // 降低整体张开程度：0.65f 缩放因子让最大张开度约为 65%
        val amplifiedMouthOpen = (currentMouthOpen * 0.65f).coerceAtMost(1.0f)

        val scaledMouthForm = (mouthForm + mouthWidthScale).coerceIn(-1f, 1.5f)

        // 缓存嘴部覆盖值，用于每帧强制覆盖 SDK Idle 动画
        overrideMouthOpenY = amplifiedMouthOpen
        overrideMouthForm = scaledMouthForm
        speakingMouthOverride = currentMouthOpen > 0f

        runOnRenderThread {
            JniBridgeJava.nativeSetParameter(Live2DParams.MOUTH_OPEN_Y, amplifiedMouthOpen, 1.0f)
            JniBridgeJava.nativeSetParameter(Live2DParams.MOUTH_FORM, scaledMouthForm, 1.0f)
        }
    }

    override fun setExpression(expressionId: String) {
        if (!_isModelLoaded) return

        currentExpression = expressionId
        runOnRenderThread {
            JniBridgeJava.nativeSetExpression(expressionId)
        }
    }

    override fun playMotion(group: String, index: Int, loop: Boolean) {
        if (!_isModelLoaded) return
        val gesture = AvatarGesture.fromValue(group)
        Log.d(TAG, "playMotion: group=$group, gesture=$gesture")

        if (enableSmoothTransition) {
            transitionToGesture(gesture)
        } else {
            currentGesture = gesture
            applyGesturePreset(gesture)
        }
    }

    override fun stopMotion() {
        if (!_isModelLoaded) return

        Log.d(TAG, "stopMotion")
        nativeMotionPlaying = false
        pendingGestureAfterNative = null
        gestureAnimationPlayer.stop()

        if (enableSmoothTransition) {
            transitionToGesture(AvatarGesture.IDLE)
        } else {
            currentGesture = AvatarGesture.IDLE
            motionTransitionManager.reset()
            applyGesturePreset(AvatarGesture.IDLE)
        }
    }

    /**
     * 平滑过渡到指定动作（公开接口）
     */
    fun transitionToGesture(
        gesture: AvatarGesture,
        durationMs: Long = DEFAULT_TRANSITION_MS
    ) {
        if (!_isModelLoaded) {
            Log.w(TAG, "transitionToGesture skipped: model not loaded")
            return
        }

        Log.d(TAG, "transitionToGesture: $currentGesture -> $gesture, durationMs=$durationMs, nativePlaying=$nativeMotionPlaying")

        // 如果正在播放原生动作，记录待执行的动作并确保动画循环在运行
        if (nativeMotionPlaying) {
            // 检查是否卡住了（超过动作时长的 2 倍）
            val elapsed = System.currentTimeMillis() - nativeMotionStartTime
            if (elapsed > nativeMotionDurationMs * 2) {
                Log.w(TAG, "Native motion appears stuck (elapsed=$elapsed > ${nativeMotionDurationMs * 2}), forcing reset")
                nativeMotionPlaying = false
                pendingGestureAfterNative = null
                // 继续执行新动作
            } else {
                Log.d(TAG, "Native motion playing, queueing: $gesture")
                pendingGestureAfterNative = gesture
                pendingTransitionMsAfterNative = durationMs
                // 确保动画循环在运行，以便检测原生动作完成
                startAnimationUpdate()
                return
            }
        }

        transitionToGestureInternal(gesture, durationMs)
    }

    /**
     * 内部方法：执行动作过渡（不检查 nativeMotionPlaying）
     */
    private fun transitionToGestureInternal(
        gesture: AvatarGesture,
        durationMs: Long
    ) {
        Log.d(TAG, "transitionToGestureInternal: $currentGesture -> $gesture, durationMs=$durationMs")

        // 停止关键帧动画
        gestureAnimationPlayer.stop()

        // IDLE 特殊处理：播放官方 Idle 动作组（动态待机动画）
        if (gesture == AvatarGesture.IDLE) {
            playIdleMotion()
            return
        }

        val motionPath = getMotionPathForGesture(gesture)

        if (motionPath != null) {
            // 有原生动作文件：直接播放
            playNativeMotion(motionPath, gesture)
        } else {
            // 无原生动作文件：使用关键帧动画或直接过渡
            val animation = GestureAnimation.fromGesture(gesture)
            if (animation != null) {
                Log.d(TAG, "Using keyframe animation for: $gesture")
                val fromParams = motionTransitionManager.getCurrentParams()
                gestureAnimationPlayer.play(animation, fromParams)
                currentGesture = gesture
            } else {
                // 直接使用过渡管理器
                Log.d(TAG, "Using motion transition for: $gesture")
                val targetParams = GestureParams.fromGesture(gesture)
                motionTransitionManager.transitionTo(gesture, targetParams, durationMs)
                currentGesture = gesture
            }
        }

        startAnimationUpdate()
    }

    /**
     * 播放官方 Idle 动作组（动态待机动画）
     * 使用 priority=1（Idle），SDK 会自动循环播放
     */
    private fun playIdleMotion() {
        Log.d(TAG, "playIdleMotion: starting official Idle motion group")
        currentGesture = AvatarGesture.IDLE
        nativeMotionPlaying = false
        gestureAnimationPlayer.stop()
        motionTransitionManager.reset()

        // 停止动画更新循环，让 SDK 完全控制参数
        // Idle 动画由 SDK 管理，不需要我们手动更新参数
        animationUpdateActive = false

        runOnRenderThread {
            // priority 1 = Idle，SDK 会自动淡入并循环
            JniBridgeJava.nativeStartRandomMotion("Idle", 1)
        }
    }

    /**
     * 播放原生 Live2D 动作
     */
    private fun playNativeMotion(motionPath: String, gesture: AvatarGesture) {
        Log.i(TAG, "=== playNativeMotion START: path=$motionPath, gesture=$gesture ===")

        currentGesture = gesture
        sdkMotionFinished = false

        // 估算动作时长
        nativeMotionDurationMs = estimateMotionDuration(motionPath)
        Log.d(TAG, "Estimated motion duration: $nativeMotionDurationMs ms")

        // 标记原生动作开始
        nativeMotionPlaying = true
        nativeMotionStartTime = System.currentTimeMillis()

        // 在渲染线程播放原生动作
        runOnRenderThread {
            Log.i(TAG, "=== Calling nativeStartMotionByPath: $motionPath ===")
            JniBridgeJava.nativeStartMotionByPath(motionPath, 3) // priority 3 = Force
            Log.i(TAG, "=== nativeStartMotionByPath returned ===")
        }

        Log.i(TAG, "=== playNativeMotion END ===")
    }

    private fun estimateMotionDuration(motionPath: String): Long = when {
        motionPath.contains("nod") -> 1200L
        motionPath.contains("shake") -> 1400L
        motionPath.contains("wave") -> 1400L
        motionPath.contains("welcome") -> 2000L
        motionPath.contains("look_left") -> 1300L
        motionPath.contains("look_right") -> 1300L
        motionPath.contains("point_forward") -> 1500L
        motionPath.contains("bow") -> 1800L
        motionPath.contains("thinking") -> 3000L
        motionPath.contains("guide") -> 1600L
        motionPath.contains("look_up") -> 1800L
        motionPath.contains("listen") -> 1800L
        else -> 1000L
    }

    private fun getMotionPathForGesture(gesture: AvatarGesture): String? = when (gesture) {
        AvatarGesture.NOD -> "live2d/hiyori/motions/Hiyori_nod.motion3.json"
        AvatarGesture.SHAKE -> "live2d/hiyori/motions/Hiyori_shake.motion3.json"
        AvatarGesture.WAVE -> "live2d/hiyori/motions/Hiyori_wave.motion3.json"
        AvatarGesture.WELCOME_GESTURE -> "live2d/hiyori/motions/Hiyori_welcome.motion3.json"
        AvatarGesture.POINT_LEFT -> "live2d/hiyori/motions/Hiyori_look_left.motion3.json"
        AvatarGesture.POINT_RIGHT -> "live2d/hiyori/motions/Hiyori_look_right.motion3.json"
        AvatarGesture.POINT_FORWARD -> "live2d/hiyori/motions/Hiyori_point_forward.motion3.json"
        AvatarGesture.BOW -> "live2d/hiyori/motions/Hiyori_bow.motion3.json"
        AvatarGesture.THINKING_POSE -> "live2d/hiyori/motions/Hiyori_thinking.motion3.json"
        AvatarGesture.GUIDE -> "live2d/hiyori/motions/Hiyori_guide.motion3.json"
        AvatarGesture.LOOK_UP -> "live2d/hiyori/motions/Hiyori_look_up.motion3.json"
        AvatarGesture.LISTEN -> "live2d/hiyori/motions/Hiyori_listen.motion3.json"
        else -> null
    }

    fun setSmoothTransitionEnabled(enabled: Boolean) {
        enableSmoothTransition = enabled
    }

    private fun startAnimationUpdate() {
        if (animationUpdateActive) {
            return
        }

        Log.d(TAG, "startAnimationUpdate: starting animation loop")
        animationUpdateActive = true
        lastFrameTime = System.currentTimeMillis()
        lastLoopTime = System.currentTimeMillis()

        // 使用主线程 Handler 定期更新
        scheduleAnimationTick()
    }

    private fun scheduleAnimationTick() {
        if (!animationUpdateActive) return
        mainHandler.postDelayed({ animationTick() }, ANIMATION_TICK_MS)
    }

    private fun animationTick() {
        if (!animationUpdateActive || !_isModelLoaded) {
            animationUpdateActive = false
            return
        }

        // 更新最后循环时间
        lastLoopTime = System.currentTimeMillis()

        val currentTime = System.currentTimeMillis()
        val deltaTime = currentTime - lastFrameTime
        lastFrameTime = currentTime

        var needsContinue = false

        // 1. 处理原生动作
        if (nativeMotionPlaying) {
            val elapsed = currentTime - nativeMotionStartTime

            // 在渲染线程检查 SDK 动作完成状态（线程安全）
            // 使用时间判断作为主要完成条件，避免频繁的跨线程调用
            val timeBasedFinished = elapsed >= nativeMotionDurationMs

            if (timeBasedFinished || sdkMotionFinished) {
                Log.i(TAG, "Native motion completed: elapsed=$elapsed, duration=$nativeMotionDurationMs, sdkFinished=$sdkMotionFinished")
                nativeMotionPlaying = false
                sdkMotionFinished = false

                // 重置眼睛参数，确保自动眨眼系统能正常接管
                runOnRenderThread {
                    JniBridgeJava.nativeSetParameter(Live2DParams.EYE_L_OPEN, 1.0f, 1.0f)
                    JniBridgeJava.nativeSetParameter(Live2DParams.EYE_R_OPEN, 1.0f, 1.0f)
                    Log.d(TAG, "Eye parameters reset to 1.0 after native motion")
                }

                if (pendingGestureAfterNative != null) {
                    Log.d(TAG, "Processing pending gesture: ${pendingGestureAfterNative}")
                    val pending = pendingGestureAfterNative!!
                    val pendingMs = pendingTransitionMsAfterNative
                    pendingGestureAfterNative = null
                    // 直接处理，不调用 transitionToGesture 避免队列化
                    transitionToGestureInternal(pending, pendingMs)
                    return
                } else {
                    // 动作完成后播放官方 Idle 动作组（动态待机）
                    playIdleMotion()
                }
            } else {
                // 原生动作仍在播放，在渲染线程异步检查 SDK 状态
                runOnRenderThread {
                    if (JniBridgeJava.nativeIsMotionFinished()) {
                        sdkMotionFinished = true
                    }
                }
                needsContinue = true
            }
        }

        // 2. 更新关键帧动画
        if (!nativeMotionPlaying && gestureAnimationPlayer.isPlaying()) {
            gestureAnimationPlayer.update()
            val animParams = gestureAnimationPlayer.getCurrentParams()
            motionTransitionManager.updateCurrentLayerParams(animParams)

            // 在渲染线程应用参数
            runOnRenderThread {
                applyGestureParamsDirect(animParams)
            }

            if (!gestureAnimationPlayer.isPlaying()) {
                motionTransitionManager.transitionTo(
                    AvatarGesture.IDLE,
                    GestureParams.IDLE,
                    DEFAULT_TRANSITION_MS
                )
                currentGesture = AvatarGesture.IDLE
            }
            needsContinue = true
        }

        // 3. 更新动作过渡
        if (!nativeMotionPlaying && !gestureAnimationPlayer.isPlaying() && motionTransitionManager.isInTransition()) {
            motionTransitionManager.update(deltaTime)
            val params = motionTransitionManager.getCurrentParams()

            runOnRenderThread {
                applyGestureParamsDirect(params)
            }
            needsContinue = true
        }

        // 4. 更新表情过渡
        if (expressionTransitionController.isTransitioning()) {
            expressionTransitionController.update()
            val (expressionId, _) = expressionTransitionController.getCurrentExpression()
            if (expressionId != currentExpression) {
                currentExpression = expressionId
                runOnRenderThread {
                    JniBridgeJava.nativeSetExpression(expressionId)
                }
            }
            needsContinue = needsContinue || expressionTransitionController.isTransitioning()
        }

        // 继续调度
        if (needsContinue || nativeMotionPlaying || gestureAnimationPlayer.isPlaying() || motionTransitionManager.isInTransition()) {
            scheduleAnimationTick()
        } else {
            Log.d(TAG, "Animation loop completed, no more updates needed")
            animationUpdateActive = false
        }
    }

    /**
     * 直接应用动作参数（必须在渲染线程调用）
     */
    private fun applyGestureParamsDirect(params: GestureParams) {
        JniBridgeJava.nativeSetParameter(Live2DParams.ANGLE_X, params.angleX, 1.0f)
        JniBridgeJava.nativeSetParameter(Live2DParams.ANGLE_Y, params.angleY, 1.0f)
        JniBridgeJava.nativeSetParameter(Live2DParams.ANGLE_Z, params.angleZ, 1.0f)
        JniBridgeJava.nativeSetParameter(Live2DParams.BODY_ANGLE_X, params.bodyAngleX, 1.0f)
        JniBridgeJava.nativeSetParameter(Live2DParams.BODY_ANGLE_Y, params.bodyAngleY, 1.0f)
        JniBridgeJava.nativeSetParameter(Live2DParams.BODY_ANGLE_Z, params.bodyAngleZ, 1.0f)
        JniBridgeJava.nativeSetParameter(Live2DParams.SHOULDER, params.shoulder, 1.0f)

        // 眼球方向
        JniBridgeJava.nativeSetParameter(Live2DParams.EYE_BALL_X, params.eyeBallX, 1.0f)
        JniBridgeJava.nativeSetParameter(Live2DParams.EYE_BALL_Y, params.eyeBallY, 1.0f)

        // 手臂参数归零
        JniBridgeJava.nativeSetParameter(Live2DParams.ARM_LA, 0f, 1.0f)
        JniBridgeJava.nativeSetParameter(Live2DParams.ARM_RA, 0f, 1.0f)
        JniBridgeJava.nativeSetParameter(Live2DParams.ARM_LB, 0f, 1.0f)
        JniBridgeJava.nativeSetParameter(Live2DParams.ARM_RB, 0f, 1.0f)
        JniBridgeJava.nativeSetParameter(Live2DParams.HAND_L, 0f, 1.0f)
        JniBridgeJava.nativeSetParameter(Live2DParams.HAND_R, 0f, 1.0f)
        JniBridgeJava.nativeSetParameter(Live2DParams.HAND_LB, 0f, 1.0f)
        JniBridgeJava.nativeSetParameter(Live2DParams.HAND_RB, 0f, 1.0f)
    }

    override fun setParameter(paramId: String, value: Float, weight: Float) {
        if (!_isModelLoaded) return
        runOnRenderThread {
            JniBridgeJava.nativeSetParameter(paramId, value, weight)
        }
    }

    override fun updateState(state: AvatarFullState) {
        if (!_isModelLoaded) return

        setMouth(state.mouthOpen, state.mouthForm)

        val expressionId = expressionFromEnum(state.expression)
        if (expressionId != currentExpression || state.expressionIntensity != currentExpressionIntensity) {
            currentExpressionIntensity = state.expressionIntensity.coerceIn(0f, 1f)
            if (enableSmoothTransition) {
                expressionTransitionController.transitionTo(expressionId, currentExpressionIntensity, state.expressionTransitionMs)
                currentExpression = expressionId
                startAnimationUpdate()
            } else {
                currentExpression = expressionId
                setExpression(expressionId)
            }
        }

        if (state.gesture != currentGesture) {
            Log.d(TAG, "updateState: gesture changed $currentGesture -> ${state.gesture}")
            if (enableSmoothTransition) {
                transitionToGesture(state.gesture, state.gestureTransitionMs)
            } else {
                if (state.gesture == AvatarGesture.IDLE) {
                    stopMotion()
                } else {
                    playMotion(state.gesture.value, 0, false)
                }
            }
        }
    }

    override fun setUpperBodyMode(enabled: Boolean) {
        runOnRenderThread {
            JniBridgeJava.nativeSetUpperBodyMode(enabled)
        }
    }

    override fun release() {
        if (!_isInitialized) return

        try {
            animationUpdateActive = false
            mainHandler.removeCallbacksAndMessages(null)
            motionTransitionManager.reset()
            gestureAnimationPlayer.stop()
            surfaceViewRef?.get()?.onAfterDrawFrame = null
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
        Log.d(TAG, "=== attachSurfaceView() CALLED ===")
        surfaceViewRef = WeakReference(surfaceView)
        hasSurfaceAttached = true

        // 在 SDK 动画渲染完成后强制覆盖嘴部参数，确保口型同步优先于 Idle 动画
        surfaceView.onAfterDrawFrame = {
            if (speakingMouthOverride) {
                JniBridgeJava.nativeSetParameter(Live2DParams.MOUTH_OPEN_Y, overrideMouthOpenY, 1.0f)
                JniBridgeJava.nativeSetParameter(Live2DParams.MOUTH_FORM, overrideMouthForm, 1.0f)
            }
        }

        if (surfaceView.isSurfaceCreated) {
            preloadCommonMotions()
        } else {
            surfaceView.onSurfaceCreatedListener = {
                preloadCommonMotions()
                surfaceView.onSurfaceCreatedListener = null
            }
        }
    }

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

    private fun expressionFromEnum(expression: AvatarExpression): String = expression.value

    private fun applyGesturePreset(gesture: AvatarGesture) {
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
                setParameter(Live2DParams.ANGLE_Y, 30f)
                setParameter(Live2DParams.BODY_ANGLE_Y, 5f)
            }
            AvatarGesture.SHAKE -> {
                setParameter(Live2DParams.ANGLE_X, -25f)
                setParameter(Live2DParams.ANGLE_Z, -20f)
            }
            AvatarGesture.WAVE -> {
                setParameter(Live2DParams.ANGLE_X, -20f)
                setParameter(Live2DParams.ANGLE_Y, -10f)
                setParameter(Live2DParams.ANGLE_Z, -15f)
            }
            AvatarGesture.POINT_LEFT -> {
                setParameter(Live2DParams.ANGLE_X, -28f)
                setParameter(Live2DParams.ANGLE_Y, 5f)
                setParameter(Live2DParams.ANGLE_Z, 12f)
            }
            AvatarGesture.POINT_RIGHT -> {
                setParameter(Live2DParams.ANGLE_X, 28f)
                setParameter(Live2DParams.ANGLE_Y, 5f)
                setParameter(Live2DParams.ANGLE_Z, -12f)
            }
            AvatarGesture.POINT_FORWARD -> {
                setParameter(Live2DParams.ANGLE_Y, 22f)
                setParameter(Live2DParams.ANGLE_X, 8f)
                setParameter(Live2DParams.ANGLE_Z, -8f)
            }
            AvatarGesture.BOW -> {
                setParameter(Live2DParams.ANGLE_Y, 28f)
                setParameter(Live2DParams.ANGLE_Z, 8f)
                setParameter(Live2DParams.BODY_ANGLE_Y, 3f)
                setParameter(Live2DParams.SHOULDER, 0.5f)
            }
            AvatarGesture.THINKING_POSE -> {
                setParameter(Live2DParams.ANGLE_X, 18f)
                setParameter(Live2DParams.ANGLE_Y, -15f)
                setParameter(Live2DParams.ANGLE_Z, 22f)
            }
            AvatarGesture.GUIDE -> {
                setParameter(Live2DParams.ANGLE_X, 25f)
                setParameter(Live2DParams.ANGLE_Y, 12f)
                setParameter(Live2DParams.ANGLE_Z, -10f)
            }
            AvatarGesture.LOOK_UP -> {
                setParameter(Live2DParams.ANGLE_Y, -28f)
                setParameter(Live2DParams.ANGLE_Z, 12f)
            }
            AvatarGesture.LISTEN -> {
                setParameter(Live2DParams.ANGLE_X, 20f)
                setParameter(Live2DParams.ANGLE_Y, 18f)
                setParameter(Live2DParams.ANGLE_Z, -18f)
            }
            AvatarGesture.WELCOME_GESTURE -> {
                setParameter(Live2DParams.ANGLE_X, -18f)
                setParameter(Live2DParams.ANGLE_Y, 15f)
                setParameter(Live2DParams.ANGLE_Z, 15f)
            }
        }
    }

    private fun runOnRenderThread(action: () -> Unit) {
        val surfaceView = surfaceViewRef?.get()
        if (surfaceView == null) {
            Log.w(TAG, "runOnRenderThread FAILED: surfaceView is null, surfaceAttached=$hasSurfaceAttached")
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
