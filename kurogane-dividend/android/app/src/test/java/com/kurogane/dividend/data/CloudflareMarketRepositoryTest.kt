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
    @Test fun targetedRefreshNeverDownloadsTheCatalogForMissingFields() = runBlocking {
        val urls = mutableListOf<String>()
        val repo = repository(mutableListOf(
            Connection(200, """{"items":[{"code":"7203","price":2600}]}"""),
            Connection(200, """{"code":"7203","name":"Toyota","market_price":2600}""")), urls)
        assertEquals(2600.0, repo.fetchSearchQuotes(listOf("7203"), allowCatalogFallback = false)["7203"]!!.price!!, 0.0)
        assertEquals(2600.0, repo.getSecurity("7203", allowCatalogFallback = false)!!.marketPrice!!, 0.0)
        assertEquals(2, urls.size)
        assertFalse(urls.any { "catalog" in it })
    }

    @Test fun productCategoriesUseTheFullUniverseApiAndPreserveUnknownAndFiveDigitCodes() = runBlocking {
        val urls = mutableListOf<String>()
        val repo = repository(mutableListOf(Connection(200, """{"categories":{"1306":"014","8951":"013","25935":"011","285A":null,"9999":"014"}}""")), urls)
        assertEquals(mapOf("1306" to "014", "8951" to "013", "25935" to "011", "285A" to null),
            repo.fetchSecurityCategories(listOf("13060", "8951.T", "25935", "285a")))
        assertEquals(1, urls.size)
        assertTrue(urls.single().contains("/api/v1/security-categories?codes="))
    }

    @Test fun malformedProductCategoryResponsesFailInsteadOfCachingAnEmptyResult() = runBlocking {
        for (body in listOf("{}", """{"categories":{"1306":"ETF"}}""")) {
            try {
                repository(Connection(200, body)).fetchSecurityCategories(listOf("1306"))
                fail("Malformed category responses must fail")
            } catch (_: java.io.IOException) { }
        }
    }

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
        val connection = Connection(200, """{"code":"7203","name":"トヨタ","dividend":{"annual_dps_adjusted":100,"yield_basis":"annual_dps"},"market_price":{"close":2000}}""")
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

    @Test fun explicitUnknownDoesNotFallBackToTheCachedCatalogDividend() = runBlocking {
        val urls = mutableListOf<String>()
        val repo = repository(mutableListOf(
            Connection(200, """{"code":"7203","name":"Toyota","dividend_status":"unknown","market_price":{"close":2000}}"""),
            Connection(200, """{"items":[{"code":"7203","price":2000,"dividend_status":"no_dividend","annual_dividend_per_share":0,"yield_rate":0}]}"""),
        ), urls)
        val stock = repo.getSecurity("7203")!!
        assertNull(stock.annualDividendPerShare)
        assertNull(stock.marketYieldRate)
        assertFalse(stock.isNoDividendConfirmed)
        assertTrue(stock.hasDividendStatus)
    }

    @Test fun classShareCodeReachesFinancialPriceAndSplitEndpointsUnchanged() = runBlocking {
        val urls = mutableListOf<String>()
        val repo = repository(mutableListOf(
            Connection(200, """{"security":{"code":"25935"}}"""),
            Connection(200, """{"items":[{"code":"25935","prices":[{"month":"2025-12","date":"2025-12-30","close":3000}]}]}"""),
            Connection(200, """{"code":"25935","history_status":"available","events":[{"date":"2025-04-01","ratio":2}]}"""),
        ), urls)
        assertEquals("25935", repo.financialDocument("25935.T")!!.getJSONObject("security").getString("code"))
        assertEquals(3000.0, repo.fetchMonthlyPriceHistory(listOf("25935.T"), listOf("2025-12")).single().prices.single().close, 0.0)
        assertEquals("25935", repo.fetchShareSplitHistory("25935.T", java.time.LocalDate.parse("2025-01-01"), java.time.LocalDate.parse("2025-12-31")).code)
        assertTrue(urls.all { "25935" in it })
        assertFalse(urls.any { "2593&" in it || it.endsWith("/2593") })
    }

    @Test fun trailingTwelveMonthDistributionsAreNotParsedAsAnnualDps() = runBlocking {
        val quotes = repository(Connection(200, """{"items":[
            {"code":"8214","price":1800,"annual_dividend_per_share":630,
             "trailing_12_month_distribution_per_share":630,"dividend_period":"trailing_12_months",
             "yield_rate":5,"dividend_status":"has_dividend"},
            {"code":"8058","price":2800,"annual_dividend_per_share":125,
             "trailing_12_month_distribution_per_share":819,"dividend_period":"next_fiscal_year_forecast",
             "yield_rate":4.4643,"dividend_status":"has_dividend"},
            {"code":"6721","price":200,"annual_dividend_per_share":0,
             "trailing_12_month_distribution_per_share":0,"dividend_period":"trailing_12_months",
             "yield_rate":0,"dividend_status":"no_dividend"}
        ]}""")).fetchSearchQuotes(listOf("8214", "8058", "6721"))

        assertNull(quotes["8214"]?.annualDividendPerShare)
        assertNull(quotes["8214"]?.yieldRate)
        assertEquals(125.0, quotes["8058"]?.annualDividendPerShare!!, 0.0)
        assertEquals(0.0, quotes["6721"]?.annualDividendPerShare!!, 0.0)
        assertEquals(0.0, quotes["6721"]?.yieldRate!!, 0.0)
    }

    @Test fun unknownDividendAmountIsNotConvertedToZeroButConfirmedNoDividendIs() = runBlocking {
        val result = repository(Connection(200, """{"items":[
            {"code":"1326","name":"金ETF","annual_dividend_per_share":0,"yield_rate":null,"dividend_status":"unknown"},
            {"code":"8306","name":"三菱UFJ","annual_dividend_per_share":0,"yield_rate":0,"dividend_status":"no_dividend"}
        ]}""")).searchSecurities("13")

        val unknown = result.first { it.code == "1326" }
        assertNull(unknown.annualDividendPerShare)
        assertNull(unknown.marketYieldRate)
        assertFalse(unknown.isNoDividendConfirmed)

        val noDividend = result.first { it.code == "8306" }
        assertEquals(0.0, noDividend.annualDividendPerShare!!, 0.0)
        assertEquals(0.0, noDividend.marketYieldRate!!, 0.0)
        assertTrue(noDividend.isNoDividendConfirmed)
    }

    @Test fun unknownDividendCatalogFallbackDoesNotPopulateZeroDps() = runBlocking {
        val connections = mutableListOf(
            Connection(200, """{"code":"1326","name":"金ETF","dividend":{"annual_dps":0,"annual_dps_adjusted":0},
                "dividend_status":"unknown","market_price":{"close":59870,"date":"2026-10-15"}}"""),
            Connection(200, """{"items":[{"code":"1326","name":"金ETF","price":59870,"annual_dividend_per_share":0,
                "yield_rate":null,"dividend_status":"unknown","quote_date":"2026-10-15"}]}""")
        )
        val stock = repository(connections, mutableListOf()).getSecurity("1326")!!

        assertNull(stock.annualDividendPerShare)
        assertNull(stock.marketYieldRate)
        assertFalse(stock.isNoDividendConfirmed)
        assertTrue(stock.hasDividendStatus)
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

    @Test fun priceSnapshotReadsPublishedCatalogAndPreservesDividendStates() = runBlocking {
        val urls = mutableListOf<String>()
        val snapshot = repository(mutableListOf(
            Connection(200, """{"market_data_date":"2026-10-09","items":[
                {"code":"7203","price":2000,"annual_dividend_per_share":100,"dividend_status":"has_dividend","quote_date":"2026-10-09"},
                {"code":"9999","price":100,"annual_dividend_per_share":25,"dividend_status":"has_dividend","quote_date":"2026-10-09"},
                {"code":"1326","price":59870,"annual_dividend_per_share":0,"yield_rate":null,"dividend_status":"unknown","quote_date":"2026-10-09"},
                {"code":"8306","price":1000,"annual_dividend_per_share":0,"yield_rate":0,"dividend_status":"no_dividend","quote_date":"2026-10-09"},
                {"code":"130A","price":500,"quote_date":"2026-10-10"}]}""")
        ), urls).fetchPriceCatalogSnapshot()

        assertEquals("2026-10-09", snapshot.marketDataDate)
        assertEquals(setOf("7203", "9999", "1326", "8306"), snapshot.quotes.keys)
        assertEquals(2000.0, snapshot.quotes.getValue("7203").price, 0.0)
        assertEquals(100.0, snapshot.quotes.getValue("7203").annualDividendPerShare!!, 0.0)
        assertEquals(25.0, snapshot.quotes.getValue("9999").annualDividendPerShare!!, 0.0)
        assertNull(snapshot.quotes.getValue("1326").annualDividendPerShare)
        assertFalse(snapshot.quotes.getValue("1326").isNoDividendConfirmed)
        assertEquals(0.0, snapshot.quotes.getValue("8306").annualDividendPerShare!!, 0.0)
        assertTrue(snapshot.quotes.getValue("8306").isNoDividendConfirmed)
        assertEquals(1, urls.size)
        assertTrue(urls.single().endsWith("/api/v1/search-catalog"))
    }

    @Test fun priceSnapshotKeepsEmptyPublishedCatalogEmpty() = runBlocking {
        val snapshot = repository(Connection(200, """{"market_data_date":null,"items":[],"complete":false}""")).fetchPriceCatalogSnapshot()

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
