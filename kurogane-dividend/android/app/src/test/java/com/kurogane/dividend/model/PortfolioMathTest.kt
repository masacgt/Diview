package com.kurogane.dividend.model

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class PortfolioMathTest {
    @Test fun excludedSecuritiesAreRemovedFromBothSidesOfAverageYield() {
        val dividend = holding()
        val zero = holding().copy(id = "zero", code = "abcd", dps = 0.0)
        val nonzero = holding().copy(id = "excluded", code = "ABCD", dps = 50.0)
        val positions = listOf(dividend, zero, nonzero)
        assertEquals(2.5, PortfolioMath.portfolioYieldForHoldings(positions, emptySet(), "gross", "eval"), 0.0001)
        assertEquals(5.0, PortfolioMath.portfolioYieldForHoldings(positions, setOf("ABCD"), "gross", "eval"), 0.0001)
        assertEquals(10.0, PortfolioMath.portfolioYieldForHoldings(positions, setOf("ABCD"), "gross", "cost"), 0.0001)
        assertEquals(3.98425, PortfolioMath.portfolioYieldForHoldings(positions, setOf("ABCD"), "net", "eval"), 0.0001)
        assertEquals(0.0, PortfolioMath.portfolioYieldForHoldings(positions, setOf("7203", "ABCD"), "gross", "eval"), 0.0)
    }

    @Test fun fundPricesAreQuotedPerTenThousandUnits() {
        val fund = holding().copy(category = Category.FUND, shares = 50_000, price = 24_460.0, acqPrice = 20_000.0, dps = 100.0)
        assertEquals(122_300.0, fund.totalValuation, 0.0)
        assertEquals(100_000.0, fund.totalCost, 0.0)
        assertEquals(500.0, fund.annualDividend, 0.0)
    }

    @Test fun taxableAndNisaAllocationsKeepTheirActualTaxTreatment() {
        val merged = PortfolioMath.mergeAllocations(holding().copy(shares = 0), listOf(
            HoldingAllocation(100, 1000.0, "新NISA成長枠"), HoldingAllocation(100, 3000.0, "特定口座")))
        assertEquals(200, merged.shares)
        assertEquals(2000.0, merged.acqPrice, 0.0)
        assertEquals(100, merged.effectiveTaxFreeShares)
        assertEquals("複数口座", merged.accountType)
        assertEquals(20_000.0, merged.annualDividend, 0.0)
        assertEquals(17_968.5, merged.getEffectiveAnnualDividend("net"), 0.001)
    }

    @Test fun addingATaxablePositionDoesNotRetaxTheOriginalNisaShares() {
        val merged = PortfolioMath.mergeAllocations(holding().copy(accountType = "NISA"),
            listOf(HoldingAllocation(100, 2000.0, "特定口座")))
        assertEquals(100, merged.effectiveTaxFreeShares)
        assertEquals(17_968.5, merged.getEffectiveAnnualDividend("net"), 0.001)
    }

    @Test fun zeroPricesAndInvalidNumbersNeverProduceInfiniteYields() {
        listOf(0.0, Double.NaN, Double.POSITIVE_INFINITY, -1.0).forEach {
            assertEquals(0.0, PortfolioMath.yieldPercent(100.0, it), 0.0)
        }
        assertEquals(0.0, PortfolioMath.yieldPercent(Double.NaN, 100.0), 0.0)
    }

    @Test fun rejectsFractionalNegativeAndOverflowingShareCounts() {
        listOf(null, -1.0, 0.1, Double.NaN, Double.POSITIVE_INFINITY, Int.MAX_VALUE.toDouble() + 1).forEach {
            assertNull(PortfolioMath.wholeShares(it))
        }
        assertEquals(Int.MAX_VALUE, PortfolioMath.wholeShares(Int.MAX_VALUE.toDouble()))
        assertEquals(0, PortfolioMath.wholeShares(0.0))
    }

    @Test fun allocationOverflowAndInvalidMoneyAreRejectedBeforeMutation() {
        assertThrows(IllegalArgumentException::class.java) {
            PortfolioMath.mergeAllocations(holding().copy(shares = Int.MAX_VALUE), listOf(HoldingAllocation(1, 1.0, "NISA")))
        }
        listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                PortfolioMath.mergeAllocations(holding(), listOf(HoldingAllocation(1, value, "NISA")))
            }
        }
    }

    @Test fun targetAdjustmentWorksWithCommaDecimalLocales() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals(3.05, PortfolioMath.adjustedTarget(3.0, 0.1), 0.0)
            assertEquals(0.1, PortfolioMath.adjustedTarget(0.1, -10.0), 0.0)
            assertEquals(100.0, PortfolioMath.adjustedTarget(100.0, 10.0), 0.0)
        } finally { Locale.setDefault(original) }
        assertThrows(IllegalArgumentException::class.java) { PortfolioMath.adjustedTarget(3.0, Double.NaN) }
    }

    @Test fun rapidIncrementCannotOverflow() {
        assertEquals(Int.MAX_VALUE, PortfolioMath.addShares(Int.MAX_VALUE - 5, 100))
        assertEquals(0, PortfolioMath.addShares(0, -10))
    }

    @Test fun zeroHoldingsHaveZeroNetDividend() {
        assertEquals(0.0, holding().copy(shares = 0).getEffectiveAnnualDividend("net"), 0.0)
    }

    @Test fun missingPayoutMonthDoesNotInventJuneOrDecember() {
        with(PortfolioMath) {
            assertEquals("配当月未取得", holding().copy(dividendMonths = emptyList()).refreshPayout().nextPayoutTiming)
        }
    }

    companion object {
        fun holding() = Holding("test-7203", "7203", "トヨタ自動車", "輸送用機器", "東証", Category.JAPAN,
            2000.0, "¥", 100, 1000.0, 100.0, 5.0, 10.0, "6月", "¥5000", listOf(6, 12), "", "特定口座", "—", true)
    }
}
