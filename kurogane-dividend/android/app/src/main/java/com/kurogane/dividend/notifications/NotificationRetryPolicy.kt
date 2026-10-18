package com.kurogane.dividend.notifications

import com.kurogane.dividend.data.MarketHttpException
import java.io.IOException

/** Match the other notification workers' initial attempt plus at most three retries. */
internal object NotificationRetryPolicy {
    fun shouldRetry(attempt: Int, errors: List<Exception>): Boolean = attempt in 0..2 && errors.any {
        when (it) {
            is MarketHttpException -> it.status == 408 || it.status == 429 || it.status in 500..599
            is IOException -> true
            else -> false
        }
    }
}
