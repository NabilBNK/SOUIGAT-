package com.souigat.mobile.util

import java.text.SimpleDateFormat
import java.util.*

private val dateFormat        = SimpleDateFormat("dd MMM yyyy", Locale.FRANCE)
private val dateTimeFormat    = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.FRANCE)
private val timeFormat        = SimpleDateFormat("HH:mm", Locale.getDefault())

/** Formats epoch millis as "14 Mar 2026" */
fun Long.toDisplayDate(): String = dateFormat.format(Date(this))

/** Formats epoch millis as "14 Mar 2026 08:30" */
fun Long.toDisplayDateTime(): String = dateTimeFormat.format(Date(this))

/** Formats epoch millis as "08:30" */
fun Long.toDisplayTime(): String = timeFormat.format(Date(this))

/**
 * Checks whether the given epoch millis timestamp is older than [thresholdMs].
 * Used by the stale data warning on Dashboard.
 */
fun Long.isOlderThan(thresholdMs: Long): Boolean =
    System.currentTimeMillis() - this > thresholdMs

/** 4-hour threshold in milliseconds for the stale data warning */
const val STALE_THRESHOLD_MS = 4 * 60 * 60 * 1000L
