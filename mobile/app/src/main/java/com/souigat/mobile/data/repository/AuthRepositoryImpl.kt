package com.souigat.mobile.data.repository

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.souigat.mobile.data.local.TokenManager
import com.souigat.mobile.data.local.SouigatDatabase
import com.souigat.mobile.data.firebase.FirebaseSessionManager
import com.souigat.mobile.data.remote.api.AuthApi
import com.souigat.mobile.data.remote.dto.FirebaseLoginRequest
import com.souigat.mobile.data.remote.dto.LogoutRequest
import com.souigat.mobile.data.remote.dto.UserProfileDto
import com.souigat.mobile.domain.repository.AuthRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val tokenManager: TokenManager,
    private val database: SouigatDatabase,
    private val firebaseSessionManager: FirebaseSessionManager
) : AuthRepository {

    override suspend fun login(phone: String, password: String): Result<UserProfileDto> {
        return try {
            val firebaseIdToken = firebaseSessionManager
                .signInWithEmployeeCredentials(phone.trim(), password)
                .getOrElse { error ->
                    return Result.failure(
                        when (error) {
                            is FirebaseNetworkException,
                            is IOException -> AuthException.NetworkUnavailable
                            is FirebaseAuthInvalidCredentialsException,
                            is FirebaseAuthInvalidUserException -> AuthException.InvalidCredentials
                            else -> AuthException.SchemaMismatch
                        }
                    )
                }

            val previousUserId = tokenManager.getUserId()
            val response = authApi.firebaseLogin(
                FirebaseLoginRequest(
                    idToken = firebaseIdToken,
                    deviceId = tokenManager.getDeviceId(),
                )
            )
            if (response.isSuccessful) {
                val body = response.body()!!
                val incomingUserId = body.user.id
                if (previousUserId != null && previousUserId != incomingUserId) {
                    clearLocalOperationalData()
                }
                tokenManager.saveTokens(body.access, body.refresh)
                tokenManager.saveUserProfile(
                    userId = incomingUserId,
                    role = body.user.role,
                    officeId = body.user.office,
                    firstName = body.user.first_name,
                    lastName = body.user.last_name
                )
                firebaseSessionManager.ensureSignedIn(forceRefresh = false)
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
            firebaseSessionManager.signOut()
            clearLocalOperationalData()
            tokenManager.clearAll()
            Result.success(Unit)
        } catch (e: Exception) {
            // Always clear local tokens even if server logout fails
            firebaseSessionManager.signOut()
            clearLocalOperationalData()
            tokenManager.clearAll()
            Result.success(Unit)
        }
    }

    private suspend fun clearLocalOperationalData() = withContext(Dispatchers.IO) {
        database.clearAllTables()
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
