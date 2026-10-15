package com.kurogane.dividend.model

import java.util.Locale

fun formatDividendYieldDisplay(
    yieldRate: Double,
    isNoDividendConfirmed: Boolean,
    locale: Locale = Locale.JAPAN
): String {
    if (isNoDividendConfirmed) return "0.00%（無配）"
    val displayRate = yieldRate.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
    return String.format(locale, "%.2f%%", displayRate)
}
