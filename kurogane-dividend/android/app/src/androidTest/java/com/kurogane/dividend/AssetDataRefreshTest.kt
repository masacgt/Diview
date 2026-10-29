package com.kurogane.dividend

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.*
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kurogane.dividend.ui.screens.StockDetailScreen
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurogane.dividend.data.*
import com.kurogane.dividend.ui.screens.AssetManagementScreen
import com.kurogane.dividend.ui.theme.KuroganeDividendTheme
import com.kurogane.dividend.viewmodel.DividendViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.util.concurrent.CopyOnWriteArrayList

/** Uses isolated preferences/files and fake HTTP; never reads or replaces the user's portfolio. */
@RunWith(AndroidJUnit4::class)
class AssetDataRefreshTest {
    @get:Rule val compose = createComposeRule()
    private val app = AuditApplication()
    private val models = ViewModelStore()
    private val requests = CopyOnWriteArrayList<String>()
    private val requestUrls = CopyOnWriteArrayList<String>()
    @Volatile private var monthlyFixture = false
    @Volatile private var offline = false
    @Volatile private var emptyFinancial = false
    @Volatile private var missingPrice = false
    @Volatile private var forecastDps: Double? = null
    @Volatile private var quoteNoDividend = false
    @Volatile private var quoteUnknown = false
    @Volatile private var eventForecast = false
    private val prefs get() = app.getSharedPreferences("cloudflare_market_cache", Context.MODE_PRIVATE)

    private fun model(refreshOnStart: Boolean = false): DividendViewModel {
        seedReviewPortfolio(app)
        val repository = CloudflareMarketRepository("https://example.com", connectionFactory = { url ->
            object : HttpURLConnection(url) {
                override fun getResponseCode(): Int {
                    requests.add(url.path)
                    requestUrls.add(url.toString())
                    if (offline) throw IOException("test offline")
                    return 200
                }
                override fun getInputStream(): ByteArrayInputStream {
                    val code = url.path.substringAfterLast('/')
                    val body = when {
                        url.path.contains("financial-data") && forecastDps != null ->
                            """{"security":{"code":"$code"},"issuer_dividends":{"forecast":{"annual_dps_current_share_basis":$forecastDps,"fiscal_year":2027,"field_units":{"annual_dps_current_share_basis":"jpy_per_share"}}}}"""
                        url.path.contains("financial-data") -> if (emptyFinancial) """{"security":{"code":"$code"}}""" else
                            """{"security":{"code":"$code"},"issuer_dividends":{"edinet_code":"E00001"},"dividends":{"records":[{"calendar_year":2024,"calendar_year_distribution_per_share":60},{"calendar_year":2025,"calendar_year_distribution_per_share":75}]}}"""
                        url.path.contains("/securities/") && quoteUnknown ->
                            """{"code":"$code","name":"検証銘柄","market_price":3000,"dividend_status":"unknown","dividend":{"yield_basis":null,"annual_dps":null}}"""
                        url.path.contains("/securities/") && quoteNoDividend ->
                            """{"code":"$code","name":"検証銘柄","market_price":3000,"dividend_status":"no_dividend","dividend":{"yield_basis":"annual","annual_dps":0}}"""
                        url.path.contains("/securities/") ->
                            """{"code":"$code","name":"検証銘柄","market_price":${if (missingPrice) "null" else "3000"},"dividend_status":"available","dividend":{"yield_basis":"annual","annual_dps":75}}"""
                        url.path.endsWith("dividend-months") && eventForecast -> {
                            val date = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Tokyo"))
                            val symbol = if (url.query.contains("2914")) "2914.T" else if (url.query.contains("AAPL")) "AAPL" else "7203.T"
                            """{"as_of":"$date","items":[{"symbol":"$symbol","months":[6,12],"history_status":"available","requested_event_years":[${date.year}],"events":[{"date":"${date.withDayOfYear(1)}","amount":30}],"upcoming_event":{"ex_dividend_date":"${date.withMonth(12).withDayOfMonth(31)}"}}]}"""
                        }
                        url.path.endsWith("dividend-months") ->
                            """{"as_of":"2026-10-25","items":[{"symbol":"7203.T","months":[6,12],"events":[]},{"symbol":"2914.T","months":[3,9],"events":[]}]}"""
                        url.path.endsWith("monthly-price-history") && monthlyFixture ->
                            """{"items":[{"code":"7203","prices":[{"month":"2024-12","date":"2024-12-30","close":2400},{"month":"2025-12","date":"2025-12-30","close":2800}]}]}"""
                        else -> """{"items":[]}"""
                    }
                    return ByteArrayInputStream(body.toByteArray())
                }
                override fun connect() = Unit
                override fun disconnect() = Unit
                override fun usingProxy() = false
            }
        })
        lateinit var vm: DividendViewModel
        compose.runOnIdle {
            vm = DividendViewModel(app, refreshOnStart, repository, notificationScheduler = { _, _ -> })
            models.put("vm", vm)
        }
        runBlocking { vm.awaitRestoration() }
        return vm
    }

