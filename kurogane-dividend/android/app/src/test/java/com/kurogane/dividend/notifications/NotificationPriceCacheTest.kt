package com.kurogane.dividend.notifications

import com.kurogane.dividend.data.CloudflareMarketQuote
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationPriceCacheTest {
    @Test fun legacyCodeAliasesNormalizeAndKeepTheNewestCachedQuote() {
        val newest = CachedNotificationPrice(2_500.0, "2026-11-03")
        val existing = linkedMapOf(
            "7203.T" to newest,
            "72030" to CachedNotificationPrice(2_400.0, "2026-11-02")
        )

        assertEquals(mapOf("7203" to newest), NotificationPriceCache.mergeQuotes(existing, emptyMap()))
    }

    @Test fun partialRefreshKeepsPreviousQuoteAndOlderDataCannotReplaceNewerQuote() {
        val existing = mapOf("7203" to CachedNotificationPrice(2_500.0, "2026-11-02"))
        val fetched = mapOf(
            "7203" to CloudflareMarketQuote(2_400.0, null, quoteDate = "2026-10-30"),
            "8306" to CloudflareMarketQuote(3_500.0, null, quoteDate = "2026-11-02"),
            "9432" to CloudflareMarketQuote(150.0, null)
        )

        assertEquals(
            mapOf(
                "7203" to CachedNotificationPrice(2_500.0, "2026-11-02"),
                "8306" to CachedNotificationPrice(3_500.0, "2026-11-02")
            ),
            NotificationPriceCache.mergeQuotes(existing, fetched)
        )
    }

    @Test fun fetchedQuoteRetainsItsAcquisitionAndCatalogProvenance() {
        val fetchedAt = 1_794_600_000_000L
        val generation = "2026-11-13-${"a".repeat(32)}"
        val merged = NotificationPriceCache.mergeQuotes(
            existing = emptyMap(),
            fetched = mapOf("7203" to CloudflareMarketQuote(2_000.0, null, quoteDate = "2026-11-13")),
            fetchedAtMillis = fetchedAt,
            marketDataDate = "2026-11-13",
            generation = generation
        )

        assertEquals(
            CachedNotificationPrice(2_000.0, "2026-11-13", fetchedAt, "2026-11-13", generation),
            merged.getValue("7203")
        )
    }

    @Test fun normalizesPaddedStandardCodesAndPreservesClassShareCodes() {
        val existing = mapOf("72030" to CachedNotificationPrice(2_500.0, "2026-11-02"))
        val fetched = mapOf("25935" to CloudflareMarketQuote(3_500.0, null, quoteDate = "2026-11-03"))

        assertEquals(
            mapOf(
                "7203" to CachedNotificationPrice(2_500.0, "2026-11-02"),
                "25935" to CachedNotificationPrice(3_500.0, "2026-11-03")
            ),
            NotificationPriceCache.mergeQuotes(existing, fetched)
        )
    }
}
