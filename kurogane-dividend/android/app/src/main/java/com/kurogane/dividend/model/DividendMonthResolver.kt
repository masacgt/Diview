package com.kurogane.dividend.model

import com.kurogane.dividend.data.CloudflareDividendEvent
import java.time.LocalDate

/** Month-only data cannot supply an event date or amount. Past events may supply indicative months. */
internal object DividendMonthResolver {
    fun resolve(months: List<Int>, events: List<CloudflareDividendEvent>, today: LocalDate): List<Int> {
        val validMonths = months.filter { it in 1..12 }.distinct().sorted()
        if (validMonths.isNotEmpty()) return validMonths
        val observed = events.filter { !it.date.isAfter(today) && it.amountPerShare > 0.0 && it.amountPerShare.isFinite() }
        val latest = observed.maxOfOrNull { it.date } ?: return emptyList()
        val cutoff = latest.minusDays(365)
        return observed.filter { !it.date.isBefore(cutoff) }.map { it.date.monthValue }.distinct().sorted()
    }

    fun monthsFromResponse(
        cachedMonths: List<Int>, responseMonths: List<Int>, requestedYears: List<Int>, today: LocalDate
    ): List<Int> {
        val validCached = cachedMonths.filter { it in 1..12 }.distinct().sorted()
        val validResponse = responseMonths.filter { it in 1..12 }.distinct().sorted()
        val recentStartYear = today.minusDays(365).year
        val coversRecentWindow = requestedYears.isEmpty() ||
            (recentStartYear..today.year).all { it in requestedYears }
        return when {
            coversRecentWindow -> validResponse
            validCached.isNotEmpty() -> validCached
            today.year in requestedYears -> validResponse
            else -> emptyList()
        }
    }
}
