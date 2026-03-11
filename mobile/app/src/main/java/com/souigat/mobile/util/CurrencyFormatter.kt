package com.souigat.mobile.util

import java.text.NumberFormat
import java.util.*

/**
 * Formats price amounts stored as centimes into a human-readable currency string.
 *
 * Example: formatCurrency(250000, "DZD") → "2 500,00 DZD"
 *
 * Note: amounts are stored as centimes (Long) to avoid floating-point precision issues.
 * Divide by 100.0 to get the base unit.
 */
fun formatCurrency(centimes: Long, currency: String = "DZD"): String {
    val units = centimes / 100.0
    val formatter = NumberFormat.getNumberInstance(Locale.FRANCE).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
    }
    return "${formatter.format(units)} $currency"
}

/**
 * Formats centimes as a compact string (no currency symbol). Useful for labels.
 * Example: 250000 → "2 500"
 */
fun formatCompact(centimes: Long): String {
    val formatter = NumberFormat.getNumberInstance(Locale.FRANCE).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 0
    }
    return formatter.format(centimes / 100.0)
}
