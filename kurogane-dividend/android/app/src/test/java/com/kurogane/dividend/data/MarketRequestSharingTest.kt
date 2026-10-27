package com.kurogane.dividend.data

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class MarketRequestSharingTest {
    private fun awaitWaiting(worker: Thread) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (worker.state != Thread.State.WAITING && System.nanoTime() < deadline) Thread.yield()
        assertEquals(Thread.State.WAITING, worker.state)
    }

    @Test fun overlappingCallsShareOneLoadAndTheNextCallStillRefreshes() {
        val sharing = MarketRequestSharing()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val ownerResult = AtomicReference<String>()
        val waiterResult = AtomicReference<String>()
        val failure = AtomicReference<Throwable>()
        val owner = thread {
            try { ownerResult.set(sharing.read("quote") {
                started.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                "first"
            }) } catch (error: Throwable) { failure.set(error) }
        }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        val waiter = thread {
            try { waiterResult.set(sharing.read("quote") { error("Duplicate HTTP") }) }
            catch (error: Throwable) { failure.set(error) }
        }
        try {
            awaitWaiting(waiter)
            assertEquals("independent", sharing.read("other") { "independent" })
        } finally {
            release.countDown()
            owner.join(5_000)
            waiter.join(5_000)
        }
        assertNull(failure.get())
        assertEquals("first", ownerResult.get())
        assertEquals("first", waiterResult.get())
        assertEquals("fresh", sharing.read("quote") { "fresh" })
    }

    @Test fun httpErrorsReachAllWaitersAndDoNotSuppressRetry() {
        val sharing = MarketRequestSharing()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val original = IOException("offline")
        val ownerFailure = AtomicReference<Throwable>()
        val waiterFailure = AtomicReference<Throwable>()
        val owner = thread {
            try { sharing.read("quote") {
                started.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                throw original
            } } catch (error: Throwable) { ownerFailure.set(error) }
        }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        val waiter = thread {
            try { sharing.read("quote") { error("Duplicate HTTP") } }
            catch (error: Throwable) { waiterFailure.set(error) }
        }
        try { awaitWaiting(waiter) } finally {
            release.countDown()
            owner.join(5_000)
            waiter.join(5_000)
        }
        assertSame(original, ownerFailure.get())
        assertSame(original, waiterFailure.get())
        assertEquals("recovered", sharing.read("quote") { "recovered" })
    }
}
