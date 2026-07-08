package com.example.scenic_avatar_guide_app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 后端 GET /api/v1/avatar/costumes 响应。 */
@Serializable
data class AvatarCostumeListResponse(
    val code: Int,
    val message: String,
    val data: AvatarCostumeListData = AvatarCostumeListData()
)

@Serializable
data class AvatarCostumeListData(
    val items: List<AvatarCostumeDto> = emptyList(),
    val total: Int = 0
)

@Serializable
data class AvatarCostumeDto(
    @SerialName("costume_id") val costumeId: String? = null,
    @SerialName("costume_name") val costumeName: String? = null,
    @SerialName("directory_name") val directoryName: String? = null,
    @SerialName("preview_url") val previewUrl: String? = null,
    val version: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val source: String? = null,
    @SerialName("is_default") val isDefault: Boolean = false,
    val readonly: Boolean = false,
    val textures: List<AvatarCostumeTextureDto> = emptyList()
) {
    val isValid: Boolean
        get() = isDefault || costumeId == "default" || (!costumeId.isNullOrBlank() &&
            !directoryName.isNullOrBlank() &&
            !version.isNullOrBlank() &&
            textures.any { it.slot == 0 } &&
            textures.any { it.slot == 1 })
}

@Serializable
data class AvatarCostumeTextureDto(
    val slot: Int = 0,
    val filename: String? = null,
    val url: String? = null,
    @SerialName("content_hash") val contentHash: String? = null,
    val size: Long = 0
)
