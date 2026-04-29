package com.example.scenic_avatar_guide_app.data.remote

import com.example.scenic_avatar_guide_app.domain.model.*
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ApiService {

    /**
     * 健康检查
     */
    @GET("api/v1/health")
    suspend fun healthCheck(): HealthResponse

    /**
     * 创建会话
     */
    @POST("api/v1/session/create")
    suspend fun createSession(@Body request: SessionCreateRequest): SessionCreateResponse

    /**
     * 统一交互接口（聊天问答 / 路线规划）
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
}
