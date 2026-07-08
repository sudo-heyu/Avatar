package com.example.scenic_avatar_guide_app.data.repository

import android.util.Log
import com.example.scenic_avatar_guide_app.data.local.SettingsDataStore
import com.example.scenic_avatar_guide_app.data.remote.ApiService
import com.example.scenic_avatar_guide_app.domain.model.AvatarCostumeDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/** 数字人形象仓库：拉取后端形象列表 + 下载后端贴图 PNG。 */
@Singleton
class AvatarCostumeRepository @Inject constructor(
    private val apiService: ApiService,
    private val okHttpClient: OkHttpClient,
    private val settingsDataStore: SettingsDataStore
) {
    companion object {
        private const val TAG = "AvatarCostumeRepo"
    }

    /** 拉取后端形象列表。请求失败返回空列表，默认形象由 Manager 本地兜底。 */
    suspend fun listCostumes(): List<AvatarCostumeDto> = withContext(Dispatchers.IO) {
        try {
            val resp = apiService.listAvatarCostumes()
            if (resp.code != 0) {
                Log.w(TAG, "listAvatarCostumes code=${resp.code} msg=${resp.message}")
                return@withContext emptyList()
            }
            resp.data.items.filter { it.isValid }
        } catch (e: Exception) {
            Log.e(TAG, "listCostumes failed", e)
            emptyList()
        }
    }

    /**
     * 下载贴图文件。relativeUrl 为后端返回的 /static/avatar/... 相对路径，
     * 用 DataStore 中的 base URL 拼成绝对地址。失败返回 null。
     */
    suspend fun downloadTexture(relativeUrl: String): ByteArray? = withContext(Dispatchers.IO) {
        if (relativeUrl.isBlank()) return@withContext null
        try {
            val base = settingsDataStore.baseUrl.first().toHttpUrlOrNull()
            val full = base?.resolve(relativeUrl)
            if (full == null) {
                Log.w(TAG, "cannot resolve texture url: $relativeUrl")
                return@withContext null
            }
            val request = Request.Builder().url(full).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "downloadTexture ${response.code} for $full")
                    return@use null
                }
                response.body?.bytes()
            }
        } catch (e: Exception) {
            Log.e(TAG, "downloadTexture failed: $relativeUrl", e)
            null
        }
    }
}
