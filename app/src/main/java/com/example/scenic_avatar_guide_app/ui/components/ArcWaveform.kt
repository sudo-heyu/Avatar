package com.example.scenic_avatar_guide_app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * 扇形波形组件 - 微信风格
 * 波形从中心向两侧分布，中间高两边低
 */
@Composable
fun ArcWaveform(
    volumeLevel: Float,
    isRecording: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 9,
    color: Color = Color.White
) {
    // 为每个竖条生成随机相位
    val phases = remember { List(barCount) { Random.nextFloat() * 2f * PI.toFloat() } }

    // 动画进度
    val infiniteTransition = rememberInfiniteTransition(label = "arcWave")
    val animationProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(80, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )

    // 音量平滑处理
    var smoothedVolume by remember { mutableFloatStateOf(0f) }
    val targetVolume = if (isRecording) volumeLevel else 0f
    smoothedVolume = smoothedVolume + (targetVolume - smoothedVolume) * 0.5f

    Canvas(modifier = modifier) {
        val barWidth = 10f
        val barSpacing = 12f
        val totalWidth = barCount * barWidth + (barCount - 1) * barSpacing
        val startX = (size.width - totalWidth) / 2
        val centerY = size.height / 2

        // 计算中间索引
        val centerIndex = barCount / 2

        // 根据容器高度计算最大高度
        val maxBarHeight = size.height * 0.9f
        val minBarHeight = size.height * 0.08f

        repeat(barCount) { index ->
            // 计算到中心的距离，用于扇形分布
            val distanceFromCenter = kotlin.math.abs(index - centerIndex)
            val maxDistance = centerIndex
            val arcFactor = 1f - (distanceFromCenter.toFloat() / maxDistance) * 0.3f

            // 波浪效果 - 只在有音量时才波动
            val phase = phases[index]
            val waveOffset = if (smoothedVolume > 0.05f) {
                sin(animationProgress * 2 * PI + phase).toFloat()
            } else {
                0f
            }

            // 音量直接影响高度
            // 加入一个小的基础值，确保静音时能看到一点波形
            val volumeWithBase = smoothedVolume.coerceIn(0f, 1f)
            val effectiveVolume = 0.1f + volumeWithBase * 0.9f  // 10% 基础 + 90% 音量

            // 高度主要由音量决定
            val baseHeight = minBarHeight + (maxBarHeight - minBarHeight) * effectiveVolume * arcFactor
            // 波浪只影响 12% 的高度变化，不掩盖音量变化
            val waveAdjustment = waveOffset * maxBarHeight * 0.12f * effectiveVolume * arcFactor
            val barHeight = (baseHeight + waveAdjustment).coerceIn(minBarHeight, maxBarHeight * arcFactor)

            val x = startX + index * (barWidth + barSpacing)
            val y = centerY - barHeight / 2

            // 根据音量调整透明度
            val alpha = if (isRecording) {
                (0.4f + 0.6f * effectiveVolume * arcFactor).coerceIn(0.3f, 1f)
            } else {
                0.2f
            }

            // 绘制圆角矩形
            drawRoundRect(
                color = color.copy(alpha = alpha),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}
