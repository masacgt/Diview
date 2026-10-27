package com.kurogane.dividend.model

import com.kurogane.dividend.data.CloudflareDividendEvent
import com.kurogane.dividend.data.CloudflareUpcomingDividendEvent
import com.kurogane.dividend.data.FinancialData
import com.kurogane.dividend.model.PortfolioMath.refreshPayout
import java.time.LocalDate
import java.time.ZoneId

/** The forecast used by both the Stock Detail top card and portfolio calculations. */
object AnnualDividendResolver {
    fun eventForecast(
        events: List<CloudflareDividendEvent>, upcoming: CloudflareUpcomingDividendEvent?,
        today: LocalDate = LocalDate.now(ZoneId.of("Asia/Tokyo"))
    ): Double? {
        if (upcoming == null) return null
        val observed = events.filter { !it.date.isAfter(today) && it.amountPerShare.isFinite() && it.amountPerShare >= 0.0 }
        val currentYearDps = observed.filter { it.date.year == today.year }.sumOf { it.amountPerShare }
        return when {
            upcoming.exDividendDate.year > today.year -> currentYearDps
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
        val issuerForecast = financial?.forecastAnnualDividend?.takeIf {
            financial.forecastFiscalYear?.let { year -> year >= today.year } == true
        }
        return issuerForecast?.takeIf { it.isFinite() && it >= 0.0 }
            ?: eventForecast(events, upcoming, today)?.takeIf { it.isFinite() && it >= 0.0 }
            ?: financial?.yahooForwardAnnualDividendRate?.takeIf { it.isFinite() && it >= 0.0 }
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
