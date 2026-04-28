package com.example.scenic_avatar_guide_app.domain.model

// ==================== 数字人状态 ====================

/**
 * 数字人高级状态
 */
enum class AvatarState {
    IDLE,       // 空闲，等待用户
    LISTENING,  // 听用户说话
    THINKING,   // 思考中
    SPEAKING,   // 说话中（TTS 播放）
    ERROR       // 出错
}

/**
 * 数字人表情类型
 */
enum class AvatarExpression(val value: String) {
    NEUTRAL("neutral"),
    HAPPY("happy"),
    THINKING("thinking"),
    SURPRISED("surprised"),
    EXCITED("excited"),
    CONCERNED("concerned"),
    APologetic("apologetic"),
    WELCOMING("welcoming");

    companion object {
        fun fromValue(value: String?): AvatarExpression {
            return entries.find { it.value == value } ?: NEUTRAL
        }
    }
}

/**
 * 数字人动作类型
 */
enum class AvatarGesture(val value: String) {
    IDLE("idle"),
    NOD("nod"),
    SHAKE("shake"),
    WAVE("wave"),
    POINT_LEFT("point_left"),
    POINT_RIGHT("point_right"),
    POINT_FORWARD("point_forward"),
    BOW("bow"),
    THINKING_POSE("thinking_pose"),
    GUIDE("guide");

    companion object {
        fun fromValue(value: String?): AvatarGesture {
            return entries.find { it.value == value } ?: IDLE
        }
    }
}

/**
 * 动作优先级
 */
enum class GesturePriority {
    LOW,    // 可被任意动作打断
    NORMAL, // 可被 HIGH 优先级动作打断
    HIGH;   // 不可打断

    companion object {
        fun fromValue(value: String?): GesturePriority {
            return when (value?.lowercase()) {
                "low" -> LOW
                "high" -> HIGH
                else -> NORMAL
            }
        }
    }
}

// ==================== 口型系统 ====================

/**
 * 口型类型（Viseme）
 * 用于口型动画控制
 */
enum class VisemeType(
    val mouthOpen: Float,      // 嘴巴开合度 0-1
    val mouthForm: Float = 0f  // 嘴型：负值=扁，正值=圆，0=中性
) {
    // ==================== 原有口型（向后兼容）====================
    CLOSED(0.0f, 0f),      // b, p, m (闭唇)
    SLIGHT(0.25f, 0f),     // d, t, n, l
    HALF(0.5f, 0f),        // e, g, k, h
    OPEN(0.9f, 0f),        // a (大开口)
    WIDE(0.6f, -0.3f),     // i, ü (扁嘴)
    ROUND(0.5f, 0.6f),     // o, u (圆唇)
    NEUTRAL(0.1f, 0f),     // 默认/静音

    // ==================== 新增高精度口型 ====================
    SIL(0.0f, 0.0f),       // 静音/停顿
    BP(0.0f, 0.0f),        // 双唇音 b,p,m
    F(0.1f, -0.2f),        // 唇齿音 f
    DT(0.15f, 0.0f),       // 舌尖中音 d,t,n,l
    GK(0.35f, 0.0f),       // 舌根音 g,k,h
    JQ(0.25f, -0.4f),      // 舌面音 j,q,x
    ZC(0.2f, 0.0f),        // 舌尖前音 z,c,s
    ZH(0.25f, 0.0f),       // 舌尖后音 zh,ch,sh,r
    A(0.9f, 0.0f),         // 开口呼 a,ai,an,ang,ao
    O(0.6f, 0.6f),         // 合口呼圆唇 o,ou,ong
    E(0.5f, 0.0f),         // 半开口 e,ei,en,eng,er
    I(0.3f, -0.5f),        // 齐齿呼扁嘴 i,ie,iu,in,ing
    U(0.4f, 0.4f),         // 合口呼收圆 u,ui,un
    V(0.35f, -0.3f),       // 撮口呼 ü,üe,ün
    UA(0.7f, 0.2f);        // 复合元音过渡 ua,uai,uan,uang,iao,ian

    companion object {
        /**
         * 根据音素映射口型（讯飞 TTS 回调）
         */
        fun fromPhoneme(phoneme: String): VisemeType {
            val p = phoneme.lowercase()
            return when {
                // 闭唇音
                p.matches(Regex("[bpmlw].*")) -> CLOSED
                // 舌尖音
                p.matches(Regex("[dtnl].*")) -> SLIGHT
                // 开口元音
                p.matches(Regex("[ae].*")) -> OPEN
                // 圆唇元音
                p.matches(Regex("[ou].*")) -> ROUND
                // 扁唇元音
                p.matches(Regex("[iüy].*")) -> WIDE
                // 舌根音
                p.matches(Regex("[gkh].*")) -> HALF
                // 其他
                else -> NEUTRAL
            }
        }

        /**
         * 根据字符映射口型（简化版，无音素时使用）
         */
        fun fromChar(char: Char): VisemeType {
            return when (char) {
                in "aeo" -> OPEN
                in "iuü" -> WIDE
                in "bpmw" -> CLOSED
                in "dtnl" -> SLIGHT
                in "gkh" -> HALF
                in "zcs" -> SLIGHT
                in "jqx" -> WIDE
                in "r" -> SLIGHT
                else -> NEUTRAL
            }
        }
    }
}

