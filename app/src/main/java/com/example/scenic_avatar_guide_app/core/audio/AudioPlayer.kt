package com.example.scenic_avatar_guide_app.core.audio

import android.content.Context
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
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

/**
 * 音频播放器
 * 支持本地文件和网络URL播放
 */
class AudioPlayer(private val context: Context) {

    val chunkStreamManager = ChunkStreamManager()

    private val dataSourceFactory = HybridDataSourceFactory(context, chunkStreamManager)

    // 缓冲控制：为流式 chunk 场景提高缓冲量，避免网络波动导致播放中断
    // 配合 AvatarPlaybackManager 的预缓冲策略，确保 PipedInputStream 有充足数据
    // 针对后端 chunk 到达不稳定的情况，增大缓冲量以提高鲁棒性
    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            /* minBufferMs = */ 5000,         // 最小缓冲：提高到 5 秒，应对后端不稳定
            /* maxBufferMs = */ 15000,       // 最大缓冲：允许更大，预存更多数据
            /* bufferForPlaybackMs = */ 1200, // 播放所需缓冲：1.2秒，确保流畅启动
            /* bufferForPlaybackAfterRebufferMs = */ 2000 // 重新缓冲后播放：提高恢复阈值
        )
        .build()

    private val player: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
        .setLoadControl(loadControl)
        .build()
    private var playJob: Job? = null
    private val progressScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentUrl: String? = null

    // 播放进度
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    // 播放状态
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    // 缓冲状态（用于通知上层音频暂时不可用）
    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    // 回调
    var onPlayStart: (() -> Unit)? = null
    var onPlayComplete: (() -> Unit)? = null
    var onPlayError: ((String) -> Unit)? = null
    var onProgressUpdate: ((Float) -> Unit)? = null
    var onMediaItemTransition: ((reason: Int, mediaItem: MediaItem?) -> Unit)? = null
    var onBufferingStateChanged: ((isBuffering: Boolean) -> Unit)? = null

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
            Log.d(TAG, "[LATENCY] state=$stateName, items=${player.mediaItemCount}, idx=${player.currentMediaItemIndex}, pos=${player.currentPosition}ms")

            // 缓冲状态处理：通知上层暂停/恢复口型同步
            val wasBuffering = _isBuffering.value
            _isBuffering.value = playbackState == Player.STATE_BUFFERING
            if (wasBuffering != _isBuffering.value) {
                Log.d(TAG, "[BUFFER] 缓冲状态变化: ${_isBuffering.value}")
                onBufferingStateChanged?.invoke(_isBuffering.value)
            }

            when (playbackState) {
                Player.STATE_READY -> {
                    Log.d(TAG, "[LATENCY] 音频准备就绪, duration=${player.duration}ms, 从play调用到READY耗时估算")
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

    /**
     * 立即播放网络音频，清空当前队列
     */
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

    /**
     * 添加到播放队列末尾（无缝衔接）
     */
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
        checkMainThread()
        if (index in 0 until player.mediaItemCount) {
            player.removeMediaItem(index)
        }
    }

    /**
     * 为指定 streamId 预先创建 PipedStream。
     * 应在启动写入协程之前同步调用，确保 writeChunk 时 stream 已存在。
     */
    fun prepareStream(streamId: String): Boolean {
        return chunkStreamManager.createStream(streamId)
    }

    /**
     * 为流式 chunk segment 创建 PipedStream 并将 MediaItem 入队到 ExoPlayer。
     * URI 格式：chunk://stream/{streamId}
     */
    fun enqueueStream(streamId: String) {
        checkMainThread()
        Log.d(TAG, "enqueueStream: $streamId, state=${player.playbackState}, items=${player.mediaItemCount}")
        val created = chunkStreamManager.createStream(streamId)
        Log.d(TAG, "enqueueStream: createStream=$created")
        val mediaItem = MediaItem.Builder()
            .setUri("chunk://stream/$streamId")
            .setMediaId(streamId)
            .setMimeType(MimeTypes.AUDIO_MPEG)
            .build()
        player.addMediaItem(mediaItem)
        when (player.playbackState) {
            Player.STATE_IDLE -> {
                Log.d(TAG, "enqueueStream: STATE_IDLE -> prepare + play")
                player.prepare()
                player.playWhenReady = true
            }
            Player.STATE_ENDED -> {
                val targetIdx = (player.currentMediaItemIndex + 1).coerceAtMost(player.mediaItemCount - 1)
                Log.d(TAG, "enqueueStream: STATE_ENDED -> seekTo $targetIdx, playWhenReady=${player.playWhenReady}")
                player.seekToDefaultPosition(targetIdx)
                if (!player.playWhenReady) {
                    player.playWhenReady = true
                }
            }
            else -> {
                Log.d(TAG, "enqueueStream: state=${player.playbackState} -> ensure playWhenReady")
                if (!player.playWhenReady) {
                    player.playWhenReady = true
                }
            }
        }
    }

    /**
     * 将解码后的 chunk 字节写入对应 stream 的 PipedOutputStream。
     * 应在 IO 线程调用。
     */
    fun writeChunk(streamId: String, bytes: ByteArray) {
        chunkStreamManager.writeChunk(streamId, bytes)
    }

    /**
     * 关闭 stream 的写端，让 ExoPlayer 自然结束该 MediaItem。
     */
    fun endStream(streamId: String) {
        Log.d(TAG, "endStream: $streamId")
        chunkStreamManager.endStream(streamId)
    }

    /**
     * 强制中断 stream，从播放列表中移除对应 MediaItem。
     */
    fun abortStream(streamId: String) {
        checkMainThread()
        Log.d(TAG, "abortStream: $streamId")
        chunkStreamManager.abortStream(streamId)
        val idx = (0 until player.mediaItemCount).indexOfFirst {
            player.getMediaItemAt(it).mediaId == streamId
        }
        if (idx >= 0) {
            player.removeMediaItem(idx)
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
        runCatching {
            checkMainThread()
            stopProgressTracking()
            player.stop()
            player.clearMediaItems()
            chunkStreamManager.cleanupAll()
            _isPlaying.value = false
            _progress.value = 0f
            currentUrl = null
        }.onFailure {
            Log.w(TAG, "stop failed: ${it.message}")
        }
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
        playJob = progressScope.launch {
            while (isActive && player.isPlaying) {
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
     *
     * 针对 Android 15 Scudo + Binder Parcel 兼容性问题的优化释放流程：
     * 1. 先停止播放并清空队列
     * 2. 移除监听器防止回调
     * 3. 短暂延迟让内部线程稳定
     * 4. 最后释放 ExoPlayer
     */
    fun release() {
        runCatching {
            checkMainThread()
            stopProgressTracking()
            progressScope.cancel()
            player.removeListener(listener)
            player.stop()
            player.clearMediaItems()
            chunkStreamManager.cleanupAll()
            player.release()
        }.onFailure {
            Log.w(TAG, "release failed: ${it.message}")
        }
    }
}
