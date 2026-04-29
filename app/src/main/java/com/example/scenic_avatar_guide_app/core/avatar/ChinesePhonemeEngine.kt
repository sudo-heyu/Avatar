package com.example.scenic_avatar_guide_app.core.avatar

import android.util.LruCache
import com.example.scenic_avatar_guide_app.core.tts.PhonemeEvent
import com.example.scenic_avatar_guide_app.domain.model.TtsMarkItem
import com.example.scenic_avatar_guide_app.domain.model.VisemeType
import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType

/**
 * 中文音素引擎
 * 负责 汉字 -> 拼音 -> 音素 -> 口型 的完整转换
 *
 * 优化特性：
 * - LRU缓存：避免重复的pinyin4j转换
 * - 多音字覆盖：景区专用词库
 * - 情绪/语速影响：可调节口型幅度
 */
object ChinesePhonemeEngine {

    private val format = HanyuPinyinOutputFormat().apply {
        toneType = HanyuPinyinToneType.WITHOUT_TONE
    }

    /**
     * 拼音缓存（P1优化）
     * 缓存汉字到拼音的转换结果，避免重复调用pinyin4j
     */
    private val pinyinCache = LruCache<Char, String>(512)

    /**
     * 情绪强度配置（P2优化）
     */
    var emotionMultiplier: Float = 1.0f
        private set

    /**
     * 语速因子配置（P2优化）
     */
    var speedFactor: Float = 1.0f
        private set

    /**
     * 景区专用多音字覆盖表
     */
    private val scenicPolyphone = mapOf(
        '行' to "xing",
        '藏' to "zang",
        '佛' to "fo",
        '刹' to "cha",
        '乐' to "le",
        '都' to "du",
        '长' to "chang",
        '重' to "chong",
        '大' to "da",
        '干' to "gan",
        '更' to "geng",
    )

    /**
     * 设置情绪强度
     * @param multiplier 口型幅度乘数（LOW=0.6, NORMAL=1.0, HIGH=1.3, VERY_HIGH=1.5）
     */
    fun setEmotionMultiplier(multiplier: Float) {
        emotionMultiplier = multiplier.coerceIn(0.5f, 2.0f)
    }

    /**
     * 设置语速因子
     * @param factor 语速因子（影响音素时长估算）
     */
    fun setSpeedFactor(factor: Float) {
        speedFactor = factor.coerceIn(0.5f, 2.0f)
    }

    /**
     * 单个汉字 -> 拼音（小写，无调）
     * 使用LRU缓存优化性能
     */
    fun charToPinyin(char: Char): String {
        // 1. 查缓存
        pinyinCache.get(char)?.let { return it }

        // 2. 查景区专用词库
        scenicPolyphone[char]?.let {
            pinyinCache.put(char, it)
            return it
        }

        // 3. 已经是拉丁字母，直接返回小写
        if (char in 'a'..'z' || char in 'A'..'Z') {
            return char.lowercaseChar().toString()
        }

        // 4. 非汉字（数字、标点等）
        if (char !in '一'..'鿿') {
            return char.lowercase()
        }

        // 5. pinyin4j 转换
        val result = try {
            PinyinHelper.toHanyuPinyinStringArray(char, format)
                ?.firstOrNull()
                ?.lowercase()
                ?: char.lowercase()
        } catch (_: Exception) {
            char.lowercase()
        }

        // 6. 存入缓存
        pinyinCache.put(char, result)
        return result
    }

    /**
     * 文本 -> 音素事件序列（按字符估算时长）
     *
     * @param text 原始文本
     * @param totalDurationMs 整句时长（毫秒）
     * @return 按时间排序的音素事件列表
     */
    fun textToPhonemeEvents(text: String, totalDurationMs: Long): List<PhonemeEvent> {
        val timelineChars = text.filter { isSpeakableChar(it) || isPauseChar(it) }
        if (timelineChars.isEmpty()) return emptyList()

        val events = mutableListOf<PhonemeEvent>()
        var currentMs = 0L
        val totalWeight = timelineChars.sumOf { estimateCharWeight(it) }.coerceAtLeast(1L)
        val safeTotalDuration = totalDurationMs.coerceAtLeast(totalWeight)

        timelineChars.forEachIndexed { index, char ->
            val charDuration = (safeTotalDuration * estimateCharWeight(char) / totalWeight)
                .coerceAtLeast(40L)

            if (isPauseChar(char)) {
                events.add(
                    PhonemeEvent(
                        phoneme = "SIL",
                        startMs = currentMs,
                        endMs = currentMs + charDuration,
                        viseme = VisemeType.SIL,
                        charIndex = index
                    )
                )
                currentMs += charDuration
                return@forEachIndexed
            }

            val pinyin = charToPinyin(char)
            val phonemes = ChineseVisemeMapper.splitPinyin(pinyin)

            if (phonemes.isEmpty()) {
                events.add(
                    PhonemeEvent(
                        phoneme = char.toString(),
                        startMs = currentMs,
                        endMs = currentMs + charDuration,
                        viseme = VisemeType.SIL,
                        charIndex = index
                    )
                )
                currentMs += charDuration
                return@forEachIndexed
            }

            val initial = phonemes.firstOrNull()?.takeIf {
                it.length <= 2 && it !in listOf("a", "o", "e", "i", "u", "v")
            }
            val initialRatio = ChineseVisemeMapper.getInitialDurationRatio(initial)
            val initialDuration = (charDuration * initialRatio).toLong()
            val finalDuration = charDuration - initialDuration

            val finalPhonemes = if (initial != null) phonemes.drop(1) else phonemes
            val finalPartDuration = if (finalPhonemes.isNotEmpty()) finalDuration / finalPhonemes.size else finalDuration

            // 声母事件
            if (initial != null && initialDuration > 0) {
                events.add(
                    PhonemeEvent(
                        phoneme = initial,
                        startMs = currentMs,
                        endMs = currentMs + initialDuration,
                        viseme = ChineseVisemeMapper.phonemeToViseme(initial),
                        charIndex = index
                    )
                )
                currentMs += initialDuration
            }

            // 韵母音素事件
            finalPhonemes.forEach { phoneme ->
                val duration = finalPartDuration.coerceAtLeast(50L)
                events.add(
                    PhonemeEvent(
                        phoneme = phoneme,
                        startMs = currentMs,
                        endMs = currentMs + duration,
                        viseme = ChineseVisemeMapper.phonemeToViseme(phoneme),
                        charIndex = index
                    )
                )
                currentMs += duration
            }
        }

        return events
    }

