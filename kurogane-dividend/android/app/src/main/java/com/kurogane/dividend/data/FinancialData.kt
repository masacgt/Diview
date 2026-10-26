package com.kurogane.dividend.data

import com.kurogane.dividend.model.StockFinancialProfile
import com.kurogane.dividend.model.StockAnalysisContext
import org.json.JSONObject
import java.util.Locale
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.pow

data class CalendarYearDividend(val year: Int, val dps: Double)

data class IssuerFiscalYearDividend(
    val fiscalYear: Int, val reportedDps: Double?, val adjustedDps: Double?,
    val fiscalPeriod: String = fiscalYear.toString()
) {
    val displayDps: Double? get() = adjustedDps ?: reportedDps
}

data class DividendHistoryOverride(
    val year: Int,
    val stockPrice: Double?,
    val annualDividend: Double?,
    val hasStockPriceOverride: Boolean,
    val hasAnnualDividendOverride: Boolean
)

data class FinancialData(
    val code: String,
    val period: String,
    val profile: StockFinancialProfile,
    val dividends: List<CalendarYearDividend>,
    val values: Map<String, Double?>,
    val dividendHistoryBasis: String = "unknown",
    val issuerDividends: List<IssuerFiscalYearDividend> = emptyList(),
    val issuerDividendHistoryStatus: String = "empty",
    val forecastAnnualDividend: Double? = null,
    val forecastEps: Double? = null,
    val forecastFiscalYear: Int? = null,
    val forecastPayoutRatio: Double? = null,
    val dividendShareBasisStatus: String = "unknown",
    val issuerDividendShareBasisStatus: String = "unknown",
    val yahooForwardAnnualDividendRate: Double? = null,
    val analysisContext: StockAnalysisContext = StockAnalysisContext()
) {
    companion object {
        fun parse(root: JSONObject, expectedCode: String): FinancialData {
            val code = root.getJSONObject("security").getString("code")
            require(code == expectedCode) { "財務情報の銘柄コードが一致しません" }
            val historyBasis = root.optJSONObject("dividends")?.optString("history_basis")
                ?.takeIf { it == "calendar_year_observed_distributions" } ?: "unknown"
            val calendarYearEvents = historyBasis == "calendar_year_observed_distributions"
            val dividendPayload = root.optJSONObject("dividends")
            val issuerDividendPayload = root.optJSONObject("issuer_dividends")
            val forecastSource = issuerDividendPayload?.optJSONObject("forecast")
            val marketPayload = root.optJSONObject("market")
            val marketSummary = marketPayload?.optJSONObject("summary")
            val yahooForwardAnnualDividendRate = if (marketPayload?.optJSONObject("summary_field_units")
                    ?.optString("dividendRate") == "jpy_per_share") {
                marketSummary?.number("dividendRate")?.takeIf { it >= 0 }
            } else null
            val dividendShareBasisStatus = dividendPayload?.optString("share_basis_status")
                ?.takeIf { it.isNotBlank() } ?: "unknown"
            val forecastAnnualDividend = forecastSource
                ?.numberWithUnit("annual_dps_current_share_basis", "jpy_per_share")
                ?.takeIf { it >= 0 }
            val forecastEps = if (marketPayload?.optJSONObject("summary_field_units")
                    ?.optString("forwardEps") == "jpy_per_share") {
                marketSummary?.number("forwardEps")?.takeIf { it > 0 }
            } else null
            val forecastPayoutRatio = forecastAnnualDividend?.let { dps ->
                forecastEps?.let { eps -> dps / eps * 100.0 }
            }
            val history = dividendPayload?.optJSONArray("records")
            val rows = (0 until (history?.length() ?: 0)).mapNotNull { history?.optJSONObject(it) }
                .filter { it.optInt("calendar_year") in 1900..2100 }
                .sortedBy { it.optInt("calendar_year") }
            val dividends = rows.mapNotNull { row ->
                row.numberWithUnit("calendar_year_distribution_per_share", "jpy_per_share")
                    ?.takeIf { it >= 0 }
                ?.let { CalendarYearDividend(row.getInt("calendar_year"), it) } }
                .associateBy { it.year }.values.sortedBy { it.year }
            val issuerDividendShareBasisStatus = issuerDividendPayload?.optString("share_basis_status")
                ?.takeIf { it.isNotBlank() } ?: "unknown"
            val issuerDividendRecords = issuerDividendPayload?.optJSONArray("records")
            val issuerDividends = (0 until (issuerDividendRecords?.length() ?: 0))
                .mapNotNull { issuerDividendRecords?.optJSONObject(it) }
                .mapNotNull { row ->
                    val fiscalYear = row.optInt("fiscal_year", -1)
                    if (fiscalYear !in 1900..2100) return@mapNotNull null
                    val reportedDps = row.numberWithUnit("annual_dps", "jpy_per_share")?.takeIf { it >= 0 }
                    val adjustedDps = row.numberWithUnit("annual_dps_adjusted", "jpy_per_share")?.takeIf { it >= 0 }
                    if (reportedDps == null && adjustedDps == null) return@mapNotNull null
                    val periodKey = listOf("period_end", "fiscal_period", "period", "fiscal_year")
                        .firstNotNullOfOrNull { key -> row.optString(key).takeIf { it.isNotBlank() && it != "null" } }
                        ?.replace('/', '-') ?: fiscalYear.toString()
                    IssuerFiscalYearDividend(fiscalYear, reportedDps, adjustedDps, periodKey)
                }
                .associateBy { it.fiscalPeriod }.values.sortedBy { it.fiscalPeriod }
            val issuerDividendHistoryStatus = issuerDividendPayload?.optString("status")
                ?.takeIf { it.isNotBlank() } ?: if (issuerDividends.isNotEmpty()) "available" else "empty"
            val annual = root.optJSONObject("financials")?.optJSONArray("annual")
            val annualRows = (0 until (annual?.length() ?: 0)).mapNotNull { annual?.optJSONObject(it) }
            val financial = annualRows.filter { it.periodOrder() != null }
                .maxWithOrNull(compareBy<JSONObject> { it.periodOrder()!! }
                    .thenBy { it.periodEnd() != null })
            val period = financial?.periodMonth().orEmpty()
            val reportedForecastFiscalYear = forecastSource?.optInt("fiscal_year", -1)?.takeIf { it in 1900..2100 }
            val forecastFiscalYear = reportedForecastFiscalYear ?: period.take(4).toIntOrNull()?.plus(1)
                ?.takeIf { it in 1900..2100 }
            val doeStatus = financial?.optJSONObject("field_status")?.optString("calculated_doe").orEmpty()
            val calculatedDoe = financial?.numberWithUnit("calculated_doe", "percent")
            val netDeRatio = financial?.numberWithUnit("net_de_ratio", "times")
            val valuationRows = root.optJSONObject("valuation")?.optJSONArray("history")
            val valuations = (0 until (valuationRows?.length() ?: 0)).mapNotNull { valuationRows?.optJSONObject(it) }
            val end = financial?.periodEnd()
            val monthRows = (annualRows + valuations).filter { it.periodMonth() == period }
            val unambiguousMonth = monthRows.mapNotNull { it.periodEnd() }.distinct().size <= 1
            val valuation = if (financial == null) null else {
                valuations.firstOrNull { end != null && it.periodEnd() == end }
                    ?: valuations.filter {
                        period.isNotBlank() && it.periodMonth() == period && unambiguousMonth &&
                            (financial.hasNoPeriodEnd() || it.hasNoPeriodEnd()) &&
                            (it.hasNoPeriodEnd() || it.periodEnd() != null) &&
                            (financial.hasNoPeriodEnd() || end != null)
                    }.singleOrNull()
            }
            val dividend = rows.lastOrNull { it.optInt("calendar_year").toString() == period.take(4) }
            fun percent(numerator: Double?, denominator: Double?): Double? =
                if (numerator != null && denominator != null && denominator > 0) numerator / denominator * 100 else null
            val sales = financial?.numberWithUnit("sales", "jpy")
            val operatingProfit = financial?.numberWithUnit("operating_profit", "jpy")
            val operating = financial?.numberWithUnit("operating_margin", "percent")
                ?: percent(operatingProfit, sales)
            val operatingCf = financial?.numberWithUnit("operating_cf", "jpy")
            val investingCf = financial?.numberWithUnit("investing_cf", "jpy")
            val cf = if (operatingCf != null && investingCf != null) operatingCf + investingCf else null
            val fcfMargin = financial?.numberWithUnit("fcf_margin", "percent") ?: percent(cf, sales)
            val equity = financial?.numberWithUnit("equity_ratio_computed", "percent")
            // The unit-aware normalizer below also handles legacy annual-row and Yahoo payload defaults.
            val payout = dividend?.let { row ->
                normalizePayoutRatio(
                    row.number("payout_ratio"),
                    row.optJSONObject("field_units")?.optString("payout_ratio"),
                    defaultUnit = "percent"
                )
            } ?: dividendPayload?.let { payload ->
                normalizePayoutRatio(
                    payload.number("payout_ratio"),
                    payload.optJSONObject("field_units")?.optString("payout_ratio"),
                    defaultUnit = "ratio"
                )
            }
            var noCut = 0
            for (i in dividends.lastIndex downTo 1) {
                if (dividends[i].year != dividends[i - 1].year + 1 || dividends[i].dps < dividends[i - 1].dps) break
                noCut++
            }
            val recent = mutableListOf<CalendarYearDividend>()
            for (row in dividends.asReversed()) {
                val previous = recent.lastOrNull()
                if (previous != null && previous.year != row.year + 1) break
                recent += row
                if (recent.size == 5) break
            }
            recent.reverse()
            val growth = if (recent.size >= 2 && recent.first().dps > 0)
                ((recent.last().dps / recent.first().dps).pow(1.0 / (recent.size - 1)) - 1) * 100 else null
            val ranges = valuations
                .filter { it.periodMonth().take(4).toIntOrNull()?.let { year ->
                    period.take(4).toIntOrNull()?.let { year in (it - 4)..it } } == true }
                .mapNotNull { it.numberWithUnit("dividend_yield_adjusted", "percent") }
            val profile = StockFinancialProfile(
                payoutRatio = payout ?: Double.NaN, equityRatio = equity ?: Double.NaN,
                consecutiveYears = dividendPayload?.number("consecutive_dividend_increase_years")?.toInt()
                    ?: if (calendarYearEvents) -1 else dividend?.number("consecutive_dividend_increase_years")?.toInt() ?: -1,
                noCutYears = dividendPayload?.number("no_cut_years")?.toInt()
                    ?: if (!calendarYearEvents && dividends.size >= 2) noCut else -1,
                growthRate = growth ?: Double.NaN,
                eps = valuation?.numberWithUnit("eps_adjusted", "jpy_per_share") ?: Double.NaN,
                bps = valuation?.numberWithUnit("bps_adjusted", "jpy_per_share") ?: Double.NaN,
                operatingMargin = operating ?: Double.NaN, fcfMargin = fcfMargin,
                fcfStatus = when {
                    cf != null && cf >= 0 -> "フリーCF黒字"
                    cf != null -> "フリーCF赤字"
                    fcfMargin == null -> "未取得"
                    fcfMargin >= 0 -> "フリーCF黒字"
                    else -> "フリーCF赤字"
                },
                calculatedDoe = calculatedDoe?.let { String.format(Locale.JAPAN, "%.2f%%", it) } ?: "未取得",
                netDeRatio = netDeRatio?.let { String.format(Locale.JAPAN, "%.2f倍", it) } ?: "未取得",
                calculatedDoeSub = when {
                    calculatedDoe != null && doeStatus == "stale" -> "$period 更新確認中"
                    calculatedDoe != null -> "$period 計算値"
                    doeStatus == "failed" -> "財務データの取得失敗"
                    else -> "配当総額または期末株主資本が未取得"
                },
                yieldRangeMin = ranges.minOrNull() ?: Double.NaN,
                yieldRangeAvg = ranges.takeIf { it.isNotEmpty() }?.average() ?: Double.NaN,
                yieldRangeMax = ranges.maxOrNull() ?: Double.NaN,
                yieldRangeBadge = "${ranges.size}期の期末実績",
                payoutStatusBadge = when { payout == null -> "未取得"; payout > 100 -> "利益を超える配当"; payout < 0 -> "赤字決算"; else -> "実績配当性向" },
                equityRatioSub = "${period.ifBlank { "決算期未取得" }} 実績",
                healthDiagnosisComment = "取得できた決算実績を表示しています。健全性の評価はAPI指標の診断文章を確認してください。"
            )
            return FinancialData(code, period, profile, dividends, mapOf(
                "payout_ratio" to payout, "equity_ratio" to equity, "operating_margin" to operating,
                "fcf_margin" to fcfMargin,
                "net_de_ratio" to netDeRatio,
                "dividend_growth_years" to (dividendPayload?.number("consecutive_dividend_increase_years")
                    ?: dividend?.number("consecutive_dividend_increase_years"))
            ), historyBasis, issuerDividends, issuerDividendHistoryStatus,
                forecastAnnualDividend, forecastEps, forecastFiscalYear, forecastPayoutRatio,
                dividendShareBasisStatus, issuerDividendShareBasisStatus, yahooForwardAnnualDividendRate, FinancialHistoryParser.parse(root))
        }
    }
}

