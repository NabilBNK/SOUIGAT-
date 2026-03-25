package com.souigat.mobile.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/**
 * Formats price amounts stored as whole currency units into a human-readable string.
 *
 * Example: formatCurrency(2500, "DZD") -> "2 500 DZD"
 */
fun formatCurrency(amount: Long, currency: String = "DZD"): String {
    val formatter = NumberFormat.getNumberInstance(Locale.FRANCE).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
    }
    return "${formatter.format(amount)} $currency"
}

/**
 * Formats an amount as a compact string (no currency symbol). Useful for labels.
 * Example: 2500 -> "2 500"
 */
fun formatCompact(amount: Long): String {
    val formatter = NumberFormat.getNumberInstance(Locale.FRANCE).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 0
    }
    return formatter.format(amount)
}

/**
 * Parses a user-entered currency amount expressed in base units (for example "1 200" DZD)
 * and stores it as a whole-unit integer.
 */
fun parseCurrencyInput(raw: String): Long? {
    val normalized = raw
        .trim()
        .replace(" ", "")
        .replace("\u00A0", "")
        .replace("\u202F", "")
        .replace(',', '.')

    if (normalized.isBlank()) {
        return null
    }

    return try {
        BigDecimal(normalized)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
    } catch (_: Exception) {
        null
    }
}
