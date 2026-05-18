package com.example.scenic_avatar_guide_app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ScenicArea(
    val id: String,
    val name: String,
    val description: String = "",
    val spots: List<ScenicSpot>
)

@Serializable
data class ScenicSpot(
    val id: String,
    val name: String,
    val description: String = "",
    val sortOrder: Int = 0
)

@Serializable
data class PublicScenicListResponse(
    val items: List<PublicScenicInfo> = emptyList()
)

@Serializable
data class PublicScenicInfo(
    @SerialName("scenic_id")
    val scenicId: String,
    val name: String,
    val description: String = ""
)

@Serializable
data class PublicScenicSpotListResponse(
    val items: List<PublicScenicSpotInfo> = emptyList()
)

@Serializable
data class PublicScenicSpotInfo(
    @SerialName("spot_id")
    val spotId: String,
    val name: String,
    val description: String = "",
    @SerialName("sort_order")
    val sortOrder: Int = 0
)
