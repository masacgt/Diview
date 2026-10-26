package com.kurogane.dividend.data

import com.kurogane.dividend.model.MetricHistoryPoint
import com.kurogane.dividend.model.StockAnalysisContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Retains history from the very same response/cache document as the current metrics. */
internal object FinancialHistoryParser {
    private val unusableStates = setOf("failed", "empty", "unsupported", "not_fetched", "stale")

    fun parse(root: JSONObject, asOf: LocalDate = LocalDate.now(ZoneId.of("Asia/Tokyo"))): StockAnalysisContext {
        val result = mutableMapOf<String, MutableList<MetricHistoryPoint>>()
        val annual = rows(root.optJSONObject("financials")?.optJSONArray("annual"))
        val valuation = rows(root.optJSONObject("valuation")?.optJSONArray("history"))
        val issuer = rows(root.optJSONObject("issuer_dividends")?.optJSONArray("records"))

        fun period(row: JSONObject, siblings: List<JSONObject>): Pair<LocalDate, String>? {
            if (row.has("period_end") && !row.isNull("period_end") && row.optString("period_end").isNotBlank()) {
                return runCatching { LocalDate.parse(row.getString("period_end")) }.getOrNull()
                    ?.takeIf { !it.isAfter(asOf) }?.let { it to "day" }
            }
            val raw = listOf("fiscal_period", "period", "fiscal_year")
                .firstNotNullOfOrNull { key -> row.optString(key).replace('/', '-').takeIf { it.matches(Regex("\\d{4}-\\d{2}(?:-\\d{2})?")) } }
                ?: return null // A year alone cannot identify an issuer fiscal period.
            if (raw.length == 10) return runCatching { LocalDate.parse(raw) }.getOrNull()
                ?.takeIf { !it.isAfter(asOf) }?.let { it to "day" }
            val month = runCatching { YearMonth.parse(raw) }.getOrNull() ?: return null
            val knownDates = siblings.mapNotNull { row ->
                runCatching { LocalDate.parse(row.optString("period_end")) }.getOrNull()
            }.filter { YearMonth.from(it) == month }.distinct()
            if (knownDates.size > 1 || month.atEndOfMonth().isAfter(asOf)) return null
            return knownDates.singleOrNull()?.let { it to "day" } ?: (month.atEndOfMonth() to "month")
        }

        fun add(key: String, row: JSONObject, siblings: List<JSONObject>, field: String, value: Double?,
                basis: String = MetricHistoryPoint.ANNUAL_ACTUAL, defaultSource: String = "api",
                derivedSource: String? = null, allowIssuerYear: Boolean = false) {
            if (value == null || !value.isFinite() || row.optString("period_type", "annual") != "annual") return
            if (row.has("period_length_months") && row.optInt("period_length_months") != 12) return
            val year = row.optString("fiscal_year").takeIf { it.matches(Regex("\\d{4}")) }?.toInt()
            val yearOnly = allowIssuerYear && year != null && year in 1900..asOf.year &&
                listOf("period_end", "fiscal_period", "period").all { key ->
                    row.optString(key).let { it.isBlank() || it == "null" || it == year.toString() }
                }
            val date = period(row, siblings) ?: if (yearOnly) LocalDate.of(year!!, 1, 1) to "year" else return
            val source = derivedSource ?: source(row, field, defaultSource) ?: return
            result.getOrPut(key) { mutableListOf() } += MetricHistoryPoint(date.first, value, source,
                if (date.second == "year") MetricHistoryPoint.ISSUER_YEAR else basis, date.second)
        }

        for (row in annual) {
            add("equity_ratio", row, annual, "equity_ratio_computed", ratio(row, "equity_ratio_computed"))
            for ((key, fields) in mapOf("operating_margin" to listOf("operating_profit"),
                "fcf_margin" to listOf("operating_cf", "investing_cf"))) {
                val direct = ratio(row, key)
                val calculated = if (direct == null) calculatedRatio(row, fields, "sales") else null
                add(key, row, annual, key, direct ?: calculated?.first, derivedSource = calculated?.second)
            }
            add("payout_ratio", row, annual, "payout_ratio", ratio(row, "payout_ratio"))
        }
        for (row in valuation) {
            for (key in listOf("per", "pbr")) {
                val field = listOf(key, "${key}_adjusted").firstOrNull {
                    row.optJSONObject("field_units")?.optString(it) == "times" && row.number(it) != null
                } ?: continue
                add(key, row, valuation, field, row.number(field), MetricHistoryPoint.YEAR_END)
            }
            add("dividend_yield", row, valuation, "dividend_yield_adjusted", ratio(row, "dividend_yield_adjusted"), MetricHistoryPoint.YEAR_END)
            // Never create historical PER/PBR with today's share price and a past EPS/BPS.
        }
        val shareBasisSafe = root.optJSONObject("issuer_dividends")?.optString("share_basis_status") != "unverified_incomplete_corporate_action_history"
        for (row in issuer) {
            val year = row.optInt("fiscal_year", -1)
            val uniqueYear = issuer.count { it.optInt("fiscal_year", -2) == year } == 1 &&
                annual.count { it.optString("period_end", it.optString("fiscal_year")).take(4).toIntOrNull() == year } <= 1
            add("payout_ratio", row, issuer + annual, "payout_ratio", ratio(row, "payout_ratio"),
                defaultSource = "IRBANK", allowIssuerYear = uniqueYear)
            if (shareBasisSafe) {
                val field = if (row.number("annual_dps_adjusted") != null) "annual_dps_adjusted" else "annual_dps"
                val value = if (row.optJSONObject("field_units")?.optString(field) == "jpy_per_share") row.number(field) else null
                add("issuer_dps", row, issuer + annual, field, value?.takeIf { it >= 0 },
                    defaultSource = "IRBANK", allowIssuerYear = uniqueYear)
            }
        }

        val dividendPayload = root.optJSONObject("dividends")
        if (dividendPayload?.optString("history_basis") == "calendar_year_observed_distributions" &&
            dividendPayload.optString("share_basis_status") != "unverified_incomplete_corporate_action_history") {
            val dividends = rows(dividendPayload.optJSONArray("records")).mapNotNull { row ->
                val year = row.optInt("calendar_year", -1)
                if (year !in 1900 until asOf.year ||
                    row.optJSONObject("field_units")?.optString("calendar_year_distribution_per_share") != "jpy_per_share") return@mapNotNull null
                row.number("calendar_year_distribution_per_share")?.takeIf { it >= 0 }?.let { year to it }
            }.groupBy { it.first }.mapNotNull { (_, values) -> values.singleOrNull() }.sortedBy { it.first }
            var increaseYears = 0
            var nonCutYears = 0
            dividends.forEachIndexed { index, (year, value) ->
                val point = MetricHistoryPoint(LocalDate.of(year, 12, 31), value, "yfinance", MetricHistoryPoint.CALENDAR_ACTUAL)
                result.getOrPut("dividend_dps") { mutableListOf() } += point
                val prior = dividends.getOrNull(index - 1)
                if (prior == null || year != prior.first + 1) {
                    increaseYears = 0
                    nonCutYears = 0
                } else {
                    increaseYears = if (value > prior.second) increaseYears + 1 else 0
                    nonCutYears = if (value >= prior.second) nonCutYears + 1 else 0
                    result.getOrPut("dividend_growth_years") { mutableListOf() } += point.copy(value = increaseYears.toDouble())
                    result.getOrPut("non_cut_years") { mutableListOf() } += point.copy(value = nonCutYears.toDouble())
                }
            }
        }
        // Conflicting providers/values for one period must not silently win by array order.
        return StockAnalysisContext(result.mapValues { (_, points) ->
            points.distinct().groupBy { it.periodEnd }.mapNotNull { (_, periodPoints) ->
                periodPoints.singleOrNull()
            }.sortedBy { it.periodEnd }.takeLast(5)
        })
    }

