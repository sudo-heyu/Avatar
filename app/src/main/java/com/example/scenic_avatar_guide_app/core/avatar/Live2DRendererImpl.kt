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
import com.example.scenic_avatar_guide_app.domain.model.AvatarFullState
import com.example.scenic_avatar_guide_app.domain.model.AvatarGesture
import com.example.scenic_avatar_guide_app.domain.model.AvatarState
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

    // 释放标志：防止 GL 线程在 renderer 释放后仍访问 SDK
    @Volatile
    private var isReleased = false

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
    // 驱动依据是 AvatarState.SPEAKING，而不是 mouthOpen>0：
    // 说话初期和词间停顿 mouthOpen=0 时也必须覆盖，防止 Idle 动画 O 型嘴固着。
    @Volatile
    private var speakingMouthOverride = false
    @Volatile
    private var isSpeaking = false
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

        // 将模型文件从 APK assets 提取到内部存储，加快后续加载速度
        // 已提取时幂等返回，首次约耗时 1-3 秒（后台 IO 线程，不阻塞 UI）
        try {
            JniBridgeJava.extractModelAssets()
            Log.d(TAG, "=== extractModelAssets() done ===")
        } catch (e: Exception) {
            Log.w(TAG, "=== extractModelAssets() failed, will use APK assets directly ===", e)
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
        // 使用 synchronized 确保与 release() 同步
        synchronized(this) {
            if (isReleased || !_isModelLoaded) return

            // 修复 Live2D 物理引擎崩溃：
            // 确保参数有效，防止 NaN/Infinity 传入 SDK
            if (mouthOpen.isNaN() || mouthOpen.isInfinite() ||
                mouthForm.isNaN() || mouthForm.isInfinite()) {
                Log.w(TAG, "setMouth: Invalid parameters detected, skipping (open=$mouthOpen, form=$mouthForm)")
                return
            }

            currentMouthOpen = mouthOpen.coerceIn(0f, 1f)
            // 降低整体张开程度：0.65f 缩放因子让最大张开度约为 65%
            val amplifiedMouthOpen = (currentMouthOpen * 0.65f).coerceAtMost(1.0f)

            // mouthWidthScale 仅在嘴巴实际张开时叠加；闭嘴时 Form 应归零，
            // 否则 ParamMouthForm 残留偏移值会与 Idle 动画残余 OpenY 叠加产生圆唇视觉。
            val scaledMouthForm = if (currentMouthOpen > 0.02f) {
                (mouthForm + mouthWidthScale).coerceIn(-1f, 1.5f)
            } else {
                0f
            }

            // 缓存嘴部覆盖值，用于每帧强制覆盖 SDK Idle 动画
            overrideMouthOpenY = amplifiedMouthOpen
            overrideMouthForm = scaledMouthForm
            // 覆盖开关：只允许在此处启用，禁用由 updateState() 在 SPEAKING 结束时负责。
            // 避免 Compose 重组延迟导致 isSpeaking 尚为 false 时误关闭每帧覆盖。
            if (isSpeaking || mouthOpen > 0.01f) {
                speakingMouthOverride = true
            }
        }

        runOnRenderThread {
            // 再次检查释放状态（runOnRenderThread 可能延迟执行）
            synchronized(this@Live2DRendererImpl) {
                if (isReleased || !_isModelLoaded) return@runOnRenderThread
            }
            try {
                JniBridgeJava.nativeSetParameter(Live2DParams.MOUTH_OPEN_Y, overrideMouthOpenY, 1.0f)
                JniBridgeJava.nativeSetParameter(Live2DParams.MOUTH_FORM, overrideMouthForm, 1.0f)
            } catch (e: Exception) {
                Log.w(TAG, "setMouth JNI call failed", e)
            }
        }
    }

    override fun setExpression(expressionId: String) {
        synchronized(this) {
            if (isReleased || !_isModelLoaded) return
        }

        currentExpression = expressionId
        runOnRenderThread {
            synchronized(this@Live2DRendererImpl) {
                if (isReleased || !_isModelLoaded) return@runOnRenderThread
            }
            try {
                JniBridgeJava.nativeSetExpression(expressionId)
            } catch (e: Exception) {
                Log.w(TAG, "setExpression JNI call failed", e)
            }
        }
    }

    override fun playMotion(group: String, index: Int, loop: Boolean) {
        synchronized(this) {
            if (isReleased || !_isModelLoaded) return
        }
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
        synchronized(this) {
            if (isReleased || !_isModelLoaded) return
        }
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
        synchronized(this) {
            if (isReleased || !_isModelLoaded) {
                Log.w(TAG, "transitionToGesture skipped: released=$isReleased, modelLoaded=$_isModelLoaded")
                return
            }
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
        synchronized(this) {
            if (isReleased || !_isModelLoaded) return
        }
        Log.d(TAG, "playIdleMotion: starting official Idle motion group")
        currentGesture = AvatarGesture.IDLE
        nativeMotionPlaying = false
        gestureAnimationPlayer.stop()
        motionTransitionManager.reset()

        // 停止动画更新循环，让 SDK 完全控制参数
        // Idle 动画由 SDK 管理，不需要我们手动更新参数
        animationUpdateActive = false

        runOnRenderThread {
            synchronized(this@Live2DRendererImpl) {
                if (isReleased || !_isModelLoaded) return@runOnRenderThread
            }
            try {
                // priority 1 = Idle，SDK 会自动淡入并循环
                JniBridgeJava.nativeStartRandomMotion("Idle", 1)
            } catch (e: Exception) {
                Log.w(TAG, "playIdleMotion JNI call failed", e)
            } catch (e: Error) {
                Log.e(TAG, "playIdleMotion native error", e)
            }
        }
    }

    /**
     * 播放原生 Live2D 动作
     */
    private fun playNativeMotion(motionPath: String, gesture: AvatarGesture) {
        synchronized(this) {
            if (isReleased || !_isModelLoaded) return
        }
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
            synchronized(this@Live2DRendererImpl) {
                if (isReleased || !_isModelLoaded) return@runOnRenderThread
            }
            Log.i(TAG, "=== Calling nativeStartMotionByPath: $motionPath ===")
            try {
                JniBridgeJava.nativeStartMotionByPath(motionPath, 3) // priority 3 = Force
            } catch (e: Exception) {
                Log.w(TAG, "playNativeMotion JNI call failed", e)
                nativeMotionPlaying = false
            } catch (e: Error) {
                Log.e(TAG, "playNativeMotion native error", e)
                nativeMotionPlaying = false
            }
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
        var deltaTime = currentTime - lastFrameTime
        lastFrameTime = currentTime

        // 修复 Live2D 物理引擎崩溃：
        // 限制 deltaTime 最大值为 100ms，防止应用暂停恢复或设备卡顿时
        // deltaTime 过大导致物理计算越界（NaN/Infinity）
        deltaTime = deltaTime.coerceIn(0, 100)

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
     * 修复 Live2D 物理引擎崩溃：添加参数有效性检查
     */
    private fun applyGestureParamsDirect(params: GestureParams) {
        // 检查参数有效性
        fun safeParam(value: Float, name: String): Float {
            if (value.isNaN() || value.isInfinite()) {
                Log.w(TAG, "Invalid $name parameter: $value, using 0")
                return 0f
            }
            return value.coerceIn(-100f, 100f)  // 合理范围限制
        }

        JniBridgeJava.nativeSetParameter(Live2DParams.ANGLE_X, safeParam(params.angleX, "angleX"), 1.0f)
        JniBridgeJava.nativeSetParameter(Live2DParams.ANGLE_Y, safeParam(params.angleY, "angleY"), 1.0f)
        JniBridgeJava.nativeSetParameter(Live2DParams.ANGLE_Z, safeParam(params.angleZ, "angleZ"), 1.0f)
        JniBridgeJava.nativeSetParameter(Live2DParams.BODY_ANGLE_X, safeParam(params.bodyAngleX, "bodyAngleX"), 1.0f)
        JniBridgeJava.nativeSetParameter(Live2DParams.BODY_ANGLE_Y, safeParam(params.bodyAngleY, "bodyAngleY"), 1.0f)
        JniBridgeJava.nativeSetParameter(Live2DParams.BODY_ANGLE_Z, safeParam(params.bodyAngleZ, "bodyAngleZ"), 1.0f)
        JniBridgeJava.nativeSetParameter(Live2DParams.SHOULDER, safeParam(params.shoulder, "shoulder"), 1.0f)

        // 眼球方向
        JniBridgeJava.nativeSetParameter(Live2DParams.EYE_BALL_X, safeParam(params.eyeBallX, "eyeBallX"), 1.0f)
        JniBridgeJava.nativeSetParameter(Live2DParams.EYE_BALL_Y, safeParam(params.eyeBallY, "eyeBallY"), 1.0f)

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
        synchronized(this) {
            if (isReleased || !_isModelLoaded) return
        }
        runOnRenderThread {
            synchronized(this@Live2DRendererImpl) {
                if (isReleased || !_isModelLoaded) return@runOnRenderThread
            }
            try {
                JniBridgeJava.nativeSetParameter(paramId, value, weight)
            } catch (e: Exception) {
                Log.w(TAG, "setParameter JNI call failed", e)
            }
        }
    }

    override fun updateState(state: AvatarFullState) {
        synchronized(this) {
            if (isReleased || !_isModelLoaded) return
        }

        // 更新说话状态标志（用于控制 Idle 动画与口型的竞争）
        val wasSpeaking = isSpeaking
        synchronized(this) {
            if (isReleased) return
            isSpeaking = (state.state == AvatarState.SPEAKING)
        }

        // 当从非 SPEAKING 变为 SPEAKING 时，提前激活覆盖标志
        // 避免 Idle 动画的嘴型在 setMouth() 首次调用前泄漏
        if (!wasSpeaking && isSpeaking) {
            speakingMouthOverride = true
        }

        // 当从 SPEAKING 变为非 SPEAKING 时，重置口型参数
        // 注意：口型参数由 mouthState 收集直接调用 setMouth()，这里只在状态切换时处理
        if (wasSpeaking && !isSpeaking) {
            speakingMouthOverride = false
            setMouth(0f, 0f)
        }

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
        synchronized(this) {
            if (isReleased) return
        }
        runOnRenderThread {
            synchronized(this@Live2DRendererImpl) {
                if (isReleased) return@runOnRenderThread
            }
            try {
                JniBridgeJava.nativeSetUpperBodyMode(enabled)
            } catch (e: Exception) {
                Log.w(TAG, "setUpperBodyMode JNI call failed", e)
            }
        }
    }

    override fun release() {
        // 在 synchronized 块内提前设置 isReleased，阻止后续任何新的 JNI 调用入队。
        // 已在 GL 队列中的 lambda 内部也会检查 isReleased，实际调用被跳过。
        // 这消除了"GL 队列残留 lambda 在 nativeOnDestroy 之后执行"的竞态窗口。
        synchronized(this) {
            if (!_isInitialized || isReleased) return
            isReleased = true
        }

        try {
            animationUpdateActive = false
            mainHandler.removeCallbacksAndMessages(null)
            motionTransitionManager.reset()
            gestureAnimationPlayer.stop()

            // 先停止 GL 持续渲染，消除 onDrawFrame 与 JNI 清理的竞态
            val surfaceView = surfaceViewRef?.get()
            surfaceView?.renderMode = android.opengl.GLSurfaceView.RENDERMODE_WHEN_DIRTY

            // 使用 CountDownLatch 确保 nativeOnDestroy() 在 GL 线程中同步执行完成
            if (surfaceView != null) {
                val latch = java.util.concurrent.CountDownLatch(1)
                // 直接调用 surfaceView.runOnRenderThread（绕过 Live2DRendererImpl.runOnRenderThread，
                // 后者已因 isReleased=true 而短路）
                surfaceView.runOnRenderThread {
                    try {
                        // isReleased 已在主线程设置，此处直接执行 Native 清理
                        JniBridgeJava.nativeOnStop()
                        JniBridgeJava.nativeOnDestroy()
                    } catch (e: Exception) {
                        Log.e(TAG, "Native release failed on GL thread", e)
                    } finally {
                        latch.countDown()
                    }
                }
                // 等待 GL 线程完成释放（最多 5 秒）
                try {
                    if (!latch.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                        // 超时：GL 线程可能卡住。不在主线程强制调用 nativeOnStop/nativeOnDestroy，
                        // 避免与 GL 线程并发析构导致 use-after-free。GL 线程最终会执行清理 lambda。
                        Log.w(TAG, "Timeout waiting for GL thread release — skipping forced cleanup to avoid double-destroy")
                    }
                } catch (e: InterruptedException) {
                    Log.w(TAG, "Interrupted while waiting for GL thread release")
                    Thread.currentThread().interrupt()
                }
            } else {
                // SurfaceView 已释放，GL 线程已退出，直接在主线程清理
                try {
                    JniBridgeJava.nativeOnStop()
                    JniBridgeJava.nativeOnDestroy()
                } catch (e: Exception) {
                    Log.e(TAG, "Native release failed on main thread (fallback)", e)
                }
            }

            isSpeaking = false
            speakingMouthOverride = false

            // 清除回调和引用
            surfaceViewRef?.get()?.onAfterDrawFrame = null
            surfaceViewRef = null
            hasSurfaceAttached = false

            _isInitialized = false
            _isModelLoaded = false
            currentModelPath = null
            Log.i(TAG, "Live2D renderer released")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release Live2D renderer", e)
        }
    }

    /**
     * 仅解绑当前 GLSurfaceView，保留进程内的 Native Cubism/模型单例。
     *
     * 官方 Android 示例在 Activity.onStop() 才调用 nativeOnStop() 释放模型；
     * Compose 页面销毁、导航切换或后台恢复时只应断开 Surface，避免重新 LoadAssets。
     */
    fun detachSurfaceView() {
        synchronized(this) {
            animationUpdateActive = false
            mainHandler.removeCallbacksAndMessages(null)
            surfaceViewRef?.get()?.let { surfaceView ->
                surfaceView.onAfterDrawFrame = null
                surfaceView.onSurfaceCreatedListener = null
            }
            surfaceViewRef = null
            hasSurfaceAttached = false
        }
    }

    fun attachSurfaceView(surfaceView: Live2DGLSurfaceView) {
        Log.d(TAG, "=== attachSurfaceView() CALLED ===")
        surfaceViewRef = WeakReference(surfaceView)
        hasSurfaceAttached = true

        // 在 SDK 动画渲染完成后强制覆盖嘴部参数，确保口型同步优先于 Idle 动画
        // 注意：此回调在 GL 线程执行，与 nativeOnDrawFrame 串行
        // 使用 synchronized 块确保与 release() 的同步
        surfaceView.onAfterDrawFrame = {
            // 使用 synchronized 确保与 release() 同步，避免竞态条件
            synchronized(this@Live2DRendererImpl) {
                if (isReleased || !_isModelLoaded || !speakingMouthOverride) {
                    return@synchronized
                }
                // 在 synchronized 块内捕获变量值，避免后续变化
                val openY = overrideMouthOpenY
                val form = overrideMouthForm

                // 修复 Live2D 物理引擎崩溃：
                // 确保参数有效，防止 NaN/Infinity 传入 SDK
                if (openY.isNaN() || openY.isInfinite() ||
                    form.isNaN() || form.isInfinite()) {
                    return@synchronized
                }

                try {
                    JniBridgeJava.nativeSetParameter(Live2DParams.MOUTH_OPEN_Y, openY, 1.0f)
                    JniBridgeJava.nativeSetParameter(Live2DParams.MOUTH_FORM, form, 1.0f)
                } catch (e: Exception) {
                    Log.w(TAG, "onAfterDrawFrame JNI call failed", e)
                }
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
            synchronized(this@Live2DRendererImpl) {
                if (isReleased || !_isModelLoaded) return@runOnRenderThread
            }
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
                setParameter(Live2DParams.EYE_BALL_X, -1f)
            }
            AvatarGesture.POINT_RIGHT -> {
                setParameter(Live2DParams.ANGLE_X, 28f)
                setParameter(Live2DParams.ANGLE_Y, 5f)
                setParameter(Live2DParams.ANGLE_Z, -12f)
                setParameter(Live2DParams.EYE_BALL_X, 1f)
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
                setParameter(Live2DParams.ANGLE_Y, 45f)
                setParameter(Live2DParams.ANGLE_Z, 10f)
                setParameter(Live2DParams.BODY_ANGLE_Y, -12f)
                setParameter(Live2DParams.EYE_BALL_Y, 1.0f)
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
        if (isReleased) return
        val surfaceView = surfaceViewRef?.get()
        if (surfaceView == null) {
            Log.w(TAG, "runOnRenderThread FAILED: surfaceView is null, surfaceAttached=$hasSurfaceAttached")
            return
        }
        surfaceView.runOnRenderThread {
            if (!isReleased) action()
        }
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
