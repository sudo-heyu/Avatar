package com.example.scenic_avatar_guide_app.core.avatar

import android.content.Context
import android.util.Log
import com.example.scenic_avatar_guide_app.data.local.SettingsDataStore
import com.example.scenic_avatar_guide_app.data.repository.AvatarCostumeRepository
import com.example.scenic_avatar_guide_app.domain.model.AvatarCostumeDto
import com.example.scenic_avatar_guide_app.domain.model.AvatarCostumeTextureDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class AvatarCostumeOption(
    val id: String,
    val name: String,
    val directoryName: String? = null,
    val version: String? = null,
    val textures: List<AvatarCostumeTextureDto> = emptyList(),
    val previewUrl: String? = null,
    val isDefault: Boolean = false
)

sealed interface AvatarCostumeSelectionState {
    data object Loading : AvatarCostumeSelectionState
    data class Ready(
        val items: List<AvatarCostumeOption>,
        val selectedId: String
    ) : AvatarCostumeSelectionState
    data class Applying(val option: AvatarCostumeOption) : AvatarCostumeSelectionState
    data class Applied(val option: AvatarCostumeOption) : AvatarCostumeSelectionState
    data class Failed(val message: String) : AvatarCostumeSelectionState
}

@Singleton
class AvatarCostumeManager @Inject constructor(
    private val repository: AvatarCostumeRepository,
    private val settingsDataStore: SettingsDataStore,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AvatarCostumeManager"
        const val DEFAULT_AVATAR_ID = "default"
        private const val TEXTURE_DIR = "live2d/hiyori/hiyori_pro_t11.2048"
        private const val COSTUME_CACHE_DIR = "avatar_costumes"
        private const val GL_RELOAD_TIMEOUT_SEC = 5L
    }

    private val defaultOption = AvatarCostumeOption(
        id = DEFAULT_AVATAR_ID,
        name = "默认形象",
        isDefault = true
    )

    private val _state = MutableStateFlow<AvatarCostumeSelectionState>(
        AvatarCostumeSelectionState.Ready(items = listOf(defaultOption), selectedId = DEFAULT_AVATAR_ID)
    )
    val state: StateFlow<AvatarCostumeSelectionState> = _state.asStateFlow()

    @Volatile
    private var renderer: Live2DRendererImpl? = null
    private val applyMutex = Mutex()
    private var lastItems: List<AvatarCostumeOption> = listOf(defaultOption)

    fun attachRenderer(r: Live2DRendererImpl) {
        renderer = r
        Log.d(TAG, "renderer attached")
    }

    fun detachRenderer(r: Live2DRendererImpl) {
        if (renderer === r) renderer = null
    }

    suspend fun refresh() {
        _state.value = AvatarCostumeSelectionState.Loading
        val baseUrl = settingsDataStore.baseUrl.first()
        val remoteItems = repository.listCostumes().map { it.toOption(baseUrl) }
        lastItems = withDefaultOption(remoteItems)
        emitReady()
    }

    suspend fun applyCostume(optionId: String) {
        applyMutex.withLock {
            val option = findOption(optionId)
            _state.value = AvatarCostumeSelectionState.Applying(option)
            try {
                if (option.isDefault) {
                    applyDefault()
                } else {
                    applyRemote(option)
                }
                settingsDataStore.setSelectedAvatar(
                    id = option.id,
                    version = option.version,
                    directoryName = option.directoryName
                )
                _state.value = AvatarCostumeSelectionState.Applied(option)
            } catch (e: Exception) {
                Log.e(TAG, "applyCostume failed", e)
                _state.value = AvatarCostumeSelectionState.Failed(e.message ?: "形象切换失败")
            }
        }
    }

    suspend fun dismissTransientState() {
        emitReady()
    }

    private suspend fun emitReady() {
        val selectedId = settingsDataStore.selectedAvatarId.first()
        val validSelected = lastItems.any { it.id == selectedId }
        val finalSelectedId = if (validSelected) selectedId else DEFAULT_AVATAR_ID
        if (!validSelected) {
            settingsDataStore.clearSelectedAvatar()
        }
        _state.value = AvatarCostumeSelectionState.Ready(
            items = lastItems,
            selectedId = finalSelectedId
        )
    }

    private suspend fun findOption(optionId: String): AvatarCostumeOption {
        if (lastItems.none { it.id == optionId }) {
            val baseUrl = settingsDataStore.baseUrl.first()
            val remoteItems = repository.listCostumes().map { it.toOption(baseUrl) }
            lastItems = withDefaultOption(remoteItems)
        }
        return lastItems.firstOrNull { it.id == optionId }
            ?: error("形象不存在")
    }

    private fun applyDefault() {
        val targetDir = File(context.filesDir, TEXTURE_DIR)
        File(targetDir, "texture_00.png").delete()
        File(targetDir, "texture_01.png").delete()
        reloadTextures()
    }

    private suspend fun applyRemote(option: AvatarCostumeOption) {
        val directory = option.directoryName ?: error("形象目录缺失")
        val cacheDir = File(context.filesDir, "$COSTUME_CACHE_DIR/$directory")
        cacheDir.mkdirs()
        val targetDir = File(context.filesDir, TEXTURE_DIR)
        targetDir.mkdirs()

        for (slot in listOf(0, 1)) {
            val tex = option.textures.firstOrNull { it.slot == slot }
                ?: error("缺少 texture_${slot.toString().padStart(2, '0')}.png")
            val filename = tex.filename ?: error("贴图文件名缺失")
            val relUrl = tex.url ?: error("贴图地址缺失: $filename")
            val cachedFile = File(cacheDir, filename)
            val bytes = if (cachedFile.isFile() && hashFile(cachedFile) == tex.contentHash) {
                cachedFile.readBytes()
            } else {
                repository.downloadTexture(relUrl)
                    ?: error("下载贴图失败: $filename")
            }
            verifyHash(bytes, tex.contentHash, filename)
            cachedFile.writeBytes(bytes)
            File(targetDir, filename).writeBytes(bytes)
            Log.i(TAG, "applied texture slot=$slot -> $filename (${bytes.size} bytes)")
        }
        reloadTextures()
    }

    private fun reloadTextures() {
        val latch = CountDownLatch(1)
        val r = renderer
        if (r != null) {
            r.reloadTexturesOnGlThread { latch.countDown() }
            if (!latch.await(GL_RELOAD_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                Log.w(TAG, "GL reload timeout; texture files will apply on next model load")
            }
        } else {
            Log.w(TAG, "no renderer attached; texture files will apply on next model load")
        }
    }

    private fun AvatarCostumeDto.toOption(baseUrl: String): AvatarCostumeOption {
        val default = isDefault || source == "android_builtin" || costumeId == DEFAULT_AVATAR_ID
        val preview = previewUrl ?: textures.firstOrNull { it.slot == 1 }?.url ?: textures.firstOrNull()?.url
        return AvatarCostumeOption(
            id = if (default) DEFAULT_AVATAR_ID else requireNotNull(costumeId),
            name = costumeName ?: directoryName ?: costumeId ?: "默认形象",
            directoryName = directoryName,
            version = version,
            textures = if (default) emptyList() else textures,
            previewUrl = preview?.toAbsoluteUrl(baseUrl),
            isDefault = default
        )
    }

    private fun withDefaultOption(options: List<AvatarCostumeOption>): List<AvatarCostumeOption> {
        val backendDefault = options.firstOrNull { it.isDefault || it.id == DEFAULT_AVATAR_ID }
        val resolvedDefault = backendDefault?.copy(
            id = DEFAULT_AVATAR_ID,
            directoryName = backendDefault.directoryName ?: DEFAULT_AVATAR_ID,
            textures = emptyList(),
            isDefault = true
        ) ?: defaultOption
        val customOptions = options.filterNot { it.isDefault || it.id == DEFAULT_AVATAR_ID }
        return listOf(resolvedDefault) + customOptions
    }

    private fun String.toAbsoluteUrl(baseUrl: String): String {
        if (startsWith("http://") || startsWith("https://")) return this
        val normalizedBase = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
        return if (startsWith("/")) "$normalizedBase$this" else "$normalizedBase/$this"
    }

    private fun verifyHash(bytes: ByteArray, expectedHash: String?, filename: String) {
        if (expectedHash.isNullOrBlank()) return
        val actual = sha256(bytes)
        if (!actual.equals(expectedHash, ignoreCase = true)) {
            error("贴图校验失败: $filename")
        }
    }

    private fun hashFile(file: File): String {
        return sha256(file.readBytes())
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
