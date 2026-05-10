package com.example.scenic_avatar_guide_app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scenic_avatar_guide_app.core.common.UiState
import com.example.scenic_avatar_guide_app.data.repository.ScenicIntroRepository
import com.example.scenic_avatar_guide_app.domain.model.ScenicIndex
import com.example.scenic_avatar_guide_app.domain.model.ScenicIntroContent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScenicIntroViewModel @Inject constructor(
    private val repository: ScenicIntroRepository
) : ViewModel() {

    private val _introState = MutableStateFlow<UiState<ScenicIntroContent>>(UiState.Loading)
    val introState: StateFlow<UiState<ScenicIntroContent>> = _introState.asStateFlow()

    private val _indexState = MutableStateFlow<UiState<ScenicIndex>>(UiState.Loading)
    val indexState: StateFlow<UiState<ScenicIndex>> = _indexState.asStateFlow()

    private val _selectedScenicId = MutableStateFlow("xinhai_museum")
    val selectedScenicId: StateFlow<String> = _selectedScenicId.asStateFlow()

    init {
        loadIndex()
        loadScenicIntro("xinhai_museum")
    }

    fun loadScenicIntro(scenicId: String) {
        _selectedScenicId.value = scenicId
        if (scenicId == "xinhai_museum") {
            _introState.value = UiState.Success(
                ScenicIntroContent(
                    scenicId = "xinhai_museum",
                    scenicName = "辛亥革命博物馆",
                    subtitle = "首义之区，共和之门"
                )
            )
            return
        }
        _introState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val content = repository.loadScenicIntro(scenicId)
                _introState.value = UiState.Success(content)
            } catch (e: Exception) {
                _introState.value = UiState.Error(e.message ?: "加载景区介绍失败")
            }
        }
    }

    fun loadIndex() {
        viewModelScope.launch {
            try {
                val index = repository.loadScenicIndex()
                _indexState.value = UiState.Success(index)
            } catch (e: Exception) {
                _indexState.value = UiState.Error(e.message ?: "加载景区列表失败")
            }
        }
    }

    fun selectScenic(scenicId: String) {
        loadScenicIntro(scenicId)
    }
}
