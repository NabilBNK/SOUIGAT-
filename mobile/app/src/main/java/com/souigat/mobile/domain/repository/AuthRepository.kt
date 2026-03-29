package com.souigat.mobile.domain.repository

import com.souigat.mobile.domain.model.UserProfile

interface AuthRepository {
    suspend fun login(phone: String, password: String): Result<UserProfile>
    suspend fun logout(): Result<Unit>
    suspend fun getStoredUserProfile(): UserProfile?
    fun isLoggedIn(): Boolean
}
