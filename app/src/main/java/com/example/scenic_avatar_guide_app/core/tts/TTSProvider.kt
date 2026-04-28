package com.example.scenic_avatar_guide_app.core.tts

/**
 * TTS 统一接口
 * 供 AvatarPlaybackManager 调用，屏蔽底层实现差异
 */
interface TTSProvider {
    var onSpeakStart: (() -> Unit)?
    var onSpeakComplete: (() -> Unit)?
    var onPhonemeCallback: ((PhonemeEvent) -> Unit)?

    fun speak(text: String)
    fun stop()
    fun release()
    fun setVoice(voiceId: String)
    fun setSpeed(speed: Float)
    fun setPitch(pitch: Float)
}
