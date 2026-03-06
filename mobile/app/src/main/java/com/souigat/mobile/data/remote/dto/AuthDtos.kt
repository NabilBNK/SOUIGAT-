package com.souigat.mobile.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// REQUEST DTOs
@Serializable
data class LoginRequest(
    val username: String, // CRITICAL: "username" not "phone"
    val password: String,
    @SerialName("device_id")
    val deviceId: String,
    val platform: String = "mobile"
)

@Serializable
data class RefreshRequest(
    val refresh: String,
    @SerialName("device_id")
    val deviceId: String
)

@Serializable
data class LogoutRequest(
    val refresh: String
)

// RESPONSE DTOs
@Serializable
data class LoginResponse(
    val access: String,
    val refresh: String,
    val user: UserProfileDto,
    @SerialName("device_bound")
    val deviceBound: Boolean? = null,
    @SerialName("token_strategy")
    val tokenStrategy: String? = null
)

@Serializable
data class UserProfileDto(
    val id: Int,
    val username: String? = null,
    @SerialName("full_name")
    val fullName: String,
    val role: String, // "conductor" | "office_staff" | "admin"
    @SerialName("office_id")
    val officeId: Int? = null,
    @SerialName("is_active")
    val isActive: Boolean? = null
)

@Serializable
data class RefreshResponse(
    val access: String,
    val refresh: String? = null, // Backend may send new refresh, may not
    val strategy: String? = null
)
