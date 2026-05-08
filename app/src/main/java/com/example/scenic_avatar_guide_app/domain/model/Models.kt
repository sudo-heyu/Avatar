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
    @SerialName("profile_id")
    val profileId: String? = null,
    val question: String,
    @SerialName("spot_id")
    val spotId: String? = null,
    val mode: String = "chat",
    @SerialName("image_url")
    val imageUrl: String? = null,
    val options: ChatOptions? = null
)

@Serializable
data class ChatOptions(
    val voice: String? = null,
    val rate: String? = null,
    val volume: String? = null,
    val pitch: String? = null
)

@Serializable
data class ChatAbortRequest(
    @SerialName("session_id")
    val sessionId: String? = null,
    @SerialName("message_id")
    val messageId: String? = null,
    val reason: String? = null
)

@Serializable
data class ChatAbortResponse(
    val code: Int,
    val message: String,
    val data: ChatAbortResponseData? = null
)

@Serializable
data class ChatAbortResponseData(
    val aborted: Boolean,
    @SerialName("aborted_message_ids")
    val abortedMessageIds: List<String> = emptyList(),
    val reason: String? = null
)

// ==================== 数字人动作系统 ====================

/**
 * 数字人动作数据（Combo 预设或实时动作）
 */
@Serializable
data class AvatarAction(
    @SerialName("text")
    val text: String? = null,

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
    val latencyMs: Long? = null,

    @SerialName("combo")
    val combo: String? = null
)

// ==================== 图片上传 ====================

@Serializable
data class UploadImageResponse(
    val code: Int,
    val message: String,
    val data: UploadImageData
)

@Serializable
data class UploadImageData(
    @SerialName("image_url")
    val imageUrl: String
)

// ==================== 路线规划 ====================

/**
 * 路线规划响应数据
 */
