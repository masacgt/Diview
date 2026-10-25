package com.kurogane.dividend

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
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
    @Volatile private var offline = false
    @Volatile private var emptyFinancial = false
    private val prefs get() = app.getSharedPreferences("cloudflare_market_cache", Context.MODE_PRIVATE)

    private fun model(): DividendViewModel {
        seedReviewPortfolio(app)
        val repository = CloudflareMarketRepository("https://example.com", connectionFactory = { url ->
            object : HttpURLConnection(url) {
                override fun getResponseCode(): Int {
                    requests.add(url.path)
                    if (offline) throw IOException("test offline")
                    return 200
                }
                override fun getInputStream(): ByteArrayInputStream {
                    val code = url.path.substringAfterLast('/')
                    val body = when {
                        url.path.contains("financial-data") -> if (emptyFinancial) """{"security":{"code":"$code"}}""" else
                            """{"security":{"code":"$code"},"dividends":{"records":[{"calendar_year":2024,"calendar_year_distribution_per_share":60},{"calendar_year":2025,"calendar_year_distribution_per_share":75}]}}"""
                        url.path.contains("/securities/") ->
                            """{"code":"$code","name":"検証銘柄","market_price":3000,"dividend_status":"available","dividend":{"yield_basis":"annual","annual_dps":75}}"""
                        url.path.endsWith("dividend-months") ->
                            """{"as_of":"2026-10-25","items":[{"symbol":"7203.T","months":[6,12],"events":[]},{"symbol":"2914.T","months":[3,9],"events":[]}]}"""
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
            vm = DividendViewModel(app, false, repository, notificationScheduler = { _, _ -> })
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

    @Test fun openingAssetsFillsMissingDataAndDoesNotRequestUsStocks() {
        val vm = model()
        compose.setContent { KuroganeDividendTheme { AssetManagementScreen(vm) } }
        awaitFilled(vm)
        fill(vm)
        assertEquals(3000.0, vm.holdings.value.first { it.code == "7203" }.price, 0.0)
        assertTrue(requests.none { it.contains("AAPL") })
        val count = requests.size
        fill(vm)
        assertEquals(count, requests.size)
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
        val count = requests.count { it.contains("financial-data") }
        fill(vm)
        assertEquals(2, count)
        assertEquals(count, requests.count { it.contains("financial-data") })
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
}
