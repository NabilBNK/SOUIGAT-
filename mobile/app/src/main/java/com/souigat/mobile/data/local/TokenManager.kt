package com.souigat.mobile.data.local

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID

@Immutable
data class SessionSnapshot(
    val deviceId: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val userRole: String? = null,
    val userId: Int? = null,
    val officeId: Int? = null,
    val firstName: String? = null,
    val lastName: String? = null
) {
    val fullName: String?
        get() = listOfNotNull(firstName, lastName)
            .joinToString(" ")
            .trim()
            .takeIf { it.isNotBlank() }
}

@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val appContext = context.applicationContext
    private val prefsName = "souigat_secure_prefs"
    private val sharedPreferences by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        initEncryptedPrefs(appContext)
    }
    private val sessionLoadMonitor = Any()

    @Volatile
    private var sessionLoaded = false

    private val _session = MutableStateFlow(SessionSnapshot())
    val session: StateFlow<SessionSnapshot> = _session.asStateFlow()

    private val _onSessionCleared = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val onSessionCleared = _onSessionCleared.asSharedFlow()

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_OFFICE_ID = "office_id"
        private const val KEY_FIRST_NAME = "first_name"
        private const val KEY_LAST_NAME = "last_name"
    }

    private fun initEncryptedPrefs(context: Context): android.content.SharedPreferences {
        return try {
            val masterKeyAlias = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                prefsName,
                masterKeyAlias,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Timber.e(e, "EncryptedSharedPreferences corrupted. Re-initializing...")
            context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().commit()

            val newMasterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                prefsName,
                newMasterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    suspend fun ensureSessionLoaded(): SessionSnapshot = withContext(Dispatchers.IO) {
        ensureSessionLoadedSync()
    }

    private fun ensureSessionLoadedSync(): SessionSnapshot {
        if (sessionLoaded) {
            return _session.value
        }

        synchronized(sessionLoadMonitor) {
            if (!sessionLoaded) {
                _session.value = readSessionSnapshot()
                sessionLoaded = true
            }
        }

        return _session.value
    }

    private fun readSessionSnapshot(): SessionSnapshot {
        val userId = sharedPreferences.getInt(KEY_USER_ID, -1).takeIf { it != -1 }
        val officeId = sharedPreferences.getInt(KEY_OFFICE_ID, -1).takeIf { it != -1 }
        return SessionSnapshot(
            deviceId = sharedPreferences.getString(KEY_DEVICE_ID, null),
            accessToken = sharedPreferences.getString(KEY_ACCESS_TOKEN, null),
            refreshToken = sharedPreferences.getString(KEY_REFRESH_TOKEN, null),
            userRole = sharedPreferences.getString(KEY_USER_ROLE, null),
            userId = userId,
            officeId = officeId,
            firstName = sharedPreferences.getString(KEY_FIRST_NAME, null),
            lastName = sharedPreferences.getString(KEY_LAST_NAME, null)
        )
    }

    private fun persistSession(snapshot: SessionSnapshot) {
        _session.value = snapshot
        sessionLoaded = true
    }

    fun getDeviceId(): String {
        val currentSession = ensureSessionLoadedSync()
        val cachedDeviceId = currentSession.deviceId
        if (!cachedDeviceId.isNullOrBlank()) {
            return cachedDeviceId
        }

        val generatedDeviceId = UUID.randomUUID().toString()
        sharedPreferences.edit().putString(KEY_DEVICE_ID, generatedDeviceId).apply()
        persistSession(currentSession.copy(deviceId = generatedDeviceId))
        return generatedDeviceId
    }

    fun saveTokens(accessToken: String, refreshToken: String) {
        val currentSession = ensureSessionLoadedSync()
        sharedPreferences.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
        persistSession(currentSession.copy(accessToken = accessToken, refreshToken = refreshToken))
    }

    fun saveUserProfile(
        userId: Int,
        role: String,
        officeId: Int?,
        firstName: String,
        lastName: String
    ) {
        val currentSession = ensureSessionLoadedSync()
        val editor = sharedPreferences.edit()
            .putInt(KEY_USER_ID, userId)
            .putString(KEY_USER_ROLE, role)
            .putString(KEY_FIRST_NAME, firstName)
            .putString(KEY_LAST_NAME, lastName)

        if (officeId != null) {
            editor.putInt(KEY_OFFICE_ID, officeId)
        } else {
            editor.remove(KEY_OFFICE_ID)
        }

        editor.apply()
        persistSession(
            currentSession.copy(
                userId = userId,
                userRole = role,
                officeId = officeId,
                firstName = firstName,
                lastName = lastName
            )
        )
    }

    fun getAccessToken(): String? = ensureSessionLoadedSync().accessToken

    fun getRefreshToken(): String? = ensureSessionLoadedSync().refreshToken

    fun getUserRole(): String? = ensureSessionLoadedSync().userRole

    fun getUserId(): Int? = ensureSessionLoadedSync().userId

    fun getOfficeId(): Int? = ensureSessionLoadedSync().officeId

    fun getFirstName(): String? = ensureSessionLoadedSync().firstName

    fun getLastName(): String? = ensureSessionLoadedSync().lastName

    fun getFullName(): String? = ensureSessionLoadedSync().fullName

    fun clearAll() {
        val currentSession = ensureSessionLoadedSync()
        val hadSession = currentSession.accessToken != null ||
            currentSession.refreshToken != null ||
            currentSession.userRole != null ||
            currentSession.userId != null

        val deviceId = getDeviceId()
        sharedPreferences.edit()
            .clear()
            .putString(KEY_DEVICE_ID, deviceId)
            .apply()

        persistSession(SessionSnapshot(deviceId = deviceId))

        if (hadSession) {
            _onSessionCleared.tryEmit(Unit)
        }
    }
}
