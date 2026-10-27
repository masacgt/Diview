package com.kurogane.dividend.model

import com.kurogane.dividend.data.CloudflareDividendEvent
import com.kurogane.dividend.data.CloudflareUpcomingDividendEvent
import com.kurogane.dividend.data.FinancialData
import com.kurogane.dividend.model.PortfolioMath.refreshPayout
import java.time.LocalDate
import java.time.ZoneId

/** The forecast used by both the Stock Detail top card and portfolio calculations. */
object AnnualDividendResolver {
    fun issuerForecast(financial: FinancialData?, today: LocalDate): Double? =
        financial?.forecastAnnualDividend?.takeIf {
            it.isFinite() && it >= 0.0 && financial.forecastFiscalYear?.let { year -> year >= today.year } == true &&
                (financial.forecastPeriodEnd == null || financial.forecastPeriodEnd.isAfter(today)) &&
                (!financial.issuerIsInvestmentFund || financial.forecastFirstPeriodEnd?.isAfter(today) == true)
        }

    fun eventForecast(
        events: List<CloudflareDividendEvent>, upcoming: CloudflareUpcomingDividendEvent?,
        today: LocalDate = LocalDate.now(ZoneId.of("Asia/Tokyo"))
    ): Double? {
        if (upcoming == null) return null
        val observed = events.filter { !it.date.isAfter(today) && it.amountPerShare.isFinite() && it.amountPerShare >= 0.0 }
        val currentYearDps = observed.filter { it.date.year == today.year }.sumOf { it.amountPerShare }
        return when {
            // Completed observations are historical distributions, not a forecast.
            upcoming.exDividendDate.year > today.year -> null
            upcoming.exDividendDate.year == today.year -> observed.maxByOrNull { it.date }
                ?.let { currentYearDps + it.amountPerShare }
            else -> null
        }
    }

    fun forecast(
        category: Category, financial: FinancialData?, events: List<CloudflareDividendEvent>,
        upcoming: CloudflareUpcomingDividendEvent?,
        today: LocalDate = LocalDate.now(ZoneId.of("Asia/Tokyo"))
    ): Double? {
        if (category == Category.FUND) return null
        val knownEquity = category == Category.US || financial?.issuerIsKnownEquity == true
        return issuerForecast(financial, today)
            ?: eventForecast(events, upcoming, today)?.takeIf { knownEquity && it.isFinite() && it >= 0.0 }
            ?: financial?.yahooForwardAnnualDividendRate?.takeIf {
                knownEquity && it.isFinite() && it > 0.0 &&
                    financial.yahooDividendFetchedDate?.let { fetched ->
                        !fetched.isAfter(today) && !fetched.isBefore(today.minusDays(45))
                    } == true
            }
    }

    fun applyForecast(holding: Holding, forecast: Double?): Holding {
        if (forecast == null || holding.category == Category.FUND) return holding
        return holding.copy(
            dps = forecast,
            yieldRate = PortfolioMath.yieldPercent(forecast, holding.price),
            yoc = PortfolioMath.yieldPercent(forecast, holding.acqPrice),
            isNoDividendConfirmed = forecast == 0.0,
            irbankYieldRate = null, irbankYieldDps = null, yfinanceYieldRate = null
        ).refreshPayout()
    }
}
