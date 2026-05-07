package com.example.scenic_avatar_guide_app.ui.components

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.scenic_avatar_guide_app.core.avatar.Live2DGLSurfaceView
import com.example.scenic_avatar_guide_app.core.avatar.Live2DRendererImpl
import com.example.scenic_avatar_guide_app.domain.model.AvatarExpression
import com.example.scenic_avatar_guide_app.domain.model.AvatarFullState
import com.example.scenic_avatar_guide_app.domain.model.AvatarGesture
import com.example.scenic_avatar_guide_app.domain.model.AvatarState
import com.example.scenic_avatar_guide_app.ui.theme.Primary
import com.example.scenic_avatar_guide_app.ui.theme.PrimaryLight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 数字人展示组件
 * 加载完成前播放 Lottie 过渡动画，随后无缝淡入 Live2D 模型
 *
 * 口型同步优化：
 * mouthState 直接通过 StateFlow 传递给渲染器，绕过 Compose 状态层，
 * 避免高频更新触发 Compose 重组导致 HWUI 崩溃。
 *
 * @param onRendererReady 渲染器初始化完成后的回调，提供渲染器引用
 *         用于设置外部状态重置回调，解决 StateFlow 合并跳过 IDLE 问题
 */
@Composable
fun AvatarView(
    avatarState: AvatarState,
    modifier: Modifier = Modifier,
    fullState: AvatarFullState? = null,
    mouthState: StateFlow<Pair<Float, Float>>? = null,
    enableLive2D: Boolean = true,
    showUpperBodyOnly: Boolean = false,
    onRendererReady: ((Live2DRendererImpl) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Live2D 渲染器状态
    var renderer by remember { mutableStateOf<Live2DRendererImpl?>(null) }
    val latestRenderer by rememberUpdatedState(renderer)
    var isLive2DReady by remember { mutableStateOf(false) }
    var live2dError by remember { mutableStateOf<String?>(null) }

    // Lottie 状态
    val composition by rememberLottieComposition(
        LottieCompositionSpec.Asset("lottie/avatar_idle.json")
    )
    var lottieFinished by remember { mutableStateOf(false) }
    val lottieState = animateLottieCompositionAsState(
        composition = composition,
        isPlaying = true,
        iterations = 1,
    )

    LaunchedEffect(lottieState.isAtEnd) {
        if (lottieState.isAtEnd) {
            lottieFinished = true
        }
    }

    LaunchedEffect(composition) {
        if (composition == null) {
            lottieFinished = true
        }
    }

    // 初始化 Live2D 渲染器（仅在启用时）
    LaunchedEffect(enableLive2D) {
        if (enableLive2D && renderer == null) {
            try {
                val newRenderer = Live2DRendererImpl(context)
                newRenderer.initialize()
                val result = newRenderer.loadModel("live2d/hiyori/Hiyori.model3.json")
                isLive2DReady = result.isSuccess
                renderer = newRenderer
                Log.d("AvatarView", "Live2D init success=$isLive2DReady")
                fullState?.let { state -> newRenderer.updateState(state) }
                // 通知外部渲染器已就绪，用于设置状态重置回调
                if (result.isSuccess) {
                    onRendererReady?.invoke(newRenderer)
                }
            } catch (e: Exception) {
                Log.e("AvatarView", "Live2D init failed", e)
                isLive2DReady = false
                live2dError = e.message
            }
        }
    }

    // 当 fullState 变化时更新渲染器（表情、动作等，不含口型）
    LaunchedEffect(fullState) {
        fullState?.let { state ->
            renderer?.updateState(state)
                ?: Log.w("AvatarView", "updateState skipped: renderer is null")
        }
    }

    // 口型同步：直接收集 mouthState 并调用 setMouth，绕过 Compose 状态层
    // 避免高频更新触发 Compose 重组导致 HWUI 崩溃
    LaunchedEffect(renderer, mouthState) {
        val currentRenderer = renderer ?: return@LaunchedEffect
        val currentMouthState = mouthState ?: return@LaunchedEffect
        currentMouthState.collect { (open, form) ->
            currentRenderer.setMouth(open, form)
        }
    }

    val live2DView = remember(enableLive2D) {
        if (!enableLive2D) {
            null
        } else {
            Live2DGLSurfaceView(context).apply { initialize() }
        }
    }

    DisposableEffect(lifecycleOwner, live2DView, enableLive2D) {
        if (!enableLive2D || live2DView == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> live2DView.onResume()
                    Lifecycle.Event.ON_PAUSE -> live2DView.onPause()
                    else -> Unit
                }
            }

            lifecycleOwner.lifecycle.addObserver(observer)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                live2DView.onResume()
            }
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                live2DView.onPause()
            }
        }
    }

    // 普通页面销毁只暂停/解绑 Surface，Native 模型按进程生命周期保活。
    // 真正清除应用进程时由系统回收；避免返回页面时重新 LoadAssets。
    DisposableEffect(live2DView, enableLive2D) {
        onDispose {
            latestRenderer?.detachSurfaceView()
        }
    }

    // 同步上半身模式到 Live2D 渲染器
    LaunchedEffect(showUpperBodyOnly) {
        renderer?.setUpperBodyMode(showUpperBodyOnly)
    }

    val showLive2D = enableLive2D && isLive2DReady && live2dError == null && lottieFinished

    Box(
        modifier = modifier
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        // Live2D 层：就绪后渲染
        if (enableLive2D && isLive2DReady && live2dError == null && live2DView != null) {
            AndroidView(
                factory = {
                    renderer?.attachSurfaceView(live2DView)
                    renderer?.setUpperBodyMode(showUpperBodyOnly)
                    fullState?.let { state -> renderer?.updateState(state) }
                    live2DView
                },
                modifier = Modifier.fillMaxSize(),
                update = {
                    renderer?.setUpperBodyMode(showUpperBodyOnly)
                    fullState?.let { state -> renderer?.updateState(state) }
                }
            )
        }

        // Lottie 过渡层：播放完成且 Live2D 就绪后淡出
        AnimatedVisibility(
            visible = !showLive2D,
            enter = EnterTransition.None,
            exit = fadeOut(animationSpec = tween(600))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                if (composition != null) {
                    LottieAnimation(
                        composition = composition,
                        progress = { lottieState.progress },
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    ComposeBreathingAnimation(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

/**
 * Compose 实现的呼吸动画兜底
 * 当 Lottie 资源缺失时作为过渡动画使用
 */
@Composable
private fun ComposeBreathingAnimation(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "breath")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(Color.White.copy(0.25f))
        )
    }
}

/**
 * 占位头像（保留为极端异常兜底，正常流程不再走这里）
 */
@Composable
private fun PlaceholderAvatar(
    avatarState: AvatarState,
    fullState: AvatarFullState?,
    modifier: Modifier = Modifier
) {
    val mouthOpen = fullState?.mouthOpen ?: 0f
    val mouthForm = fullState?.mouthForm ?: 0f
    val expression = fullState?.expression ?: AvatarExpression.NEUTRAL
    val gesture = fullState?.gesture ?: AvatarGesture.IDLE

    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(Color.White.copy(0.15f)),
            contentAlignment = Alignment.Center
        ) {
            val animatedSize by androidx.compose.animation.core.animateDpAsState(
                targetValue = 32.dp + (mouthOpen * 56).dp,
                animationSpec = tween(durationMillis = 40),
                label = "mouth_size"
            )

            val animatedColor = when {
                mouthOpen > 0.7f -> Color.White.copy(0.9f)
                mouthOpen > 0.4f -> Color.White.copy(0.7f)
                mouthOpen > 0.1f -> Color.White.copy(0.5f)
                else -> Color.White.copy(0.3f)
            }

            val mouthScaleX = 1f + (mouthForm * 0.6f)
            val mouthScaleY = 1f - (mouthForm * 0.3f)

            Box(
                modifier = Modifier
                    .size(animatedSize)
                    .graphicsLayer {
                        scaleX = mouthScaleX.coerceIn(0.5f, 1.5f)
                        scaleY = mouthScaleY.coerceIn(0.5f, 1.5f)
                    }
                    .clip(CircleShape)
                    .background(animatedColor),
                contentAlignment = Alignment.Center
            ) {
                when (avatarState) {
                    AvatarState.SPEAKING -> {
                        SpeakingWaves(
                            mouthOpen = mouthOpen,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    AvatarState.THINKING -> {
                        CircularProgressIndicator(
                            color = Primary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    AvatarState.IDLE -> {
                        Icon(
                            Icons.Default.Face,
                            contentDescription = "数字人",
                            modifier = Modifier.size(24.dp),
                            tint = Primary
                        )
                    }
                    AvatarState.LISTENING -> {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "聆听中",
                            modifier = Modifier.size(24.dp),
                            tint = Primary
                        )
                    }
                    AvatarState.ERROR -> {
                        Icon(
                            Icons.Default.Face,
                            contentDescription = "出错了",
                            modifier = Modifier.size(24.dp),
                            tint = Color.Red
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (expression != AvatarExpression.NEUTRAL) {
            val expressionEmoji = when (expression) {
                AvatarExpression.HAPPY -> "😊"
                AvatarExpression.THINKING -> "🤔"
                AvatarExpression.EXCITED -> "😃"
                AvatarExpression.CONCERNED -> "😟"
                AvatarExpression.APologetic -> "🙇"
                AvatarExpression.WELCOMING -> "👋"
                AvatarExpression.APPROVING -> "👍"
                AvatarExpression.PLAYFUL -> "😊"
                AvatarExpression.REVERENT -> "🙏"
                else -> ""
            }

            if (expressionEmoji.isNotEmpty()) {
                Text(
                    text = expressionEmoji,
                    fontSize = 24.sp
                )
                Spacer(Modifier.height(4.dp))
            }
        }

        Text(
            text = when (avatarState) {
                AvatarState.IDLE -> "您好，请问有什么可以帮助您？"
                AvatarState.SPEAKING -> "正在为您讲解..."
                AvatarState.THINKING -> "正在思考..."
                AvatarState.LISTENING -> "正在聆听..."
                AvatarState.ERROR -> "抱歉，出了点问题"
            },
            fontSize = 14.sp,
            color = Color.White.copy(0.95f),
            maxLines = 2
        )

        if (gesture != AvatarGesture.IDLE) {
            Spacer(Modifier.height(6.dp))
            Surface(
                color = Color.White.copy(0.2f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "动作: ${gestureToText(gesture)}",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    fontSize = 12.sp,
                    color = Color.White
                )
            }
        }

        if (avatarState == AvatarState.SPEAKING) {
            Spacer(Modifier.height(6.dp))
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                LinearProgressIndicator(
                    progress = { mouthOpen },
                    modifier = Modifier.width(60.dp).height(4.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(0.3f)
                )
                Text(
                    text = String.format("%.0f%%", mouthOpen * 100),
                    fontSize = 10.sp,
                    color = Color.White.copy(0.7f)
                )
            }
        }
    }
}

/**
 * 说话波纹动画
 */
@Composable
private fun SpeakingWaves(
    mouthOpen: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waves")

    val scale1 by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale1"
    )

    val scale2 by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale2"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (mouthOpen > 0.1f) {
            Box(
                modifier = Modifier
                    .size(24.dp * scale1 * (0.8f + mouthOpen * 0.4f))
                    .clip(CircleShape)
                    .background(Primary.copy(0.3f))
            )
        }
        if (mouthOpen > 0.3f) {
            Box(
                modifier = Modifier
                    .size(18.dp * scale2)
                    .clip(CircleShape)
                    .background(Primary.copy(0.5f))
            )
        }

        Icon(
            Icons.Default.RecordVoiceOver,
            contentDescription = "说话中",
            modifier = Modifier.size(16.dp),
            tint = Primary
        )
    }
}

/**
 * 动作转文字
 */
private fun gestureToText(gesture: AvatarGesture): String {
    return when (gesture) {
        AvatarGesture.IDLE -> ""
        AvatarGesture.NOD -> "点头"
        AvatarGesture.SHAKE -> "摇头"
        AvatarGesture.WAVE -> "致意"
        AvatarGesture.POINT_LEFT -> "看左"
        AvatarGesture.POINT_RIGHT -> "看右"
        AvatarGesture.POINT_FORWARD -> "示意"
        AvatarGesture.BOW -> "欠身"
        AvatarGesture.THINKING_POSE -> "沉思"
        AvatarGesture.GUIDE -> "引导"
        AvatarGesture.LOOK_UP -> "仰望"
        AvatarGesture.LISTEN -> "聆听"
        AvatarGesture.WELCOME_GESTURE -> "欢迎"
    }
}
