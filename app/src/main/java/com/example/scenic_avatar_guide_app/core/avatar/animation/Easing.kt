package com.example.scenic_avatar_guide_app.core.avatar.animation

import kotlin.math.pow

/**
 * 缓动函数库
 * 将线性进度 t (0..1) 映射为带曲线的进度值
 */
object Easing {

    fun linear(t: Float): Float = t.coerceIn(0f, 1f)

    fun easeInQuad(t: Float): Float {
        val v = t.coerceIn(0f, 1f)
        return v * v
    }

    fun easeOutQuad(t: Float): Float {
        val v = t.coerceIn(0f, 1f)
        return 1f - (1f - v) * (1f - v)
    }

    fun easeInOutQuad(t: Float): Float {
        val v = t.coerceIn(0f, 1f)
        return if (v < 0.5f) 2f * v * v else 1f - (-2f * v + 2f).pow(2f) / 2f
    }

    fun easeInCubic(t: Float): Float {
        val v = t.coerceIn(0f, 1f)
        return v * v * v
    }

    fun easeOutCubic(t: Float): Float {
        val v = t.coerceIn(0f, 1f)
        return 1f - (1f - v).pow(3f)
    }

    fun easeInOutCubic(t: Float): Float {
        val v = t.coerceIn(0f, 1f)
        return if (v < 0.5f) 4f * v * v * v else 1f - (-2f * v + 2f).pow(3f) / 2f
    }

    /**
     * 回弹效果：冲过头再回弹到位，适合活泼的动作
     */
    fun easeOutBack(t: Float): Float {
        val v = t.coerceIn(0f, 1f)
        val c1 = 1.70158f
        val c3 = c1 + 1f
        return 1f + c3 * (v - 1f).pow(3f) + c1 * (v - 1f).pow(2f)
    }

    /**
     * 弹性效果：像弹簧一样振荡收敛
     */
    fun easeOutElastic(t: Float): Float {
        val v = t.coerceIn(0f, 1f)
        if (v == 0f || v == 1f) return v
        val c4 = (2f * kotlin.math.PI) / 3f
        return (2f.pow(-10f * v) * kotlin.math.sin((v * 10f - 0.75f) * c4) + 1f).toFloat()
    }

    /**
     * 开口快、闭合慢——模拟真实说话口型
     * 用于口型从闭合到张开时快速到位，闭合时稍慢
     */
    fun speakOpen(t: Float): Float {
        val v = t.coerceIn(0f, 1f)
        return easeOutQuad(v)
    }

    /**
     * 闭合快——用于口型从张开回到闭合
     */
    fun speakClose(t: Float): Float {
        val v = t.coerceIn(0f, 1f)
        return easeInQuad(v)
    }

    /**
     * 根据方向选择开口/闭合曲线
     * @param opening 是否为开口方向（值变大）
     */
    fun speakTransition(t: Float, opening: Boolean): Float {
        return if (opening) speakOpen(t) else speakClose(t)
    }
}

enum class EasingType {
    LINEAR,
    EASE_IN_QUAD, EASE_OUT_QUAD, EASE_IN_OUT_QUAD,
    EASE_IN_CUBIC, EASE_OUT_CUBIC, EASE_IN_OUT_CUBIC,
    EASE_OUT_BACK, EASE_OUT_ELASTIC,
    SPEAK_OPEN, SPEAK_CLOSE;

    fun apply(t: Float): Float = when (this) {
        LINEAR -> Easing.linear(t)
        EASE_IN_QUAD -> Easing.easeInQuad(t)
        EASE_OUT_QUAD -> Easing.easeOutQuad(t)
        EASE_IN_OUT_QUAD -> Easing.easeInOutQuad(t)
        EASE_IN_CUBIC -> Easing.easeInCubic(t)
        EASE_OUT_CUBIC -> Easing.easeOutCubic(t)
        EASE_IN_OUT_CUBIC -> Easing.easeInOutCubic(t)
        EASE_OUT_BACK -> Easing.easeOutBack(t)
        EASE_OUT_ELASTIC -> Easing.easeOutElastic(t)
        SPEAK_OPEN -> Easing.speakOpen(t)
        SPEAK_CLOSE -> Easing.speakClose(t)
    }
}
