package com.souigat.mobile.domain.model

data class UserProfile(
    val id: Int,
    val phone: String,
    val firstName: String,
    val lastName: String,
    val role: String,
    val officeId: Int?,
    val isActive: Boolean,
    val deviceId: String?,
) {
    val fullName: String
        get() = "$firstName $lastName".trim()
}
