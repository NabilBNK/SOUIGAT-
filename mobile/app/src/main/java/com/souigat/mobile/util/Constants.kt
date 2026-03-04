package com.souigat.mobile.util

import com.souigat.mobile.BuildConfig

/**
 * Application-wide constants.
 */
object Constants {

    /**
     * Base URL switches by build type.
     *
     * debug   → 10.0.2.2:8000 (emulator host = developer machine running Docker)
     *           Use `adb reverse tcp:8000 tcp:8000` for physical device testing.
     * staging → HTTPS staging server (requires real cert pins — see P0.5)
     * release → HTTPS production server
     */
    val BASE_URL: String = when (BuildConfig.BUILD_TYPE) {
        "staging"  -> "https://staging.souigat.dz/api/"
        "release"  -> "https://api.souigat.dz/api/"
        else       -> "http://10.0.2.2:8000/api/"  // debug
    }

    const val CONNECT_TIMEOUT_S = 30L
    const val READ_TIMEOUT_S = 30L
    const val WRITE_TIMEOUT_S = 30L

    const val SYNC_BATCH_SIZE = 50
    const val SYNC_WORKER_TAG = "souigat_sync"
    const val FOREGROUND_SERVICE_NOTIFICATION_ID = 1001
}
