package com.kurogane.dividend.model

import com.kurogane.dividend.data.*
import com.kurogane.dividend.model.PortfolioMathTest.Companion.holding
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AnnualDividendResolverTest {
    private val today = LocalDate.of(2026, 10, 27)
    private val events = listOf(CloudflareDividendEvent(LocalDate.of(2026, 3, 1), 30.0))
    private val upcoming = CloudflareUpcomingDividendEvent(LocalDate.of(2026, 12, 1))

    @Test fun futureChartYearUsesFutureEventAndFiscalBoundary() {
        val event = CloudflareUpcomingDividendEvent(LocalDate.of(2027, 3, 30))
        assertEquals(2027, AnnualDividendResolver.futureEventChartYear(event, today, 3, true))
        assertEquals(2027, AnnualDividendResolver.futureEventChartYear(
            CloudflareUpcomingDividendEvent(LocalDate.of(2026, 12, 29)), today, 3, true))
        assertEquals(2026, AnnualDividendResolver.futureEventChartYear(
            CloudflareUpcomingDividendEvent(LocalDate.of(2026, 12, 29)), today, 12, true))
        assertEquals(2027, AnnualDividendResolver.futureEventChartYear(event, today, null, false))
        assertNull(AnnualDividendResolver.futureEventChartYear(event, today, null, true))
        assertNull(AnnualDividendResolver.futureEventChartYear(event, LocalDate.of(2027, 3, 30), 3, true))
        assertNull(AnnualDividendResolver.futureEventChartYear(event, LocalDate.of(2027, 3, 31), 3, true))
        assertNull(AnnualDividendResolver.futureEventChartYear(null, today, 3, true))
        assertEquals(2028, AnnualDividendResolver.futureEventChartYear(
            CloudflareUpcomingDividendEvent(LocalDate.of(2027, 9, 29)), LocalDate.of(2027, 3, 31), 3, true))
    }

    @Test fun jreitForecastMovesToNextEventYearAndCompletedEventsRemainActual() {
        val actual = listOf(CloudflareDividendEvent(LocalDate.of(2026, 2, 26), 3006.0),
            CloudflareDividendEvent(LocalDate.of(2026, 8, 28), 3012.0))
        val next = CloudflareUpcomingDividendEvent(LocalDate.of(2027, 2, 26))
        assertEquals(2027, AnnualDividendResolver.jreitNextEventYear(next, today))
        assertEquals(6018.0, AnnualDividendResolver.jreitCompletedCalendarEvents(2026, actual, next, today)!!, 0.0)
        assertNull(AnnualDividendResolver.jreitCompletedCalendarEvents(2026, actual, upcoming, today))
        assertNull(AnnualDividendResolver.jreitCompletedCalendarEvents(2026, actual, null, today))
        assertNull(AnnualDividendResolver.jreitNextEventYear(next, LocalDate.of(2027, 2, 26)))
        assertNull(AnnualDividendResolver.jreitCompletedCalendarEvents(2027, actual, next, today))
    }
    private fun financial(dps: Double? = 80.0, year: Int = 2027, yahoo: Double? = 90.0) =
        FinancialData("7203", "2026/03", StockFinancialProfile(), emptyList(), emptyMap(),
            forecastAnnualDividend = dps, forecastFiscalYear = year, yahooForwardAnnualDividendRate = yahoo,
            yahooDividendFetchedDate = today.minusDays(1), issuerIsKnownEquity = true)

    @Test fun decemberEquityChartReferenceRequiresMatchingYearFutureEventAndFreshYahoo() {
        val jt = financial(dps = null, year = 2026, yahoo = 272.0).copy(period = "2025/12")
        fun resolve(data: FinancialData = jt, event: CloudflareUpcomingDividendEvent? = upcoming, year: Int = 2026) =
            AnnualDividendResolver.equityAnnualChartReference(data, year, event, today)
        assertEquals(272.0, resolve()!!, 0.0)
        assertNull(resolve(jt.copy(period = "2025/03")))
        assertNull(resolve(jt.copy(issuerIsKnownEquity = false)))
        assertNull(resolve(jt.copy(forecastAnnualDividend = 250.0)))
        assertNull(resolve(jt.copy(yahooDividendCurrentStatus = "no_dividend")))
        assertNull(resolve(jt.copy(yahooDividendRateStatus = "failed")))
        assertNull(resolve(jt.copy(yahooDividendFetchedDate = today.minusDays(46))))
        assertNull(resolve(jt.copy(yahooDividendFetchedDate = today.plusDays(1))))
        assertNull(resolve(event = null))
        assertNull(resolve(event = CloudflareUpcomingDividendEvent(today)))
        assertNull(resolve(event = CloudflareUpcomingDividendEvent(LocalDate.of(2027, 3, 1))))
        assertNull(resolve(year = 2027))
        val aoki = financial(dps = null, year = 2027, yahoo = 120.0).copy(period = "2026/03")
        val next = CloudflareUpcomingDividendEvent(LocalDate.of(2027, 3, 30))
        assertEquals(120.0, AnnualDividendResolver.equityAnnualChartReference(aoki, 2027, next, today)!!, 0.0)
        assertNull(AnnualDividendResolver.equityAnnualChartReference(aoki, 2027, next, LocalDate.of(2027, 3, 30)))
    }

    @Test fun yearEndReferenceRequiresCompleteTwoEventPatternAndUsesAnnualRemainder() {
        val jt = financial(dps = null, year = 2026, yahoo = 272.0).copy(period = "2025/12")
        val actual = listOf(CloudflareDividendEvent(LocalDate.of(2025, 6, 27), 104.0),
            CloudflareDividendEvent(LocalDate.of(2025, 12, 29), 130.0),
            CloudflareDividendEvent(LocalDate.of(2026, 6, 29), 136.0))
        val end = CloudflareUpcomingDividendEvent(LocalDate.of(2026, 12, 29))
        fun infer(rows: List<CloudflareDividendEvent> = actual, next: CloudflareUpcomingDividendEvent? = end, total: Double? = 272.0) =
            AnnualDividendResolver.equityInterimAndYearEndReference(jt, 2026, total, rows, next, today)
        assertEquals(136.0 to 136.0, infer())
        assertNull(infer(rows = actual.drop(1)))
        assertNull(infer(next = null))
        assertNull(infer(next = CloudflareUpcomingDividendEvent(LocalDate.of(2026, 11, 29))))
        assertNull(infer(total = 100.0))
        assertNull(infer(total = null))
    }

    @Test fun fiscalChartUsesOnlyIssuerPeriodsAndKeepsZeroDistinctFromMissing() {
        val data = financial().copy(issuerDividends = listOf(
            IssuerFiscalYearDividend(2025, 95.0, 95.0, "2025-03"),
            IssuerFiscalYearDividend(2026, 0.0, 0.0, "2026-03"),
            IssuerFiscalYearDividend(2027, 20.0, 20.0, "2027-03"),
            IssuerFiscalYearDividend(2027, 30.0, 30.0, "2027-12")
        ))
        assertEquals(mapOf(2025 to 95.0, 2026 to 0.0), AnnualDividendResolver.issuerFiscalHistory(data))
        assertTrue(AnnualDividendResolver.issuerFiscalHistory(data.copy(issuerDividends = emptyList())).isEmpty())
        assertEquals(80.0, AnnualDividendResolver.issuerFiscalForecast(data, 2027, today)!!, 0.0)
        assertNull(AnnualDividendResolver.issuerFiscalForecast(data, 2026, today))
        assertNull(AnnualDividendResolver.issuerFiscalForecast(data.copy(forecastAnnualDividend = null), 2027, today))
        assertEquals(0.0, AnnualDividendResolver.issuerFiscalForecast(data.copy(forecastAnnualDividend = 0.0), 2027, today)!!, 0.0)
    }

    @Test fun reitCalendarFallbackRequiresKnownBasisAndDoesNotMixIssuerPeriods() {
        val data = financial().copy(dividendHistoryBasis = "calendar_year_observed_distributions",
            dividends = listOf(CalendarYearDividend(2025, 5628.0)))
        assertTrue(AnnualDividendResolver.useJreitCalendarHistory(true, data))
        assertFalse(AnnualDividendResolver.useJreitCalendarHistory(false, data))
        assertFalse(AnnualDividendResolver.useJreitCalendarHistory(true, data.copy(dividendHistoryBasis = "unknown")))
        assertFalse(AnnualDividendResolver.useJreitCalendarHistory(true, data.copy(dividends = emptyList())))
        assertFalse(AnnualDividendResolver.useJreitCalendarHistory(true, data.copy(issuerDividends = listOf(
            IssuerFiscalYearDividend(2025, 30.0, null, "2025-08")))))
    }

    @Test fun reitUsesTwoIssuerPeriodsAndNeverFallsBackToYahooEstimate() {
        val reit = financial(7110.0).copy(issuerIsInvestmentFund = true, issuerIsKnownEquity = false,
            forecastFirstPeriodEnd = LocalDate.of(2027, 2, 28), forecastPeriodEnd = LocalDate.of(2027, 8, 31),
            yahooForwardAnnualDividendRate = 6040.0)
        assertEquals(7110.0, AnnualDividendResolver.forecast(Category.JAPAN, reit, events, upcoming, today)!!, 0.0)
        assertNull(AnnualDividendResolver.forecast(Category.JAPAN, reit, emptyList(), null, LocalDate.of(2027, 2, 28)))
        assertNull(AnnualDividendResolver.forecast(Category.JAPAN, reit.copy(forecastAnnualDividend = null), events, upcoming, today))
    }

    @Test fun identifiedJreitCanUseRecentYahooEstimateWithoutTreatingItAsIssuerForecast() {
        val reit = financial(dps = null, yahoo = 6000.0).copy(
            issuerIsInvestmentFund = true, issuerIsKnownEquity = false,
            yahooDividendRateStatus = "available")
        assertEquals(6000.0, AnnualDividendResolver.forecast(Category.JAPAN, reit, emptyList(), null, today, isJreit = true)!!, 0.0)
        assertNull(AnnualDividendResolver.issuerFiscalForecast(reit, 2027, today))
        assertNull(AnnualDividendResolver.forecast(Category.JAPAN, reit, emptyList(), null, today))
        assertNull(AnnualDividendResolver.forecast(Category.JAPAN, reit.copy(yahooDividendRateStatus = "failed"), emptyList(), null, today, isJreit = true))
        assertNull(AnnualDividendResolver.forecast(Category.JAPAN, reit.copy(yahooDividendCurrentStatus = "no_dividend"), emptyList(), null, today, isJreit = true))
        assertNull(AnnualDividendResolver.forecast(Category.JAPAN, reit.copy(yahooDividendFetchedDate = today.minusDays(46)), emptyList(), null, today, isJreit = true))
        assertNull(AnnualDividendResolver.forecast(Category.JAPAN, reit.copy(yahooForwardAnnualDividendRate = 0.0), emptyList(), null, today, isJreit = true))
        assertNull(AnnualDividendResolver.forecast(Category.FUND, reit, emptyList(), null, today, isJreit = true))
        assertEquals(6000.0, AnnualDividendResolver.jreitAnnualChartReference(reit.copy(forecastAnnualDividend = 7000.0), today)!!, 0.0)
        assertNull(AnnualDividendResolver.jreitAnnualChartReference(reit.copy(yahooDividendFetchedDate = today.minusDays(46)), today))
        assertNull(AnnualDividendResolver.jreitAnnualChartReference(reit.copy(yahooDividendCurrentStatus = "no_dividend"), today))
    }

    @Test fun yahooForecastRequiresAnActualRecentFetchDate() {
        val data = financial(null)
        assertNull(AnnualDividendResolver.forecast(Category.JAPAN, data.copy(yahooDividendFetchedDate = null), emptyList(), null, today))
        assertNull(AnnualDividendResolver.forecast(Category.JAPAN, data.copy(issuerIsKnownEquity = false), events, upcoming, today))
        assertNull(AnnualDividendResolver.forecast(Category.JAPAN, data.copy(yahooForwardAnnualDividendRate = 0.0), emptyList(), null, today))
        assertNull(AnnualDividendResolver.forecast(Category.JAPAN, data.copy(yahooDividendFetchedDate = today.minusDays(46)), emptyList(), null, today))
        assertEquals(90.0, AnnualDividendResolver.forecast(Category.JAPAN, data, emptyList(), null, today)!!, 0.0)
    }

    @Test fun issuerForecastWinsOverEventsYahooAndOldNoDividendStatusForAnyCode() {
        for (code in listOf("8984", "7203", "1306", "AAPL")) {
            val forecast = AnnualDividendResolver.forecast(Category.JAPAN, financial(), events, upcoming, today)
            val result = AnnualDividendResolver.applyForecast(holding().copy(code = code, dps = 0.0,
                isNoDividendConfirmed = true, yfinanceYieldRate = 99.0), forecast)
            assertEquals(80.0, result.dps, 0.0)
            assertEquals(8000.0, result.annualDividend, 0.0)
            assertEquals(4.0, result.liveYield, 0.0)
            assertEquals(8.0, result.liveYoc, 0.0)
            assertFalse(result.isNoDividendConfirmed)
            assertEquals(holding().shares, result.shares)
            assertEquals(holding().accountType, result.accountType)
        }
    }

    @Test fun reitForecastOverridesStaleNoDividendYieldForBuySignal() {
        val forecast = AnnualDividendResolver.forecast(
            Category.JAPAN, financial(dps = 6018.0, year = 2026), events, upcoming, today
        )
        val result = AnnualDividendResolver.applyForecast(
            holding().copy(code = "8953", price = 106900.0, dps = 0.0,
                yieldRate = 0.0, isNoDividendConfirmed = true), forecast
        )
        assertEquals(5.62956, result.liveYield, 0.00001)
        assertFalse(result.isNoDividendConfirmed)
        assertEquals("5.63%", formatDividendYieldDisplay(result.liveYield, result.isNoDividendConfirmed))
    }

    @Test fun staleOrInvalidIssuerForecastUsesEventsBeforeYahoo() {
        for (data in listOf(financial(year = 2025), financial(Double.NaN), financial(-1.0))) {
            assertEquals(60.0, AnnualDividendResolver.forecast(Category.JAPAN, data, events, upcoming, today)!!, 0.0)
        }
        assertEquals(90.0, AnnualDividendResolver.forecast(Category.JAPAN, financial(null), events, null, today)!!, 0.0)
    }

    @Test fun zeroForecastIsAuthoritativeAndMissingForecastRemainsUnknown() {
        val zero = AnnualDividendResolver.forecast(Category.JAPAN, financial(0.0), events, upcoming, today)
        val result = AnnualDividendResolver.applyForecast(holding(), zero)
        assertTrue(result.isNoDividendConfirmed)
        assertEquals(0.0, result.annualDividend, 0.0)
        assertEquals(0.0, result.liveYield, 0.0)
        assertNull(AnnualDividendResolver.forecast(Category.JAPAN, null, emptyList(), null, today))
        assertEquals(holding(), AnnualDividendResolver.applyForecast(holding(), null))
    }

    @Test fun eventEstimateUsesJstYearAndIgnoresFutureObservations() {
        val future = events + CloudflareDividendEvent(today.plusDays(1), 100.0)
        assertEquals(60.0, AnnualDividendResolver.eventForecast(future, upcoming, today)!!, 0.0)
        assertNull(AnnualDividendResolver.eventForecast(events,
            upcoming.copy(exDividendDate = LocalDate.of(2027, 1, 1)), today))
        assertNull(AnnualDividendResolver.eventForecast(emptyList(), upcoming, today))
    }

    @Test fun fundKeepsItsDistributionAndTenThousandUnitBasis() {
        val fund = holding().copy(category = Category.FUND)
        assertNull(AnnualDividendResolver.forecast(Category.FUND, financial(), events, upcoming, today))
        assertEquals(fund, AnnualDividendResolver.applyForecast(fund, 80.0))
    }
}
