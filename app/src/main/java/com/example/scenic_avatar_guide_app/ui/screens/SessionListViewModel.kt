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

    init {
        loadSessions()
    }

    fun loadSessions() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            sessionRepository.getSessionList().fold(
                onSuccess = { list ->
                    _sessions.value = list
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
