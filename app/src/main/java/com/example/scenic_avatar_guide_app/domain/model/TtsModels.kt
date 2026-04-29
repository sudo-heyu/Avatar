package com.example.scenic_avatar_guide_app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ==================== TTS 合成请求 ====================

@Serializable
data class TtsSynthesizeRequest(
    val text: String,
    val voice: String = "zh-CN-XiaoxiaoNeural",
    val rate: String = "+0%",
    val volume: String = "+0dB",
    val pitch: String = "+0Hz",
    val format: String = "audio_with_marks"
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
    @SerialName("audio_url")
    val audioUrl: String,

    /**
     * 音频总时长（毫秒）
     * 后端暂未计算时可返回 null，因此使用可空类型
     */
    @SerialName("duration_ms")
    val durationMs: Int? = null,

    val voice: String,

    val marks: List<TtsMarkItem>? = null
)

@Serializable
data class TtsMarkItem(
    val text: String,

    @SerialName("start_ms")
    val startMs: Int,

    @SerialName("end_ms")
    val endMs: Int,

    /**
     * 该字的音素序列（可选，后端未提供时由 Android 端本地生成）
     */
    val phonemes: List<String>? = null
)

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