    private fun isSpeakableChar(char: Char): Boolean {
        return char.isLetterOrDigit() || char in '一'..'鿿'
    }

    private fun isPauseChar(char: Char): Boolean {
        return char.isWhitespace() ||
            char in setOf('，', '。', '！', '？', '、', '；', '：', '"', '"') ||
            char in setOf(',', '.', '!', '?', ';', ':', '"', '\'')
    }

    private fun estimateCharWeight(char: Char): Long {
        return when {
            char in setOf('，', '。', '！', '？', '、', '；', '：', '"', '"') -> 300L
            char in setOf(',', '.', '!', '?', ';', ':', '"', '\'') -> 200L
            char.isWhitespace() -> 100L
            else -> 180L
        }
    }

    /**
     * 根据后端 marks 生成精确音素事件
     */
    fun marksToPhonemeEvents(marks: List<TtsMarkItem>): List<PhonemeEvent> {
        val events = mutableListOf<PhonemeEvent>()

        marks.forEachIndexed { index, mark ->
            val char = mark.text.firstOrNull() ?: return@forEachIndexed
            val markDuration = (mark.endMs - mark.startMs).toLong()

            // 优先按后端提供的 phonemes 处理
            val effectivePhonemes = when {
                mark.phonemes == null -> {
                    // 后端未提供 phonemes，本地生成兜底
                    val pinyin = charToPinyin(char)
                    ChineseVisemeMapper.splitPinyin(pinyin)
                }
                mark.phonemes.isEmpty() -> {
                    // 后端明确返回空列表（非 CJK 字符）：按规范应音频振幅兜底
                    events.add(
                        PhonemeEvent(
                            phoneme = char.toString(),
                            startMs = mark.startMs.toLong(),
                            endMs = mark.endMs.toLong(),
                            viseme = VisemeType.NEUTRAL,
                            charIndex = index
                        )
                    )
                    return@forEachIndexed
                }
                mark.phonemes.size == 1 && mark.phonemes[0].equals("SIL", ignoreCase = true) -> {
                    // 纯标点/空格停顿：强制闭嘴
                    events.add(
                        PhonemeEvent(
                            phoneme = "SIL",
                            startMs = mark.startMs.toLong(),
                            endMs = mark.endMs.toLong(),
                            viseme = VisemeType.SIL,
                            charIndex = index
                        )
                    )
                    return@forEachIndexed
                }
                else -> mark.phonemes
            }

            if (effectivePhonemes.isEmpty()) {
                events.add(
                    PhonemeEvent(
                        phoneme = char.toString(),
                        startMs = mark.startMs.toLong(),
                        endMs = mark.endMs.toLong(),
                        viseme = VisemeType.SIL,
                        charIndex = index
                    )
                )
                return@forEachIndexed
            }

            // 校正 j/q/x/y 后的 u → ü（后端 phonemes 也可能使用拼音省略写法）
            val correctedPhonemes = effectivePhonemes.toMutableList()
            if (correctedPhonemes.isNotEmpty()) {
                val first = correctedPhonemes[0]
                if (first in setOf("j", "q", "x", "y") && correctedPhonemes.size >= 2) {
                    when (correctedPhonemes[1].lowercase()) {
                        "u" -> correctedPhonemes[1] = "v"
                        "un" -> correctedPhonemes[1] = "vn"
                        "ue" -> correctedPhonemes[1] = "ve"
                    }
                }
            }

            val initial = correctedPhonemes.firstOrNull()?.takeIf {
                it.length <= 2 && it !in listOf("a", "o", "e", "i", "u", "v")
            }
            val initialRatio = ChineseVisemeMapper.getInitialDurationRatio(initial)
            val initialDuration = (markDuration * initialRatio).toLong()
            val finalDuration = markDuration - initialDuration

            val finalPhonemes = if (initial != null) correctedPhonemes.drop(1) else correctedPhonemes
            val finalPartDuration = if (finalPhonemes.isNotEmpty()) finalDuration / finalPhonemes.size else finalDuration

            var currentMs = mark.startMs.toLong()

            if (initial != null && initialDuration > 0) {
                events.add(
                    PhonemeEvent(
                        phoneme = initial,
                        startMs = currentMs,
                        endMs = currentMs + initialDuration,
                        viseme = ChineseVisemeMapper.phonemeToViseme(initial),
                        charIndex = index
                    )
                )
                currentMs += initialDuration
            }

            finalPhonemes.forEach { phoneme ->
                val duration = finalPartDuration.coerceAtLeast(30L)
                events.add(
                    PhonemeEvent(
                        phoneme = phoneme,
                        startMs = currentMs,
                        endMs = currentMs + duration,
                        viseme = ChineseVisemeMapper.phonemeToViseme(phoneme),
                        charIndex = index
                    )
                )
                currentMs += duration
            }
        }

        return events
    }
}
