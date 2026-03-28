package com.souigat.mobile.util

import com.souigat.mobile.BuildConfig

/**
 * Application-wide constants.
 */
object Constants {

    // Build-type specific API base URL provided from Gradle buildConfigField.
    val BASE_URL: String = BuildConfig.API_BASE_URL

    const val CONNECT_TIMEOUT_S = 30L
    const val READ_TIMEOUT_S = 30L
    const val WRITE_TIMEOUT_S = 30L

    const val SYNC_BATCH_SIZE = 80
    const val MAX_SYNC_DRAIN_ROUNDS = 10
    const val SYNC_MAX_ATTEMPTS = 8
    const val SYNC_BASE_BACKOFF_MS = 5_000L
    const val SYNC_MAX_BACKOFF_MS = 10 * 60_000L
    const val SYNC_WORKER_TAG = "souigat_sync"
    const val TRIP_REMINDER_TAG = "souigat_trip_reminder"
    const val TRIP_REMINDER_CHANNEL_ID = "trip_reminders"
    const val TRIP_LIST_STALE_MS = 60_000L
    const val FOREGROUND_SERVICE_NOTIFICATION_ID = 1001
}
