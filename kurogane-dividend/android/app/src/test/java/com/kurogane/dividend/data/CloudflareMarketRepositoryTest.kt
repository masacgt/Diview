package com.kurogane.dividend.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

class CloudflareMarketRepositoryTest {
    private class Connection(private val status: Int, private val body: String,
                             private val failure: Exception? = null) : HttpURLConnection(URL("https://example.com")) {
        var disconnected = false
        override fun getResponseCode(): Int { failure?.let { throw it }; return status }
        override fun getInputStream(): InputStream = ByteArrayInputStream(body.toByteArray())
        override fun disconnect() { disconnected = true }
        override fun connect() {}
        override fun usingProxy() = false
    }

    private fun repository(connection: Connection, base: String = "https://example.com") =
        CloudflareMarketRepository(base, Dispatchers.Unconfined) { connection }

    private fun repository(connections: MutableList<Connection>, requestedUrls: MutableList<String>) =
        CloudflareMarketRepository("https://example.com", Dispatchers.Unconfined) { url ->
            requestedUrls += url.toString()
            connections.removeAt(0)
        }

    @Test fun parsesValidSecurityAndClosesTheConnection() = runBlocking {
        val connection = Connection(200, """{"code":"7203","name":"トヨタ","dividend":{"annual_dps_adjusted":100},"market_price":{"close":2000}}""")
        val stock = repository(connection).getSecurity(" 7203 ")!!
        assertEquals("7203", stock.code)
        assertEquals(100.0, stock.annualDividendPerShare!!, 0.0)
        assertEquals(2000.0, stock.marketPrice!!, 0.0)
        assertTrue(connection.disconnected)
        assertFalse(connection.instanceFollowRedirects)
    }

    @Test fun missingDividendAndNullPriceRemainUnknown() = runBlocking {
        val stock = repository(Connection(200, """{"code":"7203","name":"トヨタ","dividend":null,"market_price":{"close":null}}""")).getSecurity("7203")!!
        assertNull(stock.annualDividendPerShare)
        assertNull(stock.marketPrice)
    }

    @Test fun notFoundReturnsNullWhileServerErrorRemainsAnError() {
        assertNull(runBlocking { repository(Connection(404, "private response")).getSecurity("7203") })
        val connection = Connection(503, "sensitive server diagnostics")
        val error = assertThrows(MarketHttpException::class.java) { runBlocking { repository(connection).getSecurity("7203") } }
        assertEquals(503, error.status)
        assertFalse(error.message.orEmpty().contains("sensitive"))
        assertTrue(connection.disconnected)
    }

    @Test fun timeoutAndInvalidJsonAlwaysDisconnect() {
        val timeout = Connection(200, "", SocketTimeoutException("timeout"))
        assertThrows(SocketTimeoutException::class.java) { runBlocking { repository(timeout).searchSecurities("7203") } }
        assertTrue(timeout.disconnected)
        val malformed = Connection(200, "not json")
        assertThrows(org.json.JSONException::class.java) { runBlocking { repository(malformed).searchSecurities("7203") } }
        assertTrue(malformed.disconnected)
    }

    @Test fun redirectsAndCleartextAndEmbeddedCredentialsAreRejected() {
        assertThrows(MarketHttpException::class.java) { runBlocking { repository(Connection(302, "")).searchSecurities("7203") } }
        listOf("http://example.com", "https://user:password@example.com", "https://example.com?q=x").forEach { base ->
            assertThrows(IllegalArgumentException::class.java) { runBlocking { repository(Connection(200, "{}"), base).searchSecurities("7203") } }
        }
    }

    @Test fun cancellationPropagatesAndReleasesTheConnection() {
        val connection = Connection(200, "", CancellationException("cancel"))
        assertThrows(CancellationException::class.java) { runBlocking { repository(connection).getStockAnalysis("7203") } }
        assertTrue(connection.disconnected)
    }

