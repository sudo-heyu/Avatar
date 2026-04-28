package com.example.scenic_avatar_guide_app.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * 音频播放器
 * 支持本地文件和网络URL播放
 */
class AudioPlayer(private val context: Context) {

    private val player: ExoPlayer = ExoPlayer.Builder(context).build()
    private var playJob: Job? = null

    // 播放进度
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    // 播放状态
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    // 回调
    var onPlayStart: (() -> Unit)? = null
    var onPlayComplete: (() -> Unit)? = null
    var onPlayError: ((String) -> Unit)? = null
    var onProgressUpdate: ((Float) -> Unit)? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            when {
                isPlaying -> {
                    _isPlaying.value = true
                    onPlayStart?.invoke()
                    startProgressTracking()
                }
                !isPlaying && _isPlaying.value -> {
                    // 播放被暂停或停止，不触发 onPlayComplete
                    _isPlaying.value = false
                    stopProgressTracking()
                }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_ENDED -> {
                    _isPlaying.value = false
                    _progress.value = 0f
                    onPlayComplete?.invoke()
                    stopProgressTracking()
                }
                Player.STATE_IDLE -> {
                    _isPlaying.value = false
                }
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _isPlaying.value = false
            onPlayError?.invoke(error.message ?: "播放错误")
        }
    }

    init {
        player.addListener(listener)
        player.setAudioAttributes(
            androidx.media3.common.AudioAttributes.Builder()
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_SPEECH)
                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .build(),
            false
        )
    }

    /**
     * 播放网络音频
     */
    fun play(url: String) {
        stop()
        val mediaItem = MediaItem.fromUri(url)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
    }

    /**
     * 播放本地文件
     */
    fun playFile(file: File) {
        play(file.absolutePath)
    }

    /**
     * 播放 assets 文件
     */
    fun playAssets(fileName: String) {
        // ExoPlayer 需要通过 asset:// 协议访问
        play("asset:///$fileName")
    }

    /**
     * 播放 PCM 数据（用于 TTS 实时输出）
     */
    fun playPcmData(pcmData: ByteArray, sampleRate: Int = 16000) {
        // PCM 播放需要特殊处理，这里简化为写入临时文件
        CoroutineScope(Dispatchers.IO).launch {
            val tempFile = File(context.cacheDir, "tts_temp.pcm")
            tempFile.writeBytes(pcmData)
            withContext(Dispatchers.Main) {
                play(tempFile.absolutePath)
            }
        }
    }

    /**
     * 停止播放
     */
    fun stop() {
        stopProgressTracking()
        player.stop()
        _isPlaying.value = false
        _progress.value = 0f
    }

    /**
     * 暂停
     */
    fun pause() {
        player.pause()
        _isPlaying.value = false
    }

    /**
     * 恢复
     */
    fun resume() {
        player.play()
        _isPlaying.value = true
    }

    /**
     * 获取总时长（毫秒）
     */
    fun getDuration(): Long {
        return player.duration.coerceAtLeast(0)
    }

    /**
     * 获取当前播放位置（毫秒）
     */
    fun getCurrentPosition(): Long {
        return player.currentPosition.coerceAtLeast(0)
    }

    /**
     * 跳转到指定位置
     */
    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    private fun startProgressTracking() {
        playJob?.cancel()
        playJob = CoroutineScope(Dispatchers.Main).launch {
            while (player.isPlaying) {
                val duration = player.duration.coerceAtLeast(1)
                val position = player.currentPosition.coerceAtLeast(0)
                val progress = (position.toFloat() / duration).coerceIn(0f, 1f)
                _progress.value = progress
                onProgressUpdate?.invoke(progress)
                delay(50) // 20fps 更新进度
            }
        }
    }

    private fun stopProgressTracking() {
        playJob?.cancel()
        playJob = null
    }

    /**
     * 释放资源
     */
    fun release() {
        stopProgressTracking()
        player.removeListener(listener)
        player.release()
    }
}