    private fun rows(array: JSONArray?): List<JSONObject> =
        (0 until (array?.length() ?: 0)).mapNotNull { array?.optJSONObject(it) }

    private fun ratio(row: JSONObject, field: String): Double? {
        if (source(row, field) == null) return null
        val value = row.number(field) ?: return null
        return when (row.optJSONObject("field_units")?.optString(field)) {
            "percent", "percentage_points" -> value
            "ratio", "fraction" -> value * 100.0
            else -> null
        }
    }

    private fun source(row: JSONObject, field: String, fallback: String = "api"): String? {
        val state = row.optJSONObject("field_status")?.optString(field).orEmpty()
        if (state in unusableStates) return null
        val sources = row.optJSONObject("source_values")
        val calculation = sources?.optJSONObject("calculated")?.optJSONObject("values")?.optJSONObject(field)
        val inputs = calculation?.optJSONObject("input_sources")
        if (inputs != null) {
            val providers = inputs.keys().asSequence().map { inputs.optString(it) }.toSet()
            val units = calculation.optJSONObject("input_units")
            if (units != null && units.keys().asSequence().map { units.optString(it) }.toSet().size != 1) return null
            return providers.singleOrNull()?.takeIf { it.isNotBlank() }
        }
        for (provider in listOf("yfinance", "seed")) {
            val record = sources?.optJSONObject(provider)
            if (record?.optJSONObject("values")?.number(field) != null) {
                if (record.optJSONObject("field_status")?.optString(field) in unusableStates) return null
                return if (provider == "seed") record.optString("source", "seed") else provider
            }
        }
        return row.optString("source").takeIf { it.isNotBlank() }
            ?: if (state == "legacy_migrated") "seed" else fallback
    }

    private fun calculatedRatio(row: JSONObject, numeratorFields: List<String>, denominator: String): Pair<Double, String>? {
        val fields = numeratorFields + denominator
        val units = fields.map { row.optJSONObject("field_units")?.optString(it).orEmpty() }
        if (units.distinct().size != 1 || units.first() !in setOf("jpy", "million_jpy", "yfinance_native")) return null
        val sources = fields.map { source(row, it) ?: return null }
        if (sources.distinct().size != 1) return null
        val divisor = row.number(denominator)?.takeIf { it > 0 } ?: return null
        val total = numeratorFields.sumOf { row.number(it) ?: return null }
        return (total / divisor * 100.0).takeIf { it.isFinite() }?.let { it to sources.first() }
    }
}
