package com.kurogane.dividend.data

import com.kurogane.dividend.model.Category
import com.kurogane.dividend.model.PortfolioMathTest.Companion.holding
import org.junit.Assert.*
import org.junit.Test

class PortfolioImporterTest {
    private val fields = setOf("shares", "averageCost", "currentPrice", "account")
    private fun row(shares: Double = 100.0, cost: Double = 1000.0, account: String = "NISA") =
        CsvHoldingRow("7203", "トヨタ", shares, cost, 2000.0, account, false)

    @Test fun duplicateSecurityRowsAcrossAccountsAreSummedAndWeighted() {
        val stock = PortfolioImporter.importRows(listOf(holding()), listOf(row(), row(200.0, 2500.0, "特定口座")), fields).single()
        assertEquals(300, stock.shares)
        assertEquals(2000.0, stock.acqPrice, 0.0)
        assertEquals(100, stock.effectiveTaxFreeShares)
        assertEquals(25_937.0, stock.getEffectiveAnnualDividend("net"), 0.001)
    }

    @Test fun importingTheSameFileTwiceIsIdempotent() {
        val first = PortfolioImporter.importRows(emptyList(), listOf(row()), fields)
        assertEquals(first, PortfolioImporter.importRows(first, listOf(row()), fields))
    }

    @Test fun uncheckedFieldsPreserveExistingValues() {
        val stock = PortfolioImporter.importRows(listOf(holding()), listOf(row(500.0, 3000.0)), setOf("currentPrice")).single()
        assertEquals(100, stock.shares)
        assertEquals(1000.0, stock.acqPrice, 0.0)
        assertEquals("特定口座", stock.accountType)
    }

    @Test fun uncheckedFieldsAreNotSilentlyAppliedToNewStocks() {
        val stock = PortfolioImporter.importRows(emptyList(), listOf(row()), setOf("currentPrice")).single()
        assertEquals(0, stock.shares)
        assertEquals(0.0, stock.acqPrice, 0.0)
    }

    @Test fun invalidQuantityAndPriceDoNotModifyTheOriginalPortfolio() {
        val original = listOf(holding())
        listOf(-1.0, 1.5, Double.NaN, Int.MAX_VALUE.toDouble() + 1).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { PortfolioImporter.importRows(original, listOf(row(value)), fields) }
        }
        assertThrows(IllegalArgumentException::class.java) { PortfolioImporter.importRows(original, listOf(row(cost = Double.NaN)), fields) }
        assertEquals(100, original.single().shares)
    }

    @Test fun multipleRowsCannotOverflowTheShareTotal() {
        assertThrows(IllegalArgumentException::class.java) {
            PortfolioImporter.importRows(emptyList(), listOf(row(Int.MAX_VALUE.toDouble()), row(1.0)), fields)
        }
    }

    @Test fun fundUnitsUseTheFundCategoryAndKeepZeroPriceFinite() {
        val fund = CsvHoldingRow("FUND:ABC", "ファンドABC", 50_000.0, 20_000.0, 24_460.0, "NISA", true)
        val stock = PortfolioImporter.importRows(emptyList(), listOf(fund), fields).single()
        assertEquals(Category.FUND, stock.category)
        assertEquals(122_300.0, stock.totalValuation, 0.0)
        assertTrue(PortfolioImporter.importRows(emptyList(), listOf(row().copy(currentPrice = 0.0)), fields).single().yieldRate.isFinite())
    }

    @Test fun missingFundPriceIsUnavailableAndReimportedPriceRestoresValuation() {
        val fund = CsvHoldingRow("FUND:ABC", "ファンドABC", 50_000.0, 20_000.0, null, "NISA", true)
        val missing = PortfolioImporter.importRows(emptyList(), listOf(fund), fields).single()
        assertNull(missing.valuationJpy)
        assertFalse(missing.hasKnownAnnualDividend)
        assertTrue(missing.copy(isNoDividendConfirmed = true).hasKnownAnnualDividend)
        assertEquals(100_000.0, missing.costJpy!!.value, 0.0)
        val updated = PortfolioImporter.importRows(listOf(missing), listOf(fund.copy(currentPrice = 24_460.0)), fields).single()
        assertEquals(122_300.0, updated.valuationJpy!!.value, 0.0)
        assertEquals(22_300.0, updated.gainLoss, 0.0)
    }

    @Test fun legacyMixedAccountsKeepTheNisaQuantity() {
        val stock = DvmStock("7203", "トヨタ", 300.0, 2000.0, 3000.0, "複数口座", 100.0)
        val imported = PortfolioImporter.importStocks(listOf(holding()), listOf(stock)).single()
        assertEquals(100, imported.effectiveTaxFreeShares)
        assertEquals(25_937.0, imported.getEffectiveAnnualDividend("net"), 0.001)
    }

    @Test fun deselectedRowsAreIgnored() {
        assertEquals(emptyList<Any>(), PortfolioImporter.importRows(emptyList(), listOf(row().copy(selected = false)), fields))
    }
}
