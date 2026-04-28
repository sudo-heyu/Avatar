package com.example.scenic_avatar_guide_app.core.tts

import com.example.scenic_avatar_guide_app.domain.model.VisemeType

/**
 * 音素事件
 * 用于驱动数字人口型同步
 */
data class PhonemeEvent(
    val phoneme: String,
    val startMs: Long,
    val endMs: Long,
    val viseme: VisemeType,
    val charIndex: Int
)

/**
 * TTS 播放状态
 */
sealed class TTSPlayState {
    object Idle : TTSPlayState()
    data class Speaking(val text: String, val progress: Float) : TTSPlayState()
    data class Completed(val text: String) : TTSPlayState()
    data class Error(val text: String, val message: String) : TTSPlayState()
}

/**
 * 发音人信息
 */
data class VoiceInfo(
    val id: String,
    val displayName: String,
    val description: String,
    val gender: Gender,
    val style: VoiceStyle
)

enum class Gender {
    MALE, FEMALE
}

enum class VoiceStyle {
    FRIENDLY, GENTLE, INTELLECTUAL, PROFESSIONAL, CHILD, LITERARY, NEWS
}
