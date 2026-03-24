package com.souigat.mobile.util

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val frLocale = Locale.FRANCE
private val deviceZone = ZoneId.systemDefault()
private val dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", frLocale)
private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", frLocale)
private val routeDateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy 'a' HH:mm", frLocale)
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", frLocale)

/** Formats epoch millis as "14 Mar 2026". */
fun Long.toDisplayDate(): String = Instant.ofEpochMilli(this).atZone(deviceZone).format(dateFormatter)

/** Formats epoch millis as "14 Mar 2026 08:30". */
fun Long.toDisplayDateTime(): String = Instant.ofEpochMilli(this).atZone(deviceZone).format(dateTimeFormatter)

/** Formats epoch millis as "08:30". */
fun Long.toDisplayTime(): String = Instant.ofEpochMilli(this).atZone(deviceZone).format(timeFormatter)

/** Formats epoch millis as a prominent route header timestamp. */
fun Long.toRouteDateTime(): String = Instant.ofEpochMilli(this).atZone(deviceZone).format(routeDateTimeFormatter)

/** Formats an ISO-8601 API datetime into a route-friendly display string. */
fun String.toRouteDateTimeOrSelf(): String {
    return try {
        OffsetDateTime.parse(this).atZoneSameInstant(deviceZone).format(routeDateTimeFormatter)
    } catch (_: Exception) {
        this
    }
}

/** Formats an ISO-8601 API datetime into a standard display string. */
fun String.toDisplayDateTimeOrSelf(): String {
    return try {
        OffsetDateTime.parse(this).atZoneSameInstant(deviceZone).format(dateTimeFormatter)
    } catch (_: Exception) {
        this
    }
}

/**
 * Checks whether the given epoch millis timestamp is older than [thresholdMs].
 * Used by the stale data warning on trip refresh surfaces.
 */
fun Long.isOlderThan(thresholdMs: Long): Boolean = System.currentTimeMillis() - this > thresholdMs

/** 4-hour threshold in milliseconds for stale operational data. */
const val STALE_THRESHOLD_MS = 4 * 60 * 60 * 1000L
