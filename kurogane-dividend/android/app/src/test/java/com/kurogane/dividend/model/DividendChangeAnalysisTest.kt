package com.kurogane.dividend.model

import com.kurogane.dividend.data.FinancialData
import com.kurogane.dividend.data.CalendarYearDividend
import org.junit.Assert.*
import org.junit.Test

class DividendChangeAnalysisTest {
    @Test fun combinesAccountsAndAppliesTheirIndividualTaxTreatment() {
        val holdings = listOf(holding("a", shares = 100, free = 100), holding("b", shares = 200, free = 0))
        val data = mapOf("8316" to financial(listOf(CalendarYearDividend(2024, 100.0), CalendarYearDividend(2025, 120.0))))
        val gross = DividendChangeAnalysis.calculate(holdings, data, "gross")
        val net = DividendChangeAnalysis.calculate(holdings, data, "net")
        assertEquals(1, gross.securities.size)
        assertEquals(300L, gross.securities.single().shares)
        assertEquals(6000.0, gross.impact!!, 0.001)
        assertEquals(20.0, gross.growthRate!!, 0.001)
        assertEquals(2000.0 + DividendTax.net(4000.0, 0.0, false), net.impact!!, 0.001)
    }

    @Test fun excludesMissingAndNonConsecutiveYearsFromChangeCounts() {
        val data = mapOf("8316" to financial(listOf(CalendarYearDividend(2023, 100.0), CalendarYearDividend(2025, 150.0))))
        val result = DividendChangeAnalysis.calculate(listOf(holding("a"), holding("b", code = "7203")), data, "gross")
        assertEquals(2, result.securities.size)
        assertTrue(result.compared.isEmpty())
        assertNull(result.impact)
        assertTrue(result.securities.all { it.change == DividendChangeStatus.UNKNOWN })
    }

    @Test fun treatsDividendSuspensionAndResumptionAsChanges() {
        val data = mapOf(
            "8316" to financial(listOf(CalendarYearDividend(2024, 100.0), CalendarYearDividend(2025, 0.0))),
            "7203" to financial(listOf(CalendarYearDividend(2024, 0.0), CalendarYearDividend(2025, 30.0)))
        )
        val result = DividendChangeAnalysis.calculate(listOf(holding("a"), holding("b", code = "7203")), data, "gross")
        assertEquals(DividendChangeStatus.DECREASE, result.securities[0].change)
        assertEquals(DividendChangeStatus.INCREASE, result.securities[1].change)
        assertNull(result.securities[1].growthRate)
        assertEquals(-7000.0, result.impact!!, 0.001)
    }

    @Test fun payoutCoverageSeparatesMissingDataAndIncludesLossMakingCompanies() {
        val result = DividendChangeAnalysis.calculate(listOf(
            holding("a", profile = StockFinancialProfile(payoutRatio = 40.0)),
            holding("b", code = "7203", profile = StockFinancialProfile(payoutRatio = -20.0)),
            holding("c", code = "9433", profile = StockFinancialProfile())
        ), emptyMap(), "gross")
        assertEquals(2, result.payoutCovered.size)
        assertEquals(1, result.highPayout.size)
        assertEquals(1, result.monitoring.size)
        assertEquals(50, result.headroomScore)
        assertEquals(50.0, result.highPayoutDividendShare!!, 0.001)
    }

    @Test fun ignoresFundsForeignStocksAndClosedPositions() {
        val result = DividendChangeAnalysis.calculate(listOf(
            holding("a"), holding("b", category = Category.US), holding("c", category = Category.FUND), holding("d", shares = 0)
        ), emptyMap(), "gross")
        assertEquals(1, result.securities.size)
    }

    private fun financial(dividends: List<CalendarYearDividend>) = FinancialData(
        code = "8316", period = "2025/03", profile = StockFinancialProfile(), dividends = dividends, values = emptyMap()
    )

    private fun holding(
        id: String, code: String = "8316", shares: Int = 100, free: Int = 0,
        profile: StockFinancialProfile? = null, category: Category = Category.JAPAN
    ) = Holding(
        id = id, code = code, name = "銘柄$code", sector = "銀行業", market = "東証PRM", category = category,
        price = 3000.0, shares = shares, acqPrice = 2500.0, dps = 120.0, yieldRate = 4.0, yoc = 4.8,
        nextPayoutTiming = "", nextPayoutAmount = "", dividendMonths = listOf(6, 12), dividendType = "", accountType = "特定",
        dailyChange = "", isPositive = true, taxFreeShares = free, financialProfile = profile
    )
}
