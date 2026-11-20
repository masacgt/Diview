package com.kurogane.dividend.data

/** Failed attempts use a short retry delay, never the successful daily cache window. */
internal object MarketFetchPolicy {
    const val FAILURE_RETRY_MILLIS = 30_000L

    fun isUnsuccessfulStatus(status: String?): Boolean = status != null && status != "ok"

    /** Re-evaluates price work on every app start, even if other daily startup work already ran. */
    fun shouldFetchOnStartup(
        now: Long,
        attemptedAt: Long,
        attemptedDate: String?,
        status: String?,
        retryAfterAtMillis: Long = 0L
    ): Boolean {
        val hasAttemptTime = attemptedAt > 0L || !attemptedDate.isNullOrBlank()
        if (!hasAttemptTime && !isUnsuccessfulStatus(status)) return false
        return shouldFetch(now, attemptedAt, attemptedDate, isUnsuccessfulStatus(status), retryAfterAtMillis)
    }

    fun shouldFetch(
        now: Long,
        attemptedAt: Long,
        attemptedDate: String?,
        failed: Boolean,
        retryAfterAtMillis: Long = 0L
    ): Boolean =
        if (failed) now >= attemptedAt && now - attemptedAt >= maxOf(
            FAILURE_RETRY_MILLIS,
            (retryAfterAtMillis - attemptedAt).coerceAtLeast(0L)
        )
        else PriceRefreshPolicy.shouldFetch(now, attemptedAt, attemptedDate)
}