@Serializable
data class RouteData(
    @SerialName("route_id")
    val routeId: String? = null,

    val title: String,

    @SerialName("scenic_id")
    val scenicId: String? = null,

    @SerialName("interest_tags")
    val interestTags: List<String>? = null,

    @SerialName("current_spot")
    val currentSpot: String? = null,

    @SerialName("total_duration_min")
    val totalDurationMin: Int,

    @SerialName("total_distance_m")
    val totalDistanceM: Int? = null,

    val reason: String? = null,

    val highlights: List<String>? = null,

    val tips: List<String>? = null,

    val spots: List<RouteSpot>,

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

// ==================== 会话列表 ====================

@Serializable
data class SessionListResponse(
    val code: Int,
    val message: String,
    val data: SessionListData
)

@Serializable
data class SessionListData(
    val total: Int,
    val page: Int = 1,
    @SerialName("page_size")
    val pageSize: Int = 20,
    val sessions: List<SessionInfo>
)

@Serializable
data class SessionInfo(
    @SerialName("session_id")
    val sessionId: String,
    @SerialName("user_id")
    val userId: String? = null,
    @SerialName("scenic_id")
    val scenicId: String? = null,
    @SerialName("spot_id")
    val spotId: String? = null,
    @SerialName("device_id")
    val deviceId: String? = null,
    val status: String,
    val title: String? = null,
    @SerialName("message_count")
    val messageCount: Int = 0,
    @SerialName("context_summary")
    val contextSummary: String? = null,
    @SerialName("context_entities")
    val contextEntities: String? = null,
    @SerialName("first_user_message")
    val firstUserMessage: String? = null,
    @SerialName("last_message")
    val lastMessage: String? = null,
    @SerialName("last_message_at")
    val lastMessageAt: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null
) {
    fun displayTitle(): String {
        val result = firstUserMessage?.takeIf { it.isNotBlank() }
            ?: title?.takeIf { it.isNotBlank() }
            ?: "新对话"
        android.util.Log.d("SessionInfo", "displayTitle: firstUserMessage=$firstUserMessage, title=$title, result=$result")
        return result
    }
}

// ==================== 会话详情 ====================

@Serializable
data class SessionDetailResponse(
    val code: Int,
    val message: String,
    val data: SessionDetailData
)

@Serializable
data class SessionDetailData(
    @SerialName("session_id")
    val sessionId: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("scenic_id")
    val scenicId: String? = null,
    @SerialName("spot_id")
    val spotId: String? = null,
    @SerialName("device_id")
    val deviceId: String? = null,
    val title: String? = null,
    val status: String,
    @SerialName("message_count")
    val messageCount: Int = 0,
    @SerialName("context_summary")
    val contextSummary: String? = null,
    @SerialName("last_message_at")
    val lastMessageAt: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    val messages: List<MessageInfo> = emptyList()
)

@Serializable
data class MessageInfo(
    @SerialName("message_id")
    val messageId: String,
    @SerialName("session_id")
    val sessionId: String? = null,
    val role: String,
    val content: String,
    @SerialName("avatar_action")
    val avatarAction: AvatarAction? = null,
    @SerialName("sources")
    val sources: List<SourceInfo>? = null,
    @SerialName("route_data")
    val routeData: RouteData? = null,
    val emotion: String? = null,
    val intent: String? = null,
    @SerialName("latency_ms")
    val latencyMs: Int? = null,
    @SerialName("created_at")
    val createdAt: String? = null
)

// ==================== 会话操作 ====================

@Serializable
data class ArchiveSessionRequest(
    @SerialName("user_id")
    val userId: String
)

@Serializable
data class ArchiveSessionResponse(
    val code: Int,
    val message: String,
    val data: ArchiveSessionData? = null
)

@Serializable
data class ArchiveSessionData(
    @SerialName("session_id")
    val sessionId: String,
    val status: String
)

@Serializable
data class DeleteSessionResponse(
    val code: Int,
    val message: String,
    val data: DeleteSessionData? = null
)

@Serializable
data class DeleteSessionData(
    @SerialName("session_id")
    val sessionId: String,
    val deleted: Boolean
)

// ==================== 会话标题修改 ====================

@Serializable
data class PatchSessionRequest(
    val title: String
)

// ==================== 路线推荐 ====================

@Serializable
data class RouteRecommendRequest(
    @SerialName("scenic_id")
    val scenicId: String,
    @SerialName("duration_min")
    val durationMin: Int? = null,
    @SerialName("interest_tags")
    val interestTags: List<String>? = null,
    @SerialName("current_spot")
    val currentSpot: String? = null,
    val question: String? = null
)

@Serializable
data class RouteRecommendResponse(
    val code: Int,
    val message: String,
    val data: RouteData? = null
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
    val routeData: RouteData? = null,
    /**
     * 待发送的本地图片 URI（发送前预览用）
     */
    val pendingImageUri: String? = null,
    /**
     * 服务器返回的图片 URL
     */
    val imageUrl: String? = null,
    /**
     * 后端消息 ID（用于满意度反馈关联）
     */
    val backendMessageId: String? = null,
    /**
     * 是否已提交满意度反馈
     */
    val hasFeedback: Boolean = false
)

// ==================== 满意度反馈 ====================

@Serializable
data class ChatFeedbackRequest(
    @SerialName("scenic_id")
    val scenicId: String,
    val rating: Int,
    @SerialName("session_id")
    val sessionId: String? = null,
    @SerialName("user_id")
    val userId: String? = null,
    @SerialName("message_id")
    val messageId: String? = null,
    @SerialName("is_complaint")
    val isComplaint: Boolean = false,
    val comment: String? = null
)

@Serializable
data class ChatFeedbackResponse(
    val code: Int,
    val message: String,
    val data: ChatFeedbackData? = null
)

@Serializable
data class ChatFeedbackData(
    @SerialName("feedback_id")
    val feedbackId: String,
    @SerialName("scenic_id")
    val scenicId: String,
    @SerialName("session_id")
    val sessionId: String? = null,
    @SerialName("user_id")
    val userId: String? = null,
    @SerialName("message_id")
    val messageId: String? = null,
    val rating: Int,
    @SerialName("is_complaint")
    val isComplaint: Boolean = false,
    val comment: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null
)
