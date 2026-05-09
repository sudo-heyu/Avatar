package com.example.scenic_avatar_guide_app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthRegisterRequest(
    val username: String,
    val password: String,
    @SerialName("device_id")
    val deviceId: String? = null
)

@Serializable
data class AuthRegisterResponse(
    val code: Int,
    val message: String,
    val data: AuthUserData? = null
)

@Serializable
data class AuthLoginRequest(
    val username: String,
    val password: String,
    @SerialName("device_id")
    val deviceId: String? = null
)

@Serializable
data class AuthLoginResponse(
    val code: Int,
    val message: String,
    val data: AuthUserData? = null
)

@Serializable
data class AuthUserData(
    @SerialName("user_id")
    val userId: String,
    val username: String,
    @SerialName("created_at")
    val createdAt: String? = null
)
