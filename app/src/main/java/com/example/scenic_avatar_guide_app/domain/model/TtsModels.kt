package com.example.scenic_avatar_guide_app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ==================== TTS 合成请求 ====================

@Serializable
data class TtsSynthesizeRequest(
    val text: String,
    val voice: String
)

// ==================== TTS 合成响应 ====================

@Serializable
data class TtsSynthesizeResponse(
    val code: Int,
    val message: String,
    val data: TtsSynthesizeData
)

@Serializable
data class TtsSynthesizeData(
    @SerialName("file_name")
    val fileName: String? = null,

    @SerialName("audio_url")
    val audioUrl: String,

    @SerialName("duration_ms")
    val durationMs: Int? = null,

    val marks: List<TtsMarkItem>? = null
)

@Serializable
data class TtsMarkItem(
    @SerialName("word")
    val word: String = "",

    @SerialName("text")
    val text: String? = null,

    @SerialName("start_ms")
    val startMs: Int,

    @SerialName("end_ms")
    val endMs: Int,

    val phonemes: List<String>? = null
) {
    val spokenText: String
        get() = word.ifBlank { text.orEmpty() }
}

// ==================== TTS 发音人列表 ====================

@Serializable
data class TtsVoicesResponse(
    val code: Int,
    val message: String,
    val data: List<TtsVoiceInfo>
)

@Serializable
data class TtsVoiceInfo(
    val id: String,
    val locale: String,
    val gender: String,
    @SerialName("friendly_name")
    val friendlyName: String
)
