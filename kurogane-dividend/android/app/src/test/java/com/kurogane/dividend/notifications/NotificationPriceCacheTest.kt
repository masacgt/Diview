package com.kurogane.dividend.notifications

import com.kurogane.dividend.data.CloudflareMarketQuote
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationPriceCacheTest {
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
