package com.kurogane.dividend.data

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Persist only the error category needed by the existing UI, never messages or response bodies. */
internal object CachedFetchFailure {
    fun encode(error: Exception): String = when (error) {
        is SocketTimeoutException -> "timeout"
        is UnknownHostException -> "offline"
        is MarketHttpException -> "http:${error.status}"
        else -> "failed"
    }

    fun decode(value: String?): IOException? = when {
        value == null -> null
        value == "timeout" -> SocketTimeoutException()
        value == "offline" -> UnknownHostException()
        value.startsWith("http:") -> value.removePrefix("http:").toIntOrNull()
            ?.takeIf { it in 400..599 }?.let(::MarketHttpException) ?: IOException()
        else -> IOException()
    }
}
