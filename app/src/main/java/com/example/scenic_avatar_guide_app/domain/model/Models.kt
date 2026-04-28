package com.example.scenic_avatar_guide_app.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonElement

// ==================== 通用响应 ====================

@Serializable
data class ApiResponse<T>(
    val code: Int,
    val message: String,
    val data: T
)

// ==================== 健康检查 ====================

@Serializable
data class HealthResponse(
    val code: Int,
    val message: String,
    val data: HealthData
)

@Serializable
data class HealthData(
    val service: String,
    val status: String,
    val version: String? = null
)

// ==================== 会话管理 ====================

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
    val status: String,
    @SerialName("created_at")
    val createdAt: String
)

// ==================== 聊天接口 ====================

@Serializable
data class ChatTextRequest(
    @SerialName("session_id")
    val sessionId: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("scenic_id")
    val scenicId: String,
    val question: String,
    @SerialName("spot_id")
    val spotId: String? = null,
    /**
     * 交互模式
     * "chat" = 聊天问答（纯文本或图文）
     * "route" = 路线规划（返回结构化路线数据）
     */
    val mode: String = "chat",
    /**
     * 用户上传图片的 URL
     * 聊天模式下，若用户上传了图片，先调用 /api/v1/upload/image 获取 url 后填入
     */
    @SerialName("image_url")
    val imageUrl: String? = null,
    val options: ChatOptions? = null
)

@Serializable
data class ChatOptions(
    @SerialName("need_avatar")
    val needAvatar: Boolean = true,
    @SerialName("need_sources")
    val needSources: Boolean = true
)

@Serializable
data class ChatTextResponse(
    val code: Int,
    val message: String,
    val data: ChatResponseData
)

/**
 * 聊天响应数据
 *
 * TTS 由后端 Edge-TTS 服务统一提供，移动端通过 /api/v1/tts/synthesize 获取音频 URL
 */
@Serializable
data class ChatResponseData(
    @SerialName("message_id")
    val messageId: String? = null,

    @SerialName("session_id")
    val sessionId: String? = null,

    /**
     * 回复文本
     * 移动端通过后端 /api/v1/tts/synthesize 接口请求语音合成
     */
    @SerialName("reply_text")
    val replyText: String,

    /**
     * 数字人动作数据
     */
    @SerialName("avatar_action")
    val avatarAction: AvatarAction? = null,

    /**
     * 来源引用
     */
    @SerialName("sources")
    val sources: List<SourceInfo> = emptyList(),

    /**
     * 阶段一后端主链路直接返回的耗时字段
     * 若存在 metadata，则以 metadata 为准
     */
    @SerialName("latency_ms")
    val latencyMs: Long? = null,

    /**
     * 阶段一后端主链路直接返回的置信度字段
     */
    @SerialName("confidence")
    val confidence: Float? = null,

    /**
     * 阶段一后端主链路直接返回的降级标记
     */
    @SerialName("is_fallback")
    val isFallback: Boolean? = null,

    /**
     * 元数据
     */
    @SerialName("metadata")
    val metadata: ResponseMetadata? = null,

    /**
     * 路线规划数据
     * 仅在 mode=route 时返回，mode=chat 时为 null
     */
    @SerialName("route_data")
    val routeData: RouteData? = null,

    @SerialName("created_at")
    val createdAt: String? = null
) {
    val effectiveLatencyMs: Long?
        get() = metadata?.latencyMs ?: latencyMs

    val effectiveConfidence: Float?
        get() = metadata?.confidence ?: confidence

    val effectiveIsFallback: Boolean
        get() = metadata?.isFallback ?: isFallback ?: false

    val effectiveIntent: String?
        get() = metadata?.intent

    val effectiveEmotion: String?
        get() = metadata?.emotion
}

// ==================== 数字人动作系统 ====================

/**
 * 数字人动作数据
 */
@Serializable
data class AvatarAction(
    @SerialName("expression")
    val expression: AvatarExpressionData? = null,

    @SerialName("gesture")
    val gesture: AvatarGestureData? = null,

    @SerialName("motion_queue")
    val motionQueue: List<MotionQueueItem>? = null,

    @SerialName("marks")
    val marks: List<AvatarMarkData>? = null
)

/**
 * 表情控制数据
 */
@Serializable
data class AvatarExpressionData(
    @SerialName("type")
    val type: String = "neutral",

    @SerialName("intensity")
    val intensity: Float = 0.7f,

    @SerialName("transition_ms")
    val transitionMs: Long = 200
)

/**
 * 动作控制数据
 */
@Serializable
data class AvatarGestureData(
    @SerialName("type")
    val type: String = "idle",

    @SerialName("loop")
    val loop: Boolean = false,

    @SerialName("speed")
    val speed: Float = 1.0f,

    @SerialName("priority")
    val priority: String = "normal"
)

/**
 * 动作队列项
 */
@Serializable
data class MotionQueueItem(
    @SerialName("type")
    val type: String,

    @SerialName("start_offset_ms")
    val startOffsetMs: Long,

    @SerialName("duration_ms")
    val durationMs: Long = 0
)

/**
 * 特效标记数据
 */
@Serializable
data class AvatarMarkData(
    @SerialName("position")
    val position: Float,

    @SerialName("type")
    val type: String,

    @SerialName("params")
    val params: Map<String, JsonElement>? = null
)

// ==================== 来源引用 ====================

@Serializable
data class SourceInfo(
    @SerialName("document_id")
    val documentId: String? = null,

    @SerialName("chunk_id")
    val chunkId: String? = null,

    @SerialName("title")
    val title: String? = null,

    @SerialName("content")
    val content: String? = null,

    @SerialName("url")
    val url: String? = null,

    @SerialName("source_path")
    val sourcePath: String? = null,

    @SerialName("score")
    val score: Float? = null,

    @SerialName("snippet")
    val snippet: String? = null,

    @SerialName("relevance_score")
    val relevanceScore: Float? = null
)

// ==================== 元数据 ====================

@Serializable
data class ResponseMetadata(
    @SerialName("intent")
    val intent: String? = null,

    @SerialName("emotion")
    val emotion: String? = null,

    @SerialName("confidence")
    val confidence: Float? = null,

    @SerialName("is_fallback")
    val isFallback: Boolean? = null,

    @SerialName("latency_ms")
    val latencyMs: Long? = null
)

// ==================== 路线规划 ====================

/**
 * 路线规划响应数据
 */
@Serializable
data class RouteData(
    val title: String,
    @SerialName("total_duration_min")
    val totalDurationMin: Int,
    @SerialName("total_distance_m")
    val totalDistanceM: Int? = null,
    val spots: List<RouteSpot>,
    /**
     * 地图路径坐标数组（可选）
     * 用于在地图上绘制路线 polyline
     */
    val polyline: List<LatLngPoint>? = null
)

/**
 * 路线景点节点
 */
@Serializable
data class RouteSpot(
    val name: String,
    val lat: Double,
    val lng: Double,
    val order: Int,
    @SerialName("stay_min")
    val stayMin: Int,
    val description: String? = null,
    @SerialName("image_url")
    val imageUrl: String? = null
)

/**
 * 经纬度坐标点
 */
@Serializable
data class LatLngPoint(
    val lat: Double,
    val lng: Double
)

// ==================== UI 模型 ====================

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
    val sources: List<SourceInfo> = emptyList(),
    val avatarAction: AvatarAction? = null,
    /**
     * 路线规划数据（仅在路线模式下非空）
     */
    val routeData: RouteData? = null
)
