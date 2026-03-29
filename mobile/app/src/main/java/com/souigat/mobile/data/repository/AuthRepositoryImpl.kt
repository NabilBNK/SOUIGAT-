package com.souigat.mobile.data.repository

import android.util.Base64
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.souigat.mobile.data.firebase.FirebaseSessionManager
import com.souigat.mobile.data.local.SouigatDatabase
import com.souigat.mobile.data.local.TokenManager
import com.souigat.mobile.domain.model.UserProfile
import com.souigat.mobile.domain.repository.AuthRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val tokenManager: TokenManager,
    private val database: SouigatDatabase,
    private val firebaseSessionManager: FirebaseSessionManager,
) : AuthRepository {

    override suspend fun login(phone: String, password: String): Result<UserProfile> {
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

            val firebaseUser = buildOfflineFirebaseProfile(firebaseIdToken, phone.trim())
                ?: return Result.failure(AuthException.SchemaMismatch)

            val previousUserId = tokenManager.getUserId()
            if (previousUserId == null || previousUserId != firebaseUser.id) {
                clearLocalOperationalData()
            }

            tokenManager.clearBackendTokens()
            tokenManager.saveUserProfile(
                userId = firebaseUser.id,
                role = firebaseUser.role,
                officeId = firebaseUser.officeId,
                firstName = firebaseUser.firstName,
                lastName = firebaseUser.lastName,
            )

            Result.success(firebaseUser)
        } catch (e: IOException) {
            Result.failure(AuthException.NetworkUnavailable)
        } catch (e: Exception) {
            Result.failure(AuthException.SchemaMismatch)
        }
    }

    override suspend fun logout(): Result<Unit> {
        clearLocalOperationalData()
        firebaseSessionManager.signOut()
        tokenManager.clearAll()
        return Result.success(Unit)
    }

    private suspend fun clearLocalOperationalData() = withContext(Dispatchers.IO) {
        database.clearAllTables()
    }

    override suspend fun getStoredUserProfile(): UserProfile? {
        val role = tokenManager.getUserRole() ?: return null
        val id = tokenManager.getUserId() ?: return null
        val firstName = tokenManager.getFirstName() ?: return null
        val lastName = tokenManager.getLastName() ?: return null

        return UserProfile(
            id = id,
            phone = "",
            firstName = firstName,
            lastName = lastName,
            role = role,
            officeId = tokenManager.getOfficeId(),
            isActive = true,
            deviceId = tokenManager.getDeviceId(),
        )
    }

    override fun isLoggedIn(): Boolean {
        return firebaseSessionManager.hasActiveFirebaseUser() && tokenManager.getUserId() != null
    }

    private fun buildOfflineFirebaseProfile(idToken: String, phone: String): UserProfile? {
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

        return UserProfile(
            id = userId,
            phone = phone,
            firstName = firstName,
            lastName = lastName,
            role = role,
            officeId = officeId,
            isActive = true,
            deviceId = tokenManager.getDeviceId(),
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

sealed class AuthException : Exception() {
    object InvalidCredentials : AuthException()
    object AccountDisabled : AuthException()
    object NetworkUnavailable : AuthException()
    object TooManyAttempts : AuthException()
    object SchemaMismatch : AuthException()
}

