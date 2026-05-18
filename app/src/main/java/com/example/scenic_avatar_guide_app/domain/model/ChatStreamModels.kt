package com.example.scenic_avatar_guide_app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed interface ChatStreamEvent {
    data class MessageStart(
        val messageId: String?,
        val sessionId: String?,
        val createdAt: String? = null
    ) : ChatStreamEvent

    data class TextDelta(
        val delta: String
    ) : ChatStreamEvent

    data class TtsSegment(
        val segment: TtsSegmentData
    ) : ChatStreamEvent

    data class TtsSegmentReady(
        val segment: TtsSegmentData
    ) : ChatStreamEvent

    data class TtsAudioError(
        val error: TtsAudioErrorData
    ) : ChatStreamEvent

    data class AvatarActionDelta(
        val action: AvatarAction
    ) : ChatStreamEvent

    data class SourcesDelta(
        val sources: List<SourceInfo>
    ) : ChatStreamEvent

    data class ImagesDelta(
        val images: List<ChatImageInfo>
    ) : ChatStreamEvent

    data class RouteDataDelta(
        val routeData: RouteData
    ) : ChatStreamEvent

    data class MetadataDelta(
        val metadata: ResponseMetadata
    ) : ChatStreamEvent

    data object Done : ChatStreamEvent

    data object PrematurelyEnded : ChatStreamEvent

    data class Aborted(
        val messageId: String?,
        val sessionId: String?,
        val reason: String? = null
    ) : ChatStreamEvent

    data class Error(
        val code: Int? = null,
        val message: String
    ) : ChatStreamEvent
}

@Serializable
data class TtsAudioErrorData(
    @SerialName("segment_id")
    val segmentId: String? = null,
    @SerialName("segment_index")
    val segmentIndex: Int? = null,
    val code: Int? = null,
    val message: String? = null,
    val reason: String? = null,
    val error: String? = null
)

@Serializable
data class TtsSegmentData(
    @SerialName("segment_id")
    val segmentId: String,
    @SerialName("segment_index")
    val segmentIndex: Int? = null,
    val text: String = "",
    @SerialName("audio_url")
    val audioUrl: String,
    @SerialName("duration_ms")
    val durationMs: Int? = null,
    val voice: String? = null,
    /**
     * 实际使用语速（如 +0%）
     */
    val rate: String? = null,
    /**
     * 实际使用音量（如 +0%）
     */
    val volume: String? = null,
    /**
     * 实际使用音调（如 +0Hz）
     */
    val pitch: String? = null,
    /**
     * LLM 标注的情绪或后验推断的情绪，如 welcoming、excited、thinking 等
     */
    val emotion: String? = null,
    val marks: List<TtsMarkItem>? = null,
    /**
     * 流式音频相对于最终 audio_url 的时间偏移（毫秒）
     * 播放 chunk 流做口型同步时需要：marks_time = player_position_ms - stream_audio_offset_ms
     */
    @SerialName("stream_audio_offset_ms")
    val streamAudioOffsetMs: Int? = null,
    /**
     * 流式音频的实际时长（毫秒）
     */
    @SerialName("stream_audio_duration_ms")
    val streamAudioDurationMs: Int? = null
)
