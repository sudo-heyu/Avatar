package com.example.scenic_avatar_guide_app.core.tts

import android.util.Log
import androidx.media3.common.Player
import com.example.scenic_avatar_guide_app.core.audio.AudioPlayer
import com.example.scenic_avatar_guide_app.domain.model.TtsSegmentData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "StreamingTtsQueue"

/**
 * 流式 TTS 分段播放队列。
 *
 * 利用 ExoPlayer 的播放列表能力实现片段间无缝衔接：
 * - 收到 segment 后立即加入 ExoPlayer 队列，由播放器自动缓冲和过渡
 * - 通过 onMediaItemTransition(AUTO) 精确感知片段切换，驱动口型动画
 * - 消除旧方案中 "播放完 → stop → play" 带来的明显停顿
 * - 串行 enqueue 保证片段顺序严格一致
 */
class StreamingTtsQueue(
    private val audioPlayer: AudioPlayer,
    private val buildAudioUrl: suspend (String) -> String,
    private val onSegmentStart: (TtsSegmentData) -> Unit,
    private val onSegmentComplete: (TtsSegmentData) -> Unit,
    private val onWaitingForSegment: () -> Unit,
    private val onAllComplete: () -> Unit,
    private val onError: (Throwable) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val enqueueMutex = Mutex()

    // 尚未构建完整 URL 的待处理片段
    private val pendingQueue = ArrayDeque<TtsSegmentData>()

    // 已提交给 ExoPlayer 的片段（按播放顺序）
    private val submittedSegments = ArrayDeque<TtsSegmentData>()

    private var currentSegment: TtsSegmentData? = null
    private var segmentStarted = false
    private var inputFinished = false
    private var active = false

    // 精确计时（用于诊断 segment 间 gap）
    private var segmentStartTime = 0L

    init {
        audioPlayer.onMediaItemTransition = { reason, mediaItem ->
            if (active && mediaItem != null) {
                when (reason) {
                    Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> {
                        // 自然过渡：上一个片段播放完成，自动进入下一个
                        val completed = submittedSegments.removeFirstOrNull()
                        if (completed != null) {
                            val elapsed = System.currentTimeMillis() - segmentStartTime
                            Log.d(TAG, "[TIMER] segment=${completed.segmentId} 播放完成，历时=${elapsed}ms，队列剩余=${submittedSegments.size}")
                            onSegmentComplete(completed)
                            currentSegment = submittedSegments.firstOrNull()
                            segmentStartTime = System.currentTimeMillis()
                            currentSegment?.let(onSegmentStart)
                        }
                    }
                    Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED,
                    Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> {
                        // 播放列表变更或跳转：新片段开始播放
                        if (!segmentStarted && submittedSegments.isNotEmpty()) {
                            currentSegment = submittedSegments.firstOrNull()
                            segmentStarted = true
                            segmentStartTime = System.currentTimeMillis()
                            Log.d(TAG, "[TIMER] segment=${currentSegment?.segmentId} 开始播放")
                            currentSegment?.let(onSegmentStart)
                        }
                    }
                }
            }
        }

        audioPlayer.onPlayComplete = {
            if (active) {
                // 处理最后一个 segment 的 complete（没有 AUTO transition 触发它）
                val completed = submittedSegments.removeFirstOrNull()
                if (completed != null) {
                    val elapsed = System.currentTimeMillis() - segmentStartTime
                    Log.d(TAG, "[TIMER] segment=${completed.segmentId} 播放完成(ENDED)，历时=${elapsed}ms")
                    onSegmentComplete(completed)
                }
                currentSegment = null
                segmentStarted = false

                if (inputFinished) {
                    active = false
                    onAllComplete()
                } else {
                    // 输入尚未结束，保持 active，等待后续 segment
                    Log.d(TAG, "[TIMER] 播放队列暂时为空，等待新片段...")
                    onWaitingForSegment()
                }
            }
        }

        audioPlayer.onPlayError = { error ->
            fail(IllegalStateException(error))
        }
    }

    fun start() {
        active = false
        audioPlayer.stop()
        pendingQueue.clear()
        submittedSegments.clear()
        currentSegment = null
        segmentStarted = false
        inputFinished = false
        active = true
        Log.d(TAG, "队列已启动")
    }

    fun enqueue(segment: TtsSegmentData) {
        if (!active) start()
        pendingQueue.addLast(segment)
        submittedSegments.addLast(segment)

        scope.launch {
            enqueueMutex.withLock {
                try {
                    val fullUrl = buildAudioUrl(segment.audioUrl)
                    Log.d(TAG, "enqueue segment: ${segment.segmentId}, url=$fullUrl")
                    if (!active) return@withLock

                    if (!audioPlayer.hasMediaItems()) {
                        // 队列为空，立即开始播放
                        currentSegment = segment
                        audioPlayer.play(fullUrl)
                    } else {
                        // 已有内容在播放/缓冲，追加到队列实现无缝衔接
                        audioPlayer.enqueue(fullUrl)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "播放音频失败", e)
                    if (active) fail(e)
                }
            }
        }
    }

    fun finishInput() {
        inputFinished = true
        if (active && submittedSegments.isEmpty() && pendingQueue.isEmpty() && !audioPlayer.isPlaying.value) {
            Log.d(TAG, "输入已结束且队列为空，立即完成")
            active = false
            currentSegment = null
            segmentStarted = false
            onAllComplete()
        }
    }

    fun cancel() {
        active = false
        inputFinished = false
        pendingQueue.clear()
        submittedSegments.clear()
        currentSegment = null
        segmentStarted = false
        audioPlayer.stop()
        Log.d(TAG, "队列已取消")
    }

    fun release() {
        cancel()
        scope.cancel()
    }

    private fun fail(error: Throwable) {
        if (!active) return
        Log.e(TAG, "队列失败: ${error.message}")
        active = false
        inputFinished = false
        pendingQueue.clear()
        submittedSegments.clear()
        currentSegment = null
        segmentStarted = false
        audioPlayer.stop()
        onError(error)
    }
}
