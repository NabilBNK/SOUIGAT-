package com.souigat.mobile.domain.repository

import com.souigat.mobile.data.remote.dto.UserProfileDto

interface AuthRepository {
    suspend fun login(phone: String, password: String): Result<UserProfileDto>
    suspend fun logout(): Result<Unit>
    suspend fun getStoredUserProfile(): UserProfileDto?
    fun isLoggedIn(): Boolean
}
