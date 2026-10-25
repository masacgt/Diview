package com.kurogane.dividend.data

import org.junit.Assert.*
import org.junit.Test

class MarketDataContractTest {
    @Test fun explicitUnknownInvalidatesOldAmountAndBlocksPersistedHistoryFallback() {
        val amounts = mutableMapOf("7203" to 100.0)
        amounts.updateAnnualDividend("7203", null, noDividend = false, hasStatus = true)
        assertFalse(amounts.containsKey("7203"))
        assertEquals(0.0, resolveAnnualDividend(amounts["7203"], false, true) {
            error("A withdrawn forecast must not fall back to saved or historical DPS")
        }, 0.0)
        amounts.updateAnnualDividend("7203", 120.0, false, true)
        assertEquals(120.0, resolveAnnualDividend(amounts["7203"], false, true) { 100.0 }, 0.0)
    }

    @Test fun absentStatusRetainsCacheWhileConfirmedZeroIsStoredExplicitly() {
        val amounts = mutableMapOf("7203" to 100.0)
        amounts.updateAnnualDividend("7203", null, false, false)
        assertEquals(100.0, amounts["7203"]!!, 0.0)
        assertEquals(80.0, resolveAnnualDividend(null, false, false) { 80.0 }, 0.0)
        amounts.updateAnnualDividend("7203", null, true, true)
        assertTrue(amounts.containsKey("7203"))
        assertEquals(0.0, amounts["7203"]!!, 0.0)
    }

    @Test fun preservesClassSharesAndOnlyStripsOrdinaryCodePadding() {
        assertEquals("25935", normalizeJapaneseSecurityCode(" 25935.t "))
        assertEquals("7203", normalizeJapaneseSecurityCode("72030.T"))
        assertEquals("130A", normalizeJapaneseSecurityCode("130a0"))
    }
}
