package com.souigat.mobile.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.souigat.mobile.data.local.TokenManager
import com.souigat.mobile.data.remote.api.AuthApi
import com.souigat.mobile.data.remote.dto.FirebaseCustomTokenRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import timber.log.Timber

@Singleton
class FirebaseSessionManager @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val authApi: AuthApi,
    private val tokenManager: TokenManager
) {
    private val signInMutex = Mutex()

    private fun firebaseEmailForPhone(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return "$digits@accounts.souigat.local"
    }

    suspend fun signInWithEmployeeCredentials(phone: String, password: String): Result<String> {
        val normalizedPhone = phone.trim()
        val email = firebaseEmailForPhone(normalizedPhone)

        return signInMutex.withLock {
            runCatching {
                val currentUser = firebaseAuth.currentUser
                if (currentUser != null && currentUser.email != email) {
                    firebaseAuth.signOut()
                }

                firebaseAuth.signInWithEmailAndPassword(email, password).await()
                val idToken = firebaseAuth.currentUser
                    ?.getIdToken(true)
                    ?.await()
                    ?.token

                if (idToken.isNullOrBlank()) {
                    error("Firebase ID token is empty after sign-in.")
                }

                idToken
            }.onFailure { error ->
                Timber.w(error, "FirebaseSessionManager: employee credential sign-in failed.")
            }
        }
    }

    suspend fun ensureSignedIn(forceRefresh: Boolean = false): Boolean {
        return signInMutex.withLock {
            if (forceRefresh && firebaseAuth.currentUser != null) {
                firebaseAuth.signOut()
            }

            if (firebaseAuth.currentUser != null) {
                val claims = runCatching {
                    firebaseAuth.currentUser
                        ?.getIdToken(false)
                        ?.await()
                        ?.claims
                        .orEmpty()
                }.getOrDefault(emptyMap())
                val hasScopeClaims = claims["role"] != null && claims["user_id"] != null

                if (hasScopeClaims) {
                    syncLocalSessionFromFirebase(claims)
                    return@withLock true
                }

                val refreshedClaims = runCatching {
                    firebaseAuth.currentUser
                        ?.getIdToken(true)
                        ?.await()
                        ?.claims
                        .orEmpty()
                }.getOrDefault(emptyMap())

                val hasRefreshedScopeClaims =
                    refreshedClaims["role"] != null && refreshedClaims["user_id"] != null

                if (hasRefreshedScopeClaims) {
                    syncLocalSessionFromFirebase(refreshedClaims)
                    return@withLock true
                }

                Timber.w(
                    "FirebaseSessionManager: no custom claims available; continuing with Firebase user session fallback.",
                )
                syncLocalSessionFromFirebase(emptyMap())
                return@withLock true
            }

            val accessToken = tokenManager.getAccessToken()
            if (accessToken.isNullOrBlank()) {
                Timber.w("FirebaseSessionManager: no access token available for custom-token exchange.")
                return@withLock false
            }

            return@withLock try {
                val response = authApi.getFirebaseCustomToken(
                    "Bearer $accessToken",
                    FirebaseCustomTokenRequest(platform = "mobile"),
                )
                if (!response.isSuccessful) {
                    Timber.w(
                        "FirebaseSessionManager: custom-token exchange failed with code=%s",
                        response.code(),
                    )
                    false
                } else {
                    val customToken = response.body()?.token
                    if (customToken.isNullOrBlank()) {
                        Timber.w("FirebaseSessionManager: custom-token response body is empty.")
                        false
                    } else {
                        firebaseAuth.signInWithCustomToken(customToken).await()
                        true
                    }
                }
            } catch (error: Exception) {
                Timber.e(error, "FirebaseSessionManager: sign-in with custom token failed.")
                false
            }
        }
    }

    fun signOut() {
        try {
            firebaseAuth.signOut()
        } catch (error: Exception) {
            Timber.w(error, "FirebaseSessionManager: signOut failed.")
        }
    }

    private fun syncLocalSessionFromFirebase(claims: Map<String, Any>) {
        val firebaseUser = firebaseAuth.currentUser ?: return

        val userId = extractInt(claims["user_id"]) ?: extractUserIdFromUid(firebaseUser.uid) ?: return
        val role = (claims["role"] as? String)?.trim().takeUnless { it.isNullOrBlank() }
            ?: tokenManager.getUserRole()
            ?: "conductor"
        val officeId = extractInt(claims["office_id"]) ?: tokenManager.getOfficeId()

        val existingName = tokenManager.getFullName().orEmpty()
        val sourceName = firebaseUser.displayName?.trim().takeUnless { it.isNullOrBlank() } ?: existingName
        val nameParts = sourceName.split(' ').filter { it.isNotBlank() }
        val firstName = nameParts.firstOrNull() ?: tokenManager.getFirstName() ?: "Conducteur"
        val lastName = nameParts.drop(1).joinToString(" ").ifBlank { tokenManager.getLastName() ?: "Souigat" }

        val currentUserId = tokenManager.getUserId()
        val currentRole = tokenManager.getUserRole()
        val currentOfficeId = tokenManager.getOfficeId()
        if (currentUserId == userId && currentRole == role && currentOfficeId == officeId) {
            return
        }

        tokenManager.saveUserProfile(
            userId = userId,
            role = role,
            officeId = officeId,
            firstName = firstName,
            lastName = lastName,
        )
        Timber.i("FirebaseSessionManager: synced local session profile from Firebase token/uid.")
    }

    private fun extractInt(raw: Any?): Int? {
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
