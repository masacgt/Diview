package com.kurogane.dividend.model

import com.kurogane.dividend.data.CloudflareSecurity
import com.kurogane.dividend.model.PortfolioMathTest.Companion.holding
import org.junit.Assert.*
import org.junit.Test

class StockDetailResolverTest {
    @Test fun unrelatedHoldingIsNeverUsedAsTheSelectedStock() {
        assertNull(StockDetailResolver.resolve("9999", listOf(holding()), emptyList(), null))
    }

    @Test fun heldStockKeepsItsQuantityAndAcquisitionPrice() {
        assertEquals(holding(), StockDetailResolver.resolve("7203", listOf(holding()), emptyList(), null))
    }

    @Test fun remoteUnownedStockShowsItsOwnIdentityWithZeroHoldings() {
        val security = CloudflareSecurity("130A", "別の銘柄", 50.0, 2000.0, "情報・通信業")
        val stock = StockDetailResolver.resolve("130A", listOf(holding()), emptyList(), security)!!
        assertEquals("130A", stock.code)
        assertEquals("別の銘柄", stock.name)
        assertEquals(0, stock.shares)
        assertEquals(2.5, stock.liveYield, 0.0)
    }

    @Test fun mismatchedResponseIsRejected() {
        assertNull(StockDetailResolver.resolve("130A", emptyList(), emptyList(), CloudflareSecurity("9999", "誤応答", null, null)))
    }

    @Test fun withdrawnDividendDoesNotReuseHeldDpsOrConfirmNoDividend() {
        val held = holding().copy(dps = 100.0, irbankYieldRate = 5.0, irbankYieldDps = 100.0)
        val security = CloudflareSecurity("7203", "Toyota", null, 2000.0, hasDividendStatus = true)
        val resolved = StockDetailResolver.resolve("7203", listOf(held), emptyList(), security)!!
        assertEquals(held.shares, resolved.shares)
        assertEquals(held.acqPrice, resolved.acqPrice, 0.0)
        assertEquals(0.0, resolved.dps, 0.0)
        assertEquals(0.0, resolved.annualDividend, 0.0)
        assertNull(resolved.irbankYieldRate)
        assertFalse(resolved.isNoDividendConfirmed)
    }
}
