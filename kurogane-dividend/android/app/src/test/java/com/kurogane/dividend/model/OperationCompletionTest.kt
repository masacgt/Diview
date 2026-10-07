package com.kurogane.dividend.model

import org.junit.Assert.*
import org.junit.Test

class OperationCompletionTest {
    private fun achievement(completed: Int, monthly: Long = 600000) = RoadmapAchievement(
        30, "STEP 30: 月600,000円", 600000, monthly, completed, "完全FIRE期", "経済的自由", 600000
    )

    @Test fun certificateRequiresCompletedStageAndCapsAtLevelThree() {
        assertFalse(achievement(9).canCertify)
        assertEquals(1, achievement(10).completedLevel)
        assertEquals(2, achievement(20).completedLevel)
        assertEquals(3, achievement(30).completedLevel)
        assertEquals(3, achievement(30).currentLevel)
    }

    @Test fun fullRoadmapNeverExportsTenThousandPercent() {
        assertEquals(100, achievement(30).progressPercent)
        assertEquals(100, achievement(30, 1200000).progressPercent)
        assertEquals(50, achievement(29, 300000).progressPercent)
        assertEquals(0, achievement(0, 0).progressPercent)
    }

    @Test fun calculationBasisChangesDenominatorAndHandlesEmptyPortfolio() {
        assertEquals(4.0, PortfolioMath.portfolioYield(400.0, 10000.0, 8000.0, "eval"), 0.0)
        assertEquals(5.0, PortfolioMath.portfolioYield(400.0, 10000.0, 8000.0, "cost"), 0.0)
        assertEquals(0.0, PortfolioMath.portfolioYield(0.0, 0.0, 0.0, "cost"), 0.0)
    }

    @Test fun compositionThresholdPreservesAllPositionsAndExactBoundary() {
        val values = listOf(90.0, 5.0, 3.0, 2.0)
        assertEquals(listOf(listOf(0), listOf(1), listOf(2, 3)), PortfolioMath.compositionGroups(values, 5))
        assertEquals(listOf(listOf(0), listOf(1, 2, 3)), PortfolioMath.compositionGroups(values, 10))
        val groups = PortfolioMath.compositionGroups(values, 10)
        assertEquals(values.indices.toList(), groups.flatten().sorted())
        assertTrue(PortfolioMath.compositionGroups(emptyList(), 5).isEmpty())
    }

    private fun stock(code: String, category: String, months: List<Int>, fx: Double? = null) = WatchlistStock(
        code, code, "", "", category, 100.0, if (category == "us") "$" else "¥", 10.0,
        10.0, 4.0, 250.0, "", true, months, "", "", usdJpyRate = fx
    )

    @Test fun watchlistUsesSameUnitsAndNeverInventsMissingMonthsOrFx() {
        val jp = stock("7203", "jp", listOf(6, 12, 12, 13))
        val us = stock("AAPL", "us", listOf(3, 6, 9, 12), 150.0)
        val unknown = stock("9999", "jp", emptyList())
        val projection = WatchlistProjection(listOf(jp, us, unknown))
        assertEquals(3500.0, projection.annualTotal, 0.0)
        assertEquals(2500.0, projection.scheduledTotal, 0.0)
        assertEquals(1, projection.unallocatedCount)
        assertEquals(6, projection.paymentCount)
        assertEquals(875.0, projection.monthlyTotals[5], 0.0)
        assertEquals(0.0, projection.monthlyTotals[0], 0.0)
        val missing = WatchlistProjection(listOf(us.copy(usdJpyRate = null)))
        assertTrue(missing.missingExchangeRate)
        assertEquals(0.0, missing.annualTotal, 0.0)
        assertEquals(12, missing.emptyMonths.size)
    }
}
