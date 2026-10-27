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
    private fun financial(dps: Double? = 80.0, year: Int = 2027, yahoo: Double? = 90.0) =
        FinancialData("7203", "2026/03", StockFinancialProfile(), emptyList(), emptyMap(),
            forecastAnnualDividend = dps, forecastFiscalYear = year, yahooForwardAnnualDividendRate = yahoo)

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
