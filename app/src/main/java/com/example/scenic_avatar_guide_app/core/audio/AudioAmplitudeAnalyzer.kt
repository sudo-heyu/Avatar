package com.example.scenic_avatar_guide_app.core.audio

import android.content.Context
import android.media.audiofx.Visualizer
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "AudioAmplitudeAnalyzer"

/**
 * 音频振幅分析器（P4优化）
 *
 * 功能：
 * - 实时获取音频播放振幅
 * - 作为后端 marks 缺失时的口型同步兜底机制
 * - 有声段放大口型，无声段快速归零
 *
 * 使用方式：
 * 1. 调用 attachToAudioSession(audioSessionId) 绑定音频会话
 * 2. 观察 amplitude StateFlow 获取实时振幅
 * 3. 调用 applyAmplitudeCorrection() 校正口型幅度
 * 4. 播放结束时调用 release() 释放资源
 */
class AudioAmplitudeAnalyzer {

    // 当前振幅值 (0.0 - 1.0)
    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    // 是否已初始化
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    // Visualizer 实例
    private var visualizer: Visualizer? = null
    private var captureJob: Job? = null

    // 振幅阈值（用于区分有声/无声段）
    var amplitudeThreshold: Float = 0.02f

    // 平滑系数（0-1，越大越平滑）
    var smoothingFactor: Float = 0.3f

    // 上一次的振幅值（用于平滑）
    private var lastAmplitude: Float = 0f

    /**
     * 绑定到音频会话
     *
     * @param audioSessionId MediaPlayer 或 ExoPlayer 的音频会话 ID
     * @return 是否成功绑定
     */
    fun attachToAudioSession(audioSessionId: Int): Boolean {
        if (audioSessionId == 0) {
            Log.w(TAG, "Invalid audio session ID: 0")
            return false
        }

        try {
            release()

            // 创建 Visualizer
            visualizer = Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[0] // 使用最小捕获尺寸以减少开销

                // 设置数据捕获监听器
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int
                        ) {
                            waveform?.let { processWaveform(it) }
                        }

                        override fun onFftDataCapture(
                            visualizer: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int
                        ) {
                            // FFT 数据可用于频率分析，当前仅使用波形数据
                        }
                    },
                    Visualizer.getMaxCaptureRate() / 2, // 采样率设为最大的一半
                    true, // 启用波形捕获
                    false // 不需要 FFT
                )

                enabled = true
            }

            _isInitialized.value = true
            Log.d(TAG, "Attached to audio session: $audioSessionId")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach to audio session: ${e.message}", e)
            _isInitialized.value = false
            return false
        }
    }

    /**
     * 处理波形数据，计算振幅
     */
    private fun processWaveform(waveform: ByteArray) {
        if (waveform.isEmpty()) return

        // 计算波形的有效值（RMS）
        var sum = 0.0
        for (byte in waveform) {
            // 将字节值 (-128 to 127) 转换为无符号值 (0 to 255)，然后归一化到 -1 到 1
            val sample = (byte.toInt() and 0xFF) - 128
            sum += sample * sample
        }

        val rms = kotlin.math.sqrt(sum / waveform.size)

        // 归一化到 0-1 范围（128 为最大值）
        val rawAmplitude = (rms / 128.0).coerceIn(0.0, 1.0).toFloat()

        // 应用平滑处理
        lastAmplitude = lastAmplitude * smoothingFactor + rawAmplitude * (1 - smoothingFactor)
        _amplitude.value = lastAmplitude
    }

    /**
     * 应用振幅校正到口型幅度
     *
     * @param baseMouthOpen 基础口型开度（来自音素映射）
     * @param currentAmplitude 当前振幅（可选，默认使用实时值）
     * @return 校正后的口型开度
     */
    fun applyAmplitudeCorrection(
        baseMouthOpen: Float,
        currentAmplitude: Float = _amplitude.value
    ): Float {
        return when {
            // 振幅分析器未初始化：返回原始值
            !_isInitialized.value -> baseMouthOpen

            // 有声段：振幅放大口型
            currentAmplitude > amplitudeThreshold -> {
                // 基础口型占 70%，振幅贡献占 30%
                baseMouthOpen * (0.7f + currentAmplitude * 0.5f)
            }

            // 无声段：快速衰减到 30%
            else -> {
                baseMouthOpen * 0.3f
            }
        }
    }

    /**
     * 判断当前是否为有声段
     */
    fun isVoiced(): Boolean {
        return _amplitude.value > amplitudeThreshold
    }

    /**
     * 获取当前振幅级别（用于调试）
     */
    fun getAmplitudeLevel(): String {
        val amp = _amplitude.value
        return when {
            amp < 0.02f -> "SILENT"
            amp < 0.1f -> "QUIET"
            amp < 0.3f -> "NORMAL"
            amp < 0.6f -> "LOUD"
            else -> "VERY_LOUD"
        }
    }

    /**
     * 重置振幅状态
     */
    fun reset() {
        lastAmplitude = 0f
        _amplitude.value = 0f
    }

    /**
     * 释放资源
     */
    fun release() {
        captureJob?.cancel()
        captureJob = null

        visualizer?.let {
            try {
                it.enabled = false
                it.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing visualizer: ${e.message}")
            }
        }
        visualizer = null

        _isInitialized.value = false
        _amplitude.value = 0f
        lastAmplitude = 0f

        Log.d(TAG, "Released audio amplitude analyzer")
    }

    companion object {
        /**
         * 振幅级别常量
         */
        const val LEVEL_SILENT = 0.0f
        const val LEVEL_QUIET = 0.1f
        const val LEVEL_NORMAL = 0.3f
        const val LEVEL_LOUD = 0.6f
        const val LEVEL_VERY_LOUD = 1.0f
    }
}
