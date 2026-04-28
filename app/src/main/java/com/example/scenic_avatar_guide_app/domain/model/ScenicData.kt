package com.example.scenic_avatar_guide_app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ScenicArea(
    val id: String,
    val name: String,
    val spots: List<ScenicSpot>
)

@Serializable
data class ScenicSpot(
    val id: String,
    val name: String
)
