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
        val rawDigits = buildString {
            phone.forEach { ch ->
                val numeric = Character.getNumericValue(ch)
                if (numeric in 0..9) {
                    append(numeric)
                }
            }
        }

        val normalizedDigits = when {
            rawDigits.startsWith("00213") && rawDigits.length == 14 -> "0${rawDigits.substring(5)}"
            rawDigits.startsWith("213") && rawDigits.length == 12 -> "0${rawDigits.substring(3)}"
            else -> rawDigits
        }

        return "$normalizedDigits@accounts.souigat.local"
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
}
