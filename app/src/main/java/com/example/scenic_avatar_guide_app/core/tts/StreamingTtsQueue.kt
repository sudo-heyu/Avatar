package com.example.scenic_avatar_guide_app.core.tts

import android.os.Looper
import android.util.Log
import androidx.media3.common.Player
import com.example.scenic_avatar_guide_app.core.audio.AudioPlayer
import com.example.scenic_avatar_guide_app.core.audio.AudioPlayerEvent
import com.example.scenic_avatar_guide_app.domain.model.TtsSegmentData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "StreamingTtsQueue"

/**
 * 流式 TTS 分段播放队列。
 *
 * Phase 1 修复: 线程契约
 * 所有公开方法（enqueue、finishInput、cancel、start）必须从主线程调用。
 * 内部协程使用 Dispatchers.Main scope，状态字段无需额外同步。
 */
class StreamingTtsQueue(
    private val audioPlayer: AudioPlayer,
    private val buildAudioUrl: suspend (String) -> String,
    private val onSegmentEnqueue: (TtsSegmentData) -> Unit,
    private val onSegmentStart: (TtsSegmentData) -> Unit,
    private val onSegmentComplete: (TtsSegmentData, actualDurationMs: Long) -> Unit,
    private val onWaitingForSegment: () -> Unit,
    private val onAllComplete: () -> Unit,
    private val onError: (Throwable) -> Unit,
    private val onBufferingStateChanged: ((Boolean) -> Unit)? = null
) {
    // Phase 1 修复: 主线程断言
    private fun assertMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "StreamingTtsQueue 必须在主线程调用，当前: ${Thread.currentThread().name}"
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val segmentChannel = Channel<TtsSegmentData>(20)

    private val segmentList = mutableListOf<TtsSegmentData>()
    private var currentPlayingIndex = -1
    private var inputFinished = false
    private var active = false
    private var segmentStartTime = 0L
    private var nextEnqueueIndex = 0

    // 状态保护：防止回调重入和竞态条件
    private val isTransitioning = AtomicBoolean(false)
    private val isCompleting = AtomicBoolean(false)
    private val pendingSegmentIndex = AtomicInteger(-1)

    init {
        // Phase 2: 用 Flow collect 替代 var 回调
        // 生命周期由 scope 控制，scope 取消时自动停止收集
        scope.launch {
            audioPlayer.events.collect { event ->
                when (event) {
                    is AudioPlayerEvent.MediaItemTransition -> {
                        handleMediaItemTransition(event.reason, event.mediaItem)
                    }
                    is AudioPlayerEvent.PlayComplete -> {
                        handlePlayComplete()
                    }
                    is AudioPlayerEvent.PlayError -> {
                        handleError(event.message)
                    }
                    is AudioPlayerEvent.BufferingStateChanged -> {
                        if (active) {
                            onBufferingStateChanged?.invoke(event.isBuffering)
                        }
                    }
                    is AudioPlayerEvent.PlayStart -> {
                        // PlayStart 由 AudioPlayer 内部处理，这里不需要额外处理
                    }
                    is AudioPlayerEvent.IsPlayingChanged -> {
                        // IsPlayingChanged 由 AudioPlayer 内部处理
                    }
                }
            }
        }

        // 单一协程串行处理所有 segment，确保严格顺序
        scope.launch {
            for (segment in segmentChannel) {
                if (!active) continue
                processSegmentSerial(segment)
            }
        }
    }

    // Phase 2: MediaItemTransition 处理逻辑（从 init 块提取）
    private fun handleMediaItemTransition(reason: Int, mediaItem: androidx.media3.common.MediaItem?) {
        if (!active) {
            // 跳过非活动状态
        } else if (!isTransitioning.compareAndSet(false, true)) {
            // 防止重入：如果已经在处理过渡，跳过
            Log.w(TAG, "[TRANSITION] 跳过重入的过渡请求, reason=$reason")
        } else {
            try {
                Log.d(TAG, "[TRANSITION] reason=$reason, playingIdx=$currentPlayingIndex, listSize=${segmentList.size}")

                when (reason) {
                    Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> {
                        // 自动过渡到下一个 segment
                        segmentList.getOrNull(currentPlayingIndex)?.let { completed ->
                            val elapsed = System.currentTimeMillis() - segmentStartTime
                            Log.d(TAG, "[COMPLETE] idx=$currentPlayingIndex segment=${completed.segmentId}, 历时=${elapsed}ms")
                            onSegmentComplete(completed, elapsed)
                        }

                        currentPlayingIndex++
                        val nextSegment = segmentList.getOrNull(currentPlayingIndex)
                        if (nextSegment != null) {
                            segmentStartTime = System.currentTimeMillis()
                            Log.d(TAG, "[START] idx=$currentPlayingIndex segment=${nextSegment.segmentId}")
                            onSegmentStart(nextSegment)
                        } else {
                            Log.d(TAG, "[QUEUE_END] 播放列表结束, inputFinished=$inputFinished")
                            currentPlayingIndex = -1
                            if (inputFinished) {
                                active = false
                                onAllComplete()
                            } else {
                                onWaitingForSegment()
                            }
                        }
                    }
                    Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> {
                        // 播放列表变化（首个 segment 加入）
                        if (currentPlayingIndex < 0 && segmentList.isNotEmpty()) {
                            currentPlayingIndex = 0
                            val firstSegment = segmentList[0]
                            segmentStartTime = System.currentTimeMillis()
                            Log.d(TAG, "[START-FIRST] idx=0 segment=${firstSegment.segmentId}")
                            onSegmentStart(firstSegment)
                        }
                    }
                    else -> {}
                }
            } finally {
                isTransitioning.set(false)
            }
        }
    }

    // Phase 2: PlayComplete 处理逻辑（从 init 块提取）
    private fun handlePlayComplete() {
        if (!active) {
            // 跳过非活动状态
        } else if (!isCompleting.compareAndSet(false, true)) {
            // 防止重入：如果已经在处理完成，跳过
            Log.w(TAG, "[PLAY_COMPLETE] 跳过重入的完成请求")
        } else {
            try {
                Log.d(TAG, "[PLAY_COMPLETE] playingIdx=$currentPlayingIndex, listSize=${segmentList.size}")

                // 只有当播放器真正结束且不是过渡中时才处理
                // 避免与 onMediaItemTransition 竞争
                if (currentPlayingIndex >= 0 && currentPlayingIndex < segmentList.size) {
                    // 检查是否是最后一个 segment
                    val isLastSegment = currentPlayingIndex == segmentList.size - 1
                    if (isLastSegment) {
                        val completed = segmentList[currentPlayingIndex]
                        val elapsed = System.currentTimeMillis() - segmentStartTime
                        Log.d(TAG, "[COMPLETE-LAST] idx=$currentPlayingIndex segment=${completed.segmentId}, 历时=${elapsed}ms")
                        onSegmentComplete(completed, elapsed)
                    }
                }

                currentPlayingIndex = -1

                if (inputFinished) {
                    Log.d(TAG, "[ALL_COMPLETE] 所有 segment 播放完成")
                    active = false
                    onAllComplete()
                } else {
                    onWaitingForSegment()
                }
            } finally {
                isCompleting.set(false)
            }
        }
    }

    // Phase 2: Error 处理逻辑（从 init 块提取）
    private fun handleError(error: String) {
        Log.e(TAG, "[ERROR] $error")
        if (active) {
            active = false
            onError(IllegalStateException(error))
        }
    }

    private suspend fun processSegmentSerial(segment: TtsSegmentData) {
        try {
            val index = segmentList.size
            Log.d(TAG, "[PROCESS] idx=$index segmentId=${segment.segmentId}")

            segmentList.add(segment)

            // Phase 0 修复 P0-2: onSegmentEnqueue 在主线程执行
            // preloadSegmentLipSync 是 CPU 计算，不涉及 IO，无需切换到 IO 线程
            onSegmentEnqueue(segment)

            // Phase 0 修复 P0-4: 只有 buildAudioUrl 在 IO 线程执行
            val fullUrl = withContext(Dispatchers.IO) {
                buildAudioUrl(segment.audioUrl)
            }
            Log.d(TAG, "[URL] idx=$index")

            if (!active) return

            // Phase 0 修复 P0-4: 显式保证在主线程调用 ExoPlayer
            withContext(Dispatchers.Main.immediate) {
                if (!active) return@withContext
                if (!audioPlayer.hasMediaItems()) {
                    Log.d(TAG, "[PLAY] idx=$index")
                    audioPlayer.play(fullUrl)
                } else {
                    Log.d(TAG, "[ENQUEUE] idx=$index")
                    audioPlayer.enqueue(fullUrl)
                }
            }

            nextEnqueueIndex++
        } catch (e: CancellationException) {
            // Phase 0 修复: CancellationException 必须重新抛出
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "[ERROR] ${e.message}", e)
            if (active) {
                active = false
                onError(e)
            }
        }
    }

    fun start() {
        assertMainThread()
        Log.d(TAG, "[START] 队列初始化")
        audioPlayer.stop()
        segmentList.clear()
        currentPlayingIndex = -1
        nextEnqueueIndex = 0
        inputFinished = false
        active = true
    }

    fun enqueue(segment: TtsSegmentData) {
        assertMainThread()
        if (!active) start()

        Log.d(TAG, "[ENQUEUE-REQ] segmentId=${segment.segmentId}")

        // 发送到 channel，由单一协程串行处理
        segmentChannel.trySend(segment)
    }

    fun finishInput() {
        assertMainThread()
        Log.d(TAG, "[FINISH] inputFinished=true, listSize=${segmentList.size}, playingIdx=$currentPlayingIndex")
        inputFinished = true

        if (currentPlayingIndex < 0 || currentPlayingIndex >= segmentList.size) {
            if (segmentList.isEmpty() || currentPlayingIndex >= segmentList.size) {
                Log.d(TAG, "[ALL_COMPLETE] finishInput 触发完成")
                active = false
                onAllComplete()
            }
        }
    }

    fun cancel() {
        assertMainThread()
        Log.d(TAG, "[CANCEL]")
        active = false
        inputFinished = false
        segmentList.clear()
        currentPlayingIndex = -1
        nextEnqueueIndex = 0
        // 重置所有状态标志
        isTransitioning.set(false)
        isCompleting.set(false)
        pendingSegmentIndex.set(-1)
        audioPlayer.stop()
    }

    fun release() {
        assertMainThread()
        cancel()
        segmentChannel.close()
        scope.cancel()
        audioPlayer.release()
    }
}
