package com.example.scenic_avatar_guide_app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "guide_settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val BASE_URL_KEY = stringPreferencesKey("base_url")
        private val DEVICE_ID_KEY = stringPreferencesKey("device_id")
        private val SESSION_ID_KEY = stringPreferencesKey("session_id")
        private val USER_ID_KEY = stringPreferencesKey("user_id")
        private val VOICE_ID_KEY = stringPreferencesKey("voice_id")
        private val SCENIC_ID_KEY = stringPreferencesKey("scenic_id")
        private val SPOT_ID_KEY = stringPreferencesKey("spot_id")
        private val RATE_KEY = stringPreferencesKey("rate")
        private val VOLUME_KEY = stringPreferencesKey("volume")
        private val PITCH_KEY = stringPreferencesKey("pitch")
        private val AUTH_USER_ID_KEY = stringPreferencesKey("auth_user_id")
        private val AUTH_USERNAME_KEY = stringPreferencesKey("auth_username")
        private val IS_AUTHENTICATED_KEY = booleanPreferencesKey("is_authenticated")

        const val DEFAULT_BASE_URL = "http://10.0.2.2:8000/"
        const val DEFAULT_VOICE_ID = "zh-CN-XiaoyiNeural"
        const val DEFAULT_RATE = "+0%"
        const val DEFAULT_VOLUME = "+0%"
        const val DEFAULT_PITCH = "+0Hz"
    }

    val baseUrl: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[BASE_URL_KEY] ?: DEFAULT_BASE_URL
    }

    val deviceId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[DEVICE_ID_KEY]
    }

    val sessionId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[SESSION_ID_KEY]
    }

    val userId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[USER_ID_KEY]
    }

    val voiceId: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[VOICE_ID_KEY] ?: DEFAULT_VOICE_ID
    }

    val scenicId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[SCENIC_ID_KEY]
    }

    val spotId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[SPOT_ID_KEY]
    }

    val rate: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[RATE_KEY] ?: DEFAULT_RATE
    }

    val volume: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[VOLUME_KEY] ?: DEFAULT_VOLUME
    }

    val pitch: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PITCH_KEY] ?: DEFAULT_PITCH
    }

    val authUserId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[AUTH_USER_ID_KEY]
    }

    val authUsername: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[AUTH_USERNAME_KEY]
    }

    val isAuthenticated: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_AUTHENTICATED_KEY] ?: false
    }

    suspend fun setBaseUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[BASE_URL_KEY] = normalizeBaseUrl(url)
        }
    }

    suspend fun setDeviceId(id: String) {
        context.dataStore.edit { preferences ->
            preferences[DEVICE_ID_KEY] = id
        }
    }

    suspend fun setSessionId(id: String) {
        context.dataStore.edit { preferences ->
            preferences[SESSION_ID_KEY] = id
        }
    }

    suspend fun setUserId(id: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_ID_KEY] = id
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { preferences ->
            preferences.remove(SESSION_ID_KEY)
        }
    }

    suspend fun setVoiceId(id: String) {
        context.dataStore.edit { preferences ->
            preferences[VOICE_ID_KEY] = id
        }
    }

    suspend fun setScenicId(id: String) {
        context.dataStore.edit { preferences ->
            preferences[SCENIC_ID_KEY] = id
        }
    }

    suspend fun setSpotId(id: String) {
        context.dataStore.edit { preferences ->
            preferences[SPOT_ID_KEY] = id
        }
    }

    suspend fun setRate(rate: String) {
        context.dataStore.edit { preferences ->
            preferences[RATE_KEY] = rate
        }
    }

    suspend fun setVolume(volume: String) {
        context.dataStore.edit { preferences ->
            preferences[VOLUME_KEY] = volume
        }
    }

    suspend fun setPitch(pitch: String) {
        context.dataStore.edit { preferences ->
            preferences[PITCH_KEY] = pitch
        }
    }

    suspend fun clearScenicSpot() {
        context.dataStore.edit { preferences ->
            preferences.remove(SCENIC_ID_KEY)
            preferences.remove(SPOT_ID_KEY)
        }
    }

    suspend fun setAuthUserId(id: String) {
        context.dataStore.edit { preferences ->
            preferences[AUTH_USER_ID_KEY] = id
        }
    }

    suspend fun setAuthUsername(username: String) {
        context.dataStore.edit { preferences ->
            preferences[AUTH_USERNAME_KEY] = username
        }
    }

    suspend fun setIsAuthenticated(value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_AUTHENTICATED_KEY] = value
        }
    }

    suspend fun clearAuth() {
        context.dataStore.edit { preferences ->
            preferences.remove(AUTH_USER_ID_KEY)
            preferences.remove(AUTH_USERNAME_KEY)
            preferences.remove(IS_AUTHENTICATED_KEY)
        }
    }

    private fun normalizeBaseUrl(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }
}
