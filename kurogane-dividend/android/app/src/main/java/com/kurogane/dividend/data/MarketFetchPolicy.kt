package com.kurogane.dividend.data

/** Failed attempts use a short retry delay, never the successful daily cache window. */
internal object MarketFetchPolicy {
    const val FAILURE_RETRY_MILLIS = 30_000L

    fun isUnsuccessfulStatus(status: String?): Boolean = status != null && status != "ok"

    /** Re-evaluates price work on every app start, even if other daily startup work already ran. */
    fun shouldFetchOnStartup(now: Long, attemptedAt: Long, attemptedDate: String?, status: String?): Boolean {
        val hasAttemptTime = attemptedAt > 0L || !attemptedDate.isNullOrBlank()
        if (!hasAttemptTime && !isUnsuccessfulStatus(status)) return false
        return shouldFetch(now, attemptedAt, attemptedDate, isUnsuccessfulStatus(status))
    }

    fun shouldFetch(now: Long, attemptedAt: Long, attemptedDate: String?, failed: Boolean): Boolean =
        if (failed) now >= attemptedAt && now - attemptedAt >= FAILURE_RETRY_MILLIS
        else PriceRefreshPolicy.shouldFetch(now, attemptedAt, attemptedDate)
}
