package com.kurogane.dividend.data

import java.util.Locale

internal fun normalizeJapaneseSecurityCode(code: String): String =
    code.trim().uppercase(Locale.ROOT).removeSuffix(".T").let {
        if (it.matches(Regex("[A-Z0-9]{4}0"))) it.dropLast(1) else it
    }

/** A successful response with no annual amount invalidates the previous amount.
 * A response without a dividend status (or a failed request) does not erase it. */
internal fun MutableMap<String, Double>.updateAnnualDividend(
    code: String, amount: Double?, noDividend: Boolean, hasStatus: Boolean
) {
    when {
        noDividend -> this[code] = 0.0
        amount != null -> this[code] = amount
        hasStatus -> remove(code)
    }
}

/** Zero is the existing calculation placeholder; only noDividend confirms a zero dividend.
 * Never substitute historical or persisted DPS after the API explicitly withdrew it. */
internal fun resolveAnnualDividend(
    cached: Double?, noDividend: Boolean, hasStatus: Boolean, fallback: () -> Double
): Double = cached ?: if (noDividend || hasStatus) 0.0 else fallback()
