package com.example.scenic_avatar_guide_app.core.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.iflytek.sparkchain.core.tts.OnlineTTS
import com.iflytek.sparkchain.core.tts.TTS
import com.iflytek.sparkchain.core.tts.TTSCallbacks
import com.example.scenic_avatar_guide_app.domain.model.VisemeType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * 讯飞 TTS 控制器
 * 使用科大讯飞 SparkChain SDK 实现在线语音合成
 */
class XunfeiTTSController(
    private val context: Context
) {
    companion object {
        private const val TAG = "XunfeiTTSController"

        // 音频参数
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        // TTS 数据状态
        private const val TTS_STATUS_FIRST = 0    // 首帧
        private const val TTS_STATUS_MIDDLE = 1   // 中间帧
        private const val TTS_STATUS_LAST = 2     // 尾帧

        // 已开通的发音人
        val AVAILABLE_VOICES = listOf(
            VoiceInfo("xiaoyan", "小燕", "亲和女声", Gender.FEMALE, VoiceStyle.FRIENDLY),
            VoiceInfo("aisjiuxu", "许久", "亲切女声", Gender.FEMALE, VoiceStyle.FRIENDLY),
            VoiceInfo("aisjinger", "小婧", "柔美女声", Gender.FEMALE, VoiceStyle.GENTLE),
            VoiceInfo("aisbabyxu", "许小宝", "童声女童", Gender.FEMALE, VoiceStyle.CHILD),
        )

        val DEFAULT_VOICE = AVAILABLE_VOICES[0]

        fun getRecommendedVoices(): Map<VoiceStyle, List<VoiceInfo>> {
            return AVAILABLE_VOICES.groupBy { it.style }
        }
    }

    private var onlineTTS: OnlineTTS? = null
    private var isInitialized = false
    private var currentVoiceId = DEFAULT_VOICE.id

    // 播放状态
    private val _state = MutableStateFlow<TTSPlayState>(TTSPlayState.Idle)
    val state: StateFlow<TTSPlayState> = _state.asStateFlow()

    // 当前发音人
    private val _currentVoice = MutableStateFlow(DEFAULT_VOICE)
    val currentVoice: StateFlow<VoiceInfo> = _currentVoice.asStateFlow()

    // 回调
    var onPhonemeCallback: ((PhonemeEvent) -> Unit)? = null
    var onSpeakStart: (() -> Unit)? = null
    var onSpeakComplete: (() -> Unit)? = null

    // 当前播放文本
    private var currentText = ""
    private var lipSyncJob: Job? = null
    private var isSpeaking = false

    // 音频播放器
    private var audioTrack: AudioTrack? = null
    private val audioQueue = LinkedBlockingQueue<ByteArray>()
    private var playJob: Job? = null
    private var isPlayerStarted = false  // 播放器是否已启动

    /**
     * 初始化 TTS
     */
    fun initialize() {
        if (isInitialized) return

        try {
            onlineTTS = createOnlineTts(currentVoiceId)
            isInitialized = true
            Log.i(TAG, "讯飞 TTS 初始化成功，发音人: $currentVoiceId")
        } catch (e: Exception) {
            Log.e(TAG, "讯飞 TTS 初始化失败", e)
        }
    }

    /**
     * 初始化音频播放器
     */
    private fun initAudioTrack() {
        val existingTrack = audioTrack
        if (existingTrack?.state == AudioTrack.STATE_INITIALIZED) {
            runCatching {
                existingTrack.pause()
                existingTrack.flush()
            }.onFailure {
                Log.w(TAG, "重置旧 AudioTrack 失败: ${it.message}")
            }
            return
        }

        releaseAudioTrack()

        val minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = (minBufferSize.coerceAtLeast(SAMPLE_RATE) * 2)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_CONFIG)
                    .setEncoding(AUDIO_FORMAT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        Log.d(
            TAG,
            "AudioTrack 初始化完成: state=${audioTrack?.state}, bufferSize=$bufferSize, minBufferSize=$minBufferSize"
        )
    }

    /**
     * 启动音频播放线程
     */
    private fun startAudioPlayback() {
        playJob?.cancel()
        audioQueue.clear()

        playJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                initAudioTrack()
                val track = audioTrack
                if (track?.state != AudioTrack.STATE_INITIALIZED) {
                    throw IllegalStateException("AudioTrack 未初始化，state=${track?.state}")
                }

                track.play()
                Log.d(TAG, "音频播放器已启动: playState=${track.playState}")

                while (isActive) {
                    val audioData = audioQueue.poll(200, TimeUnit.MILLISECONDS) ?: continue

                    if (audioData.isEmpty()) {
                        // 收到结束标记
                        Log.d(TAG, "音频播放结束")
                        break
                    }

                    val writeResult = track.write(audioData, 0, audioData.size)
                    if (writeResult < 0) {
                        throw IllegalStateException("AudioTrack.write 失败: $writeResult")
                    }
                }

                // 播放完成
                withContext(Dispatchers.Main) {
                    resetPlaybackState()
                    _state.value = TTSPlayState.Completed(currentText)
                    onSpeakComplete?.invoke()
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "音频播放任务已取消")
            } catch (e: Exception) {
                Log.e(TAG, "音频播放异常: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    resetPlaybackState()
                    _state.value = TTSPlayState.Error(currentText, "音频播放异常: ${e.message}")
                }
            } finally {
                runCatching {
                    audioTrack?.pause()
                    audioTrack?.flush()
                    audioTrack?.stop()
                }.onFailure {
                    Log.w(TAG, "停止 AudioTrack 失败: ${it.message}")
                }
            }
        }
    }

    /**
     * 设置发音人
     */
    fun setVoice(voiceId: String) {
        currentVoiceId = voiceId

        if (isInitialized) {
            stop()
            onlineTTS = createOnlineTts(voiceId)
        }

        val voice = AVAILABLE_VOICES.find { it.id == voiceId } ?: DEFAULT_VOICE
        _currentVoice.value = voice

        Log.d(TAG, "切换发音人: $voiceId (${voice.displayName})")
    }

    /**
     * 设置语速 (0.5 - 2.0)
     */
    fun setSpeed(speed: Float) {
        onlineTTS?.speed((speed * 50).toInt().coerceIn(0, 100))
    }

    /**
     * 设置音调 (0.5 - 2.0)
     */
    fun setPitch(pitch: Float) {
        onlineTTS?.pitch((pitch * 50).toInt().coerceIn(0, 100))
    }

    /**
     * 设置音量 (0.0 - 1.0)
     */
    fun setVolume(volume: Float) {
        onlineTTS?.volume((volume * 100).toInt().coerceIn(0, 100))
    }

    /**
     * 播放文本
     */
    fun speak(text: String) {
        if (!isInitialized) {
            Log.w(TAG, "TTS 未初始化")
            return
        }

        // 无论是否正在播放，先停止之前的
        if (isSpeaking) {
            Log.w(TAG, "TTS 正在播放，先停止")
            stop()
        }

        currentText = text
        isSpeaking = true  // 标记为正在播放（等待TTS响应）
        // isPlayerStarted 由回调中收到音频数据时设置

        // 启动口型模拟
        startLipSyncSimulation(text)

        // 调用讯飞 TTS
        try {
            val result = onlineTTS?.aRun(text) ?: -1
            if (result != 0) {
                Log.e(TAG, "TTS aRun 失败，错误码: $result")
                isSpeaking = false
                isPlayerStarted = false
                _state.value = TTSPlayState.Error(text, "播放失败: $result")
            } else {
                Log.d(TAG, "TTS aRun 成功，等待音频数据...")
            }
        } catch (e: Exception) {
            Log.e(TAG, "TTS aRun 异常: ${e.message}", e)
            isSpeaking = false
            isPlayerStarted = false
            _state.value = TTSPlayState.Error(text, "播放异常: ${e.message}")
        }
    }

    /**
     * 停止播放
     */
    fun stop() {
        resetPlaybackState()
        playJob?.cancel()
        playJob = null
        audioQueue.clear()
        runCatching {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.stop()
        }.onFailure {
            Log.w(TAG, "停止 AudioTrack 失败: ${it.message}")
        }
        runCatching {
            onlineTTS?.stop()
        }.onFailure {
            Log.w(TAG, "停止 OnlineTTS 失败: ${it.message}")
        }
        _state.value = TTSPlayState.Idle
    }

    /**
     * 释放资源
     */
    fun release() {
        stop()
        releaseAudioTrack()
        onlineTTS = null
        isInitialized = false
    }

    private fun createOnlineTts(voiceId: String): OnlineTTS {
        return OnlineTTS(voiceId).apply {
            aue("raw")
            speed(50)
            pitch(50)
            volume(50)
            registerCallbacks(object : TTSCallbacks {
                override fun onResult(result: TTS.TTSResult?, usrTag: Any?) {
                    result?.let {
                        val audio = it.data
                        val status = it.status

                        Log.d(TAG, "TTS 音频数据: status=$status, len=${audio?.size ?: 0}")

                        if (audio != null && audio.isNotEmpty()) {
                            if (!isPlayerStarted) {
                                isPlayerStarted = true
                                isSpeaking = true
                                _state.value = TTSPlayState.Speaking(currentText, 0f)
                                onSpeakStart?.invoke()
                                startAudioPlayback()
                                Log.d(TAG, "启动音频播放器")
                            }
                            audioQueue.offer(audio)
                        }

                        if (status == TTS_STATUS_LAST) {
                            audioQueue.offer(ByteArray(0))
                        }
                    }
                }

                override fun onError(error: TTS.TTSError?, usrTag: Any?) {
                    error?.let {
                        Log.e(TAG, "TTS 错误: ${it.code}, ${it.errMsg}")
                        resetPlaybackState()
                        _state.value = TTSPlayState.Error(currentText, "错误: ${it.code} - ${it.errMsg}")
                    }
                }
            })
        }
    }

    private fun resetPlaybackState() {
        isSpeaking = false
        isPlayerStarted = false
        lipSyncJob?.cancel()
        lipSyncJob = null
    }

    private fun releaseAudioTrack() {
        runCatching {
            audioTrack?.release()
        }.onFailure {
            Log.w(TAG, "释放 AudioTrack 失败: ${it.message}")
        }
        audioTrack = null
    }

    /**
     * 模拟口型同步
     */
    private fun startLipSyncSimulation(text: String) {
        lipSyncJob?.cancel()

        val charDuration = estimateCharDurations(text)

        lipSyncJob = CoroutineScope(Dispatchers.Main).launch {
            var elapsed = 0L

            text.forEachIndexed { index, char ->
                if (!isActive) return@launch

                val viseme = VisemeType.fromChar(char)
                val duration = charDuration.getOrElse(index) { 150L }

                onPhonemeCallback?.invoke(
                    PhonemeEvent(
                        phoneme = char.toString(),
                        startMs = elapsed,
                        endMs = elapsed + duration,
                        viseme = viseme,
                        charIndex = index
                    )
                )

                delay(duration)
                elapsed += duration
            }
        }
    }

    private fun estimateCharDurations(text: String): List<Long> {
        return text.map { char ->
            when {
                char.isLetterOrDigit() -> 150L
                char in setOf('，', '。', '！', '？', '、', '；', '：', '"', '"') -> 300L
                char in setOf(',', '.', '!', '?', ';', ':', '"', '\'') -> 200L
                char.isWhitespace() -> 100L
                else -> 100L
            }
        }
    }
}

// VoiceInfo / Gender / VoiceStyle 已迁移到 TtsCommon.kt
