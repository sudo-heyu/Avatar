package com.example.scenic_avatar_guide_app.core.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.scenic_avatar_guide_app.domain.model.VisemeType
import kotlinx.coroutines.*
import java.util.*

/**
 * 系统 TTS 控制器（降级兜底）
 * 当后端 Edge-TTS 不可用时回退到 Android 系统 TTS
 */
class SystemTTSController(private val context: Context) : TTSProvider {

    companion object {
        private const val TAG = "SystemTTSController"
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    override var onPhonemeCallback: ((PhonemeEvent) -> Unit)? = null
    override var onSpeakStart: (() -> Unit)? = null
    override var onSpeakComplete: (() -> Unit)? = null
    override var onPhonemeEvents: ((List<PhonemeEvent>) -> Unit)? = null

    private var currentText = ""
    private var currentVoiceLocale = Locale.CHINESE

    init {
        initialize()
    }

    private fun initialize() {
        if (isInitialized) return
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(currentVoiceLocale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "Chinese language not supported, trying default")
                    tts?.setLanguage(Locale.getDefault())
                }
                setupUtteranceListener()
                isInitialized = true
                Log.i(TAG, "System TTS initialized successfully")
            } else {
                Log.e(TAG, "System TTS initialization failed")
            }
        }
    }

    override fun setSpeed(speed: Float) {
        tts?.setSpeechRate(speed.coerceIn(0.5f, 2.0f))
    }

    override fun setPitch(pitch: Float) {
        tts?.setPitch(pitch.coerceIn(0.5f, 2.0f))
    }

    override fun setVoice(voiceId: String) {
        // 系统 TTS 不支持按 ID 切换发音人，仅支持切换语言区域
        currentVoiceLocale = when {
            voiceId.startsWith("zh-HK") -> Locale.TRADITIONAL_CHINESE
            voiceId.startsWith("zh-TW") -> Locale.TRADITIONAL_CHINESE
            else -> Locale.CHINESE
        }
        tts?.language = currentVoiceLocale
    }

    override fun speak(text: String) {
        if (!isInitialized) {
            Log.w(TAG, "TTS not initialized")
            return
        }

        currentText = text
        val events = generatePhonemeEvents(text)
        onSpeakStart?.invoke()
        onPhonemeEvents?.invoke(events)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    override fun stop() {
        tts?.stop()
    }

    override fun release() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(TAG, "TTS started: $utteranceId")
            }

            override fun onDone(utteranceId: String?) {
                Log.d(TAG, "TTS done: $utteranceId")
                onSpeakComplete?.invoke()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.e(TAG, "TTS error: $utteranceId")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.e(TAG, "TTS error: $utteranceId, code: $errorCode")
            }
        })
    }

    /**
     * 预生成音素事件列表（系统 TTS 无精确时间戳，按字符估算）
     */
    private fun generatePhonemeEvents(text: String): List<PhonemeEvent> {
        val events = mutableListOf<PhonemeEvent>()
        var elapsed = 0L
        text.forEachIndexed { index, char ->
            val duration = when {
                char.isLetterOrDigit() -> 180L
                char in setOf('，', '。', '！', '？', '、', '；', '：', '"', '"') -> 300L
                char in setOf(',', '.', '!', '?', ';', ':', '"', '\'') -> 200L
                char.isWhitespace() -> 100L
                else -> 100L
            }
            val viseme = VisemeType.fromChar(char)
            events.add(
                PhonemeEvent(
                    phoneme = char.toString(),
                    startMs = elapsed,
                    endMs = elapsed + duration,
                    viseme = viseme,
                    charIndex = index
                )
            )
            elapsed += duration
        }
        return events
    }
}
