package com.kurogane.dividend.model

import com.kurogane.dividend.data.CloudflareDividendEvent
import com.kurogane.dividend.data.CloudflareUpcomingDividendEvent
import com.kurogane.dividend.data.FinancialData
import com.kurogane.dividend.model.PortfolioMath.refreshPayout
import java.time.LocalDate
import java.time.ZoneId

/** The forecast used by both the Stock Detail top card and portfolio calculations. */
object AnnualDividendResolver {
    /** A J-REIT calendar history is only used as a whole-series fallback. */
    fun useJreitCalendarHistory(isJreit: Boolean, financial: FinancialData?): Boolean =
        isJreit && financial?.dividendHistoryBasis == "calendar_year_observed_distributions" &&
            financial.issuerDividends.isEmpty() && financial.dividends.isNotEmpty()

    /** Yahoo annual reference only; it does not identify an issuer fiscal period. */
    fun jreitAnnualChartReference(financial: FinancialData?, today: LocalDate): Double? =
        financial?.let { forecast(Category.JAPAN, it.copy(forecastAnnualDividend = null), emptyList(), null, today, isJreit = true) }

    /** Determine the annual chart period from a future ex-dividend event. */
    fun futureEventChartYear(
        upcoming: CloudflareUpcomingDividendEvent?, today: LocalDate, fiscalMonth: Int?, fiscalChart: Boolean
    ): Int? {
        val event = upcoming?.exDividendDate?.takeIf { it.isAfter(today) } ?: return null
        if (!fiscalChart) return event.year
        val month = fiscalMonth?.takeIf { it in 1..12 } ?: return null
        return event.year + if (event.monthValue > month) 1 else 0
    }

    /** Annual Yahoo reference for a known fiscal year with a matching future event. */
    fun equityAnnualChartReference(
        financial: FinancialData?, fiscalYear: Int, upcoming: CloudflareUpcomingDividendEvent?, today: LocalDate
    ): Double? {
        val fiscalMonth = financial?.period?.substringAfterLast('/')?.toIntOrNull()?.takeIf { it in 1..12 }
        if (financial?.issuerIsKnownEquity != true || financial.issuerIsInvestmentFund ||
            fiscalMonth == null || financial.period != "%04d/%02d".format(fiscalYear - 1, fiscalMonth) ||
            financial.forecastFiscalYear != fiscalYear ||
            futureEventChartYear(upcoming, today, fiscalMonth, true) != fiscalYear ||
            !java.time.YearMonth.of(fiscalYear, fiscalMonth).atEndOfMonth().isAfter(today) ||
            financial.forecastAnnualDividend != null || financial.yahooDividendRateStatus == "failed" ||
            financial.yahooDividendCurrentStatus == "no_dividend") return null
        return financial.yahooForwardAnnualDividendRate?.takeIf { rate ->
            rate.isFinite() && rate > 0.0 && financial.yahooDividendFetchedDate?.let {
                !it.isAfter(today) && !it.isBefore(today.minusDays(45))
            } == true
        }
    }

    /** Infer a remaining year-end amount only when the previous year had the same two-event pattern. */
    fun equityInterimAndYearEndReference(
        financial: FinancialData?, year: Int, annualForecast: Double?, events: List<CloudflareDividendEvent>,
        upcoming: CloudflareUpcomingDividendEvent?, today: LocalDate
    ): Pair<Double, Double>? {
        if (financial?.issuerIsKnownEquity != true || financial.issuerIsInvestmentFund ||
            financial.period != "${year - 1}/12" || year != today.year ||
            upcoming?.exDividendDate?.let { it.isAfter(today) && it.year == year && it.monthValue == 12 } != true ||
            annualForecast == null || !annualForecast.isFinite() || annualForecast <= 0.0) return null
        val previous = events.filter { it.date.year == year - 1 && it.amountPerShare.isFinite() && it.amountPerShare > 0.0 }
        val current = events.filter { it.date.year == year && !it.date.isAfter(today) && it.amountPerShare.isFinite() && it.amountPerShare > 0.0 }
        if (previous.size != 2 || current.size != 1 || previous.map { it.date.monthValue }.toSet() != setOf(current.single().date.monthValue, 12) ||
            current.single().date.monthValue >= 12 || current.single().amountPerShare > annualForecast) return null
        val interim = current.single().amountPerShare
        return interim to (annualForecast - interim)
    }

    fun jreitNextEventYear(upcoming: CloudflareUpcomingDividendEvent?, today: LocalDate): Int? =
        upcoming?.exDividendDate?.takeIf { it.isAfter(today) }?.year

    fun jreitCompletedCalendarEvents(
        year: Int, events: List<CloudflareDividendEvent>, upcoming: CloudflareUpcomingDividendEvent?, today: LocalDate
    ): Double? {
        if (jreitNextEventYear(upcoming, today)?.let { it > year } != true || year > today.year) return null
        val actual = events.filter { it.date.year == year && !it.date.isAfter(today) && it.amountPerShare.isFinite() && it.amountPerShare > 0.0 }
        return actual.takeIf { it.isNotEmpty() }?.sumOf { it.amountPerShare }
    }

    /** Annual fiscal-period chart values must not incorporate calendar-year events. */
    fun issuerFiscalHistory(financial: FinancialData?): Map<Int, Double> =
        financial?.noDividendFiscalYears.orEmpty().associateWith { 0.0 } + financial?.issuerDividends.orEmpty().groupBy { it.fiscalYear }.mapNotNull { (year, periods) ->
            periods.singleOrNull()?.displayDps?.let { year to it }
        }.toMap()

    fun issuerFiscalForecast(financial: FinancialData?, fiscalYear: Int, today: LocalDate): Double? =
        financial?.takeIf { it.forecastFiscalYear == fiscalYear }?.let { issuerForecast(it, today) }

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
        if (upcoming == null || !upcoming.exDividendDate.isAfter(today)) return null
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
        today: LocalDate = LocalDate.now(ZoneId.of("Asia/Tokyo")),
        isJreit: Boolean = false
    ): Double? {
        if (category == Category.FUND) return null
        val knownEquity = category == Category.US || financial?.issuerIsKnownEquity == true
        return issuerForecast(financial, today)
            ?: eventForecast(events, upcoming, today)?.takeIf { knownEquity && it.isFinite() && it >= 0.0 }
            ?: financial?.yahooForwardAnnualDividendRate?.takeIf {
                (knownEquity || (category == Category.JAPAN && isJreit && financial.yahooDividendRateStatus != "failed" &&
                    financial.yahooDividendCurrentStatus != "no_dividend")) && it.isFinite() && it > 0.0 &&
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
