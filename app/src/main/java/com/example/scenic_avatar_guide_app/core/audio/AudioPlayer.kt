package com.example.scenic_avatar_guide_app.core.audio

import android.content.Context
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

private const val TAG = "AudioPlayer"

class AudioPlayer(private val context: Context) {

    private val dataSourceFactory = DefaultDataSource.Factory(context)

    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            2000,
            10000,
            500,
            1000
        )
        .build()

    private val player: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
        .setLoadControl(loadControl)
        .build()
    private var playJob: Job? = null
    private val progressScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentUrl: String? = null

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    var onPlayStart: (() -> Unit)? = null
    var onPlayComplete: (() -> Unit)? = null
    var onPlayError: ((String) -> Unit)? = null
    var onProgressUpdate: ((Float) -> Unit)? = null
    var onMediaItemTransition: ((reason: Int, mediaItem: MediaItem?) -> Unit)? = null
    var onBufferingStateChanged: ((isBuffering: Boolean) -> Unit)? = null
    var onIsPlayingChanged: ((isPlaying: Boolean) -> Unit)? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Log.d(TAG, "onIsPlayingChanged: $isPlaying, currentUrl=$currentUrl")
            onIsPlayingChanged?.invoke(isPlaying)
            when {
                isPlaying -> {
                    _isPlaying.value = true
                    onPlayStart?.invoke()
                    startProgressTracking()
                }
                !isPlaying && _isPlaying.value -> {
                    _isPlaying.value = false
                    stopProgressTracking()
                }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val stateName = when (playbackState) {
                Player.STATE_IDLE -> "IDLE"
                Player.STATE_BUFFERING -> "BUFFERING"
                Player.STATE_READY -> "READY"
                Player.STATE_ENDED -> "ENDED"
                else -> "UNKNOWN"
            }
            Log.d(TAG, "[LATENCY] state=$stateName, items=${player.mediaItemCount}, idx=${player.currentMediaItemIndex}, pos=${player.currentPosition}ms")

            val wasBuffering = _isBuffering.value
            _isBuffering.value = playbackState == Player.STATE_BUFFERING
            if (wasBuffering != _isBuffering.value) {
                Log.d(TAG, "[BUFFER] 缓冲状态变化: ${_isBuffering.value}")
                onBufferingStateChanged?.invoke(_isBuffering.value)
            }

            when (playbackState) {
                Player.STATE_READY -> {
                    Log.d(TAG, "[LATENCY] 音频准备就绪, duration=${player.duration}ms")
                }
                Player.STATE_ENDED -> {
                    Log.d(TAG, "音频播放完成")
                    _isPlaying.value = false
                    _isBuffering.value = false
                    _progress.value = 0f
                    onPlayComplete?.invoke()
                    stopProgressTracking()
                }
                Player.STATE_IDLE -> {
                    _isPlaying.value = false
                    _isBuffering.value = false
                }
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Log.e(TAG, "播放错误: ${error.message}", error)
            _isPlaying.value = false
            onPlayError?.invoke(error.message ?: "播放错误")
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            Log.d(TAG, "onMediaItemTransition: reason=$reason, mediaItem=$mediaItem")
            onMediaItemTransition?.invoke(reason, mediaItem)
        }
    }

    private fun checkMainThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Log.wtf(TAG, "ExoPlayer 方法必须在主线程调用", Throwable())
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

    fun play(url: String) {
        checkMainThread()
        Log.d(TAG, "play: $url")
        currentUrl = url
        player.stop()
        player.clearMediaItems()
        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setMediaId(url)
            .build()
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
    }

    fun enqueue(url: String) {
        checkMainThread()
        Log.d(TAG, "enqueue: $url, state=${player.playbackState}, items=${player.mediaItemCount}, idx=${player.currentMediaItemIndex}")
        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setMediaId(url)
            .build()
        player.addMediaItem(mediaItem)
        when (player.playbackState) {
            Player.STATE_IDLE -> {
                player.prepare()
                player.playWhenReady = true
            }
            Player.STATE_ENDED -> {
                val targetIdx = (player.currentMediaItemIndex + 1).coerceAtMost(player.mediaItemCount - 1)
                Log.d(TAG, "从 ENDED 恢复，seekTo $targetIdx")
                player.seekToDefaultPosition(targetIdx)
            }
            else -> {
                if (!player.playWhenReady) {
                    player.playWhenReady = true
                }
            }
        }
    }

    fun hasMediaItems(): Boolean = player.mediaItemCount > 0

    fun removeMediaItem(index: Int) {
        checkMainThread()
        if (index in 0 until player.mediaItemCount) {
            player.removeMediaItem(index)
        }
    }

    fun playFile(file: File) {
        play(file.absolutePath)
    }

    fun playAssets(fileName: String) {
        play("asset:///$fileName")
    }

    fun stop() {
        runCatching {
            checkMainThread()
            stopProgressTracking()
            player.stop()
            player.clearMediaItems()
            _isPlaying.value = false
            _progress.value = 0f
            currentUrl = null
        }.onFailure {
            Log.w(TAG, "stop failed: ${it.message}")
        }
    }

    fun pause() {
        player.pause()
        _isPlaying.value = false
    }

    fun resume() {
        player.play()
        _isPlaying.value = true
    }

    fun getDuration(): Long {
        return player.duration.coerceAtLeast(0)
    }

    fun getCurrentPosition(): Long {
        return player.currentPosition.coerceAtLeast(0)
    }

    fun isActuallyPlaying(): Boolean {
        return player.isPlaying
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    private fun startProgressTracking() {
        playJob?.cancel()
        playJob = progressScope.launch {
            while (isActive && player.isPlaying) {
                val duration = player.duration.coerceAtLeast(1)
                val position = player.currentPosition.coerceAtLeast(0)
                val progress = (position.toFloat() / duration).coerceIn(0f, 1f)
                _progress.value = progress
                onProgressUpdate?.invoke(progress)
                delay(50)
            }
        }
    }

    private fun stopProgressTracking() {
        playJob?.cancel()
        playJob = null
    }

    fun release() {
        runCatching {
            checkMainThread()
            stopProgressTracking()
            progressScope.cancel()
            player.removeListener(listener)
            player.stop()
            player.clearMediaItems()
            player.release()
        }.onFailure {
            Log.w(TAG, "release failed: ${it.message}")
        }
    }
}