    private fun fill(vm: DividendViewModel) = runBlocking { vm.refreshAssetManagementData().join() }

    private fun awaitFilled(vm: DividendViewModel) {
        compose.waitUntil(10_000) {
            vm.financialDataSnapshot("7203") != null &&
                vm.holdings.value.first { it.code == "7203" }.dividendMonths == listOf(6, 12)
        }
    }

    @Test fun missingJapanesePriceRetriesAfterCooldownAndPersists() {
        val vm = model()
        missingPrice = true
        runBlocking { vm.importCsvRows(listOf(CsvHoldingRow("7203", "トヨタ", 100.0, 2000.0, 0.0, "特定口座", false)),
            setOf("shares", "averageCost", "currentPrice", "account")) }
        runBlocking { vm.retryMissingJapaneseHoldingPrices().join() }
        val key = "cloudflare_security_fetch_attempt_at_7203"
        assertEquals(0.0, vm.holdings.value.first { it.code == "7203" }.price, 0.0)
        assertTrue(prefs.getBoolean("${key}_failed", false))
        val count = requests.count { it == "/api/v1/securities/7203" }
        assertTrue(count > 0)
        runBlocking { vm.retryMissingJapaneseHoldingPrices().join() }
        assertEquals(count, requests.count { it == "/api/v1/securities/7203" })
        prefs.edit().putLong(key, System.currentTimeMillis() - 31_000).commit()
        missingPrice = false
        runBlocking { vm.retryMissingJapaneseHoldingPrices().join() }
        assertTrue(requests.count { it == "/api/v1/securities/7203" } > count)
        assertEquals(3000.0, vm.holdings.value.first { it.code == "7203" }.price, 0.0)
        compose.waitUntil(10_000) { LocalImportStore(app).loadHoldings()?.firstOrNull { it.code == "7203" }?.price == 3000.0 }
    }

    @Test fun visibleAssetScreenAutomaticallyRetriesMissingJapanesePrice() {
        val vm = model()
        missingPrice = true
        runBlocking { vm.importCsvRows(listOf(CsvHoldingRow("7203", "トヨタ", 100.0, 2000.0, 0.0, "特定口座", false)),
            setOf("shares", "averageCost", "currentPrice", "account")) }
        runBlocking { vm.retryMissingJapaneseHoldingPrices().join() }
        assertEquals(0.0, vm.holdings.value.first { it.code == "7203" }.price, 0.0)
        val count = requests.count { it == "/api/v1/securities/7203" }
        assertTrue(count > 0)
        missingPrice = false
        var owner: androidx.lifecycle.LifecycleOwner? = null
        compose.setContent { KuroganeDividendTheme { owner = LocalLifecycleOwner.current; AssetManagementScreen(vm) } }
        try {
            compose.waitUntil(45_000) { vm.holdings.value.first { it.code == "7203" }.price == 3000.0 }
        } catch (error: Throwable) {
            val key = "cloudflare_security_fetch_attempt_at_7203"
            throw AssertionError("lifecycle=${owner?.lifecycle?.currentState}, attempts=${requests.count { it == "/api/v1/securities/7203" }}, failed=${prefs.getBoolean("${key}_failed", false)}, elapsed=${System.currentTimeMillis() - prefs.getLong(key, 0L)}, missing=${vm.hasMissingJapaneseHoldingPrices()}", error)
        }
        assertTrue(requests.count { it == "/api/v1/securities/7203" } > count)
    }

