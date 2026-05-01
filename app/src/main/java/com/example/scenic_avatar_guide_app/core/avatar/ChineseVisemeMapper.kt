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

        // 带介音韵母
        "ua" to listOf("u", "a"),
        "uai" to listOf("u", "a", "i"),
        "uan" to listOf("u", "a", "n"),
        "uang" to listOf("u", "a", "ng"),
        "uo" to listOf("u", "o"),
        "iao" to listOf("i", "a", "o"),
        "ian" to listOf("i", "a", "n"),
        "ia" to listOf("i", "a"),
        "iang" to listOf("i", "a", "ng"),

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

        // 复韵母整体（后端 marks 直接使用或本地拆分后的中间形式）
        "ai" to VisemeType.A,
        "ei" to VisemeType.E,
        "ui" to VisemeType.U,
        "ao" to VisemeType.A,
        "ou" to VisemeType.O,
        "iu" to VisemeType.I,
        "ie" to VisemeType.I,
        "er" to VisemeType.E,

        // 前鼻韵母整体
        "an" to VisemeType.A,
        "en" to VisemeType.E,
        "in" to VisemeType.I,
        "un" to VisemeType.U,
        "vn" to VisemeType.V,

        // 后鼻韵母整体
        "ang" to VisemeType.A,
        "eng" to VisemeType.E,
        "ing" to VisemeType.I,
        "ong" to VisemeType.O,

        // 带介音韵母整体
        "ua" to VisemeType.UA,
        "uai" to VisemeType.UA,
        "uan" to VisemeType.UA,
        "uang" to VisemeType.UA,
        "uo" to VisemeType.O,
        "iao" to VisemeType.UA,
        "ian" to VisemeType.UA,
        "ia" to VisemeType.UA,
        "iang" to VisemeType.UA,

        // üe / ün 整体
        "ve" to VisemeType.V,
        "iong" to VisemeType.I,

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

        // 3. j/q/x/y 后的 u/un/ue 实际为 ü/ün/üe（拼音省略两点规则）
        val correctedRemaining = when {
            effectiveInitial in setOf("j", "q", "x", "y") -> when (remaining.lowercase()) {
                "u" -> "v"
                "un" -> "vn"
                "ue" -> "ve"
                else -> remaining.lowercase()
            }
            else -> remaining.lowercase()
        }

        // 4. 韵母拆分
        val finalParts = FINALS[correctedRemaining] ?: listOf(correctedRemaining)

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
     *
     * 分类依据：
     * - 爆破音 / 塞擦音（b,p,d,t,g,k,zh,ch,z,c,j,q）：有闭气 + 释放过程，占时较长 → 0.30
     * - 擦音（f,h,sh,r,s,x）：连续气流，占时较短 → 0.20
     * - 鼻音 / 边音（m,n,l）：连续 voiced，占时较短 → 0.20
     * - 零声母 / 半元音（y,w,空）：无声母，不占独立时长 → 0.00
     */
    fun getInitialDurationRatio(initial: String?): Float {
        return when (initial) {
            null, "", "y", "w" -> 0.00f
            in listOf("b", "p", "d", "t", "g", "k", "zh", "ch", "z", "c", "j", "q") -> 0.30f
            in listOf("f", "h", "sh", "r", "s", "x") -> 0.20f
            in listOf("m", "n", "l") -> 0.20f
            else -> 0.25f
        }
    }
}
