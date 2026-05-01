package com.example.scenic_avatar_guide_app.data.repository

import android.util.Log
import com.example.scenic_avatar_guide_app.data.local.SettingsDataStore
import com.example.scenic_avatar_guide_app.data.remote.ApiService
import com.example.scenic_avatar_guide_app.domain.model.ChatMessage
import com.example.scenic_avatar_guide_app.domain.model.MessageInfo
import com.example.scenic_avatar_guide_app.domain.model.SessionDetailData
import com.example.scenic_avatar_guide_app.domain.model.SessionInfo
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val apiService: ApiService,
    private val settingsDataStore: SettingsDataStore
) {
    companion object {
        private const val TAG = "SessionRepository"
    }

    /**
     * 获取会话列表
     */
    suspend fun getSessionList(): Result<List<SessionInfo>> {
        return try {
            val userId = requireUserId()
            val response = apiService.getSessionList(
                userId = userId,
                status = "active",
                page = 1,
                pageSize = 20
            )
            if (response.code == 0) {
                Result.success(response.data.sessions)
            } else {
                Log.w(TAG, "getSessionList failed: ${response.message}")
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getSessionList error", e)
            Result.failure(e)
        }
    }

    /**
     * 获取会话详情（含历史消息）
     */
    suspend fun getSessionDetail(sessionId: String): Result<SessionDetailData> {
        return try {
            val userId = requireUserId()
            val response = apiService.getSessionDetail(
                sessionId = sessionId,
                userId = userId,
                includeMessages = true,
                messageLimit = 50
            )
            if (response.code == 0) {
                Result.success(response.data)
            } else {
                Log.w(TAG, "getSessionDetail failed: ${response.message}")
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getSessionDetail error", e)
            Result.failure(e)
        }
    }

    /**
     * 归档会话
     */
    suspend fun archiveSession(sessionId: String): Result<Unit> {
        return try {
            val userId = requireUserId()
            val request = com.example.scenic_avatar_guide_app.domain.model.ArchiveSessionRequest(userId = userId)
            val response = apiService.archiveSession(sessionId, request)
            if (response.code == 0) {
                Result.success(Unit)
            } else {
                Log.w(TAG, "archiveSession failed: ${response.message}")
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Log.e(TAG, "archiveSession error", e)
            Result.failure(e)
        }
    }

    /**
     * 删除会话
     */
    suspend fun deleteSession(sessionId: String): Result<Unit> {
        return try {
            val userId = requireUserId()
            val response = apiService.deleteSession(sessionId, userId)
            if (response.code == 0) {
                Result.success(Unit)
            } else {
                Log.w(TAG, "deleteSession failed: ${response.message}")
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Log.e(TAG, "deleteSession error", e)
            Result.failure(e)
        }
    }

    private suspend fun requireUserId(): String {
        return settingsDataStore.userId.first()
            ?: throw IllegalStateException("userId not initialized")
    }
}

/**
 * 将后端返回的消息转换为 UI 用 ChatMessage
 */
fun MessageInfo.toChatMessage(): ChatMessage {
    val timeFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    val timestamp = try {
        createdAt?.let { timeFormatter.parse(it)?.time } ?: System.currentTimeMillis()
    } catch (_: Exception) {
        System.currentTimeMillis()
    }

    return ChatMessage(
        id = messageId,
        content = content,
        isUser = role == "user",
        timestamp = timestamp,
        isLoading = false,
        isError = false,
        sources = sources ?: emptyList(),
        avatarAction = avatarAction,
        routeData = routeData
    )
}
