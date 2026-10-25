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

    @Test fun clockCorrectionDoesNotBlockFailedRequestForADay() {
        assertTrue(MarketFetchPolicy.shouldFetch(attempt - 1_000, attempt, date, true))
    }
}
