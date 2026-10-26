package com.kurogane.dividend

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToKey
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurogane.dividend.data.CloudflareMarketRepository
import com.kurogane.dividend.data.LocalImportStore
import com.kurogane.dividend.model.Category
import com.kurogane.dividend.model.Holding
import com.kurogane.dividend.ui.screens.AssetManagementScreen
import com.kurogane.dividend.ui.theme.KuroganeDividendTheme
import com.kurogane.dividend.viewmodel.DividendViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class SecurityCategoryDisplayTest {
    @get:Rule val compose = createComposeRule()
    private val app = AuditApplication()
    private val stores = ViewModelStore()
    private val categoryRequests = AtomicInteger()

    @After fun cleanUp() {
        compose.runOnIdle { stores.clear() }
        app.cleanUp()
    }

    private fun holding(code: String) = Holding(
        id = code, code = code, name = code, sector = "東証33業種未分類", market = "東証",
        category = Category.JAPAN, price = 2000.0, shares = 100, acqPrice = 1000.0,
        dps = 0.0, yieldRate = 0.0, yoc = 0.0, nextPayoutTiming = "—", nextPayoutAmount = "—",
        dividendMonths = emptyList(), dividendType = "配当金", accountType = "特定口座", dailyChange = "—", isPositive = true,
    )

    private fun model(offline: Boolean = false): DividendViewModel {
        val repo = CloudflareMarketRepository("https://example.com", connectionFactory = { url ->
            if (url.path != "/api/v1/security-categories") throw IOException("Other market data is offline")
            categoryRequests.incrementAndGet()
            assertFalse(url.query.contains("7203"))
            if (offline) throw IOException("Category service is offline")
            object : HttpURLConnection(url) {
                override fun getResponseCode() = 200
                override fun getInputStream() = ByteArrayInputStream("""{"categories":{"1306":"014","8951":"013"}}""".toByteArray())
                override fun connect() = Unit
                override fun disconnect() = Unit
                override fun usingProxy() = false
            }
        })
        lateinit var model: DividendViewModel
        compose.runOnIdle {
            model = DividendViewModel(app, false, repo, notificationScheduler = { _, _ -> })
            stores.put("categories", model)
        }
        runBlocking { model.awaitRestoration() }
        return model
    }

    @Test fun categoryResponseUpdatesVisibleLabelsAndSurvivesModelRestartWithoutChangingIndustries() {
        LocalImportStore(app).saveHoldings(listOf(holding("1306"), holding("8951"), holding("7203")))
        val vm = model()
        runBlocking { vm.refreshAssetManagementData().join() }
        val labels = runBlocking { vm.assetManagementListItems("val").first() }.associate { it.code to it.sector }
        assertEquals("ETF（上場投資信託）", labels["1306"])
        assertEquals("REIT（不動産投資信託）", labels["8951"])
        assertEquals("輸送用機器", labels["7203"])
        assertTrue(vm.holdings.value.filter { it.code != "7203" }.all { it.sector == "東証33業種未分類" })
        compose.setContent { KuroganeDividendTheme { AssetManagementScreen(vm) } }
        compose.onNodeWithTag("asset_management_list").performScrollToKey("holding:1306")
        compose.onNodeWithText("ETF（上場投資信託）").assertIsDisplayed()
        compose.onNodeWithTag("asset_management_list").performScrollToKey("holding:8951")
        compose.onNodeWithText("REIT（不動産投資信託）").assertIsDisplayed()
        compose.runOnIdle { stores.clear() }
        val restored = model()
        runBlocking { restored.refreshAssetManagementData().join() }
        assertEquals(1, categoryRequests.get())
        assertEquals("ETF（上場投資信託）", runBlocking { restored.assetManagementListItems("val").first() }.first { it.code == "1306" }.sector)
    }

    @Test fun expiredOfflineCacheKeepsItsLabelAndTimestampAndResetClearsIt() {
        LocalImportStore(app).saveHoldings(listOf(holding("1306")))
        val oldTimestamp = System.currentTimeMillis() - 46L * 86_400_000
        val prefs = app.getSharedPreferences("cloudflare_market_cache", Context.MODE_PRIVATE)
        val raw = JSONObject().put("1306", JSONObject().put("category", "014").put("fetched_at", oldTimestamp)).toString()
        prefs.edit().putString("security_categories_v1", raw).commit()
        val vm = model(offline = true)
        runBlocking { vm.refreshAssetManagementData().join() }
        assertEquals(1, categoryRequests.get())
        assertEquals(raw, prefs.getString("security_categories_v1", null))
        assertEquals("ETF（上場投資信託）", runBlocking { vm.assetManagementListItems("val").first() }.single().sector)
        runBlocking { vm.resetLocalData() }
        assertFalse(prefs.contains("security_categories_v1"))
        assertTrue(vm.holdings.value.isEmpty())
    }
}
