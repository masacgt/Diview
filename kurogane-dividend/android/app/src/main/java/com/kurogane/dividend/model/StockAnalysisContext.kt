package com.kurogane.dividend.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A verified observation, never a current quote projected back onto a past period. */
data class MetricHistoryPoint(
    val periodEnd: LocalDate,
    val value: Double,
    val source: String,
    val basis: String = ANNUAL_ACTUAL,
    val periodPrecision: String = "day"
) {
    companion object {
        const val ANNUAL_ACTUAL = "annual_actual"
        const val YEAR_END = "fiscal_year_end"
        const val CALENDAR_ACTUAL = "calendar_actual"
        // periodEnd uses January 1 as a sorting anchor when only the issuer's fiscal-year label is known.
        const val ISSUER_YEAR = "issuer_year_actual"
    }
}

data class StockAnalysisContext(
    val history: Map<String, List<MetricHistoryPoint>> = emptyMap()
) {
    /** Stop at a missing year, changed source/basis, or changed fiscal year-end. */
    fun series(key: String): List<MetricHistoryPoint> {
        val points = history[key].orEmpty().filter { it.value.isFinite() }
            .groupBy { it.periodEnd }.mapNotNull { (_, rows) -> rows.singleOrNull() }
            .sortedByDescending { it.periodEnd }
        val result = mutableListOf<MetricHistoryPoint>()
        for (point in points) {
            val newer = result.lastOrNull()
            if (newer != null && (
                    newer.source != point.source || newer.basis != point.basis ||
                    newer.periodPrecision != point.periodPrecision ||
                    newer.periodEnd.month != point.periodEnd.month ||
                    ChronoUnit.DAYS.between(point.periodEnd, newer.periodEnd) !in 330L..400L
                )) break
            result += point
            if (result.size == 5) break
        }
        return result.reversed()
    }

    /** Each metric must have its latest observation in the same actual reporting period. */
    fun alignedLatest(vararg keys: String): Map<String, Double>? {
        val latest = keys.associateWith { series(it).lastOrNull() ?: return null }
        if (latest.values.map { it.periodEnd }.distinct().size != 1 ||
            latest.values.map { it.periodPrecision }.distinct().size != 1 ||
            latest.values.any { it.basis !in setOf(MetricHistoryPoint.ANNUAL_ACTUAL, MetricHistoryPoint.YEAR_END) }
        ) return null
        return latest.mapValues { it.value.value }
    }
}
