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
 *
 * 表情体系（13个）：
 * - 基础态：neutral
 * - 积极层：happy（温和愉悦）、excited（热情兴奋）、welcoming（热情欢迎）、approving（认同赞许）
 * - 交互层：thinking（思考中）、playful（俏皮可爱）、focused（专注聆听）
 * - 特殊层：concerned（关切担心）、apologetic（歉意致歉）、reverent（庄重敬畏）
 * - 情感层：surprised（惊叹惊喜）、grateful（感恩欣慰）
 */
enum class AvatarExpression(val value: String) {
    NEUTRAL("neutral"),
    HAPPY("happy"),
    THINKING("thinking"),
    EXCITED("excited"),
    CONCERNED("concerned"),
    APologetic("apologetic"),
    WELCOMING("welcoming"),
    APPROVING("approving"),
    PLAYFUL("playful"),
    REVERENT("reverent"),
    SURPRISED("surprised"),
    GRATEFUL("grateful"),
    FOCUSED("focused");

    companion object {
        fun fromValue(value: String?): AvatarExpression {
            return entries.find { it.value == value } ?: NEUTRAL
        }
    }
}

/**
 * 数字人动作类型（无手可见模式）
 *
 * 设计约束：当前仅展示上半身，且手在画面外不可见。
 * 因此所有动作均通过头部角度 + 身体旋转 + 肩膀来表达语义，
 * 不涉及手臂/手部参数（ARM_* / HAND_*）。
 *
 * 语义映射：
 * - NOD/SHAKE/BOW：头部/肩膀动作，本身不依赖手
 * - WAVE/WELCOME_GESTURE：侧首致意（头部微侧 + 身体微倾）
 * - POINT_LEFT/RIGHT：侧首向左/右（用视线引导）
 * - POINT_FORWARD：颔首示意（头部微前倾）
 * - THINKING_POSE：仰首思考（头部微仰 + 侧偏）
 * - GUIDE：侧身引导（身体侧转 + 头部跟随）
 * - LOOK_UP：抬头仰望（头部后仰，引导看上方）
 * - LISTEN：侧耳倾听（头部微侧前倾，表达聆听姿态）
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
    GUIDE("guide"),
    LOOK_UP("look_up"),
    LISTEN("listen"),
    WELCOME_GESTURE("welcome_gesture");

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
    SLIGHT(0.35f, 0f),     // d, t, n, l
    HALF(0.65f, 0f),       // e, g, k, h
    OPEN(1.0f, 0f),        // a (大开口)
    WIDE(0.75f, -0.3f),    // i, ü (扁嘴)
    ROUND(0.7f, 0.6f),     // o, u (圆唇)
    NEUTRAL(0.15f, 0f),    // 默认/静音

    // ==================== 新增高精度口型 ====================
    SIL(0.0f, 0.0f),       // 静音/停顿
    BP(0.05f, 0.0f),       // 双唇音 b,p,m（微张更自然）
    F(0.2f, -0.2f),        // 唇齿音 f
    DT(0.3f, 0.0f),        // 舌尖中音 d,t,n,l
    GK(0.55f, 0.0f),       // 舌根音 g,k,h
    JQ(0.15f, -0.3f),      // 舌面音 j,q,x（擦音，半闭扁嘴）
    ZC(0.15f, 0.0f),       // 舌尖前音 z,c,s（擦音，半闭）
    ZH(0.2f, 0.0f),        // 舌尖后音 zh,ch,sh,r（翘舌擦音，半闭）
    A(1.0f, 0.0f),         // 开口呼 a,ai,an,ang,ao
    O(0.85f, 0.6f),        // 合口呼圆唇 o,ou,ong
    E(0.7f, 0.0f),         // 半开口 e,ei,en,eng,er
    I(0.55f, -0.5f),       // 齐齿呼扁嘴 i,ie,iu,in,ing
    U(0.65f, 0.4f),        // 合口呼收圆 u,ui,un
    V(0.55f, -0.3f),       // 撮口呼 ü,üe,ün
    UA(0.9f, 0.2f);        // 复合元音过渡 ua,uai,uan,uang,iao,ian

    companion object {
        // 预编译正则，避免高频调用时重复创建 Regex 对象
        private val REGEX_BPMLW = Regex("[bpmlw].*")
        private val REGEX_DTNL = Regex("[dtnl].*")
        private val REGEX_AE = Regex("[ae].*")
        private val REGEX_OU = Regex("[ou].*")
        private val REGEX_IUY = Regex("[iüy].*")
        private val REGEX_GKH = Regex("[gkh].*")

        /**
         * 根据音素映射口型（讯飞 TTS 回调）
         */
        fun fromPhoneme(phoneme: String): VisemeType {
            val p = phoneme.lowercase()
            return when {
                // 闭唇音
                p.matches(REGEX_BPMLW) -> CLOSED
                // 舌尖音
                p.matches(REGEX_DTNL) -> SLIGHT
                // 开口元音
                p.matches(REGEX_AE) -> OPEN
                // 圆唇元音
                p.matches(REGEX_OU) -> ROUND
                // 扁唇元音
                p.matches(REGEX_IUY) -> WIDE
                // 舌根音
                p.matches(REGEX_GKH) -> HALF
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
    val currentText: String = "",

    // 表情过渡时长（毫秒）
    val expressionTransitionMs: Long = 200,

    // 动作过渡时长（毫秒）
    val gestureTransitionMs: Long = 300
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
        "farewell" to AvatarGesture.BOW,
        "introduction" to AvatarGesture.POINT_FORWARD,
        "direction_left" to AvatarGesture.POINT_LEFT,
        "direction_right" to AvatarGesture.POINT_RIGHT,
        "agreement" to AvatarGesture.NOD,
        "disagreement" to AvatarGesture.SHAKE,
        "thinking" to AvatarGesture.THINKING_POSE,
        "apology" to AvatarGesture.BOW,
        "route_recommendation" to AvatarGesture.GUIDE,
        "photo_recommendation" to AvatarGesture.POINT_FORWARD,
        "facilities" to AvatarGesture.POINT_FORWARD,
        "food_recommendation" to AvatarGesture.POINT_FORWARD,
        "shopping" to AvatarGesture.POINT_FORWARD,
        "weather_warning" to AvatarGesture.NOD,
        "crowd_warning" to AvatarGesture.POINT_FORWARD,
        "storytelling" to AvatarGesture.THINKING_POSE,
        "photo_pose" to AvatarGesture.WAVE,
        "transport" to AvatarGesture.POINT_FORWARD,
        "listening" to AvatarGesture.LISTEN,
        "look_up" to AvatarGesture.LOOK_UP,
        "welcome" to AvatarGesture.WELCOME_GESTURE
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
        // 直接情绪名称（后端 tts_segment.emotion 直接返回）
        "welcoming" to AvatarExpression.WELCOMING,
        "happy" to AvatarExpression.HAPPY,
        "excited" to AvatarExpression.EXCITED,
        "thinking" to AvatarExpression.THINKING,
        "apologetic" to AvatarExpression.APologetic,
        "concerned" to AvatarExpression.CONCERNED,
        "surprised" to AvatarExpression.SURPRISED,
        "grateful" to AvatarExpression.GRATEFUL,
        "reverent" to AvatarExpression.REVERENT,
        "playful" to AvatarExpression.PLAYFUL,
        "approving" to AvatarExpression.APPROVING,
        "focused" to AvatarExpression.FOCUSED,
        "neutral" to AvatarExpression.NEUTRAL,

        // 积极情感（LLM 情绪标注）
        "joy" to AvatarExpression.HAPPY,
        "happiness" to AvatarExpression.HAPPY,
        "excitement" to AvatarExpression.EXCITED,
        "anticipation" to AvatarExpression.EXCITED,

        // 认同与信任
        "trust" to AvatarExpression.WELCOMING,
        "approval" to AvatarExpression.APPROVING,
        "agreement" to AvatarExpression.APPROVING,

        // 中性/交互
        "curiosity" to AvatarExpression.THINKING,

        // 俏皮/轻松
        "playfulness" to AvatarExpression.PLAYFUL,

        // 关切与歉意
        "sadness" to AvatarExpression.CONCERNED,
        "worry" to AvatarExpression.CONCERNED,
        "guilt" to AvatarExpression.APologetic,

        // 庄重/敬畏
        "awe" to AvatarExpression.REVERENT,
        "reverence" to AvatarExpression.REVERENT,

        // 惊叹与感恩
        "surprise" to AvatarExpression.SURPRISED,
        "amazement" to AvatarExpression.SURPRISED,
        "gratitude" to AvatarExpression.GRATEFUL,
        "thankfulness" to AvatarExpression.GRATEFUL,

        // 专注
        "focus" to AvatarExpression.FOCUSED,
        "attention" to AvatarExpression.FOCUSED
    )

    fun map(emotion: String?): AvatarExpression {
        if (emotion == null) return AvatarExpression.NEUTRAL
        return mapping[emotion.lowercase()] ?: AvatarExpression.NEUTRAL
    }
}
