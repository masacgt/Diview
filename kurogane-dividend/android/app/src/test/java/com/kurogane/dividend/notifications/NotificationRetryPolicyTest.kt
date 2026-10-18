package com.kurogane.dividend.notifications

import com.kurogane.dividend.data.MarketHttpException
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class NotificationRetryPolicyTest {
    @Test fun transientFailuresAreBoundedAndANewScheduledRunCanRetry() {
        for (error in listOf(IOException(), SocketTimeoutException(), UnknownHostException(),
            MarketHttpException(408), MarketHttpException(429), MarketHttpException(503))) {
            assertTrue(NotificationRetryPolicy.shouldRetry(0, listOf(error)))
            assertTrue(NotificationRetryPolicy.shouldRetry(2, listOf(error)))
            assertFalse(NotificationRetryPolicy.shouldRetry(3, listOf(error)))
            assertFalse(NotificationRetryPolicy.shouldRetry(Int.MAX_VALUE, listOf(error)))
            assertTrue(NotificationRetryPolicy.shouldRetry(0, listOf(error)))
        }
    }

    @Test fun permanentPayloadAndClientErrorsDoNotRepeat() {
        for (error in listOf(IllegalArgumentException(), MarketHttpException(400), MarketHttpException(401),
            MarketHttpException(403), MarketHttpException(404), MarketHttpException(422))) {
            assertFalse(NotificationRetryPolicy.shouldRetry(0, listOf(error)))
        }
        assertFalse(NotificationRetryPolicy.shouldRetry(0, emptyList()))
    }

    @Test fun mixedBatchFailuresRetryOnlyWhileATransientErrorCanRecover() {
        val failures = listOf(MarketHttpException(403), MarketHttpException(503))
        assertTrue(NotificationRetryPolicy.shouldRetry(1, failures))
        assertFalse(NotificationRetryPolicy.shouldRetry(3, failures))
    }
}
