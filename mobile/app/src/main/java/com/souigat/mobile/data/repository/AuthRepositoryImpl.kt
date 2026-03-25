package com.souigat.mobile.data.repository

import android.util.Base64
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
import org.json.JSONObject
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
            val response = try {
                authApi.firebaseLogin(
                    FirebaseLoginRequest(
                        idToken = firebaseIdToken,
                        deviceId = tokenManager.getDeviceId(),
                    )
                )
            } catch (networkError: IOException) {
                val offlineUser = buildOfflineFirebaseProfile(firebaseIdToken, phone.trim())
                if (offlineUser != null) {
                    if (previousUserId != null && previousUserId != offlineUser.id) {
                        clearLocalOperationalData()
                    }

                    tokenManager.saveTokens(firebaseIdToken, firebaseIdToken)
                    tokenManager.saveUserProfile(
                        userId = offlineUser.id,
                        role = offlineUser.role,
                        officeId = offlineUser.office,
                        firstName = offlineUser.first_name,
                        lastName = offlineUser.last_name,
                    )

                    return Result.success(offlineUser)
                }

                return Result.failure(AuthException.NetworkUnavailable)
            }

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
                firebaseSessionManager.ensureSignedIn(forceRefresh = true)
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
        val officeId = tokenManager.getOfficeId()
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

    private fun buildOfflineFirebaseProfile(idToken: String, phone: String): UserProfileDto? {
        val payload = decodeJwtPayload(idToken) ?: return null

        val role = payload.optString("role").ifBlank { "conductor" }
        val userId = extractIntClaim(payload, "user_id")
            ?: extractUserIdFromUid(payload.optString("uid", payload.optString("user_id", "")))
            ?: return null
        val officeId = extractIntClaim(payload, "office_id")

        val fullName = payload.optString("name").trim()
        val nameParts = fullName.split(' ').filter { it.isNotBlank() }
        val firstName = nameParts.firstOrNull() ?: "Conducteur"
        val lastName = nameParts.drop(1).joinToString(" ").ifBlank { "Souigat" }

        return UserProfileDto(
            id = userId,
            phone = phone,
            first_name = firstName,
            last_name = lastName,
            role = role,
            department = payload.optString("department").ifBlank { null },
            office = officeId,
            office_name = null,
            office_city = null,
            is_active = true,
            device_id = tokenManager.getDeviceId(),
            last_login = null,
            permissions = emptyList(),
        )
    }

    private fun decodeJwtPayload(token: String): JSONObject? {
        return runCatching {
            val parts = token.split('.')
            if (parts.size < 2) {
                return null
            }

            val decoded = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP)
            JSONObject(String(decoded, Charsets.UTF_8))
        }.getOrNull()
    }

    private fun extractIntClaim(payload: JSONObject, key: String): Int? {
        val raw = payload.opt(key) ?: return null
        return when (raw) {
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull()
            else -> null
        }
    }

    private fun extractUserIdFromUid(uid: String?): Int? {
        if (uid.isNullOrBlank()) {
            return null
        }

        val digits = uid.substringAfterLast('-').trim()
        return digits.toIntOrNull()
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
