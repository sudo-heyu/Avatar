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

    data class AvatarActionDelta(
        val action: AvatarAction
    ) : ChatStreamEvent

    data class SourcesDelta(
        val sources: List<SourceInfo>
    ) : ChatStreamEvent

    data class RouteDataDelta(
        val routeData: RouteData
    ) : ChatStreamEvent

    data class MetadataDelta(
        val metadata: ResponseMetadata
    ) : ChatStreamEvent

    data object Done : ChatStreamEvent

    data class Error(
        val code: Int? = null,
        val message: String
    ) : ChatStreamEvent
}

@Serializable
data class TtsSegmentData(
    @SerialName("segment_id")
    val segmentId: String,
    val text: String,
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
    val marks: List<TtsMarkItem>? = null
)
