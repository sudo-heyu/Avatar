package com.example.scenic_avatar_guide_app.core.tts

import android.content.Context
import android.util.Log
import com.example.scenic_avatar_guide_app.core.audio.AudioPlayer
import com.example.scenic_avatar_guide_app.core.avatar.ChinesePhonemeEngine
import com.example.scenic_avatar_guide_app.data.repository.GuideRepository
import kotlinx.coroutines.*

/**
 * 远程 TTS 控制器
 * 调用后端 Edge-TTS 服务获取音频 URL，用 ExoPlayer 播放
 */
class RemoteTTSController(
    private val context: Context,
    private val repository: GuideRepository
) : TTSProvider {

    companion object {
        private const val TAG = "RemoteTTSController"
        private const val MARKS_MIN_COVERAGE_RATIO = 0.9f

        // 预置 Edge-TTS 中文音色（第一阶段写死，避免额外网络请求）
        val AVAILABLE_VOICES = listOf(
            VoiceInfo("zh-CN-XiaoxiaoNeural", "晓晓", "亲和女声", Gender.FEMALE, VoiceStyle.FRIENDLY),
            VoiceInfo("zh-CN-XiaoyiNeural", "晓伊", "活泼女声", Gender.FEMALE, VoiceStyle.GENTLE),
            VoiceInfo("zh-CN-YunyangNeural", "云扬", "稳重男声", Gender.MALE, VoiceStyle.PROFESSIONAL),
            VoiceInfo("zh-CN-YunjianNeural", "云健", "新闻男声", Gender.MALE, VoiceStyle.NEWS)
        )

        val DEFAULT_VOICE = AVAILABLE_VOICES[0]
    }

    private val audioPlayer = AudioPlayer(context)

    private var currentVoiceId = DEFAULT_VOICE.id
    private var currentSpeed = 1.0f
    private var currentPitch = 1.0f

    private var lipSyncJob: Job? = null
    private var currentText = ""
    private var isSpeaking = false

    override var onPhonemeCallback: ((PhonemeEvent) -> Unit)? = null
    override var onSpeakStart: (() -> Unit)? = null
    override var onSpeakComplete: (() -> Unit)? = null

    override var onPhonemeEvents: ((List<PhonemeEvent>) -> Unit)? = null

    /**
     * 预计算的音素事件（等待音频播放开始时触发）
     */
    private var pendingPhonemeEvents: List<PhonemeEvent>? = null

    init {
        setupAudioPlayerCallbacks()
    }

    private fun setupAudioPlayerCallbacks() {
        audioPlayer.onPlayStart = {
            Log.d(TAG, "[TTS] 音频播放开始")
            isSpeaking = true
            onSpeakStart?.invoke()

            // 音频开始时，启动预计算好的口型动画
            pendingPhonemeEvents?.let { events ->
                val eventCount = events.size
                val lastEvent = events.lastOrNull()
                Log.d(TAG, "[TTS] 触发口型事件: count=$eventCount, 时间范围=0-${lastEvent?.endMs}ms")
                onPhonemeEvents?.invoke(events)

                // 向后兼容：逐个回调旧接口
                lipSyncJob?.cancel()
                lipSyncJob = CoroutineScope(Dispatchers.Main).launch {
                    val playStartTime = System.currentTimeMillis()
                    events.forEach { event ->
                        if (!isActive) return@launch
                        val elapsed = System.currentTimeMillis() - playStartTime
                        val waitTime = event.startMs - elapsed
                        if (waitTime > 0) delay(waitTime)
                        onPhonemeCallback?.invoke(event)
                    }
                }
            }
        }
        audioPlayer.onPlayComplete = {
            Log.d(TAG, "[TTS] 音频播放完成, duration=${audioPlayer.getDuration()}ms")
            isSpeaking = false
            lipSyncJob?.cancel()
            pendingPhonemeEvents = null
            onSpeakComplete?.invoke()
        }
        audioPlayer.onPlayError = { error ->
            Log.e(TAG, "音频播放错误: $error")
            isSpeaking = false
            lipSyncJob?.cancel()
            pendingPhonemeEvents = null
            onSpeakComplete?.invoke()
        }
    }

    override fun speak(text: String) {
        if (isSpeaking) {
            stop()
        }
        val cleanText = sanitizeTtsText(text)
        currentText = cleanText

        // 预计算音素事件（字符估算兜底），等音频播放开始时触发
        precomputePhonemeEvents(cleanText)

        // 异步请求后端合成
        CoroutineScope(Dispatchers.Main).launch {
            try {
                Log.d(TAG, "TTS 请求文本: $cleanText")
                var result = repository.synthesizeTTS(
                    text = cleanText,
                    voice = currentVoiceId,
                    rate = formatRate(currentSpeed),
                    pitch = formatPitch(currentPitch),
                    format = "audio_with_marks"
                )
                if (result.isFailure) {
                    Log.w(TAG, "audio_with_marks 请求失败，重试普通 audio 格式", result.exceptionOrNull())
                    result = repository.synthesizeTTS(
                        text = cleanText,
                        voice = currentVoiceId,
                        rate = formatRate(currentSpeed),
                        pitch = formatPitch(currentPitch),
                        format = "audio"
                    )
                }
                result.fold(
                    onSuccess = { data ->
                        updatePendingPhonemeEvents(cleanText, data)

                        val fullUrl = repository.buildAudioUrl(data.audioUrl)
                        Log.d(TAG, "TTS 合成成功，播放: $fullUrl")
                        audioPlayer.play(fullUrl)
                    },
                    onFailure = { e ->
                        Log.e(TAG, "后端 TTS 请求失败", e)
                        onSpeakComplete?.invoke()
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "TTS 请求异常", e)
                onSpeakComplete?.invoke()
            }
        }
    }

    override fun stop() {
        isSpeaking = false
        lipSyncJob?.cancel()
        pendingPhonemeEvents = null
        audioPlayer.stop()
    }

    override fun release() {
        stop()
        audioPlayer.release()
    }

    override fun setVoice(voiceId: String) {
        currentVoiceId = voiceId
    }

    override fun setSpeed(speed: Float) {
        currentSpeed = speed.coerceIn(0.5f, 2.0f)
    }

    override fun setPitch(pitch: Float) {
        currentPitch = pitch.coerceIn(0.5f, 2.0f)
    }

    /**
     * 清洗 TTS 文本：去除 Markdown/HTML 标签、控制字符，保留纯文本
     * Edge TTS 会把 '<' 当成 SSML 标签入口，含 HTML/Markdown 的文本会导致合成失败
     */
    private fun sanitizeTtsText(text: String): String {
        return text
            // 去除 HTML/XML 标签 <...>
            .replace(Regex("<[^>]+>"), "")
            // 去除 Markdown 链接 [text](url)
            .replace(Regex("\\[(.*?)\\]\\(.*?\\)"), "$1")
            // 去除 Markdown 图片 ![alt](url)
            .replace(Regex("!\\[(.*?)\\]\\(.*?\\)"), "")
            // 去除 Markdown 表格/粗体/斜体等符号 * _ | ` > #
            .replace(Regex("[*_|`>#~-]"), "")
            // 去除 URL
            .replace(Regex("https?://\\S+"), "")
            // 去除控制字符（零宽空格、零宽连接符等）
            .replace(Regex("[\\u200B-\\u200F\\uFEFF\\u2060]"), "")
            // 合并多余空白
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    /**
     * 格式化语速为 edge-tts 参数，如 +10%、-20%
     */
    private fun formatRate(speed: Float): String {
        val percent = ((speed - 1.0f) * 100).toInt()
        return if (percent >= 0) "+$percent%" else "$percent%"
    }

    /**
     * 格式化音调为 edge-tts 参数，如 +5Hz、-5Hz
     */
    private fun formatPitch(pitch: Float): String {
        val hz = ((pitch - 1.0f) * 10).toInt()
        return if (hz >= 0) "+$hz Hz" else "$hz Hz"
    }

    /**
     * 优先使用后端 marks；若 marks 缺失或明显短于音频时长，则用音频时长重建本地时间轴。
     */
    private fun updatePendingPhonemeEvents(
        text: String,
        data: com.example.scenic_avatar_guide_app.domain.model.TtsSynthesizeData
    ) {
        val durationMs = data.durationMs?.toLong()?.takeIf { it > 0 }
        val marks = data.marks

        if (!marks.isNullOrEmpty()) {
            val markEvents = ChinesePhonemeEngine.marksToPhonemeEvents(marks)
            val markEndMs = markEvents.maxOfOrNull { it.endMs } ?: 0L

            if (isMarksTimelineUsable(markEvents, durationMs)) {
                Log.d(
                    TAG,
                    "后端返回 ${marks.size} 个 marks，使用高精度口型: markEnd=${markEndMs}ms, duration=${durationMs ?: -1}ms"
                )
                pendingPhonemeEvents = markEvents
                return
            }

            Log.w(
                TAG,
                "后端 marks 覆盖不足，改用本地时间轴: markEnd=${markEndMs}ms, duration=${durationMs ?: -1}ms"
            )
        } else {
            Log.w(TAG, "后端未返回 marks，使用本地时间轴")
        }

        precomputePhonemeEvents(text, durationMs)
    }

    private fun isMarksTimelineUsable(events: List<PhonemeEvent>, durationMs: Long?): Boolean {
        if (events.isEmpty()) return false
        if (durationMs == null) return true

        val markEndMs = events.maxOf { it.endMs }
        return markEndMs >= durationMs * MARKS_MIN_COVERAGE_RATIO
    }

    /**
     * 预计算音素事件（字符估算，等音频播放开始时触发）
     */
    private fun precomputePhonemeEvents(text: String, durationMs: Long? = null) {
        val totalDuration = durationMs ?: estimateTextDuration(text)
        pendingPhonemeEvents = ChinesePhonemeEngine.textToPhonemeEvents(text, totalDuration)
    }

    private fun estimateTextDuration(text: String): Long {
        return text.sumOf { char ->
            when {
                char in setOf('，', '。', '！', '？', '、', '；', '：', '"', '"') -> 300L
                char in setOf(',', '.', '!', '?', ';', ':', '"', '\'') -> 200L
                char.isWhitespace() -> 100L
                else -> 180L
            }
        }
    }

    /**
     * 获取当前音频播放位置（毫秒）
     * 用于口型同步
     */
    fun getCurrentPosition(): Long {
        return audioPlayer.getCurrentPosition()
    }

    /**
     * 获取当前是否正在播放（直接查询 ExoPlayer 状态）
     * 用于口型同步判断
     */
    fun isAudioPlaying(): Boolean {
        return audioPlayer.isActuallyPlaying()
    }
}
