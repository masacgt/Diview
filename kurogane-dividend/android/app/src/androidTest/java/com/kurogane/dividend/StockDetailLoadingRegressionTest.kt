package com.kurogane.dividend

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurogane.dividend.data.CloudflareMarketRepository
import com.kurogane.dividend.ui.screens.StockDetailScreen
import com.kurogane.dividend.ui.theme.KuroganeDividendTheme
import com.kurogane.dividend.viewmodel.DividendViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class StockDetailLoadingRegressionTest {
    @get:Rule val compose = createComposeRule()
    private val app = AuditApplication()
    private val models = ViewModelStore()
    private val financialGate = CountDownLatch(1)

    @After fun cleanup() {
        financialGate.countDown()
        compose.runOnIdle { models.clear() }
        app.cleanUp()
    }

    @Test fun uncachedDividendChartShowsSkeletonUntilFinancialLoadCompletes() {
        val repository = CloudflareMarketRepository("https://example.com", connectionFactory = { url ->
            object : HttpURLConnection(url) {
                override fun getResponseCode(): Int {
                    if (url.path.contains("financial-data")) financialGate.await(20, TimeUnit.SECONDS)
                    return 200
                }
                override fun getInputStream(): ByteArrayInputStream {
                    val body = when {
                        url.path.contains("financial-data") -> """{"security":{"code":"7203"},"dividends":{"records":[{"calendar_year":2025,"calendar_year_distribution_per_share":75}]}}"""
                        url.path.contains("/securities/") -> """{"code":"7203","name":"検証銘柄","market_price":3000}"""
                        url.path.endsWith("dividend-months") -> """{"items":[{"symbol":"7203.T","months":[],"events":[],"history_status":"available"}]}"""
                        else -> """{"items":[]}"""
                    }
                    return ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
                }
                override fun connect() = Unit
                override fun disconnect() = Unit
                override fun usingProxy() = false
            }
        })
        lateinit var vm: DividendViewModel
        compose.runOnIdle {
            vm = DividendViewModel(app, true, repository, notificationScheduler = { _, _ -> })
            models.put("loading", vm)
        }
        runBlocking { vm.awaitRestoration() }
        compose.setContent { KuroganeDividendTheme { StockDetailScreen("7203", vm) } }
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasContentDescription("配当グラフを読み込み中")).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(compose.onAllNodes(hasContentDescription("配当グラフを読み込み中")).fetchSemanticsNodes().isNotEmpty())
        financialGate.countDown()
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasContentDescription("配当グラフを読み込み中")).fetchSemanticsNodes().isEmpty() &&
                vm.financialDataSnapshot("7203") != null
        }
    }
}
