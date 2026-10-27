package com.kurogane.dividend

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kurogane.dividend.data.CloudflareMarketRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in public reads only: no portfolio access, cache clearing or notification delivery. */
@RunWith(AndroidJUnit4::class)
class MarketFetchEfficiencyLiveTest {
    private suspend fun <T> simultaneousReads(read: suspend (CloudflareMarketRepository) -> T): List<T> = coroutineScope {
        val ready = Channel<Unit>(4)
        val start = CompletableDeferred<Unit>()
        val jobs = List(4) {
            async(Dispatchers.IO) {
                val repository = CloudflareMarketRepository()
                ready.send(Unit)
                start.await()
                read(repository)
            }
        }
        repeat(4) { ready.receive() }
        start.complete(Unit)
        jobs.awaitAll()
    }

    @Test fun defaultRepositoriesShareLiveReadsAndPreserveIndependentResults(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("allowLiveMarketReads") == "true")
        assertEquals("https://diview-irbank.omasa.workers.dev", BuildConfig.DIVIEW_D1_API_BASE_URL)
        Log.i("MarketFetchAcceptance", "BEGIN default repositories; public code=285A")
        val documents = simultaneousReads { it.financialDocument("285A")!! }
        assertEquals(4, documents.size)
        documents.forEach { assertEquals("285A", it.getJSONObject("security").getString("code")) }
        val original = documents.first().toString()
        documents.forEach { assertEquals(original, it.toString()) }
        documents.first().put("test_mutation", true)
        documents.drop(1).forEach { assertFalse(it.has("test_mutation")) }
        // The completed shared read must not suppress a subsequent fresh request.
        val fresh = CloudflareMarketRepository().financialDocument("285A")!!
        assertFalse(fresh.has("test_mutation"))
        assertEquals("285A", fresh.getJSONObject("security").getString("code"))

        val repository = CloudflareMarketRepository()
        val catalog = repository.fetchPriceCatalogSnapshot()
        assertTrue(catalog.quotes.getValue("285A").price > 0.0)
        // The preceding explicit catalog fetch must also satisfy this fallback.
        val security = repository.getSecurity("285A", includeHistoryBounds = false)!!
        assertEquals("285A", security.code)
        assertTrue(security.marketPrice!! > 0.0)
        assertEquals("not_requested", security.dataRetrieval!!.dividendHistory!!.storedHistoryStatus)
        val analyses = simultaneousReads { it.getStockAnalysis("285A")!! }
        assertEquals(4, analyses.size)
        analyses.forEach { assertEquals(analyses.first(), it) }
        assertEquals(7, analyses.first().values.size)
        Log.i("MarketFetchAcceptance", "PASS independentBodies=true freshRequest=true catalogReuse=true historyBounds=not_requested analysisResponses=4")
    }
}
