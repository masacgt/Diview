package com.kurogane.dividend.model

import org.junit.Assert.*
import org.junit.Test

class DividendTaxTest {
    @Test fun domesticTaxAndNisa() {
        assertEquals(7968.5, DividendTax.net(10000.0, 0.0, false), 0.00001)
        assertEquals(10000.0, DividendTax.net(10000.0, 1.0, false), 0.0)
        assertEquals(8984.25, DividendTax.net(10000.0, .5, false), .00001)
    }
    @Test fun usWithholdingRemainsInNisaAndPrecedesJapaneseTax() {
        assertEquals(9000.0, DividendTax.net(10000.0, 1.0, true), 0.0)
        assertEquals(7171.65, DividendTax.net(10000.0, 0.0, true), .00001)
        assertEquals(8085.825, DividendTax.net(10000.0, .5, true), .00001)
    }
    @Test fun handlesInvalidMoneyAndRatioBoundaries() {
        for (gross in listOf(-1.0, 0.0, Double.NaN, Double.POSITIVE_INFINITY)) assertEquals(0.0, DividendTax.net(gross, .5, true), 0.0)
        assertEquals(9000.0, DividendTax.net(10000.0, 2.0, true), 0.0)
        assertEquals(7171.65, DividendTax.net(10000.0, Double.NaN, true), .00001)
    }
    @Test fun fxRevaluationUsesTheProvidedRateAndMissingRateIsNotInvented() {
        val h = Holding("id", "AAPL", "アップル", "米国株", "NASDAQ", Category.US, 200.0, "$", 10, 150.0, 1.0, .5, .66, "", "", emptyList(), "", "NISA", "", true)
        assertEquals(0.0, h.totalValuation, 0.0)
        assertEquals(320000.0, h.copy(usdJpyRate = 160.0).totalValuation, 0.0)
        assertEquals(1440.0, h.copy(usdJpyRate = 160.0).getEffectiveAnnualDividend("net"), 0.0)
        assertEquals(1600.0, h.copy(usdJpyRate = 160.0, usWithholdingEnabled = false).getEffectiveAnnualDividend("net"), 0.0)
        assertEquals(0.0, h.copy(usdJpyRate = Double.NaN).totalValuation, 0.0)
    }
    @Test fun additionalSharesPreserveTaxAllocationAndDoNotOverflowInt() {
        val h = Holding("id", "AAPL", "アップル", "米国株", "NASDAQ", Category.US, 200.0, "$", 10, 150.0, 1.0, .5, .66, "", "", emptyList(), "", "特定", "", true, taxFreeShares = 5, usdJpyRate = 160.0)
        assertEquals(2587.464, h.getEffectiveDividendForShares(20, "net"), .00001)
        assertEquals(3200.0, h.getEffectiveDividendForShares(20, "gross"), .00001)
        assertEquals(343597383680.0, h.getEffectiveDividendForShares(Int.MAX_VALUE.toLong() + 1, "gross"), .0001)
        assertEquals(0.0, h.getEffectiveDividendForShares(-1, "net"), 0.0)
        assertEquals(0.0, h.copy(dps = Double.NaN).getEffectiveDividendForShares(20, "gross"), 0.0)
    }
    @Test fun fundPerTenThousandUnitsAndNewNisaHoldingUseCorrectBasis() {
        val h = Holding("id", "fund", "投資信託", "投資信託", "", Category.FUND, 10000.0, shares = 0, acqPrice = 10000.0, dps = 100.0, yieldRate = 1.0, yoc = 1.0, nextPayoutTiming = "", nextPayoutAmount = "", dividendMonths = emptyList(), dividendType = "", accountType = "NISA", dailyChange = "", isPositive = true)
        assertEquals(100.0, h.getEffectiveDividendForShares(10000, "net"), 0.0)
        assertEquals(79.685, h.copy(accountType = "特定").getEffectiveDividendForShares(10000, "net"), .00001)
    }
}
