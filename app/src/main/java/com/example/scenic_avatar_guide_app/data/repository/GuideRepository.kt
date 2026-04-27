package com.example.scenic_avatar_guide_app.data.repository

import com.example.scenic_avatar_guide_app.core.common.NetworkResult
import com.example.scenic_avatar_guide_app.data.local.SettingsDataStore
import com.example.scenic_avatar_guide_app.data.remote.ApiService
import com.example.scenic_avatar_guide_app.domain.model.*
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GuideRepository @Inject constructor(
    private val apiService: ApiService,
    private val settingsDataStore: SettingsDataStore
) {
    /**
     * 健康检查
     */
    suspend fun healthCheck(): NetworkResult<HealthData> {
        return try {
            val response = apiService.healthCheck()
            if (response.code == 0) {
                NetworkResult.Success(response.data)
            } else {
                NetworkResult.Error(response.code, response.message)
            }
        } catch (e: Exception) {
            NetworkResult.Exception(e)
        }
    }

    /**
     * 创建会话
     */
    suspend fun createSession(deviceId: String): Result<SessionData> {
        return try {
            // 获取或生成用户 ID
            var userId = settingsDataStore.userId.first()
            if (userId == null) {
                userId = "u_${UUID.randomUUID().toString().take(8)}"
                settingsDataStore.setUserId(userId)
            }

            settingsDataStore.setDeviceId(deviceId)

            val request = SessionCreateRequest(
                userId = userId,
                scenicId = "scenic_001",
                spotId = null,
                deviceId = deviceId
            )

            val response = apiService.createSession(request)
            if (response.code == 0) {
                // 保存 session_id
                settingsDataStore.setSessionId(response.data.sessionId)
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 发送文本消息
     */
    suspend fun sendTextMessage(
        sessionId: String,
        message: String,
        mode: String = "chat"
    ): Result<ChatResponseData> {
        return try {
            val userId = settingsDataStore.userId.first()
                ?: return Result.failure(Exception("用户 ID 不存在"))

            val request = ChatTextRequest(
                sessionId = sessionId,
                userId = userId,
                question = message,
                spotId = null,
                needAudio = false,
                needAvatar = false
            )

            val response = apiService.chatText(request)
            if (response.code == 0) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 获取当前会话 ID
     */
    suspend fun getCurrentSessionId(): String? {
        return settingsDataStore.sessionId.first()
    }

    /**
     * 清除会话
     */
    suspend fun clearSession() {
        settingsDataStore.clearSession()
    }
}
