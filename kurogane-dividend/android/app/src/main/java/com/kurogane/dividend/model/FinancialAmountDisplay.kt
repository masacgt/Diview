package com.kurogane.dividend.model

import com.kurogane.dividend.data.JPXTickSizeMaster
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

/** Formats monetary values to at most one decimal place without unnecessary trailing zeroes. */
fun formatFinancialAmountDisplay(
    value: Double,
    locale: Locale = Locale.JAPAN,
    alwaysShowOneDecimal: Boolean = false
): String {
    if (!value.isFinite()) return "—"

    val formatter = NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = 1
        minimumFractionDigits = if (alwaysShowOneDecimal || value % 1.0 != 0.0) 1 else 0
        roundingMode = RoundingMode.HALF_UP
    }
    return formatter.format(value)
}

/** Formats a stock price using the official JPX tick-table rule for tenth-yen prices. */
fun formatStockPriceDisplay(
    value: Double,
    code: String,
    locale: Locale = Locale.JAPAN,
    date: LocalDate = LocalDate.now(java.time.ZoneId.of("Asia/Tokyo"))
): String = formatFinancialAmountDisplay(
    value = value,
    locale = locale,
    alwaysShowOneDecimal = JPXTickSizeMaster.hasTenthYenTick(code, value, date)
)

fun formatStockPriceDisplay(
    value: Long,
    code: String,
    locale: Locale = Locale.JAPAN,
    date: LocalDate = LocalDate.now(java.time.ZoneId.of("Asia/Tokyo"))
): String = formatStockPriceDisplay(value.toDouble(), code, locale, date)

fun formatFinancialAmountDisplay(
    value: Long,
    locale: Locale = Locale.JAPAN,
    alwaysShowOneDecimal: Boolean = false
): String = formatFinancialAmountDisplay(value.toDouble(), locale, alwaysShowOneDecimal)
