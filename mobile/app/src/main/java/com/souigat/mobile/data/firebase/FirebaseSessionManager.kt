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

        // Quick path: if already signed in as this user, return cached token immediately
        // This avoids the mutex lock entirely for refresh logins
        val currentUser = firebaseAuth.currentUser
        if (currentUser != null && currentUser.email == email) {
            Timber.i("[FIREBASE] Already signed in as requested user, attempting cached token...")
            val cachedTokenStartMs = System.currentTimeMillis()
            val cachedToken = runCatching {
                currentUser.getIdToken(false).await().token
            }.getOrNull()
            val cachedTokenDurationMs = System.currentTimeMillis() - cachedTokenStartMs
            
            if (!cachedToken.isNullOrBlank()) {
                Timber.i("[FIREBASE] Cached token obtained in ${cachedTokenDurationMs}ms")
                return Result.success(cachedToken)
            }
            Timber.i("[FIREBASE] Cached token was blank after ${cachedTokenDurationMs}ms, proceeding with refresh")
        }

        // Full flow with mutex lock only if needed
        return signInMutex.withLock {
            val stepStartMs = System.currentTimeMillis()
            runCatching {
                val currentUserInMutex = firebaseAuth.currentUser
                if (currentUserInMutex != null) {
                    if (currentUserInMutex.email == email) {
                        Timber.i("[FIREBASE] Attempting to use cached token for same user (in mutex)...")
                        val cachedTokenStartMs = System.currentTimeMillis()
                        val cachedToken = currentUserInMutex.getIdToken(false).await().token
                        val cachedTokenDurationMs = System.currentTimeMillis() - cachedTokenStartMs
                        if (!cachedToken.isNullOrBlank()) {
                            Timber.i("[FIREBASE] Cached token obtained in ${cachedTokenDurationMs}ms")
                            return@runCatching cachedToken
                        }
                        Timber.i("[FIREBASE] Cached token was blank after ${cachedTokenDurationMs}ms, proceeding with new sign-in")
                    } else {
                        Timber.i("[FIREBASE] Different user detected, signing out and re-authenticating")
                        firebaseAuth.signOut()
                    }
                }

                Timber.i("[FIREBASE] Performing new sign-in with email/password...")
                val signInStartMs = System.currentTimeMillis()
                firebaseAuth.signInWithEmailAndPassword(email, password).await()
                val signInDurationMs = System.currentTimeMillis() - signInStartMs
                Timber.i("[FIREBASE] Email/password sign-in completed in ${signInDurationMs}ms")
                
                val tokenFetchStartMs = System.currentTimeMillis()
                val idToken = firebaseAuth.currentUser
                    ?.getIdToken(false)
                    ?.await()
                    ?.token
                    ?: firebaseAuth.currentUser
                        ?.getIdToken(true)
                        ?.await()
                        ?.token
                val tokenFetchDurationMs = System.currentTimeMillis() - tokenFetchStartMs
                Timber.i("[FIREBASE] Token fetch completed in ${tokenFetchDurationMs}ms")

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
            val ensureStartMs = System.currentTimeMillis()
            Timber.i("[FIREBASE] ensureSignedIn called (forceRefresh=$forceRefresh)")
            
            if (forceRefresh && firebaseAuth.currentUser != null) {
                firebaseAuth.signOut()
            }

            if (firebaseAuth.currentUser != null) {
                Timber.i("[FIREBASE] User already signed in, checking custom claims...")
                val claimsCheckStartMs = System.currentTimeMillis()
                
                val claims = runCatching {
                    firebaseAuth.currentUser
                        ?.getIdToken(false)
                        ?.await()
                        ?.claims
                        .orEmpty()
                }.getOrDefault(emptyMap())
                val claimsCheckDurationMs = System.currentTimeMillis() - claimsCheckStartMs
                val hasScopeClaims = claims["role"] != null && claims["user_id"] != null
                Timber.i("[FIREBASE] Initial claims check took ${claimsCheckDurationMs}ms, hasClaims=$hasScopeClaims")

                if (hasScopeClaims) {
                    syncLocalSessionFromFirebase(claims)
                    val totalDurationMs = System.currentTimeMillis() - ensureStartMs
                    Timber.i("[FIREBASE] ensureSignedIn completed with valid claims in ${totalDurationMs}ms")
                    return@withLock true
                }

                Timber.i("[FIREBASE] No scope claims found, attempting refresh...")
                val refreshStartMs = System.currentTimeMillis()
                val refreshedClaims = runCatching {
                    firebaseAuth.currentUser
                        ?.getIdToken(true)
                        ?.await()
                        ?.claims
                        .orEmpty()
                }.getOrDefault(emptyMap())
                val refreshDurationMs = System.currentTimeMillis() - refreshStartMs
                Timber.i("[FIREBASE] Token refresh took ${refreshDurationMs}ms")

                val hasRefreshedScopeClaims =
                    refreshedClaims["role"] != null && refreshedClaims["user_id"] != null

                if (hasRefreshedScopeClaims) {
                    syncLocalSessionFromFirebase(refreshedClaims)
                    val totalDurationMs = System.currentTimeMillis() - ensureStartMs
                    Timber.i("[FIREBASE] ensureSignedIn completed with refreshed claims in ${totalDurationMs}ms")
                    return@withLock true
                }

                Timber.w(
                    "FirebaseSessionManager: no custom claims available; continuing with Firebase user session fallback.",
                )
                syncLocalSessionFromFirebase(emptyMap())
                val totalDurationMs = System.currentTimeMillis() - ensureStartMs
                Timber.i("[FIREBASE] ensureSignedIn completed with fallback in ${totalDurationMs}ms")
                return@withLock true
            }

            Timber.i("[FIREBASE] User not signed in, attempting custom token exchange...")
            val accessToken = tokenManager.getAccessToken()
            if (accessToken.isNullOrBlank()) {
                Timber.w("FirebaseSessionManager: no access token available for custom-token exchange.")
                val totalDurationMs = System.currentTimeMillis() - ensureStartMs
                Timber.i("[FIREBASE] ensureSignedIn completed with no token in ${totalDurationMs}ms")
                return@withLock false
            }

            return@withLock try {
                val customTokenStartMs = System.currentTimeMillis()
                val response = authApi.getFirebaseCustomToken(
                    "Bearer $accessToken",
                    FirebaseCustomTokenRequest(platform = "mobile"),
                )
                val customTokenDurationMs = System.currentTimeMillis() - customTokenStartMs
                Timber.i("[FIREBASE] Custom token API call took ${customTokenDurationMs}ms")
                
                if (!response.isSuccessful) {
                    Timber.w(
                        "FirebaseSessionManager: custom-token exchange failed with code=%s",
                        response.code(),
                    )
                    val totalDurationMs = System.currentTimeMillis() - ensureStartMs
                    Timber.i("[FIREBASE] ensureSignedIn failed in ${totalDurationMs}ms")
                    false
                } else {
                    val customToken = response.body()?.token
                    if (customToken.isNullOrBlank()) {
                        Timber.w("FirebaseSessionManager: custom-token response body is empty.")
                        val totalDurationMs = System.currentTimeMillis() - ensureStartMs
                        Timber.i("[FIREBASE] ensureSignedIn failed (empty token) in ${totalDurationMs}ms")
                        false
                    } else {
                        val signInStartMs = System.currentTimeMillis()
                        firebaseAuth.signInWithCustomToken(customToken).await()
                        val signInDurationMs = System.currentTimeMillis() - signInStartMs
                        Timber.i("[FIREBASE] Custom token sign-in took ${signInDurationMs}ms")
                        
                        val totalDurationMs = System.currentTimeMillis() - ensureStartMs
                        Timber.i("[FIREBASE] ensureSignedIn completed with custom token in ${totalDurationMs}ms")
                        true
                    }
                }
            } catch (error: Exception) {
                Timber.e(error, "FirebaseSessionManager: sign-in with custom token failed.")
                val totalDurationMs = System.currentTimeMillis() - ensureStartMs
                Timber.i("[FIREBASE] ensureSignedIn failed with exception in ${totalDurationMs}ms")
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
