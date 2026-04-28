package com.example.scenic_avatar_guide_app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.scenic_avatar_guide_app.core.avatar.*
import com.example.scenic_avatar_guide_app.domain.model.*
import com.example.scenic_avatar_guide_app.ui.theme.Primary
import com.example.scenic_avatar_guide_app.ui.theme.PrimaryLight
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * 数字人展示组件
 * 优先显示真实 Live2D；异常时降级为占位头像
 */
@Composable
fun AvatarView(
    avatarState: AvatarState,
    modifier: Modifier = Modifier,
    fullState: AvatarFullState? = null,
    enableLive2D: Boolean = true,  // 启用 Live2D 渲染
    showUpperBodyOnly: Boolean = false  // 只显示上半身（裁剪下半身）
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Live2D 渲染器状态
    var renderer by remember { mutableStateOf<Live2DRendererImpl?>(null) }
    var isLive2DReady by remember { mutableStateOf(false) }
    var live2dError by remember { mutableStateOf<String?>(null) }

    // 初始化 Live2D 渲染器（仅在启用时）
    LaunchedEffect(enableLive2D) {
        if (enableLive2D && renderer == null) {
            try {
                val newRenderer = Live2DRendererImpl(context)
                newRenderer.initialize()
                val result = newRenderer.loadModel("live2d/hiyori/Hiyori.model3.json")
                isLive2DReady = result.isSuccess
                renderer = newRenderer
            } catch (e: Exception) {
                isLive2DReady = false
                live2dError = e.message
            }
        }
    }

    // 更新 Live2D 状态
    LaunchedEffect(fullState) {
        fullState?.let { state ->
            renderer?.updateState(state)
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

    // 清理资源
    DisposableEffect(Unit) {
        onDispose {
            renderer?.release()
        }
    }

    val upperBodyModifier = if (showUpperBodyOnly) {
        Modifier.graphicsLayer {
            clip = true
            // 只保留上半部分（约 65%），下半身被裁掉
            // 通过 scaleY 放大后再向上偏移，使画面中心落在胸部以上
            scaleY = 1.5f
            translationY = -size.height * 0.15f
        }
    } else Modifier

    Box(
        modifier = modifier
            .then(upperBodyModifier)
            .background(Brush.verticalGradient(colors = listOf(Primary, PrimaryLight))),
        contentAlignment = Alignment.Center
    ) {
        if (enableLive2D && isLive2DReady && live2dError == null && live2DView != null) {
            AndroidView(
                factory = {
                    renderer?.attachSurfaceView(live2DView)
                    live2DView
                },
                modifier = Modifier.fillMaxSize(),
                update = {
                    renderer?.attachSurfaceView(it)
                }
            )
        } else {
            PlaceholderAvatar(
                avatarState = avatarState,
                fullState = fullState,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * 占位头像
 * 可视化显示表情、动作和口型
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
        verticalArrangement = Arrangement.Center
    ) {
        // 主显示区域
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(Color.White.copy(0.15f)),
            contentAlignment = Alignment.Center
        ) {
            // 内部动态圆圈（口型可视化）
            val animatedSize by animateDpAsState(
                targetValue = 40.dp + (mouthOpen * 30).dp,
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "mouth_size"
            )

            val animatedColor = when {
                mouthOpen > 0.7f -> Color.White.copy(0.9f)
                mouthOpen > 0.4f -> Color.White.copy(0.7f)
                mouthOpen > 0.1f -> Color.White.copy(0.5f)
                else -> Color.White.copy(0.3f)
            }

            // 动态口型圆
            Box(
                modifier = Modifier
                    .size(animatedSize)
                    .clip(CircleShape)
                    .background(animatedColor),
                contentAlignment = Alignment.Center
            ) {
                // 状态图标
                when (avatarState) {
                    AvatarState.SPEAKING -> {
                        // 显示动态波纹
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

        // 表情指示
        if (expression != AvatarExpression.NEUTRAL) {
            val expressionEmoji = when (expression) {
                AvatarExpression.HAPPY -> "😊"
                AvatarExpression.THINKING -> "🤔"
                AvatarExpression.SURPRISED -> "😲"
                AvatarExpression.EXCITED -> "😃"
                AvatarExpression.CONCERNED -> "😟"
                AvatarExpression.APologetic -> "🙇"
                AvatarExpression.WELCOMING -> "👋"
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

        // 状态文字
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

        // 动作指示
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

        // 口型数值（调试）
        if (avatarState == AvatarState.SPEAKING) {
            Spacer(Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 口型进度条
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
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale1"
    )

    val scale2 by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale2"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // 波纹效果
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

        // 中心图标
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
        AvatarGesture.WAVE -> "挥手"
        AvatarGesture.POINT_LEFT -> "指左"
        AvatarGesture.POINT_RIGHT -> "指右"
        AvatarGesture.POINT_FORWARD -> "指前"
        AvatarGesture.BOW -> "鞠躬"
        AvatarGesture.THINKING_POSE -> "思考"
        AvatarGesture.GUIDE -> "引导"
    }
}