/**
 * 音素事件（TTS 回调）
 */
data class PhonemeEvent(
    val phoneme: String,       // 音素
    val startMs: Long,         // 开始时间（毫秒）
    val endMs: Long,           // 结束时间（毫秒）
    val viseme: VisemeType,    // 对应口型
    val charIndex: Int = 0     // 字符位置
)

// ==================== 完整状态 ====================

/**
 * 数字人完整状态
 * 用于实时控制和渲染
 */
data class AvatarFullState(
    // 高级状态
    val state: AvatarState = AvatarState.IDLE,

    // 表情状态
    val expression: AvatarExpression = AvatarExpression.NEUTRAL,
    val expressionIntensity: Float = 0.7f,

    // 动作状态
    val gesture: AvatarGesture = AvatarGesture.IDLE,
    val gesturePriority: GesturePriority = GesturePriority.NORMAL,

    // 口型状态
    val mouthOpen: Float = 0f,
    val mouthForm: Float = 0f,

    // 播放状态
    val speakProgress: Float = 0f,
    val currentText: String = ""
)

// ==================== TTS 配置（含端侧与远程）====================

/**
 * TTS 引擎类型
 */
enum class TTSEngine {
    XUNFEI,   // 讯飞（优先）
    SYSTEM    // 系统 TTS（降级）
}

/**
 * TTS 配置
 */
data class TTSConfig(
    val engine: TTSEngine = TTSEngine.XUNFEI,
    val voiceId: String = "xiaoyan",    // 发音人
    val speed: Float = 1.0f,            // 语速 0.5-2.0
    val pitch: Float = 1.0f,            // 音调 0.5-2.0
    val volume: Float = 1.0f,           // 音量 0.0-1.0
    val enablePhoneme: Boolean = true   // 启用音素回调
)

/**
 * TTS 播放状态
 */
sealed class TTSState {
    object Idle : TTSState()
    data class Synthesizing(val text: String) : TTSState()
    data class Speaking(
        val text: String,
        val progress: Float,
        val currentCharIndex: Int,
        val totalChars: Int
    ) : TTSState()
    data class Completed(val text: String) : TTSState()
    data class Error(val text: String, val message: String) : TTSState()
    data class Paused(val text: String, val progress: Float) : TTSState()
}

// ==================== 意图/情感映射（降级使用）====================

/**
 * 意图到动作的映射
 * 当后端未返回 gesture 时使用
 */
object IntentToGesture {
    private val mapping = mapOf(
        "greeting" to AvatarGesture.WAVE,
        "farewell" to AvatarGesture.WAVE,
        "introduction" to AvatarGesture.POINT_FORWARD,
        "direction_left" to AvatarGesture.POINT_LEFT,
        "direction_right" to AvatarGesture.POINT_RIGHT,
        "agreement" to AvatarGesture.NOD,
        "disagreement" to AvatarGesture.SHAKE,
        "thinking" to AvatarGesture.THINKING_POSE,
        "apology" to AvatarGesture.BOW,
        "route_recommendation" to AvatarGesture.GUIDE
    )

    fun map(intent: String?): AvatarGesture {
        if (intent == null) return AvatarGesture.IDLE
        return mapping[intent.lowercase()] ?: AvatarGesture.IDLE
    }
}

/**
 * 情感到表情的映射
 * 当后端未返回 expression 时使用
 */
object EmotionToExpression {
    private val mapping = mapOf(
        "joy" to AvatarExpression.HAPPY,
        "happiness" to AvatarExpression.HAPPY,
        "sadness" to AvatarExpression.CONCERNED,
        "surprise" to AvatarExpression.SURPRISED,
        "trust" to AvatarExpression.WELCOMING,
        "anticipation" to AvatarExpression.EXCITED
    )

    fun map(emotion: String?): AvatarExpression {
        if (emotion == null) return AvatarExpression.NEUTRAL
        return mapping[emotion.lowercase()] ?: AvatarExpression.NEUTRAL
    }
}
