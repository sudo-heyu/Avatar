package com.example.scenic_avatar_guide_app.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/**
 * 健康检查响应
 */
@Serializable
data class HealthResponse(
    val code: Int,
    val message: String,
    val data: HealthData
)

@Serializable
data class HealthData(
    val service: String,
    val status: String
)

/**
 * 创建会话响应
 */
@Serializable
data class SessionCreateResponse(
    val code: Int,
    val message: String,
    val data: SessionData
)

@Serializable
data class SessionData(
    @SerialName("session_id")
    val sessionId: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("scenic_id")
    val scenicId: String,
    @SerialName("spot_id")
    val spotId: String? = null,
    @SerialName("device_id")
    val deviceId: String? = null,
    val status: String,
    @SerialName("created_at")
    val createdAt: String
)

/**
 * 聊天响应
 */
@Serializable
data class ChatTextResponse(
    val code: Int,
    val message: String,
    val data: ChatResponseData
)

@Serializable
data class ChatResponseData(
    @SerialName("message_id")
    val messageId: String,
    @SerialName("session_id")
    val sessionId: String,
    @SerialName("reply_text")
    val replyText: String,
    @SerialName("audio_url")
    val audioUrl: String? = null,
    @SerialName("avatar_action")
    val avatarAction: String? = null,
    val sources: List<SourceInfo> = emptyList(),
    @SerialName("latency_ms")
    val latencyMs: Long,
    @SerialName("created_at")
    val createdAt: String
)

/**
 * 来源信息
 */
@Serializable
data class SourceInfo(
    val title: String? = null,
    val content: String? = null,
    val url: String? = null
)

/**
 * 会话创建请求
 */
@Serializable
data class SessionCreateRequest(
    @SerialName("user_id")
    val userId: String,
    @SerialName("scenic_id")
    val scenicId: String,
    @SerialName("spot_id")
    val spotId: String? = null,
    @SerialName("device_id")
    val deviceId: String? = null
)

/**
 * 文本聊天请求
 */
@Serializable
data class ChatTextRequest(
    @SerialName("session_id")
    val sessionId: String,
    @SerialName("user_id")
    val userId: String,
    val question: String,
    @SerialName("spot_id")
    val spotId: String? = null,
    @SerialName("need_audio")
    val needAudio: Boolean = false,
    @SerialName("need_avatar")
    val needAvatar: Boolean = false
)

/**
 * 聊天消息（UI 用）
 */
data class ChatMessage(
    val id: String,
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val isLoading: Boolean = false,
    val isError: Boolean = false,
    val sources: List<SourceInfo> = emptyList()
)
