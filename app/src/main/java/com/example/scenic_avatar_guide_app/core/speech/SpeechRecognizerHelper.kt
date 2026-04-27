package com.example.scenic_avatar_guide_app.core.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.app.ActivityCompat
import com.iflytek.sparkchain.core.asr.ASR

class SpeechRecognizerHelper(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onReadyForSpeech: () -> Unit,
    private val onEndOfSpeech: () -> Unit,
    private val onVolumeChanged: ((Float) -> Unit)? = null
) {
    private var asr: ASR? = null
    private var audioRecord: AudioRecord? = null
    private var isListening = false
    private var recordingThread: Thread? = null
    private var callback: XunfeiAsrCallback? = null

    companion object {
        private const val TAG = "XunfeiASR"
        private const val SAMPLE_RATE = 16000
    }

    fun hasPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startListening() {
        if (isListening) {
            Log.w(TAG, "已在录音中，忽略重复请求")
            return
        }

        if (!hasPermission()) {
            Log.e(TAG, "没有录音权限")
            onError("没有录音权限，请授予麦克风权限")
            return
        }

        try {
            // 创建回调
            callback = XunfeiAsrCallback()
            callback?.setOnResultListener { text, status ->
                // 只处理最终结果 (status == 2)
                if (status == 2 && text.isNotEmpty()) {
                    onResult(text)
                }
            }
            callback?.setOnErrorListener { message ->
                onError(message)
                onEndOfSpeech()
                isListening = false
            }
            callback?.setOnEndOfSpeech {
                onEndOfSpeech()
            }

            // 初始化 ASR
            asr = ASR()
            asr?.registerCallbacks(callback)

            // 配置 ASR 参数
            asr?.language("zh_cn")
            asr?.domain("iat")
            asr?.accent("mandarin")
            asr?.vinfo(true)
            asr?.dwa("wpgs")

            // 启动识别
            val ret = asr?.start("") ?: -1
            if (ret != 0) {
                Log.e(TAG, "启动 ASR 失败，错误码: $ret")
                onError("启动识别失败")
                return
            }

            isListening = true
            onReadyForSpeech()
            Log.d(TAG, "讯飞 ASR 启动成功")

            // 启动录音线程
            startAudioRecording()

        } catch (e: Exception) {
            Log.e(TAG, "启动语音识别异常: ${e.message}", e)
            onError("启动失败: ${e.message}")
        }
    }

    private fun startAudioRecording() {
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ) * 2

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord 初始化失败")
            return
        }

        audioRecord?.startRecording()

        recordingThread = Thread {
            val buffer = ByteArray(bufferSize)

            while (isListening) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (read > 0) {
                    // 计算音量
                    var sumSquares = 0.0
                    val sampleCount = read / 2
                    for (i in 0 until read step 2) {
                        val sample = ((buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)).toShort()
                        sumSquares += sample.toDouble() * sample.toDouble()
                    }
                    val rms = kotlin.math.sqrt(sumSquares / sampleCount)
                    // 增强音量灵敏度：放大 3 倍
                    val normalizedVolume = ((rms / 32767.0) * 3.0).coerceIn(0.0, 1.0).toFloat()

                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        onVolumeChanged?.invoke(normalizedVolume)
                    }

                    // 写入 ASR
                    val data = buffer.copyOf(read)
                    asr?.write(data)
                } else if (read < 0) {
                    Log.e(TAG, "读取音频失败: $read")
                    break
                }
            }
        }.apply { start() }
    }

    fun stopListening() {
        if (!isListening) return

        isListening = false

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

            recordingThread?.join(1000)
            recordingThread = null

            asr?.stop(false)

        } catch (e: Exception) {
            Log.e(TAG, "停止录音异常: ${e.message}")
        }

        Log.d(TAG, "停止语音识别")
    }

    fun cancel() {
        isListening = false

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

            recordingThread?.join(500)
            recordingThread = null

            asr?.stop(true)
        } catch (e: Exception) {
            Log.e(TAG, "取消录音异常: ${e.message}")
        }

        onEndOfSpeech()
        Log.d(TAG, "取消语音识别")
    }

    fun destroy() {
        cancel()
        asr = null
        callback = null
        Log.d(TAG, "销毁语音识别器")
    }

    private fun stopInternal() {
        isListening = false

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

            recordingThread?.join(500)
            recordingThread = null
        } catch (e: Exception) {
            Log.e(TAG, "内部停止异常: ${e.message}")
        }

        android.os.Handler(android.os.Looper.getMainLooper()).post {
            onEndOfSpeech()
        }
    }
}
