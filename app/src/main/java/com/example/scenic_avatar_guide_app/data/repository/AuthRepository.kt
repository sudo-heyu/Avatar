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
            if (response.code == 0 && response.data != null && response.data.userId.isNotEmpty()) {
                saveAuthData(response.data)
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(Exception(resolveFriendlyError(e)))
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
            if (response.code == 0 && response.data != null && response.data.userId.isNotEmpty()) {
                saveAuthData(response.data)
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(Exception(resolveFriendlyError(e)))
        }
    }

    suspend fun logout() {
        settingsDataStore.clearAuth()
        settingsDataStore.clearSession()
        val newGuestId = "guest_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        settingsDataStore.setUserId(newGuestId)
    }

    private fun resolveFriendlyError(e: Throwable): String {
        return when (e) {
            is java.net.UnknownHostException -> "服务器地址无法访问，请检查网络连接或服务器配置"
            is java.net.ConnectException -> "无法连接服务器，请检查服务器地址是否正确"
            is java.net.SocketTimeoutException -> "连接超时，请检查网络状况或稍后重试"
            is java.io.IOException -> "网络连接异常，请检查网络后重试"
            is javax.net.ssl.SSLException -> "安全连接失败，请检查网络环境或服务器证书"
            is retrofit2.HttpException -> {
                when (e.code()) {
                    400 -> "请求参数错误"
                    401 -> "登录已过期，请重新登录"
                    403 -> "访问被拒绝"
                    404 -> "请求的服务不存在，请检查服务器地址"
                    429 -> "请求过于频繁，请稍后再试"
                    500, 502, 503, 504 -> "服务器暂时不可用，请稍后重试"
                    else -> "服务器响应异常 (${e.code()})，请稍后重试"
                }
            }
            else -> e.message ?: "网络异常，请检查网络后重试"
        }
    }

    private suspend fun saveAuthData(data: AuthUserData) {
        settingsDataStore.setAuthUserId(data.userId)
        settingsDataStore.setAuthUsername(data.username)
        settingsDataStore.setIsAuthenticated(true)
        // 同步更新通用 userId，保证旧代码兼容
        settingsDataStore.setUserId(data.userId)
    }
}