    @After fun cleanup() {
        compose.runOnIdle { models.clear() }
        app.cleanUp()
    }

    @Test fun existingCsvImportStartsMissingDataFetchWithoutNewHolding() {
        val vm = model()
        runBlocking { vm.importCsvRows(listOf(CsvHoldingRow("7203", "トヨタ", 100.0, 2000.0, 2600.0, "特定口座", false)),
            setOf("shares", "averageCost", "currentPrice", "account")) }
        awaitFilled(vm)
        assertEquals(3, vm.holdings.value.size)
        assertTrue(requests.contains("/api/v1/financial-data/7203"))
    }

    @Test fun existingDvmImportStartsMissingDataFetch() {
        val vm = model()
        runBlocking { vm.importDvmBackups(listOf(DvmBackupPreview(DvmBackupKind.STOCKS, 1,
            listOf(DvmStock("7203", "トヨタ", 100.0, 2000.0, 2600.0, "特定口座")), "[]".toByteArray()))) }
        awaitFilled(vm)
        assertEquals(3, vm.holdings.value.size)
    }

    @Test fun openingAssetsUsesCacheWithoutStartingRequests() {
        val vm = model()
        compose.setContent { KuroganeDividendTheme { AssetManagementScreen(vm) } }
        compose.waitForIdle()
        assertTrue(requests.isEmpty())
        fill(vm)
        awaitFilled(vm)
        assertEquals(3000.0, vm.holdings.value.first { it.code == "7203" }.price, 0.0)
        assertTrue(requests.contains("/api/v1/financial-data/AAPL"))
        val count = requests.size
        fill(vm)
        assertEquals(count, requests.size)
    }

    @Test fun startupSkipsTheSameDayEvenAfterTwelveHours() {
        val now = System.currentTimeMillis()
        prefs.edit().putLong("startup_fetch_attempt_at", now - 13 * 3_600_000L)
            .putString("startup_fetch_attempt_jst", PriceRefreshPolicy.japanDate(now)).commit()
        model(refreshOnStart = true)
        compose.waitForIdle()
        assertTrue(requests.isEmpty())
    }

    @Test fun startupSkipsRecentFetchEvenAcrossDateBoundary() {
        val now = System.currentTimeMillis()
        prefs.edit().putLong("startup_fetch_attempt_at", now - 3_600_000L)
            .putString("startup_fetch_attempt_jst", PriceRefreshPolicy.japanDate(now - 86_400_000L)).commit()
        model(refreshOnStart = true)
        compose.waitForIdle()
        assertTrue(requests.isEmpty())
    }

    @Test fun dueStartupFetchesHoldingsWithoutCatalogOrSpeculativeHistory() {
        val now = System.currentTimeMillis()
        prefs.edit().putLong("startup_fetch_attempt_at", now - 86_400_000L)
            .putString("startup_fetch_attempt_jst", PriceRefreshPolicy.japanDate(now - 86_400_000L))
            .putLong("fx_fetch_attempt_at", now)
            .putString("fx_fetch_attempt_jst_date", PriceRefreshPolicy.japanDate(now)).commit()
        val vm = model(refreshOnStart = true)
        awaitFilled(vm)
        fill(vm)
        assertTrue(prefs.getLong("startup_fetch_attempt_at", 0L) >= now)
        assertTrue(requests.none { "catalog" in it || "monthly-prices" in it || "share-splits" in it })
    }

