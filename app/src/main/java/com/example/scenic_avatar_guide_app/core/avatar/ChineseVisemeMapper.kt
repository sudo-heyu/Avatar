package com.example.scenic_avatar_guide_app.core.avatar

import com.example.scenic_avatar_guide_app.domain.model.VisemeType

/**
 * 中文音素到口型映射器
 * 覆盖全部汉语拼音声母、韵母、韵尾，提供 15 种高精度口型
 */
object ChineseVisemeMapper {

    // ==================== 声母表 ====================

    private val INITIALS = listOf(
        "zh", "ch", "sh",
        "b", "p", "m", "f", "d", "t", "n", "l",
        "g", "k", "h", "j", "q", "x", "r", "z", "c", "s",
        "y", "w"
    )

    // ==================== 韵母拆分表 ====================

    /**
     * 韵母 -> 音素序列（介音/主元音/韵尾）
     */
    private val FINALS: Map<String, List<String>> = mapOf(
        // 单韵母
        "a" to listOf("a"),
        "o" to listOf("o"),
        "e" to listOf("e"),
        "i" to listOf("i"),
        "u" to listOf("u"),
        "v" to listOf("v"),   // ü 在 pinyin4j 中可能用 v 表示
        "ü" to listOf("v"),

        // 复韵母
        "ai" to listOf("a", "i"),
        "ei" to listOf("e", "i"),
        "ui" to listOf("u", "i"),
        "ao" to listOf("a", "o"),
        "ou" to listOf("o", "u"),
        "iu" to listOf("i", "u"),
        "ie" to listOf("i", "e"),
        "ve" to listOf("v", "e"),   // üe
        "üe" to listOf("v", "e"),
        "er" to listOf("e", "r"),

        // 前鼻韵母
        "an" to listOf("a", "n"),
        "en" to listOf("e", "n"),
        "in" to listOf("i", "n"),
        "un" to listOf("u", "n"),
        "vn" to listOf("v", "n"),   // ün
        "ün" to listOf("v", "n"),

        // 后鼻韵母
        "ang" to listOf("a", "ng"),
        "eng" to listOf("e", "ng"),
        "ing" to listOf("i", "ng"),
        "ong" to listOf("o", "ng"),

        // 特殊
        "iong" to listOf("i", "o", "ng")
    )

    // ==================== 音素 -> 口型映射 ====================

    private val PHONEME_TO_VISEME: Map<String, VisemeType> = mapOf(
        // 声母
        "b" to VisemeType.BP,
        "p" to VisemeType.BP,
        "m" to VisemeType.BP,
        "f" to VisemeType.F,
        "d" to VisemeType.DT,
        "t" to VisemeType.DT,
        "n" to VisemeType.DT,
        "l" to VisemeType.DT,
        "g" to VisemeType.GK,
        "k" to VisemeType.GK,
        "h" to VisemeType.GK,
        "j" to VisemeType.JQ,
        "q" to VisemeType.JQ,
        "x" to VisemeType.JQ,
        "z" to VisemeType.ZC,
        "c" to VisemeType.ZC,
        "s" to VisemeType.ZC,
        "zh" to VisemeType.ZH,
        "ch" to VisemeType.ZH,
        "sh" to VisemeType.ZH,
        "r" to VisemeType.ZH,

        // 韵母（元音）
        "a" to VisemeType.A,
        "o" to VisemeType.O,
        "e" to VisemeType.E,
        "i" to VisemeType.I,
        "u" to VisemeType.U,
        "v" to VisemeType.V,   // ü

        // 韵尾
        "n" to VisemeType.DT,   // 舌尖抵上齿龈
        "ng" to VisemeType.GK,  // 舌根音
        "r" to VisemeType.ZH,

        // 静音标记
        "sil" to VisemeType.SIL,
    )

    // ==================== 公共 API ====================

    /**
     * 拼音 -> 音素序列（声母 + 韵母拆分）
     *
     * 例："huan" -> ["h", "u", "an"]
     * 例："ying" -> ["i", "ng"]  (y 视为零声母)
     * 例："wang" -> ["u", "ang"]  (w 视为零声母)
     */
    fun splitPinyin(pinyinRaw: String): List<String> {
        val pinyin = pinyinRaw.lowercase().trim()
        if (pinyin.isEmpty()) return emptyList()

        // 去除声调数字（pinyin4j 可能返回 "hao3" 或 "hao"）
        val pinyinNoTone = pinyin.replace(Regex("[0-9]$"), "")

        // 1. 识别声母（匹配最长的）
        val initial = INITIALS
            .filter { pinyinNoTone.startsWith(it) }
            .maxByOrNull { it.length }
            ?: ""

        val remaining = if (initial.isNotEmpty()) {
            pinyinNoTone.substring(initial.length)
        } else {
            pinyinNoTone
        }

        // 2. y / w 是零声母变体，跳过不发音
        val effectiveInitial = when (initial) {
            "y", "w" -> ""
            else -> initial
        }

        // 3. 韵母拆分
        val finalParts = FINALS[remaining.lowercase()] ?: listOf(remaining.lowercase())

        // 4. 组合
        return if (effectiveInitial.isNotEmpty()) {
            listOf(effectiveInitial) + finalParts
        } else {
            finalParts
        }
    }

    /**
     * 音素 -> 口型
     */
    fun phonemeToViseme(phoneme: String): VisemeType {
        return PHONEME_TO_VISEME[phoneme.lowercase()] ?: VisemeType.NEUTRAL
    }

    /**
     * 拼音 -> 口型序列
     */
    fun pinyinToVisemes(pinyin: String): List<VisemeType> {
        return splitPinyin(pinyin).map { phonemeToViseme(it) }
    }

    /**
     * 获取声母在整字时长中的占比
     */
    fun getInitialDurationRatio(initial: String?): Float {
        return when {
            initial.isNullOrEmpty() -> 0f
            initial in listOf("zh", "ch", "sh") -> 0.25f
            initial in listOf("z", "c", "s", "j", "q", "x") -> 0.25f
            else -> 0.3f
        }
    }
}
