package com.souigat.mobile.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefsName = "souigat_secure_prefs"
    private var sharedPreferences = initEncryptedPrefs(context)

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
            // Fallback for Keystore corruption (common on MIUI/Xiaomi)
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

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_OFFICE_ID = "office_id"
        private const val KEY_FULL_NAME = "full_name"
    }

    fun getDeviceId(): String {
        var deviceId = sharedPreferences.getString(KEY_DEVICE_ID, null)
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString()
            sharedPreferences.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }
        return deviceId!!
    }

    fun saveTokens(accessToken: String, refreshToken: String) {
        sharedPreferences.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
    }

    fun saveUserProfile(userId: Int, role: String, officeId: Int?, fullName: String) {
        val editor = sharedPreferences.edit()
            .putInt(KEY_USER_ID, userId)
            .putString(KEY_USER_ROLE, role)
            .putString(KEY_FULL_NAME, fullName)
            
        if (officeId != null) {
            editor.putInt(KEY_OFFICE_ID, officeId)
        } else {
            editor.remove(KEY_OFFICE_ID)
        }
        
        editor.apply()
    }

    fun getAccessToken(): String? = sharedPreferences.getString(KEY_ACCESS_TOKEN, null)
    
    fun getRefreshToken(): String? = sharedPreferences.getString(KEY_REFRESH_TOKEN, null)
    
    fun getUserRole(): String? = sharedPreferences.getString(KEY_USER_ROLE, null)
    
    fun getUserId(): Int? {
        val id = sharedPreferences.getInt(KEY_USER_ID, -1)
        return if (id != -1) id else null
    }
    
    fun getOfficeId(): Int? {
        val id = sharedPreferences.getInt(KEY_OFFICE_ID, -1)
        return if (id != -1) id else null
    }
    
    fun getFullName(): String? = sharedPreferences.getString(KEY_FULL_NAME, null)

    fun clearAll() {
        val deviceId = getDeviceId() // Keep deviceId intact
        sharedPreferences.edit()
            .clear()
            .putString(KEY_DEVICE_ID, deviceId)
            .apply()
    }
}
