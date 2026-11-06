package com.kurogane.dividend

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurogane.dividend.data.CloudflareMarketRepository
import com.kurogane.dividend.data.LocalImportStore
import com.kurogane.dividend.model.WatchlistStock
import com.kurogane.dividend.ui.screens.WatchlistScreen
import com.kurogane.dividend.ui.theme.KuroganeDividendTheme
import com.kurogane.dividend.viewmodel.DividendViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class IncomeAdviceCatalogCacheTest {
    @get:Rule val compose = createComposeRule()

    private val application = AuditApplication()
    private val models = ViewModelStore()
    private val requests = CopyOnWriteArrayList<String>()

    @After fun cleanUp() {
        compose.runOnIdle { models.clear() }
        application.cleanUp()
    }

    @Test fun cachedIncomeAdviceRemainsVisibleInWatchlistWhenOffline() {
        val generation = "2026-11-02-daa434e899b0493a9fdf346067419041"
        val cachedCatalog = """{"generation":"$generation","generated_at":"2026-11-03T19:41:21.044036+00:00","items":[
            {"code":"8306","name":"三菱UFJフィナンシャル・グループ","currency":"JPY","annual_dividend_per_share":60,"annual_dividend_basis":"next_fiscal_year_forecast","observed_months":[3,9],"monthly_estimate_per_100_shares":3000,"score":85,"source_catalog_generation":"$generation"}
        ]}"""
        application.getSharedPreferences("cloudflare_market_cache", Context.MODE_PRIVATE)
            .edit().putString("income_advice_catalog", cachedCatalog).commit()
        LocalImportStore(application).saveWatchlist(listOf(
            WatchlistStock(
                code = "7203",
                name = "トヨタ自動車",
                market = "東証",
                sector = "輸送用機器",
                category = "jp",
                price = 2600.0,
                dps = 75.0,
                currentYield = 75.0 / 2600.0 * 100.0,
                targetYield = 3.0,
                targetPrice = 2500.0,
                dailyChange = "—",
                isPositive = true,
                payoutMonths = listOf(6),
                payoutTiming = "未取得",
                desc = "テスト用ローカルウォッチリスト"
            )
        ))

        val repository = CloudflareMarketRepository("https://example.com") { url ->
            requests += url.toString()
            throw IOException("offline test")
        }
        lateinit var viewModel: DividendViewModel
        compose.runOnIdle {
            viewModel = DividendViewModel(
                application,
                refreshOnStart = false,
                marketRepository = repository,
                notificationScheduler = { _, _ -> }
            )
            models.put("income-advice", viewModel)
        }
        runBlocking { viewModel.awaitRestoration() }

        assertEquals(generation, viewModel.incomeAdviceCatalog.value?.generation)
        compose.setContent {
            KuroganeDividendTheme { WatchlistScreen(viewModel) }
        }
        compose.onNodeWithText("受取月別試算").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("インカム空白月の候補").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodes(isRoot()).get(1).performTouchInput {
            swipeUp(startY = height * 0.9f, endY = height * 0.1f, durationMillis = 300)
        }
        compose.onNodeWithText("8306").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("三菱UFJフィナンシャル・グループ").assertIsDisplayed()
        compose.onNodeWithText("3月").assertIsDisplayed()
        compose.onNodeWithText("9月").assertIsDisplayed()
        assertTrue("cached candidate display should not make a request", requests.isEmpty())
    }
}