    @Test fun failedDataRetriesSameDayAfterCooldownAndSuccessStopsFurtherRequests() {
        val vm = model()
        offline = true
        fill(vm)
        val failedCount = requests.size
        fill(vm)
        assertEquals(failedCount, requests.size)
        // Advance the stored attempt times, without sleeping or changing the device clock.
        val editor = prefs.edit()
        prefs.all.filter { (key, value) -> key.contains("attempt_at") && value is Long }.keys.forEach {
            editor.putLong(it, System.currentTimeMillis() - 31_000)
        }
        editor.commit()
        offline = false
        fill(vm)
        awaitFilled(vm)
        assertTrue(requests.size > failedCount)
        val successCount = requests.size
        fill(vm)
        assertEquals(successCount, requests.size)
    }

    @Test fun successfulEmptyFinancialHistoryIsNotRetriedOnEveryVisit() {
        val vm = model()
        emptyFinancial = true
        fill(vm)
        val securityPaths = listOf("7203", "2914", "AAPL").map { "/api/v1/financial-data/$it" }
        val count = requests.count { it in securityPaths }
        val totalRequests = requests.size
        fill(vm)
        assertEquals(3, count)
        securityPaths.forEach { path -> assertEquals(path, 1, requests.count { it == path }) }
        assertEquals(count, requests.count { it in securityPaths })
        assertEquals(totalRequests, requests.size)
    }

