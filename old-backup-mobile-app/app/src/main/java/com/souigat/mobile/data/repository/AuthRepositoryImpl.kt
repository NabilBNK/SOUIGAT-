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

    override suspend fun login(phone: String, password: String): Result<UserProfileDto> {
        return try {
            val response = authApi.login(
                LoginRequest(
                    phone = phone,
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
                    officeId = body.user.office,
                    firstName = body.user.first_name,
                    lastName = body.user.last_name
                )
                Result.success(body.user)
            } else {
                val errorCode = response.code()
                Result.failure(
                    when (errorCode) {
                        401 -> AuthException.InvalidCredentials
                        403 -> AuthException.AccountDisabled
                        429 -> AuthException.TooManyAttempts
                        else -> AuthException.ServerError(errorCode)
                    }
                )
            }
        } catch (e: IOException) {
            Result.failure(AuthException.NetworkUnavailable)
        } catch (e: Exception) {
            // Catch SerializationException or other unexpected runtime errors at the boundary
            Result.failure(AuthException.SchemaMismatch)
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
        val officeId = tokenManager.getOfficeId() ?: return null
        val firstName = tokenManager.getFirstName() ?: return null
        val lastName = tokenManager.getLastName() ?: return null
        
        return UserProfileDto(
            id = id,
            phone = "", // Not stored locally currently, but optional in the app's offline flow
            first_name = firstName, 
            last_name = lastName, 
            role = role,
            department = null,
            office = officeId,
            office_name = null,
            office_city = null,
            is_active = true,
            device_id = tokenManager.getDeviceId(),
            last_login = null,
            permissions = emptyList()
        )
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
    object TooManyAttempts : AuthException()   // 429 from throttle
    object SchemaMismatch : AuthException()     // JSON parsing failure
    data class ServerError(val code: Int) : AuthException()
}
