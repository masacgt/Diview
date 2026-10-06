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