    @Test fun forecastReplacesNoDividendQuoteAndSurvivesCachedRefreshForEveryHeldJapaneseCode() {
        forecastDps = 80.0
        quoteNoDividend = true
        val vm = model()
        fill(vm)
        for (code in listOf("7203", "2914")) {
            val held = vm.holdings.value.first { it.code == code }
            assertEquals(80.0, held.dps, 0.0)
            assertFalse(held.isNoDividendConfirmed)
            assertEquals(held.shares * 80.0, held.annualDividend, 0.0)
            assertEquals(80.0 / held.price * 100.0, held.liveYield, 0.00001)
        }
        fill(vm)
        assertEquals(80.0, vm.holdings.value.first { it.code == "7203" }.dps, 0.0)
        val detail = mutableStateOf(false)
        compose.setContent {
            KuroganeDividendTheme {
                if (detail.value) StockDetailScreen("7203", vm) else AssetManagementScreen(vm)
            }
        }
        compose.onNodeWithTag("asset_management_list")
            .performScrollToNode(hasText("¥8,000/年"))
        assertTrue(compose.onAllNodesWithText("¥8,000/年").fetchSemanticsNodes().isNotEmpty())
        compose.runOnIdle { detail.value = true }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("¥80").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("¥80").onFirst().assertIsDisplayed()
        // Stock Detail resolves the zero quote again; its buy signal must keep the issuer forecast.
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("配当利回り 2.67%"))
        compose.onNodeWithText("配当利回り 2.67%").assertIsDisplayed()
        compose.onAllNodesWithText("配当利回り 0.00%（無配）").assertCountEquals(0)

    }

    @Test fun noDividendQuoteIsShownAsNoDividendOnAssetAndDetailScreens() {
        quoteNoDividend = true
        val vm = model()
        fill(vm)
        val holding = vm.holdings.value.first { it.code == "7203" }
        assertTrue(holding.isNoDividendConfirmed)
        assertEquals(0.0, holding.dps, 0.0)
        compose.setContent { KuroganeDividendTheme { StockDetailScreen("7203", vm) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("0.00%（無配）").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("0.00%（無配）").onFirst().assertIsDisplayed()
    }

    @Test fun unknownQuoteDoesNotBecomeNoDividendOnAssetAndDetailScreens() {
        quoteUnknown = true
        val vm = model()
        fill(vm)
        assertFalse(vm.holdings.value.first { it.code == "7203" }.isNoDividendConfirmed)
        assertFalse(vm.holdings.value.first { it.code == "7203" }.hasKnownAnnualDividend)
        compose.setContent { KuroganeDividendTheme { StockDetailScreen("7203", vm) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("未取得").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("0.00%（無配）").assertCountEquals(0)
    }

    @Test fun eventForecastRefreshesEvenWhenHoldingAlreadyHasDividendMonths() {
        quoteNoDividend = true
        val vm = model()
        fill(vm)
        assertEquals(listOf(6, 12), vm.holdings.value.first { it.code == "7203" }.dividendMonths)
        eventForecast = true
        val editor = prefs.edit()
        prefs.all.filter { (key, value) -> key.contains("attempt_at") && value is Long }.keys.forEach {
            editor.putLong(it, System.currentTimeMillis() - 31_000)
        }
        editor.commit()
        fill(vm)
        for (code in listOf("7203", "2914")) {
            val held = vm.holdings.value.first { it.code == code }
            assertEquals(60.0, held.dps, 0.0)
            assertFalse(held.isNoDividendConfirmed)
        }
    }

    @Test fun japaneseFiscalChartDoesNotTreatCalendarEventsAsIssuerForecast() {
        quoteNoDividend = true
        eventForecast = true
        val vm = model()
        fill(vm)
        assertEquals(60.0, vm.holdings.value.first { it.code == "7203" }.dps, 0.0)
        compose.setContent { KuroganeDividendTheme { StockDetailScreen("7203", vm) } }
        val year = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Tokyo")).year
        val label = "${year}期"
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(label))
        val bar = compose.onNode(hasClickAction() and hasAnyDescendant(hasText(label)), useUnmergedTree = true)
        bar.assert(hasAnyDescendant(hasText("¥未取得")))
        bar.assert(!hasAnyDescendant(hasText("予想")))
    }

    @Test fun monthlyAndSplitFailuresCanRetrySameDay() {
        val vm = model()
        offline = true
        assertThrows(IOException::class.java) { runBlocking { vm.loadMonthlyPriceHistory(listOf("7203"), listOf("2025-12")) } }
        val from = java.time.LocalDate.of(2024, 1, 1)
        val to = java.time.LocalDate.of(2025, 12, 31)
        assertThrows(IOException::class.java) { runBlocking { vm.loadShareSplitHistory("7203", from, to) } }
        val editor = prefs.edit()
        prefs.all.filter { (key, value) -> key.contains("attempt_at") && value is Long }.keys.forEach {
            editor.putLong(it, System.currentTimeMillis() - 31_000)
        }
        editor.commit()
        offline = false
        runBlocking { vm.loadMonthlyPriceHistory(listOf("7203"), listOf("2025-12")) }
        assertEquals(2, requests.count { it.contains("monthly-price-history") })
        // A matching split response is covered by the Repository tests; here verify it reaches HTTP again.
        runCatching { runBlocking { vm.loadShareSplitHistory("7203", from, to) } }
        assertEquals(2, requests.count { it.contains("share-split-history") })
    }

    @Test fun monthlyRefreshRequestsOnlyDueMonthsAndReturnsTheCompleteCachedSeries() {
        monthlyFixture = true
        val vm = model()
        val first = runBlocking { vm.loadMonthlyPriceHistory(listOf("7203"), listOf("2024-12")) }
        assertEquals(2400.0, first.single().prices.single().close, 0.0)
        val result = runBlocking {
            vm.loadMonthlyPriceHistory(listOf("7203"), listOf("2024-12", "2025-12"))
        }.single().prices
        assertEquals(listOf("2024-12", "2025-12"), result.map { it.month })
        assertEquals(listOf(2400.0, 2800.0), result.map { it.close })
        val monthlyRequests = requestUrls.filter { "monthly-price-history" in it }
        assertEquals(2, monthlyRequests.size)
        assertTrue(monthlyRequests.last().endsWith("months=2025-12"))
        runBlocking { vm.loadMonthlyPriceHistory(listOf("7203"), listOf("2024-12", "2025-12")) }
        assertEquals(2, requestUrls.count { "monthly-price-history" in it })
    }
}
