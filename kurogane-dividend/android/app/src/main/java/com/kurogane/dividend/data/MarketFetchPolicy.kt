package com.kurogane.dividend.data

/** Failed attempts use a short retry delay, never the successful daily cache window. */
internal object MarketFetchPolicy {
    const val FAILURE_RETRY_MILLIS = 30_000L

    fun shouldFetch(now: Long, attemptedAt: Long, attemptedDate: String?, failed: Boolean): Boolean =
        if (failed) now < attemptedAt || now - attemptedAt >= FAILURE_RETRY_MILLIS
        else PriceRefreshPolicy.shouldFetch(now, attemptedAt, attemptedDate)
}
