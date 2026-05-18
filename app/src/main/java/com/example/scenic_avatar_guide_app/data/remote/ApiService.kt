package com.example.scenic_avatar_guide_app.data.remote

import com.example.scenic_avatar_guide_app.domain.model.*
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    /**
     * 健康检查
     */
    @GET("api/v1/health")
    suspend fun healthCheck(): HealthResponse

    /**
     * 获取公开景区列表
     */
    @GET("api/v1/scenics")
    suspend fun listPublicScenics(): PublicScenicListResponse

    /**
     * 获取公开景区下的景点列表
     */
    @GET("api/v1/scenics/{scenic_id}/spots")
    suspend fun listPublicScenicSpots(
        @Path("scenic_id") scenicId: String
    ): PublicScenicSpotListResponse

    /**
     * 创建会话
     */
    @POST("api/v1/session/create")
    suspend fun createSession(@Body request: SessionCreateRequest): SessionCreateResponse

    /**
     * 文本问答（非流式）
     */
    @POST("api/v1/chat/text")
    suspend fun chatText(@Body request: ChatTextRequest): ChatTextResponse

    /**
     * 中止当前对话
     */
    @POST("api/v1/chat/abort")
    suspend fun abortChat(@Body request: ChatAbortRequest): ChatAbortResponse

    /**
     * 图片上传（前置接口，用于图文问答）
     */
    @Multipart
    @POST("api/v1/upload/image")
    suspend fun uploadImage(@Part image: MultipartBody.Part): UploadImageResponse

    /**
     * TTS 文本合成
     */
    @POST("api/v1/tts/synthesize")
    suspend fun ttsSynthesize(@Body request: TtsSynthesizeRequest): TtsSynthesizeResponse

    /**
     * TTS 获取发音人列表
     */
    @GET("api/v1/tts/voices")
    suspend fun ttsVoices(): TtsVoicesResponse

    // ==================== 会话管理 ====================

    /**
     * 获取会话列表
     */
    @GET("api/v1/session/list")
    suspend fun getSessionList(
        @Query("user_id") userId: String,
        @Query("status") status: String = "active",
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 20
    ): SessionListResponse

    /**
     * 获取会话详情（含历史消息）
     */
    @GET("api/v1/session/{session_id}")
    suspend fun getSessionDetail(
        @Path("session_id") sessionId: String,
        @Query("user_id") userId: String,
        @Query("include_messages") includeMessages: Boolean = true,
        @Query("message_limit") messageLimit: Int = 50
    ): SessionDetailResponse

    /**
     * 归档会话
     */
    @POST("api/v1/session/{session_id}/archive")
    suspend fun archiveSession(
        @Path("session_id") sessionId: String,
        @Body request: ArchiveSessionRequest
    ): ArchiveSessionResponse

    /**
     * 删除会话
     */
    @DELETE("api/v1/session/{session_id}")
    suspend fun deleteSession(
        @Path("session_id") sessionId: String,
        @Query("user_id") userId: String
    ): DeleteSessionResponse

    /**
     * 修改会话标题
     */
    @PATCH("api/v1/session/{session_id}")
    suspend fun patchSession(
        @Path("session_id") sessionId: String,
        @Query("user_id") userId: String,
        @Body request: PatchSessionRequest
    ): SessionDetailResponse

    /**
     * 满意度反馈上报
     */
    @POST("api/v1/chat/feedback")
    suspend fun submitFeedback(@Body request: ChatFeedbackRequest): ChatFeedbackResponse

    // ==================== 路线推荐 ====================

    /**
     * 路线推荐（GET）
     */
    @GET("api/v1/route/recommend")
    suspend fun getRouteRecommend(
        @Query("scenic_id") scenicId: String,
        @Query("duration_min") durationMin: Int? = null,
        @Query("current_spot") currentSpot: String? = null,
        @Query("interest_tags") interestTags: String? = null
    ): RouteRecommendResponse

    /**
     * 路线推荐（POST）
     */
    @POST("api/v1/route/recommend")
    suspend fun postRouteRecommend(@Body request: RouteRecommendRequest): RouteRecommendResponse

    // ==================== 认证 ====================

    /**
     * 用户注册
     */
    @POST("api/v1/auth/register")
    suspend fun authRegister(@Body request: AuthRegisterRequest): AuthRegisterResponse

    /**
     * 用户登录
     */
    @POST("api/v1/auth/login")
    suspend fun authLogin(@Body request: AuthLoginRequest): AuthLoginResponse
}
