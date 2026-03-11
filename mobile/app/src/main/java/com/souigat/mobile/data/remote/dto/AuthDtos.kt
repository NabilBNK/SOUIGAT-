package com.souigat.mobile.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// REQUEST DTOs
@Serializable
data class LoginRequest(
    val phone: String,
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
    val deviceBound: Boolean,
    @SerialName("token_strategy")
    val tokenStrategy: String
)

@Serializable
data class UserProfileDto(
    val id: Int,
    val phone: String,
    val first_name: String,
    val last_name: String,
    val role: String,
    val department: String?,
    val office: Int,
    val office_name: String?,
    val office_city: String?,
    val is_active: Boolean,
    val device_id: String?,
    val last_login: String?,
    val permissions: List<String>
) {
    val fullName: String get() = "$first_name $last_name".trim()
}

@Serializable
data class RefreshResponse(
    val access: String,
    val refresh: String? = null, // Backend may send new refresh, may not
    val strategy: String? = null
)
