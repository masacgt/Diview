package com.kurogane.dividend.data

import kotlinx.coroutines.sync.Mutex

/** Serializes refresh checks and fetches so concurrent app-start triggers cannot duplicate a catalog request. */
internal class MarketCatalogRefreshGate {
    private val mutex = Mutex()

    suspend fun <T> withLock(action: suspend () -> T): T {
        mutex.lock()
        try {
            return action()
        } finally {
            mutex.unlock()
        }
    }
}
