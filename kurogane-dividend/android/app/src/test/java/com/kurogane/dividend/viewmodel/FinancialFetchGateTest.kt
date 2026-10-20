package com.kurogane.dividend.viewmodel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class FinancialFetchGateTest {
    @Test fun ownerCancellationReleasesOwnershipBeforeWaitingRetry() = runTest {
        val gate = FinancialFetchGate()
        val started = CompletableDeferred<Unit>()
        var attempt: Int? = null
        val first = launch {
            gate.run("7203") {
                attempt = 1
                started.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    attempt = null
                }
            }
        }
        started.await()
        val retry = async(start = CoroutineStart.UNDISPATCHED) {
            gate.run("7203") {
                assertNull(attempt)
                attempt = 2
                "retried"
            }
        }
        assertFalse(retry.isCompleted)
        assertEquals(1, attempt)
        // Different securities can proceed while this one is suspended.
        assertEquals("other", gate.run("2914") { "other" })
        first.cancelAndJoin()
        assertEquals("retried", retry.await())
        assertEquals(2, attempt)
    }

    @Test fun cancelledWaiterDoesNotTouchOwnerAndNextWaiterReceivesCompletedCache() = runTest {
        val gate = FinancialFetchGate()
        val finish = CompletableDeferred<Unit>()
        var cache: String? = null
        var attempt: Int? = null
        val owner = async(start = CoroutineStart.UNDISPATCHED) {
            gate.run("7203") {
                attempt = 1
                finish.await()
                cache = "cached financial data"
                cache
            }
        }
        var cancelledWaiterEntered = false
        val cancelledWaiter = launch(start = CoroutineStart.UNDISPATCHED) {
            gate.run("7203") {
                cancelledWaiterEntered = true
                try {
                    awaitCancellation()
                } finally {
                    attempt = null
                }
            }
        }
        var readerEntered = false
        val reader = async(start = CoroutineStart.UNDISPATCHED) {
            gate.run("7203") {
                readerEntered = true
                cache
            }
        }
        assertFalse(cancelledWaiterEntered)
        assertFalse(readerEntered)
        assertFalse(reader.isCompleted)
        cancelledWaiter.cancelAndJoin()
        assertFalse(cancelledWaiterEntered)
        assertEquals(1, attempt)
        assertFalse(owner.isCompleted)
        assertNull(cache)
        finish.complete(Unit)
        assertEquals("cached financial data", owner.await())
        assertEquals("cached financial data", reader.await())
        assertEquals(1, attempt)
    }

    @Test fun forcedWaiterRefreshesAfterOwnerCompletesInsteadOfReturningItsCache() = runTest {
        val gate = FinancialFetchGate()
        val finish = CompletableDeferred<Unit>()
        var cache: String? = null
        var requests = 0
        val owner = async(start = CoroutineStart.UNDISPATCHED) {
            gate.run("7203") {
                requests++
                finish.await()
                cache = "first"
            }
        }
        val forced = async(start = CoroutineStart.UNDISPATCHED) {
            gate.run("7203") {
                assertEquals("first", cache)
                if (!useFinancialDailyCache(true, false, cache != null)) {
                    requests++
                    cache = "refreshed"
                }
                cache
            }
        }
        assertFalse(forced.isCompleted)
        assertEquals(1, requests)
        finish.complete(Unit)
        owner.await()
        assertEquals("refreshed", forced.await())
        assertEquals(2, requests)
    }

    @Test fun failureAlsoReleasesOwnership() = runTest {
        val gate = FinancialFetchGate()
        try {
            gate.run("7203") { throw IllegalStateException("failed") }
            fail("failure must propagate")
        } catch (_: IllegalStateException) {
            assertEquals("retry", gate.run("7203") { "retry" })
        }
    }

    @Test fun forceBypassesSuccessfulDailyCache() {
        assertTrue(useFinancialDailyCache(force = false, requiresContractRefresh = false, sameDay = true))
        assertFalse(useFinancialDailyCache(force = true, requiresContractRefresh = false, sameDay = true))
        assertFalse(useFinancialDailyCache(force = false, requiresContractRefresh = true, sameDay = true))
        assertFalse(useFinancialDailyCache(force = false, requiresContractRefresh = false, sameDay = false))
    }
}
