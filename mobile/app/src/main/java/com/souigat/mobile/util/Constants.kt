package com.souigat.mobile.util

import android.os.Build
import com.souigat.mobile.BuildConfig

/**
 * Application-wide constants.
 */
object Constants {

    /**
     * Base URL switches by build type.
     *
     * debug   → 10.0.2.2:8002 (emulator host = developer machine)
     *           Use `adb reverse tcp:8002 tcp:8002` for physical device testing.
     * staging → HTTPS staging server (requires real cert pins — see P0.5)
     * release → HTTPS production server
     */
    val BASE_URL: String = when (BuildConfig.BUILD_TYPE) {
        "staging"  -> "https://staging.souigat.dz/api/"
        "release"  -> "https://api.souigat.dz/api/"
        else       -> if (isEmulator()) {
            "http://10.0.2.2:8002/api/"  // emulator -> host machine
        } else {
            "http://localhost:8002/api/"  // physical device via `adb reverse tcp:8002 tcp:8002`
        }
    }

    private fun isEmulator(): Boolean {
        return Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
            Build.MODEL.contains("Emulator", ignoreCase = true) ||
            Build.MODEL.contains("Android SDK built for", ignoreCase = true)
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