internal fun JSONObject.number(key: String): Double? =
    if (!has(key) || isNull(key)) null else optDouble(key).takeIf(Double::isFinite)

private fun normalizePayoutRatio(value: Double?, unit: String?, defaultUnit: String): Double? {
    if (value == null) return null
    return when (unit?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() && it != "null" } ?: defaultUnit) {
        "percent", "percentage", "percentage_points", "percent_points" -> value
        "ratio", "fraction" -> value * 100.0
        else -> null
    }
}

private fun JSONObject.numberWithUnit(key: String, expectedUnit: String): Double? =
    if (optJSONObject("field_units")?.optString(key) == expectedUnit) number(key) else null

private fun JSONObject.hasNoPeriodEnd(): Boolean =
    !has("period_end") || isNull("period_end") || optString("period_end").isBlank()

private fun JSONObject.periodEnd(): LocalDate? =
    if (hasNoPeriodEnd()) null else runCatching { LocalDate.parse(getString("period_end")) }.getOrNull()

private fun JSONObject.periodMonth(): String =
    periodEnd()?.toString()?.take(7)?.replace('-', '/')
        ?: optString("fiscal_year").takeIf {
            it.matches(Regex("\\d{4}/\\d{2}")) &&
                runCatching { YearMonth.parse(it.replace('/', '-')) }.isSuccess
        }.orEmpty()

private fun JSONObject.periodOrder(): LocalDate? =
    periodEnd() ?: if (hasNoPeriodEnd()) {
        // An undated legacy month must not outrank a known day within that month.
        runCatching { YearMonth.parse(periodMonth().replace('/', '-')).atDay(1) }.getOrNull()
    } else null

fun Double.financialText(digits: Int = 1, suffix: String = ""): String =
    if (isFinite()) String.format(Locale.JAPAN, "%.${digits}f", this) + suffix else "未取得"

fun Double.financialSignedText(digits: Int = 1, suffix: String = ""): String =
    if (isFinite()) (if (this > 0) "+" else "") + financialText(digits, suffix) else "未取得"

fun Int.financialYears(): String = if (this >= 0) "${this}年" else "未取得"
