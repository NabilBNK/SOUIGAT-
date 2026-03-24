package com.souigat.mobile.util

import android.os.Build
import com.souigat.mobile.BuildConfig

/**
 * Application-wide constants.
 */
object Constants {

    private fun isEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        val product = Build.PRODUCT.lowercase()

        return fingerprint.startsWith("generic") ||
            fingerprint.contains("emulator") ||
            model.contains("sdk") ||
            model.contains("emulator") ||
            manufacturer.contains("genymotion") ||
            product.contains("sdk")
    }

    fun usesDynamicDebugBackend(): Boolean = BuildConfig.DEBUG && !isEmulator()

    /**
     * Bootstrap base URL used to initialize Retrofit.
     *
     * For debug physical devices this is only a placeholder. Requests are rewritten at runtime
     * to the currently reachable LAN backend so DHCP changes do not break the app.
     */
    val BASE_URL: String = when (BuildConfig.BUILD_TYPE) {
        "staging" -> "https://staging.souigat.dz/api/"
        "release" -> "https://api.souigat.dz/api/"
        else -> if (isEmulator()) {
            "http://10.0.2.2:8000/api/"
        } else {
            "http://127.0.0.1:8000/api/"
        }
    }

    const val CONNECT_TIMEOUT_S = 30L
    const val READ_TIMEOUT_S = 30L
    const val WRITE_TIMEOUT_S = 30L

    const val SYNC_BATCH_SIZE = 50
    const val MAX_SYNC_DRAIN_ROUNDS = 6
    const val SYNC_WORKER_TAG = "souigat_sync"
    const val TRIP_REMINDER_TAG = "souigat_trip_reminder"
    const val TRIP_REMINDER_CHANNEL_ID = "trip_reminders"
    const val TRIP_LIST_STALE_MS = 60_000L
    const val FOREGROUND_SERVICE_NOTIFICATION_ID = 1001
}
