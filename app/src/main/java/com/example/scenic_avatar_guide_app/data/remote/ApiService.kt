package com.example.scenic_avatar_guide_app.data.remote

import com.example.scenic_avatar_guide_app.domain.model.*
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

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
     * 文本问答
     */
    @POST("api/v1/chat/text")
    suspend fun chatText(@Body request: ChatTextRequest): ChatTextResponse
}
