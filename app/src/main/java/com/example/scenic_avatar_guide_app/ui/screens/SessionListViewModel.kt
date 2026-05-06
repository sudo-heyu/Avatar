package com.example.scenic_avatar_guide_app.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scenic_avatar_guide_app.data.repository.SessionRepository
import com.example.scenic_avatar_guide_app.domain.model.ChatMessage
import com.example.scenic_avatar_guide_app.domain.model.SessionInfo
import com.example.scenic_avatar_guide_app.data.repository.toChatMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SessionListViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val _sessions = MutableStateFlow<List<SessionInfo>>(emptyList())
    val sessions: StateFlow<List<SessionInfo>> = _sessions.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _restoredMessages = MutableStateFlow<List<ChatMessage>?>(null)
    val restoredMessages: StateFlow<List<ChatMessage>?> = _restoredMessages.asStateFlow()

    /**
     * 缓存会话标题（key=sessionId, value=firstUserMessage）。
     * 用于避免后端 firstUserMessage 延迟填充导致的"新对话"闪烁。
     */
    private val sessionTitleCache = mutableMapOf<String, String>()

    init {
        loadSessions()
    }

    fun loadSessions() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            sessionRepository.getSessionList().fold(
                onSuccess = { list ->
                    // 用缓存补充后端返回为空的 firstUserMessage，避免闪烁
                    val enrichedList = list.map { session ->
                        if (session.firstUserMessage.isNullOrBlank()) {
                            sessionTitleCache[session.sessionId]?.let { cachedTitle ->
                                session.copy(firstUserMessage = cachedTitle)
                            } ?: session
                        } else {
                            session
                        }
                    }
                    // 更新缓存：只缓存非空标题
                    enrichedList.forEach { session ->
                        session.firstUserMessage?.takeIf { it.isNotBlank() }?.let {
                            sessionTitleCache[session.sessionId] = it
                        }
                    }
                    _sessions.value = enrichedList.sortedByDescending { it.sortTimestamp() }
                },
                onFailure = { e ->
                    Log.e("SessionListVM", "loadSessions failed", e)
                    _errorMessage.value = e.message ?: "加载会话列表失败"
                }
            )
            _isLoading.value = false
        }
    }

    fun restoreSession(sessionId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            sessionRepository.getSessionDetail(sessionId).fold(
                onSuccess = { detail ->
                    _restoredMessages.value = detail.messages.map { it.toChatMessage() }
                },
                onFailure = { e ->
                    Log.e("SessionListVM", "restoreSession failed", e)
                    _errorMessage.value = e.message ?: "加载会话失败"
                }
            )
            _isLoading.value = false
        }
    }

    fun archiveSession(sessionId: String) {
        viewModelScope.launch {
            sessionRepository.archiveSession(sessionId).fold(
                onSuccess = {
                    _sessions.value = _sessions.value.filter { it.sessionId != sessionId }
                    sessionTitleCache.remove(sessionId)
                },
                onFailure = { e ->
                    Log.e("SessionListVM", "archiveSession failed", e)
                    _errorMessage.value = e.message ?: "归档失败"
                }
            )
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            sessionRepository.deleteSession(sessionId).fold(
                onSuccess = {
                    _sessions.value = _sessions.value.filter { it.sessionId != sessionId }
                    sessionTitleCache.remove(sessionId)
                },
                onFailure = { e ->
                    Log.e("SessionListVM", "deleteSession failed", e)
                    _errorMessage.value = e.message ?: "删除失败"
                }
            )
        }
    }

    fun clearRestoredMessages() {
        _restoredMessages.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }
}

private val sessionTimeFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())

/**
 * 获取会话用于排序的时间戳。
 * 优先使用最后一条消息时间，其次使用创建时间。
 */
private fun SessionInfo.sortTimestamp(): Long {
    val timeStr = lastMessageAt ?: createdAt ?: return 0L
    return try {
        sessionTimeFormatter.parse(timeStr)?.time ?: 0L
    } catch (_: Exception) {
        0L
    }
}
