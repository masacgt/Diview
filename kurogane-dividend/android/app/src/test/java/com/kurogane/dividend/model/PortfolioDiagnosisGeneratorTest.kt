package com.kurogane.dividend.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortfolioDiagnosisGeneratorTest {
    @Test
    fun generatesSevenPortfolioWeightedFindingsAndTheExpectedPatternCount() {
        val result = PortfolioDiagnosisGenerator.generate(sampleHoldings())

        assertEquals(7, result.findings.size)
        assertEquals(PortfolioDiagnosis.TOTAL_COMBINATIONS, result.possibleCombinations)
        assertEquals(15L * 15L * 15L * 15L * 15L * 15L * 15L, result.possibleCombinations)
        assertTrue(result.complete)
        assertTrue(result.narrative.contains("評価額比率"))
        assertTrue(result.narrative.contains("増配・非減配"))
    }

    @Test
    fun narrativeAndRiskAssessmentChangeWhenPortfolioWeightsChange() {
        val holdings = sampleHoldings()
        val original = PortfolioDiagnosisGenerator.generate(holdings)
        val concentrated = PortfolioDiagnosisGenerator.generate(
            holdings.mapIndexed { index, holding ->
                if (index == 0) holding.copy(shares = holding.shares * 8) else holding
            }
        )

        assertNotEquals(original.narrative, concentrated.narrative)
        assertNotEquals(original.score, concentrated.score)
        assertNotEquals(original.findings.first().combinationDigit, concentrated.findings.first().combinationDigit)
    }

    @Test
    fun missingDividendMonthDataIsExcludedAndReducesAvailablePatterns() {
        val result = PortfolioDiagnosisGenerator.generate(
            sampleHoldings().map { it.copy(dividendMonths = emptyList()) }
        )

        assertEquals(6, result.findings.size)
        assertTrue(result.missingLabels.contains("最大月集中度"))
        assertEquals(15L * 15L * 15L * 15L * 15L * 15L, result.possibleCombinations)
        assertNull(result.combinationIndex)
        assertFalse(result.complete)
    }

    @Test
    fun emptyPortfolioIsNotGivenAnInventedGradeOrRiskScore() {
        val result = PortfolioDiagnosisGenerator.generate(emptyList())

        assertNull(result.score)
        assertEquals("-", result.grade)
        assertEquals(0L, result.possibleCombinations)
        assertTrue(result.findings.isEmpty())
    }

    private fun sampleHoldings(): List<Holding> = listOf(
        holding("7203", "自動車A", "輸送用機器", Category.JAPAN, 100, 3_000.0, 100.0, listOf(3, 9)),
        holding("2914", "食品B", "食料品", Category.JAPAN, 200, 2_000.0, 100.0, listOf(6, 12)),
        holding("8316", "銀行C", "銀行業", Category.JAPAN, 100, 3_000.0, 130.0, listOf(6, 12)),
        holding("AAPL", "米国D", "テクノロジー", Category.US, 20, 200.0, 1.0, listOf(2, 5, 8, 11)),
        holding("FUND", "投信E", "投資信託", Category.FUND, 100, 100.0, 1.0, listOf(3, 6, 9, 12))
    )

    private fun holding(
        code: String,
        name: String,
        sector: String,
        category: Category,
        shares: Int,
        price: Double,
        dps: Double,
        dividendMonths: List<Int>
    ) = Holding(
        id = code,
        code = code,
        name = name,
        sector = sector,
        market = category.name,
        category = category,
        price = price,
        currency = if (category == Category.US) "$" else "¥",
        shares = shares,
        acqPrice = price,
        dps = dps,
        yieldRate = if (price > 0) dps / price * 100 else 0.0,
        yoc = if (price > 0) dps / price * 100 else 0.0,
        nextPayoutTiming = "",
        nextPayoutAmount = "",
        dividendMonths = dividendMonths,
        dividendType = "",
        accountType = "特定",
        dailyChange = "",
        isPositive = true,
        usdJpyRate = if (category == Category.US) 160.0 else null,
        financialProfile = StockFinancialProfile(payoutRatio = 35.0, noCutYears = 10, consecutiveYears = 5)
    )

    @Test fun missingFinancialDataDoesNotUseStaticCompanyProfiles() {
        val result = PortfolioDiagnosisGenerator.generate(sampleHoldings().map { it.copy(financialProfile = null) })
        assertEquals(5, result.findings.size)
        assertFalse(result.complete)
        assertEquals(2, result.missingLabels.size)
    }
}
