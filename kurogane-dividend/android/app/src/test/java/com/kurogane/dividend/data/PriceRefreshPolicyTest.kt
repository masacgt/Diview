package com.kurogane.dividend.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PriceRefreshPolicyTest {
    @Test
    fun firstRunFetches() {
        assertTrue(PriceRefreshPolicy.shouldFetch(Instant.parse("2026-10-04T15:00:00Z").toEpochMilli(), 0L, null))
    }

    @Test
    fun doesNotFetchTwiceOnTheSameJapanDateEvenAfterTwelveHours() {
        val lastFetch = Instant.parse("2026-10-03T15:00:00Z").toEpochMilli() // 00:00 JST
        val lastJapanDate = PriceRefreshPolicy.japanDate(lastFetch)
        val sameDayLater = Instant.parse("2026-10-04T04:00:00Z").toEpochMilli() // 13:00 JST
        assertFalse(PriceRefreshPolicy.shouldFetch(sameDayLater, lastFetch, lastJapanDate))
    }

    @Test
    fun waitsForTwelveHoursAfterJapanDateChanges() {
        val lastFetch = Instant.parse("2026-10-04T14:59:59Z").toEpochMilli() // 23:59:59 JST
        val justAfterMidnight = lastFetch + 1_000L
        assertFalse(
            PriceRefreshPolicy.shouldFetch(
                justAfterMidnight,
                lastFetch,
                PriceRefreshPolicy.japanDate(lastFetch)
            )
        )
    }

    @Test
    fun fetchesOnNewJapanDateOnceTwelveHoursHavePassed() {
        val lastFetch = Instant.parse("2026-10-04T14:59:59Z").toEpochMilli()
        val afterTwelveHours = lastFetch + 12L * 60L * 60L * 1_000L
        assertTrue(
            PriceRefreshPolicy.shouldFetch(
                afterTwelveHours,
                lastFetch,
                PriceRefreshPolicy.japanDate(lastFetch)
            )
        )
    }
}
