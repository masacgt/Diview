package com.kurogane.dividend

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kurogane.dividend.data.CloudflareMarketRepository
import com.kurogane.dividend.model.WatchlistStock
import com.kurogane.dividend.ui.screens.StockDetailScreen
import com.kurogane.dividend.ui.screens.SettingsScreen
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
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** Every preference and backup file belongs to this test, including applicationContext users. */
internal open class AuditApplication : Application() {
    private val prefix = "release_audit_${UUID.randomUUID()}_"
    private val preferences = mutableSetOf<String>()
    init { attachBaseContext(InstrumentationRegistry.getInstrumentation().targetContext) }
    override fun getApplicationContext(): Context = this
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        synchronized(preferences) { preferences += name }
        return baseContext.getSharedPreferences(prefix + name, mode)
    }
    override fun getFilesDir(): File = File(baseContext.cacheDir, prefix).apply { mkdirs() }
    fun cleanUp() {
        synchronized(preferences) {
            preferences.forEach { baseContext.getSharedPreferences(prefix + it, MODE_PRIVATE).edit().clear().commit() }
        }
        filesDir.listFiles()?.forEach { it.delete() }
        filesDir.delete()
    }
}

@RunWith(AndroidJUnit4::class)
class ReleaseAuditRegressionTest {
    @get:Rule val compose = createComposeRule()
    private val application = AuditApplication()
    private val stores = ViewModelStore()

    @After fun cleanUp() {
        compose.runOnIdle { stores.clear() }
        application.cleanUp()
    }

    private fun model(response: (URL) -> String): DividendViewModel {
        val repository = CloudflareMarketRepository("https://example.com", connectionFactory = { url ->
            object : HttpURLConnection(url) {
                override fun getResponseCode() = 200
                override fun getInputStream() = ByteArrayInputStream(response(url).toByteArray(Charsets.UTF_8))
                override fun connect() = Unit
                override fun disconnect() = Unit
                override fun usingProxy() = false
            }
        })
        lateinit var model: DividendViewModel
        compose.runOnIdle {
            model = DividendViewModel(application, false, repository, notificationScheduler = { _, _ -> })
            stores.put("audit", model)
        }
        runBlocking { model.awaitRestoration() }
        return model
    }

    @Test fun explicitFinancialRetryRecoversAfterFailureAndKeepsSuccessfulDailyCache() {
        val attempts = AtomicInteger()
        val vm = model {
            if (attempts.incrementAndGet() == 1) throw IOException()
            """{"security":{"code":"1306"}}"""
        }
        assertThrows(IOException::class.java) { runBlocking { vm.fetchFinancialData("1306") } }
        assertNotNull(runBlocking { vm.fetchFinancialData("1306", force = true) })
        assertNotNull(runBlocking { vm.fetchFinancialData("1306") })
        assertEquals(2, attempts.get())
    }

    @Test fun cancelledFinancialFetchCanBeExplicitlyRetried() {
        val attempts = AtomicInteger()
        val vm = model {
            if (attempts.incrementAndGet() == 1) throw CancellationException()
            """{"security":{"code":"1306"}}"""
        }
        assertThrows(CancellationException::class.java) { runBlocking { vm.fetchFinancialData("1306") } }
        assertNotNull(runBlocking { vm.fetchFinancialData("1306", force = true) })
        assertEquals(2, attempts.get())
    }

    @Test fun messageLessSplitFailureShowsRetryAndRetryPerformsARequest() {
        val attempts = AtomicInteger()
        val vm = model { url ->
            when (url.path) {
                "/api/v1/share-split-history" -> {
                    if (attempts.incrementAndGet() == 1) throw IOException()
                    """{"code":"1306","history_status":"empty","events":[]}"""
                }
                "/api/v1/financial-data/1306" -> """{"security":{"code":"1306"}}"""
                else -> """{"items":[]}"""
            }
        }
        compose.runOnIdle {
            assertTrue(vm.restoreWatchlistStock(WatchlistStock(
                code = "1306", name = "監査テスト", market = "東証", sector = "その他", category = "jp",
                price = 100.0, dps = 1.0, currentYield = 1.0, targetYield = 4.0, targetPrice = 25.0,
                dailyChange = "—", isPositive = true, payoutMonths = emptyList(), payoutTiming = "", desc = ""
            )))
        }
        compose.setContent { KuroganeDividendTheme { StockDetailScreen("1306", vm) } }
        compose.onNodeWithContentDescription("配当金の推移を編集").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("分割・併合情報を取得できません。").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("キャンセル").performClick()
        compose.onNodeWithContentDescription("配当金の推移を編集").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("分割・併合情報を取得できません。").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals("Reopening a failed request must not fetch automatically", 1, attempts.get())
        compose.onNodeWithText("再試行").performClick()
        compose.waitUntil(10_000) {
            attempts.get() == 2 &&
                compose.onAllNodesWithText("分割・併合情報を取得できません。").fetchSemanticsNodes().isEmpty() &&
                compose.onAllNodesWithText("分割・併合情報を取得中です。").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("キャンセル").performClick()
        compose.onNodeWithContentDescription("配当金の推移を編集").performScrollTo().performClick()
        compose.onNodeWithText("分割・併合情報を取得中です。").assertDoesNotExist()
        assertEquals("Reopening must use the normal daily cache", 2, attempts.get())
    }

    @Test fun throttledSplitFailureRemainsAnErrorUntilExplicitRetrySucceeds() {
        val attempts = AtomicInteger()
        val vm = model {
            if (attempts.incrementAndGet() == 1) throw IOException()
            """{"code":"1306","history_status":"empty","events":[]}"""
        }
        val from = LocalDate.of(2020, 1, 1)
        val to = LocalDate.of(2025, 12, 31)
        repeat(2) {
            assertThrows(IOException::class.java) { runBlocking { vm.loadShareSplitHistory("1306", from, to) } }
        }
        assertEquals(1, attempts.get())
        assertEquals("empty", runBlocking { vm.loadShareSplitHistory("1306", from, to, force = true) }.historyStatus)
        assertEquals("empty", runBlocking { vm.loadShareSplitHistory("1306", from, to) }.historyStatus)
        assertEquals(2, attempts.get())
    }

    @Test fun thresholdAccessibilityAlwaysDescribesAllTenIntegerValues() {
        val vm = model { """{"items":[]}""" }
        compose.setContent { KuroganeDividendTheme { SettingsScreen(vm) } }
        val rangeMatcher = androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(
            androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo
        )
        compose.onNode(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.ScrollToIndex))
            .performScrollToNode(rangeMatcher)
        val slider = compose.onNode(rangeMatcher)
        for (value in listOf(1f, 5f, 10f)) {
            slider.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) {
                assertTrue(it(value))
            }
            slider.assertRangeInfoEquals(androidx.compose.ui.semantics.ProgressBarRangeInfo(value, 1f..10f, 8))
        }
    }
}
