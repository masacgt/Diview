package com.kurogane.dividend.data

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException

/** Shares only overlapping HTTP reads. A completed response never suppresses a refresh. */
internal class MarketRequestSharing {
    private val pending = ConcurrentHashMap<String, CompletableFuture<String>>()

    fun read(key: String, load: () -> String): String {
        val owned = CompletableFuture<String>()
        val existing = pending.putIfAbsent(key, owned)
        if (existing != null) {
            try {
                return existing.get()
            } catch (failure: ExecutionException) {
                throw failure.cause ?: failure
            }
        }
        try {
            return load().also { owned.complete(it) }
        } catch (failure: Throwable) {
            owned.completeExceptionally(failure)
            throw failure
        } finally {
            pending.remove(key, owned)
        }
    }
}
