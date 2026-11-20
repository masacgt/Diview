package com.kurogane.dividend.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class MarketCatalogRefreshGateTest {
    @Test
    fun concurrentRefreshChecksRunOneAtATime() = runBlocking {
        val gate = MarketCatalogRefreshGate()
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondEntered = CompletableDeferred<Unit>()
        val active = AtomicInteger()
        val maximumActive = AtomicInteger()
        fun enter() {
            val count = active.incrementAndGet()
            maximumActive.updateAndGet { current -> maxOf(current, count) }
        }
        fun leave() { active.decrementAndGet() }

        val first = async {
            gate.withLock {
                enter()
                firstEntered.complete(Unit)
                releaseFirst.await()
                leave()
            }
        }
        firstEntered.await()
        val second = async {
            gate.withLock {
                enter()
                secondEntered.complete(Unit)
                leave()
            }
        }

        yield()
        assertFalse(secondEntered.isCompleted)
        releaseFirst.complete(Unit)
        first.await()
        second.await()

        assertTrue(secondEntered.isCompleted)
        assertEquals(1, maximumActive.get())
    }
}
