package com.example.scenic_avatar_guide_app.data.repository

import android.util.Log
import android.content.Context
import android.net.Uri
import com.example.scenic_avatar_guide_app.core.common.NetworkResult
import com.example.scenic_avatar_guide_app.data.local.SettingsDataStore
import com.example.scenic_avatar_guide_app.data.remote.ApiService
import com.example.scenic_avatar_guide_app.data.remote.StreamingChatClient
import com.example.scenic_avatar_guide_app.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GuideRepository @Inject constructor(
    private val apiService: ApiService,
    private val streamingChatClient: StreamingChatClient,
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

            val scenicId = settingsDataStore.scenicId.first() ?: "lingshan"
            val spotId = settingsDataStore.spotId.first()

            val request = SessionCreateRequest(
                userId = userId,
                scenicId = scenicId,
                spotId = spotId,
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
        mode: String = "chat",
        imageUrl: String? = null
    ): Result<ChatResponseData> {
        return try {
            val userId = settingsDataStore.userId.first()
                ?: return Result.failure(Exception("用户 ID 不存在"))

            val scenicId = settingsDataStore.scenicId.first() ?: "lingshan"
            val spotId = settingsDataStore.spotId.first()
            val voiceId = settingsDataStore.voiceId.first()

            val request = ChatTextRequest(
                sessionId = sessionId,
                userId = userId,
                scenicId = scenicId,
                question = message,
                spotId = spotId,
                mode = mode,
                imageUrl = imageUrl,
                options = ChatOptions(
                    needAvatar = true,
                    needSources = true,
                    voice = voiceId
                )
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
     * 发送文本消息（流式）
     */
    suspend fun sendTextMessageStream(
        sessionId: String,
        message: String,
        mode: String = "chat",
        imageUrl: String? = null
    ): Flow<ChatStreamEvent> {
        Log.d("GuideRepository", "sendTextMessageStream: sessionId=$sessionId, message=$message")
        val userId = settingsDataStore.userId.first()
            ?: return flowOf(ChatStreamEvent.Error(message = "用户 ID 不存在"))

        val scenicId = settingsDataStore.scenicId.first() ?: "lingshan"
        val spotId = settingsDataStore.spotId.first()
        val voiceId = settingsDataStore.voiceId.first()

        Log.d("GuideRepository", "用户选择的发音人: $voiceId")

        val request = ChatTextRequest(
            sessionId = sessionId,
            userId = userId,
            scenicId = scenicId,
            question = message,
            spotId = spotId,
            mode = mode,
            imageUrl = imageUrl,
            options = ChatOptions(
                needAvatar = true,
                needSources = true,
                voice = voiceId
            )
        )

        Log.d("GuideRepository", "调用 streamingChatClient.streamChat, voice=$voiceId")
        return streamingChatClient.streamChat(request)
    }

    /**
     * 通知后端中止当前对话（best-effort）
     */
    suspend fun abortChat(sessionId: String, messageId: String): Result<Unit> {
        return try {
            val request = ChatAbortRequest(
                sessionId = sessionId,
                messageId = messageId
            )
            val response = apiService.abortChat(request)
            if (response.code == 0) {
                Result.success(Unit)
            } else {
                Log.w("GuideRepository", "abortChat returned code=${response.code}: ${response.message}")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.w("GuideRepository", "abortChat failed (best-effort, ignored)", e)
            Result.success(Unit)
        }
    }

    /**
     * 上传图片
     */
    suspend fun uploadImage(imageUri: Uri, context: Context): Result<String> {
        return try {
            val inputStream = context.contentResolver.openInputStream(imageUri)
                ?: return Result.failure(Exception("无法打开图片"))
            val bytes = inputStream.use { it.readBytes() }

            val requestBody = bytes.toRequestBody("image/*".toMediaTypeOrNull())
            val multipartBody = MultipartBody.Part.createFormData("image", "upload.jpg", requestBody)

            val response = apiService.uploadImage(multipartBody)
            if (response.code == 0) {
                Result.success(response.data.imageUrl)
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
     * TTS 文本合成
     */
    suspend fun synthesizeTTS(
        text: String,
        voice: String = "zh-CN-XiaoxiaoNeural",
        rate: String = "+0%",
        volume: String = "+0dB",
        pitch: String = "+0Hz",
        format: String = "audio_with_marks"
    ): Result<TtsSynthesizeData> {
        return try {
            val request = TtsSynthesizeRequest(
                text = text,
                voice = voice,
                rate = rate,
                volume = volume,
                pitch = pitch,
                format = format
            )
            val response = apiService.ttsSynthesize(request)
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
     * 获取 TTS 发音人列表
     */
    suspend fun getTtsVoices(): Result<List<TtsVoiceInfo>> {
        return try {
            val response = apiService.ttsVoices()
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
     * 拼接完整音频 URL
     */
    suspend fun buildAudioUrl(relativePath: String): String {
        val baseUrl = settingsDataStore.baseUrl.first() ?: SettingsDataStore.DEFAULT_BASE_URL
        return if (relativePath.startsWith("http")) {
            relativePath
        } else {
            val cleanBase = baseUrl.removeSuffix("/")
            val cleanPath = relativePath.removePrefix("/")
            "$cleanBase/$cleanPath"
        }
    }

    /**
     * 清除会话
     */
    suspend fun clearSession() {
        settingsDataStore.clearSession()
    }
}
