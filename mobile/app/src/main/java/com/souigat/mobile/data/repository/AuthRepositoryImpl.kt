package com.souigat.mobile.data.repository

import com.souigat.mobile.data.local.TokenManager
import com.souigat.mobile.data.remote.api.AuthApi
import com.souigat.mobile.data.remote.dto.LoginRequest
import com.souigat.mobile.data.remote.dto.LogoutRequest
import com.souigat.mobile.data.remote.dto.UserProfileDto
import com.souigat.mobile.domain.repository.AuthRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val tokenManager: TokenManager
) : AuthRepository {

    override suspend fun login(username: String, password: String): Result<UserProfileDto> {
        return try {
            val response = authApi.login(
                LoginRequest(
                    username = username,
                    password = password,
                    deviceId = tokenManager.getDeviceId()
                )
            )
            if (response.isSuccessful) {
                val body = response.body()!!
                tokenManager.saveTokens(body.access, body.refresh)
                tokenManager.saveUserProfile(
                    userId = body.user.id,
                    role = body.user.role,
                    officeId = body.user.officeId,
                    fullName = body.user.fullName
                )
                Result.success(body.user)
            } else {
                val errorCode = response.code()
                Result.failure(
                    when (errorCode) {
                        401 -> AuthException.InvalidCredentials
                        403 -> AuthException.AccountDisabled
                        else -> AuthException.ServerError(errorCode)
                    }
                )
            }
        } catch (e: IOException) {
            Result.failure(AuthException.NetworkUnavailable)
        }
    }

    override suspend fun logout(): Result<Unit> {
        return try {
            val refreshToken = tokenManager.getRefreshToken()
            if (refreshToken != null) {
                authApi.logout(LogoutRequest(refreshToken))
                // Fire and forget — clear local tokens regardless of server response
            }
            tokenManager.clearAll()
            Result.success(Unit)
        } catch (e: Exception) {
            // Always clear local tokens even if server logout fails
            tokenManager.clearAll()
            Result.success(Unit)
        }
    }

    override suspend fun getStoredUserProfile(): UserProfileDto? {
        val role = tokenManager.getUserRole() ?: return null
        val id = tokenManager.getUserId() ?: return null
        val officeId = tokenManager.getOfficeId()
        val fullName = tokenManager.getFullName() ?: return null
        return UserProfileDto(id, "", fullName, role, officeId, true)
    }

    override fun isLoggedIn(): Boolean {
        return tokenManager.getAccessToken() != null
                && tokenManager.getRefreshToken() != null
    }
}

// Typed error hierarchy — NO generic "Error" states
sealed class AuthException : Exception() {
    object InvalidCredentials : AuthException()
    object AccountDisabled : AuthException()
    object NetworkUnavailable : AuthException()
    data class ServerError(val code: Int) : AuthException()
}
