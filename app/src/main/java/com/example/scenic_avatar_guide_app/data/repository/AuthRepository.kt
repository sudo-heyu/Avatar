package com.example.scenic_avatar_guide_app.data.repository

import com.example.scenic_avatar_guide_app.data.local.SettingsDataStore
import com.example.scenic_avatar_guide_app.data.remote.ApiService
import com.example.scenic_avatar_guide_app.domain.model.AuthLoginRequest
import com.example.scenic_avatar_guide_app.domain.model.AuthRegisterRequest
import com.example.scenic_avatar_guide_app.domain.model.AuthUserData
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val apiService: ApiService,
    private val settingsDataStore: SettingsDataStore
) {

    suspend fun register(username: String, password: String): Result<AuthUserData> {
        return try {
            val deviceId = settingsDataStore.deviceId.first() ?: UUID.randomUUID().toString().also {
                settingsDataStore.setDeviceId(it)
            }
            val request = AuthRegisterRequest(
                username = username,
                password = password,
                deviceId = deviceId
            )
            val response = apiService.authRegister(request)
            if (response.code == 0 && response.data != null) {
                saveAuthData(response.data)
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(username: String, password: String): Result<AuthUserData> {
        return try {
            val deviceId = settingsDataStore.deviceId.first() ?: UUID.randomUUID().toString().also {
                settingsDataStore.setDeviceId(it)
            }
            val request = AuthLoginRequest(
                username = username,
                password = password,
                deviceId = deviceId
            )
            val response = apiService.authLogin(request)
            if (response.code == 0 && response.data != null) {
                saveAuthData(response.data)
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout() {
        settingsDataStore.clearAuth()
        settingsDataStore.clearSession()
        val newGuestId = "guest_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        settingsDataStore.setUserId(newGuestId)
    }

    private suspend fun saveAuthData(data: AuthUserData) {
        settingsDataStore.setAuthUserId(data.userId)
        settingsDataStore.setAuthUsername(data.username)
        settingsDataStore.setIsAuthenticated(true)
        // 同步更新通用 userId，保证旧代码兼容
        settingsDataStore.setUserId(data.userId)
    }
}
