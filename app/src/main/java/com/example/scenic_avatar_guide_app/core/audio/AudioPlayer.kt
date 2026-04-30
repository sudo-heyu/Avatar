package com.example.scenic_avatar_guide_app.core.audio

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

private const val TAG = "AudioPlayer"

/**
 * 音频播放器
 * 支持本地文件和网络URL播放
 */
class AudioPlayer(private val context: Context) {

    private val dataSourceFactory = DefaultDataSource.Factory(context)

    private val player: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
        .build()
    private var playJob: Job? = null
    private var currentUrl: String? = null

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
    var onMediaItemTransition: ((reason: Int, mediaItem: MediaItem?) -> Unit)? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Log.d(TAG, "onIsPlayingChanged: $isPlaying, currentUrl=$currentUrl")
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
            val stateName = when (playbackState) {
                Player.STATE_IDLE -> "IDLE"
                Player.STATE_BUFFERING -> "BUFFERING"
                Player.STATE_READY -> "READY"
                Player.STATE_ENDED -> "ENDED"
                else -> "UNKNOWN"
            }
            Log.d(TAG, "state=$stateName, items=${player.mediaItemCount}, idx=${player.currentMediaItemIndex}, pos=${player.currentPosition}ms")
            when (playbackState) {
                Player.STATE_READY -> {
                    Log.d(TAG, "音频准备就绪, duration=${player.duration}ms")
                }
                Player.STATE_ENDED -> {
                    Log.d(TAG, "音频播放完成")
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
            Log.e(TAG, "播放错误: ${error.message}", error)
            _isPlaying.value = false
            onPlayError?.invoke(error.message ?: "播放错误")
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            Log.d(TAG, "onMediaItemTransition: reason=$reason, mediaItem=$mediaItem")
            onMediaItemTransition?.invoke(reason, mediaItem)
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
     * 立即播放网络音频，清空当前队列
     */
    fun play(url: String) {
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

    /**
     * 添加到播放队列末尾（无缝衔接）
     */
    fun enqueue(url: String) {
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
                // 前一个已播放结束，seek 到新加入的 item 并恢复播放
                // playWhenReady 在 ENDED 后通常仍为 true，只需 seek 即可触发缓冲
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

    /**
     * 是否有待播放的媒体项
     */
    fun hasMediaItems(): Boolean = player.mediaItemCount > 0

    /**
     * 移除指定索引的媒体项（用于清理已播放的片段，保持播放列表精简）
     */
    fun removeMediaItem(index: Int) {
        if (index in 0 until player.mediaItemCount) {
            player.removeMediaItem(index)
        }
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
     * 停止播放
     */
    fun stop() {
        stopProgressTracking()
        player.stop()
        player.clearMediaItems()
        _isPlaying.value = false
        _progress.value = 0f
        currentUrl = null
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
     * 获取播放器是否真的在播放（直接查询 ExoPlayer 状态）
     * 比 isPlaying StateFlow 更可靠，用于口型同步
     */
    fun isActuallyPlaying(): Boolean {
        return player.isPlaying
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
