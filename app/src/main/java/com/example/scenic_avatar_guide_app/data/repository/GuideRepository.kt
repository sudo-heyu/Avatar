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
    private companion object {
        const val MAX_UPLOAD_IMAGE_BYTES = 5 * 1024 * 1024
    }

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
     * 获取后端公开景区与景点列表。
     * 后端 scenic_id 为 canonical ID，前端优先使用这里的数据，本地 assets 仅作为离线兜底。
     */
    suspend fun getPublicScenicAreas(): Result<List<ScenicArea>> {
        return try {
            val scenicItems = apiService.listPublicScenics().items
            val areas = scenicItems.map { scenic ->
                val spots = runCatching {
                    apiService.listPublicScenicSpots(scenic.scenicId).items
                        .sortedBy { it.sortOrder }
                        .map { spot ->
                            ScenicSpot(
                                id = spot.spotId,
                                name = spot.name,
                                description = spot.description,
                                sortOrder = spot.sortOrder
                            )
                        }
                }.getOrDefault(emptyList())

                ScenicArea(
                    id = scenic.scenicId,
                    name = scenic.name,
                    description = scenic.description,
                    spots = spots
                )
            }
            Result.success(areas)
        } catch (e: Exception) {
            Result.failure(e)
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
                userId = "guest_${UUID.randomUUID().toString().replace("-", "").take(16)}"
                settingsDataStore.setUserId(userId)
            }

            settingsDataStore.setDeviceId(deviceId)

            val scenicId = settingsDataStore.scenicId.first() ?: "1911museum"
            val spotId = settingsDataStore.spotId.first()

            Log.d("GuideRepository", "createSession: userId=$userId, scenicId=$scenicId, spotId=$spotId, deviceId=$deviceId")

            val request = SessionCreateRequest(
                userId = userId,
                scenicId = scenicId,
                spotId = spotId,
                deviceId = deviceId
            )

            val response = apiService.createSession(request)
            Log.d("GuideRepository", "createSession response: code=${response.code}, message=${response.message}")
            if (response.code == 0) {
                // 保存 session_id
                settingsDataStore.setSessionId(response.data.sessionId)
                Result.success(response.data)
            } else {
                Result.failure(Exception("后端返回错误: ${response.message} (code=${response.code})"))
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

        val scenicId = settingsDataStore.scenicId.first() ?: "1911museum"
        val spotId = settingsDataStore.spotId.first()
        val voiceId = settingsDataStore.voiceId.first()
        val rate = settingsDataStore.rate.first()
        val volume = settingsDataStore.volume.first()
        val pitch = settingsDataStore.pitch.first()

        Log.d("GuideRepository", "spotId=$spotId, scenicId=$scenicId, userId=$userId")
        Log.d("GuideRepository", "用户选择的发音人: $voiceId, rate=$rate, volume=$volume, pitch=$pitch")

        val request = ChatTextRequest(
            sessionId = sessionId,
            userId = userId,
            scenicId = scenicId,
            question = message,
            spotId = spotId,
            mode = mode,
            imageUrl = imageUrl,
            options = ChatOptions(
                voice = voiceId,
                rate = rate,
                volume = volume,
                pitch = pitch
            )
        )

        Log.d("GuideRepository", "调用 streamingChatClient.streamChat, voice=$voiceId")
        return streamingChatClient.streamChat(request)
    }

    /**
     * 发送文本消息（非流式）
     */
    suspend fun sendTextMessage(
        sessionId: String,
        message: String,
        mode: String = "chat",
        imageUrl: String? = null
    ): Result<ChatResponseData> {
        return try {
            val userId = settingsDataStore.userId.first()
                ?: return Result.failure(IllegalStateException("用户 ID 不存在"))

            val scenicId = settingsDataStore.scenicId.first() ?: "1911museum"
            val spotId = settingsDataStore.spotId.first()
            val voiceId = settingsDataStore.voiceId.first()
            val rate = settingsDataStore.rate.first()
            val volume = settingsDataStore.volume.first()
            val pitch = settingsDataStore.pitch.first()

            val request = ChatTextRequest(
                sessionId = sessionId,
                userId = userId,
                scenicId = scenicId,
                question = message,
                spotId = spotId,
                mode = mode,
                imageUrl = imageUrl,
                options = ChatOptions(
                    voice = voiceId,
                    rate = rate,
                    volume = volume,
                    pitch = pitch
                )
            )

            val response = apiService.chatText(request)
            if (response.code == 0) {
                Result.success(
                    response.data.copy(
                        images = response.data.images.resolveImageUrls(settingsDataStore.baseUrl.first())
                    )
                )
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 通知后端中止当前对话（best-effort）
     */
    suspend fun abortChat(sessionId: String, messageId: String): Result<Unit> {
        return try {
            val request = ChatAbortRequest(
                sessionId = sessionId,
                messageId = messageId,
                reason = "client_abort"
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
            val mimeType = context.contentResolver.getType(imageUri)
                ?: return Result.failure(Exception("无法识别图片类型"))
            if (!mimeType.startsWith("image/")) {
                return Result.failure(Exception("请选择图片文件"))
            }

            val inputStream = context.contentResolver.openInputStream(imageUri)
                ?: return Result.failure(Exception("无法打开图片"))
            val bytes = inputStream.use { it.readBytes() }
            if (bytes.size > MAX_UPLOAD_IMAGE_BYTES) {
                return Result.failure(Exception("图片不能超过 5MB"))
            }

            val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
            val fileName = "upload.${mimeType.substringAfter('/', "jpg").substringBefore('+')}"
            val multipartBody = MultipartBody.Part.createFormData("image", fileName, requestBody)

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
        voice: String = "zh-CN-XiaoxiaoNeural"
    ): Result<TtsSynthesizeData> {
        return try {
            val request = TtsSynthesizeRequest(
                text = text,
                voice = voice
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
        return buildMediaUrl(relativePath)
    }

    /**
     * 拼接后端返回的静态资源 URL（音频、图片等）
     */
    suspend fun buildMediaUrl(relativePath: String): String {
        if (relativePath.startsWith("http")) return relativePath
        val baseUrl = settingsDataStore.baseUrl.first()
        return "${baseUrl.removeSuffix("/")}/${relativePath.removePrefix("/")}"
    }

    /**
     * 清除会话
     */
    suspend fun clearSession() {
        settingsDataStore.clearSession()
    }

    /**
     * 提交满意度反馈
     */
    suspend fun submitFeedback(
        rating: Int,
        messageId: String? = null,
        isComplaint: Boolean = false,
        comment: String? = null
    ): Result<ChatFeedbackData> {
        return try {
            val userId = settingsDataStore.userId.first()
            val scenicId = settingsDataStore.scenicId.first() ?: "1911museum"
            val sessionId = settingsDataStore.sessionId.first()

            val request = ChatFeedbackRequest(
                scenicId = scenicId,
                rating = rating,
                sessionId = sessionId,
                userId = userId,
                messageId = messageId,
                isComplaint = isComplaint,
                comment = comment
            )

            val response = apiService.submitFeedback(request)
            if (response.code == 0 && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

private fun List<ChatImageInfo>.resolveImageUrls(baseUrl: String): List<ChatImageInfo> {
    return map { image ->
        val path = image.url?.takeIf { it.isNotBlank() }
            ?: image.publicPath?.takeIf { it.isNotBlank() }
        if (path == null || path.startsWith("http")) {
            image
        } else {
            image.copy(url = "${baseUrl.removeSuffix("/")}/${path.removePrefix("/")}")
        }
    }
}