    @Test fun invalidInputAndImpossibleDatesAreRejected() {
        val repository = repository(Connection(200, "{}"))
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.getSecurity("../7203") } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.searchSecurities("a".repeat(257)) } }
        assertThrows(java.time.DateTimeException::class.java) { runBlocking { repository.fetchPopularStocks(0, "2026-02-30") } }
    }

    @Test fun mismatchedSecurityResponseCannotRegisterADifferentStock() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository(Connection(200, """{"code":"9999","name":"別銘柄"}""")).getSecurity("7203") }
        }
    }

    @Test fun searchSkipsNullRowsAndUnsafeCodes() = runBlocking {
        val result = repository(Connection(200, """{"items":[null,{"code":"../x","name":"bad"},{"code":"130A","name":"正常"}]}""")).searchSecurities("13")
        assertEquals(listOf("130A"), result.map { it.code })
    }

    @Test fun catalogFiltersNonfiniteNegativeAndZeroPrices() = runBlocking {
        val result = repository(Connection(200, """{"items":[{"code":"7203","price":2000},{"code":"9999","price":-1},{"code":"130A","price":"NaN"},{"code":"0000","price":0}]}""")).fetchPriceCatalog()
        assertEquals(mapOf("7203" to 2000.0), result)
    }

    @Test fun priceSnapshotPagesCompletedQuotesAndIgnoresPartialCatalogRows() = runBlocking {
        val connections = mutableListOf(
            Connection(200, """{"market_data_date":"2026-10-09","items":[
                {"code":"7203","price":2000,"annual_dividend_per_share":100,"quote_date":"2026-10-09"},
                {"code":"9999","price":100,"annual_dividend_per_share":25,"quote_date":"2026-10-09"},
                {"code":"130A","price":500,"quote_date":"2026-10-10"}]}"""),
            Connection(200, """{"run_id":"2026-10-09","market_data_date":"2026-10-09","complete":true,
                "items":[{"code":"7203","price":2100}],"next_cursor":"next /="}"""),
            Connection(200, """{"run_id":"2026-10-09","market_data_date":"2026-10-09","complete":true,
                "items":[{"code":"9999","price":110}],"next_cursor":null}""")
        )
        val urls = mutableListOf<String>()
        val snapshot = repository(connections, urls).fetchPriceCatalogSnapshot()

        assertEquals("2026-10-09", snapshot.marketDataDate)
        assertEquals(setOf("7203", "9999"), snapshot.quotes.keys)
        assertEquals(2100.0, snapshot.quotes.getValue("7203").price, 0.0)
        assertEquals(100.0, snapshot.quotes.getValue("7203").annualDividendPerShare!!, 0.0)
        assertEquals(110.0, snapshot.quotes.getValue("9999").price, 0.0)
        assertEquals(25.0, snapshot.quotes.getValue("9999").annualDividendPerShare!!, 0.0)
        assertEquals(3, urls.size)
        assertTrue(urls[1].contains("/api/v1/live-quotes?limit=200"))
        assertTrue(urls[2].contains("run_id=2026-10-09"))
        assertTrue(urls[2].contains("market_data_date=2026-10-09"))
        assertTrue(urls[2].contains("cursor=next+%2F%3D"))
    }

    @Test fun priceSnapshotDoesNotTrustLegacyCompletionWithoutRunSnapshot() = runBlocking {
        val snapshot = repository(mutableListOf(
            Connection(200, """{"market_data_date":"2026-10-10","items":[{"code":"7203","price":2000,"quote_date":"2026-10-10"}]}"""),
            Connection(200, """{"run_id":null,"market_data_date":null,"complete":false,"legacy_unpublished":true,"items":[],"next_cursor":null}""")
        ), mutableListOf()).fetchPriceCatalogSnapshot()

        assertNull(snapshot.marketDataDate)
        assertTrue(snapshot.quotes.isEmpty())
    }

    @Test fun parsesCloudflareDividendMonthsAndKeepsOnlyRequestedSymbolsAndValidMonths() = runBlocking {
        val response = """{"as_of":"2026-10-05","lookback_days":365,"items":[
            {"symbol":"7203.T","months":[12,6,6,0,13]},
            {"symbol":"MSFT","months":[]},
            {"symbol":"AAPL","months":[3]}]}"""
        val result = repository(Connection(200, response)).fetchDividendMonths(listOf("7203.T", "msft"))

        assertEquals(java.time.LocalDate.parse("2026-10-05"), result.asOf)
        assertEquals(mapOf("7203.T" to listOf(6, 12), "MSFT" to emptyList<Int>()), result.monthsBySymbol)
    }

    @Test fun dividendMonthRequestRejectsEmptyOversizedAndUnsafeSymbolBatches() {
        val repo = repository(Connection(200, "{}"))
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.fetchDividendMonths(emptyList()) } }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.fetchDividendMonths((1..51).map { "T$it" }) }
        }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.fetchDividendMonths(listOf("../private")) } }
    }

    @Test fun oversizedResponseFailsWithConnectionClosed() {
        val connection = Connection(200, " ".repeat(8 * 1024 * 1024 + 1))
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository(connection).searchSecurities("7203") } }
        assertTrue(connection.disconnected)
    }
}
