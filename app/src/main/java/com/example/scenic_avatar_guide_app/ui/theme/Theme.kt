package com.example.scenic_avatar_guide_app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// 设计规范颜色
val Primary = Color(0xFFB71C1C)        // 中国红
val PrimaryLight = Color(0xFFE57373)
val Secondary = Color(0xFFF2A541)      // 暖阳橙
val SecondaryLight = Color(0xFFF5C77A)
val Accent = Color(0xFF2B59C3)
val Success = Color(0xFFD32F2F)
val Warning = Color(0xFFD9822B)
val Error = Color(0xFFC44536)
val Surface = Color(0xFFFEF8F8)
val SurfaceVariant = Color(0xFFF0E8E8)
val TextPrimary = Color(0xFF1C2328)
val TextSecondary = Color(0xFF5A6772)
val TextHint = Color(0xFF9EA8A6)

// 聊天气泡颜色
val UserBubbleBg = Color(0xFFB71C1C)
val UserBubbleText = Color.White
val AssistantBubbleBg = Color.White
val AssistantBubbleText = Color(0xFF1C2328)
val ErrorBubbleBg = Color(0xFFFFEBEE)
val ErrorBubbleBorder = Color(0xFFC44536)

// 输入框背景
val InputBarBg = Color(0xFFF5F0F0)

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    primaryContainer = PrimaryLight,
    onPrimaryContainer = TextPrimary,
    secondary = Secondary,
    onSecondary = Color.White,
    secondaryContainer = SecondaryLight,
    onSecondaryContainer = TextPrimary,
    tertiary = Accent,
    error = Error,
    onError = Color.White,
    background = Color(0xFF121212),
    onBackground = Color.White,
    surface = Color(0xFF1E1E1E),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2A2A2A),
    outline = Color(0xFF4A4A4A)
)

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFEBEE),
    onPrimaryContainer = Primary,
    secondary = Secondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFF3E0),
    onSecondaryContainer = Secondary,
    tertiary = Accent,
    error = Error,
    onError = Color.White,
    background = Surface,
    onBackground = TextPrimary,
    surface = Color.White,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariant,
    outline = Color(0xFFD0D8D6)
)

@Composable
fun GuideTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
