package com.kurogane.dividend.data

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class CachedFetchFailureTest {
    @Test fun onlySafeCategoriesSurviveRecreation() {
        for (error in listOf(SocketTimeoutException("private"), UnknownHostException("private"), IOException("private"))) {
            val encoded = CachedFetchFailure.encode(error)
            assertFalse(encoded.contains("private"))
            assertEquals(error.javaClass, CachedFetchFailure.decode(encoded)!!.javaClass)
            assertNull(CachedFetchFailure.decode(encoded)!!.message)
        }
        for (status in listOf(408, 429, 503)) {
            assertEquals(status, (CachedFetchFailure.decode(CachedFetchFailure.encode(MarketHttpException(status))) as MarketHttpException).status)
        }
    }

    @Test fun missingAndInvalidCacheEntriesNeverInventSuccessOrAnHttpStatus() {
        assertNull(CachedFetchFailure.decode(null))
        for (value in listOf("unknown", "http:bad", "http:200", "http:999")) {
            assertEquals(IOException::class.java, CachedFetchFailure.decode(value)!!.javaClass)
        }
    }
}
