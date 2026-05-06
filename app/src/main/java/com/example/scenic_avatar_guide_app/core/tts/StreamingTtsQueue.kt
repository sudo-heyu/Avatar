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
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "StreamingTtsQueue"

// Phase 3: 显式状态机，替代 active+inputFinished+currentPlayingIndex 三个散落字段
sealed class PlaybackState {
    object Idle : PlaybackState()
    data class Receiving(val playingIndex: Int) : PlaybackState()   // 还在接收 segment
    data class Draining(val playingIndex: Int) : PlaybackState()    // 已 finishInput，等待播完

    val isActive: Boolean get() = this !is Idle
    val currentIndex: Int get() = when (this) {
        is Receiving -> playingIndex
        is Draining -> playingIndex
        else -> -1
    }
}

/**
 * 流式 TTS 分段播放队列。
 *
 * Phase 1 修复: 线程契约 — 所有公开方法必须从主线程调用。
 * Phase 2 修复: Flow collect 替代 var 回调。
 * Phase 3 修复: PlaybackState 密封类替代多标志。
 * N1 修复: trySend 返回值检查。
 * N2 修复: sessionEpoch 计数器，丢弃跨会话陈旧事件。
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
    private fun assertMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "StreamingTtsQueue 必须在主线程调用，当前: ${Thread.currentThread().name}"
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val segmentChannel = Channel<TtsSegmentData>(20)

    // N2: 会话 epoch，cancel()/start() 时递增，使上一轮未消费事件失效
    private val sessionEpoch = AtomicInteger(0)

    private val segmentList = mutableListOf<TtsSegmentData>()
    private var playbackState: PlaybackState = PlaybackState.Idle
    private var segmentStartTime = 0L
    private var nextEnqueueIndex = 0
    private val pendingSegmentIndex = AtomicInteger(-1)
    private var acceptedSegmentCount = 0
    private var processedSegmentCount = 0

    init {
        // Phase 2: Flow collect 事件收集
        scope.launch {
            audioPlayer.events.collect { event ->
                val capturedEpoch = sessionEpoch.get()
                when (event) {
                    is AudioPlayerEvent.MediaItemTransition ->
                        handleMediaItemTransition(event.reason, event.mediaItem, capturedEpoch)
                    is AudioPlayerEvent.PlayComplete ->
                        handlePlayComplete(capturedEpoch)
                    is AudioPlayerEvent.PlayError ->
                        handleError(event.message, capturedEpoch)
                    is AudioPlayerEvent.BufferingStateChanged ->
                        if (playbackState.isActive) onBufferingStateChanged?.invoke(event.isBuffering)
                    is AudioPlayerEvent.PlayStart -> {}
                    is AudioPlayerEvent.IsPlayingChanged -> {}
                }
            }
        }

        // 单一协程串行处理所有 segment，确保严格顺序
        scope.launch {
            for (segment in segmentChannel) {
                if (!playbackState.isActive) continue
                processSegmentSerial(segment)
            }
        }
    }

    private fun handleMediaItemTransition(
        reason: Int,
        mediaItem: androidx.media3.common.MediaItem?,
        epoch: Int
    ) {
        if (sessionEpoch.get() != epoch) {
            Log.d(TAG, "[STALE_TRANSITION] 丢弃陈旧事件 (epoch=$epoch, current=${sessionEpoch.get()})")
            return
        }
        val state = playbackState
        if (!state.isActive) return

        Log.d(TAG, "[TRANSITION] reason=$reason, state=$state, listSize=${segmentList.size}")

        when (reason) {
            Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> {
                val completedIndex = state.currentIndex
                segmentList.getOrNull(completedIndex)?.let { completed ->
                    val elapsed = System.currentTimeMillis() - segmentStartTime
                    Log.d(TAG, "[COMPLETE] idx=$completedIndex segment=${completed.segmentId}, 历时=${elapsed}ms")
                    onSegmentComplete(completed, elapsed)
                }

                val nextIndex = completedIndex + 1
                val nextSegment = segmentList.getOrNull(nextIndex)
                if (nextSegment != null) {
                    playbackState = when (state) {
                        is PlaybackState.Receiving -> PlaybackState.Receiving(nextIndex)
                        is PlaybackState.Draining  -> PlaybackState.Draining(nextIndex)
                        else -> state
                    }
                    segmentStartTime = System.currentTimeMillis()
                    Log.d(TAG, "[START] idx=$nextIndex segment=${nextSegment.segmentId}")
                    onSegmentStart(nextSegment)
                } else {
                    Log.d(TAG, "[QUEUE_END] 播放列表结束, state=$state")
                    if (state is PlaybackState.Draining) {
                        if (hasAcceptedSegmentsWaiting()) {
                            Log.d(TAG, "[QUEUE_WAIT] 等待已接收但尚未处理的 segment: accepted=$acceptedSegmentCount processed=$processedSegmentCount")
                            playbackState = PlaybackState.Draining(-1)
                            onWaitingForSegment()
                        } else {
                            triggerAllComplete()
                        }
                    } else {
                        playbackState = PlaybackState.Receiving(-1)
                        onWaitingForSegment()
                    }
                }
            }
            Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> {
                if (state.currentIndex < 0 && segmentList.isNotEmpty()) {
                    playbackState = when (state) {
                        is PlaybackState.Receiving -> PlaybackState.Receiving(0)
                        is PlaybackState.Draining  -> PlaybackState.Draining(0)
                        else -> state
                    }
                    val firstSegment = segmentList[0]
                    segmentStartTime = System.currentTimeMillis()
                    Log.d(TAG, "[START-FIRST] idx=0 segment=${firstSegment.segmentId}")
                    onSegmentStart(firstSegment)
                }
            }
            else -> {}
        }
    }

    private fun handlePlayComplete(epoch: Int) {
        if (sessionEpoch.get() != epoch) {
            Log.d(TAG, "[STALE_COMPLETE] 丢弃陈旧 PlayComplete (epoch=$epoch, current=${sessionEpoch.get()})")
            return
        }
        val state = playbackState
        if (!state.isActive) return

        Log.d(TAG, "[PLAY_COMPLETE] state=$state, listSize=${segmentList.size}")

        val completedIndex = state.currentIndex
        if (completedIndex >= 0 && completedIndex < segmentList.size) {
            if (completedIndex == segmentList.size - 1) {
                val completed = segmentList[completedIndex]
                val elapsed = System.currentTimeMillis() - segmentStartTime
                Log.d(TAG, "[COMPLETE-LAST] idx=$completedIndex segment=${completed.segmentId}, 历时=${elapsed}ms")
                onSegmentComplete(completed, elapsed)
            }
        }

        if (state is PlaybackState.Draining) {
            if (hasAcceptedSegmentsWaiting()) {
                Log.d(TAG, "[PLAY_COMPLETE_WAIT] 等待已接收但尚未处理的 segment: accepted=$acceptedSegmentCount processed=$processedSegmentCount")
                playbackState = PlaybackState.Draining(-1)
                onWaitingForSegment()
                return
            }
            if (completedIndex >= 0 && completedIndex < segmentList.lastIndex) {
                Log.d(TAG, "[PLAY_COMPLETE_WAIT] 播放完成但本地队列还有未播 segment: completed=$completedIndex listSize=${segmentList.size}")
                playbackState = PlaybackState.Draining(completedIndex)
                onWaitingForSegment()
                return
            }
            Log.d(TAG, "[ALL_COMPLETE] PlayComplete 触发完成")
            triggerAllComplete()
        } else {
            playbackState = PlaybackState.Receiving(-1)
            onWaitingForSegment()
        }
    }

    private fun handleError(error: String, epoch: Int) {
        Log.e(TAG, "[ERROR] $error")
        if (sessionEpoch.get() != epoch) return
        if (playbackState.isActive) {
            playbackState = PlaybackState.Idle
            onError(IllegalStateException(error))
        }
    }

    private fun triggerAllComplete() {
        playbackState = PlaybackState.Idle
        onAllComplete()
    }

    private suspend fun processSegmentSerial(segment: TtsSegmentData) {
        try {
            val index = segmentList.size
            // N2-补: 在 IO 挂起前捕获 epoch，IO 返回后校验，防止旧 segment 注入新 session
            val capturedEpoch = sessionEpoch.get()
            Log.d(TAG, "[PROCESS] idx=$index segmentId=${segment.segmentId} epoch=$capturedEpoch")

            segmentList.add(segment)
            processedSegmentCount++

            // Phase 0 修复 P0-2: onSegmentEnqueue 在主线程执行
            onSegmentEnqueue(segment)

            // Phase 0 修复 P0-4: 只有 buildAudioUrl 在 IO 线程执行
            val fullUrl = withContext(Dispatchers.IO) {
                buildAudioUrl(segment.audioUrl)
            }
            Log.d(TAG, "[URL] idx=$index")

            // 双重保护：同时检查 playbackState 和 epoch（防止 cancel→start 间隙注入旧 URL）
            if (!playbackState.isActive || sessionEpoch.get() != capturedEpoch) {
                Log.d(TAG, "[PROCESS-ABORT] stale segment, epoch=$capturedEpoch current=${sessionEpoch.get()}")
                return
            }

            // Phase 0 修复 P0-4: 显式保证在主线程调用 ExoPlayer
            withContext(Dispatchers.Main.immediate) {
                if (!playbackState.isActive || sessionEpoch.get() != capturedEpoch) return@withContext
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
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "[ERROR] ${e.message}", e)
            if (playbackState.isActive) {
                playbackState = PlaybackState.Idle
                onError(e)
            }
        }
    }

    fun start() {
        assertMainThread()
        sessionEpoch.incrementAndGet()
        Log.d(TAG, "[START] 队列初始化, epoch=${sessionEpoch.get()}")
        audioPlayer.stop()
        segmentList.clear()
        nextEnqueueIndex = 0
        acceptedSegmentCount = 0
        processedSegmentCount = 0
        pendingSegmentIndex.set(-1)
        playbackState = PlaybackState.Receiving(-1)
    }

    fun enqueue(segment: TtsSegmentData) {
        assertMainThread()
        if (!playbackState.isActive) start()

        Log.d(TAG, "[ENQUEUE-REQ] segmentId=${segment.segmentId}")

        // N1 修复: 检查返回值，channel 满时报错而非静默丢弃
        val result = segmentChannel.trySend(segment)
        if (result.isSuccess) {
            acceptedSegmentCount++
        } else {
            Log.e(TAG, "[ENQUEUE-FAIL] channel full, segment dropped: id=${segment.segmentId}, " +
                    "listSize=${segmentList.size}, playingIdx=${playbackState.currentIndex}")
            onError(IllegalStateException("TTS segment channel full, segment ${segment.segmentId} dropped"))
        }
    }

    fun finishInput() {
        assertMainThread()
        val state = playbackState
        Log.d(TAG, "[FINISH] state=$state, listSize=${segmentList.size}")

        when (state) {
            is PlaybackState.Receiving -> {
                val idx = state.playingIndex
                playbackState = PlaybackState.Draining(idx)
                if (idx < 0 || idx >= segmentList.size) {
                    if (hasAcceptedSegmentsWaiting()) {
                        Log.d(TAG, "[FINISH_WAIT] 等待已接收但尚未处理的 segment: accepted=$acceptedSegmentCount processed=$processedSegmentCount")
                    } else if (segmentList.isEmpty() || idx >= segmentList.size) {
                        Log.d(TAG, "[ALL_COMPLETE] finishInput 触发完成（无 segment）")
                        triggerAllComplete()
                    }
                }
            }
            is PlaybackState.Idle -> Log.w(TAG, "[FINISH] 忽略：队列未激活")
            is PlaybackState.Draining -> Log.w(TAG, "[FINISH] 重复调用，已在 Draining 状态")
        }
    }

    fun cancel() {
        assertMainThread()
        val newEpoch = sessionEpoch.incrementAndGet()
        Log.d(TAG, "[CANCEL] from state=$playbackState, epoch→$newEpoch")
        playbackState = PlaybackState.Idle
        segmentList.clear()
        nextEnqueueIndex = 0
        acceptedSegmentCount = 0
        processedSegmentCount = 0
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

    private fun hasAcceptedSegmentsWaiting(): Boolean {
        return processedSegmentCount < acceptedSegmentCount
    }
}
