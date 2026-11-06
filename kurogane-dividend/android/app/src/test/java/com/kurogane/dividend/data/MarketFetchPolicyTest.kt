package com.kurogane.dividend.data

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class MarketFetchPolicyTest {
    private val attempt = Instant.parse("2026-10-25T01:00:00Z").toEpochMilli()
    private val date = PriceRefreshPolicy.japanDate(attempt)

    @Test fun failureCanRetrySameDayAfterThirtySeconds() {
        assertFalse(MarketFetchPolicy.shouldFetch(attempt + 29_999, attempt, date, true))
        assertTrue(MarketFetchPolicy.shouldFetch(attempt + 30_000, attempt, date, true))
    }

    @Test fun interruptedAndSupersededCatalogAttemptsUseTheFailureRetryWindow() {
        listOf("running", "failed", "superseded", "unknown").forEach {
            assertTrue(MarketFetchPolicy.isUnsuccessfulStatus(it))
        }
        assertFalse(MarketFetchPolicy.isUnsuccessfulStatus("ok"))
        assertFalse(MarketFetchPolicy.isUnsuccessfulStatus(null))
        assertFalse(MarketFetchPolicy.shouldFetch(attempt + 29_999, attempt, date, true))
        assertTrue(MarketFetchPolicy.shouldFetch(attempt + 30_000, attempt, date, true))
    }

    @Test fun successfulRetryRestoresDailyLimitIncludingEmptyResponses() {
        val retry = attempt + 30_000
        assertFalse(MarketFetchPolicy.shouldFetch(retry + 60_000, retry, date, false))
        assertFalse(MarketFetchPolicy.shouldFetch(attempt + 13 * 3_600_000L, attempt, date, false))
    }

    @Test fun successStillRequiresTwelveHoursAndDateChange() {
        val night = Instant.parse("2026-10-25T14:59:00Z").toEpochMilli()
        assertFalse(MarketFetchPolicy.shouldFetch(night + 120_000, night, date, false))
        assertTrue(MarketFetchPolicy.shouldFetch(night + 12 * 3_600_000L, night, date, false))
    }

    @Test fun startupRechecksSuccessfulPriceFetchAfterTwelveHoursEvenWhenDailyStartupAlreadyRan() {
        val lastFetch = Instant.parse("2026-11-04T14:30:00Z").toEpochMilli() // 23:30 JST
        val jstDate = PriceRefreshPolicy.japanDate(lastFetch)
        val earlyOpen = Instant.parse("2026-11-05T01:00:00Z").toEpochMilli() // 10:00 JST, only 10.5 hours later
        val laterOpen = Instant.parse("2026-11-05T03:00:00Z").toEpochMilli() // 12:00 JST, 12.5 hours later

        assertFalse(MarketFetchPolicy.shouldFetchOnStartup(earlyOpen, lastFetch, jstDate, "ok"))
        assertTrue(MarketFetchPolicy.shouldFetchOnStartup(laterOpen, lastFetch, jstDate, "ok"))
    }

    @Test fun startupDoesNotBypassThrottleWhenNoPriceAttemptWasRecorded() {
        assertFalse(MarketFetchPolicy.shouldFetchOnStartup(attempt + 13 * 3_600_000L, 0L, null, null))
        assertFalse(MarketFetchPolicy.shouldFetchOnStartup(attempt + 13 * 3_600_000L, 0L, null, "ok"))
    }

    @Test fun clockMovingBackwardsDoesNotShortenTheFailureRetryDelay() {
        assertFalse(MarketFetchPolicy.shouldFetch(attempt - 1_000, attempt, date, true))
        assertFalse(MarketFetchPolicy.shouldFetch(attempt + 29_999, attempt, date, true))
        assertTrue(MarketFetchPolicy.shouldFetch(attempt + 30_000, attempt, date, true))
    }
}
