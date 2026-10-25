package com.kurogane.dividend

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurogane.dividend.data.CloudflareMarketRepository
import com.kurogane.dividend.data.MarketHttpException
import com.kurogane.dividend.model.WatchlistStock
import com.kurogane.dividend.ui.screens.StockDetailScreen
import com.kurogane.dividend.ui.theme.KuroganeDividendTheme
import com.kurogane.dividend.viewmodel.DividendViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class ReleaseFailureRegressionTest {
    @get:Rule val compose = createComposeRule()
    private val application = AuditApplication()
    private val models = ViewModelStore()
    private val requests = AtomicInteger()
    private var status = 200
    private var failure: Exception? = null
    private var body = """{"security":{"code":"1306"}}"""

    private fun model(): DividendViewModel {
        val repository = CloudflareMarketRepository("https://example.com", connectionFactory = { url ->
            object : HttpURLConnection(url) {
                override fun getResponseCode(): Int {
                    if (url.path == "/api/v1/financial-data/revision") return 200
                    if (url.path.startsWith("/api/v1/financial-data/")) {
                        requests.incrementAndGet()
                        failure?.let { throw it }
                        return status
                    }
                    return 200
                }
                override fun getInputStream() = ByteArrayInputStream(
                    (when {
                        url.path == "/api/v1/financial-data/revision" -> """{"revision":"0123456789abcdef"}"""
                        url.path.startsWith("/api/v1/financial-data/") -> body
                        else -> """{"items":[]}"""
                    })
                        .toByteArray(Charsets.UTF_8)
                )
                override fun disconnect() = Unit
                override fun connect() = Unit
                override fun usingProxy() = false
            }
        })
        lateinit var vm: DividendViewModel
        compose.runOnIdle {
            models.clear()
            vm = DividendViewModel(application, false, repository, notificationScheduler = { _, _ -> })
            models.put("failure", vm)
        }
        runBlocking { vm.awaitRestoration() }
        return vm
    }

    @After fun cleanUp() {
        compose.runOnIdle { models.clear() }
        application.cleanUp()
    }

    @Test fun failedFinancialReadRemainsAFailureWithoutAnotherAutomaticRequest() {
        failure = IOException()
        val vm = model()
        repeat(2) { assertThrows(IOException::class.java) { runBlocking { vm.fetchFinancialData("1306") } } }
        assertEquals(1, requests.get())
    }

    @Test fun failedStateSurvivesModelRecreationAndExplicitRetryClearsIt() {
        failure = SocketTimeoutException()
        assertThrows(SocketTimeoutException::class.java) { runBlocking { model().fetchFinancialData("1306") } }
        val restored = model()
        assertThrows(SocketTimeoutException::class.java) { runBlocking { restored.fetchFinancialData("1306") } }
        assertEquals(1, requests.get())
        failure = null
        assertNotNull(runBlocking { restored.fetchFinancialData("1306", force = true) })
        assertNotNull(runBlocking { model().fetchFinancialData("1306") })
        assertEquals(2, requests.get())
    }

    @Test fun aReal404StaysEmptyAndExplicitRetryCanAcquireData() {
        status = 404
        val vm = model()
        repeat(2) { assertNull(runBlocking { vm.fetchFinancialData("1306") }) }
        assertEquals(1, requests.get())
        status = 200
        assertNotNull(runBlocking { vm.fetchFinancialData("1306", force = true) })
        assertEquals(2, requests.get())
    }

    @Test fun offlineTimeoutHttpErrorsAndMalformedResponsesNeverBecomeEmptySuccess() {
        val vm = model()
        val cases = listOf(UnknownHostException(), SocketTimeoutException(), MarketHttpException(429),
            MarketHttpException(503), IOException())
        cases.forEachIndexed { index, error ->
            failure = error
            val code = "13${index.toString().padStart(2, '0')}"
            repeat(2) { assertThrows(IOException::class.java) { runBlocking { vm.fetchFinancialData(code) } } }
        }
        failure = null
        for ((index, invalid) in listOf("not-json", "{\"security\":").withIndex()) {
            body = invalid
            val code = "14${index.toString().padStart(2, '0')}"
            repeat(2) { assertThrows(Exception::class.java) { runBlocking { vm.fetchFinancialData(code) } } }
        }
        assertEquals(7, requests.get())
    }

    @Test fun cancellingAnAttemptDoesNotPersistAnOrdinaryFailure() {
        failure = CancellationException()
        val vm = model()
        assertThrows(CancellationException::class.java) { runBlocking { vm.fetchFinancialData("1306") } }
        failure = null
        assertNotNull(runBlocking { vm.fetchFinancialData("1306", force = true) })
        assertEquals(2, requests.get())
    }

    @Test fun leavingAndReturningToStockDetailKeepsTheErrorAndExplicitRetryWorks() {
        failure = UnknownHostException()
        val vm = model()
        compose.runOnIdle {
            assertTrue(vm.restoreWatchlistStock(WatchlistStock(
                code = "1306", name = "通信障害検証", market = "東証", sector = "検証", category = "jp",
                price = 100.0, dps = 1.0, currentYield = 1.0, targetYield = 4.0, targetPrice = 25.0,
                dailyChange = "—", isPositive = true, payoutMonths = emptyList(), payoutTiming = "", desc = ""
            )))
        }
        val visible = mutableStateOf(true)
        compose.setContent { KuroganeDividendTheme { if (visible.value) StockDetailScreen("1306", vm) } }
        val offline = application.getString(R.string.network_offline)
        compose.waitUntil(10_000) { compose.onAllNodesWithText(offline).fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { visible.value = false }
        compose.runOnIdle { visible.value = true }
        compose.waitUntil(10_000) { compose.onAllNodesWithText(offline).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, requests.get())
        failure = null
        compose.onNodeWithText(offline).performScrollTo()
        compose.onNodeWithText(application.getString(R.string.retry)).performClick()
        compose.waitUntil(10_000) {
            requests.get() == 2 && compose.onAllNodesWithText(offline).fetchSemanticsNodes().isEmpty()
        }
    }
}
