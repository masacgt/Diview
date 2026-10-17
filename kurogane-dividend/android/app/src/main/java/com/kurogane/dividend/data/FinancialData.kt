package com.kurogane.dividend.data

import com.kurogane.dividend.model.StockFinancialProfile
import org.json.JSONObject
import java.util.Locale
import kotlin.math.pow

data class CalendarYearDividend(val year: Int, val dps: Double)

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
    val dividendHistoryBasis: String = "unknown"
) {
    companion object {
        fun parse(root: JSONObject, expectedCode: String): FinancialData {
            val code = root.getJSONObject("security").getString("code")
            require(code == expectedCode) { "財務情報の銘柄コードが一致しません" }
            val historyBasis = root.optJSONObject("dividends")?.optString("history_basis")
                ?.takeIf { it == "calendar_year_observed_distributions" } ?: "unknown"
            val dividendPayload = root.optJSONObject("dividends")
            val history = dividendPayload?.optJSONArray("records")
            val rows = (0 until (history?.length() ?: 0)).mapNotNull { history?.optJSONObject(it) }
                .filter { it.optInt("calendar_year") in 1900..2100 }
                .sortedBy { it.optInt("calendar_year") }
            val dividends = rows.mapNotNull { row ->
                row.number("calendar_year_distribution_per_share")
                    ?.takeIf { it >= 0 }
                ?.let { CalendarYearDividend(row.getInt("calendar_year"), it) } }
                .associateBy { it.year }.values.sortedBy { it.year }
            val annual = root.optJSONObject("financials")?.optJSONArray("annual")
            val financial = (0 until (annual?.length() ?: 0)).mapNotNull { annual?.optJSONObject(it) }
                .filter { it.optString("fiscal_year").matches(Regex("\\d{4}/\\d{2}")) }
                .maxByOrNull { it.getString("fiscal_year") }
            val period = financial?.optString("fiscal_year").orEmpty()
            val valuationRows = root.optJSONObject("valuation")?.optJSONArray("history")
            val valuation = (0 until (valuationRows?.length() ?: 0)).mapNotNull { valuationRows?.optJSONObject(it) }
                .filter { it.optString("fiscal_year") == period }.firstOrNull()
            val dividend = rows.lastOrNull { it.optInt("calendar_year").toString() == period.take(4) }
            fun percent(numerator: Double?, denominator: Double?): Double? =
                if (numerator != null && denominator != null && denominator > 0) numerator / denominator * 100 else null
            val sales = financial?.number("sales")
            val operating = percent(financial?.number("operating_profit"), sales)
            val cf = financial?.number("operating_cf")?.let { a -> financial.number("investing_cf")?.let { a + it } }
            val fcfMargin = percent(cf, sales)
            val equity = financial?.number("equity_ratio_computed")
            val payout = dividend?.number("payout_ratio") ?: root.optJSONObject("dividends")?.number("payout_ratio")
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
            val ranges = (0 until (valuationRows?.length() ?: 0)).mapNotNull { valuationRows?.optJSONObject(it) }
                .filter { it.optString("fiscal_year").take(4).toIntOrNull()?.let { year ->
                    period.take(4).toIntOrNull()?.let { year in (it - 4)..it } } == true }
                .mapNotNull { it.number("dividend_yield_adjusted") }
            val calendarYearEvents = historyBasis == "calendar_year_observed_distributions"
            val profile = StockFinancialProfile(
                payoutRatio = payout ?: Double.NaN, equityRatio = equity ?: Double.NaN,
                consecutiveYears = dividendPayload?.number("consecutive_dividend_increase_years")?.toInt()
                    ?: if (calendarYearEvents) -1 else dividend?.number("consecutive_dividend_increase_years")?.toInt() ?: -1,
                noCutYears = dividendPayload?.number("no_cut_years")?.toInt()
                    ?: if (!calendarYearEvents && dividends.size >= 2) noCut else -1,
                growthRate = growth ?: Double.NaN,
                eps = valuation?.number("eps_adjusted") ?: Double.NaN,
                bps = valuation?.number("bps_adjusted") ?: Double.NaN,
                operatingMargin = operating ?: Double.NaN, fcfMargin = fcfMargin,
                fcfStatus = when { cf == null -> "未取得"; cf >= 0 -> "フリーCF黒字"; else -> "フリーCF赤字" },
                doePolicy = dividend?.number("doe")?.let { String.format(Locale.JAPAN, "%.2f%%", it) } ?: "未取得",
                doePolicySub = "実績DOE（会社の配当方針とは異なります）",
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
                "dividend_growth_years" to (dividendPayload?.number("consecutive_dividend_increase_years")
                    ?: dividend?.number("consecutive_dividend_increase_years"))
            ), historyBasis)
        }
    }
}

internal fun JSONObject.number(key: String): Double? =
    if (!has(key) || isNull(key)) null else optDouble(key).takeIf(Double::isFinite)

fun Double.financialText(digits: Int = 1, suffix: String = ""): String =
    if (isFinite()) String.format(Locale.JAPAN, "%.${digits}f", this) + suffix else "未取得"

fun Int.financialYears(): String = if (this >= 0) "${this}年" else "未取得"
