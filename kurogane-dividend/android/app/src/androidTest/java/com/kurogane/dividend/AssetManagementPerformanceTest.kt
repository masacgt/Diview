package com.kurogane.dividend

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kurogane.dividend.data.CloudflareMarketRepository
import com.kurogane.dividend.data.LocalImportStore
import com.kurogane.dividend.model.Category
import com.kurogane.dividend.model.Holding
import com.kurogane.dividend.ui.screens.AssetManagementScreen
import com.kurogane.dividend.ui.theme.KuroganeDividendTheme
import com.kurogane.dividend.viewmodel.DividendViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class AssetManagementPerformanceTest {
    @get:Rule val compose = createComposeRule()
    private val models = ViewModelStore()
    private val app = AuditApplication()
    private val preferences = listOf("local_import_store", "display_preferences", "cloudflare_market_cache", "notification_preferences", "notification_delivery")
    private lateinit var model: DividendViewModel

    @Before fun prepare() {
        clearPreferences()
        // Identical valuations preserve fixture order in the default stable sort.
        LocalImportStore(app).saveHoldings((0 until 300).map { index ->
            Holding(
                id = "asset-$index", code = "ZTEST$index", name = "表示検証銘柄 $index",
                sector = "表示検証", market = "東証", category = Category.JAPAN,
                price = 2000.0, shares = 100, acqPrice = 1000.0, dps = 80.0,
                yieldRate = 4.0, yoc = 8.0, nextPayoutTiming = "6月", nextPayoutAmount = "¥4,000",
                dividendMonths = listOf(6, 12), dividendType = "配当金", accountType = "特定口座",
                dailyChange = "—", isPositive = true
            )
        })
        compose.runOnUiThread {
            model = DividendViewModel(app, false, CloudflareMarketRepository("https://example.com") {
                throw IOException("offline")
            }, notificationScheduler = { _, _ -> })
            models.put("asset", model)
        }
        runBlocking { model.awaitRestoration() }
        assertEquals(300, model.holdings.value.size)
    }

    @After fun clean() {
        compose.runOnIdle { models.clear() }
        clearPreferences()
        app.cleanUp()
    }

    private fun clearPreferences() {
        preferences.forEach { app.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
    }

    private fun assertBoundedCards() {
        val count = compose.onAllNodes(hasText("表示検証銘柄", substring = true)).fetchSemanticsNodes().size
        assertTrue("Only the viewport should compose cards, found $count of 300", count < 20)
    }

    @Test fun largePortfolioOnlyComposesVisibleCardsAndCanOpenTheLastHolding() {
        var opened = ""
        compose.setContent { KuroganeDividendTheme {
            AssetManagementScreen(model, onNavigateToDetail = { opened = it })
        } }
        compose.onNodeWithText("総資産評価額").assertIsDisplayed()
        assertBoundedCards()
        compose.onNodeWithText("表示検証銘柄 299").assertDoesNotExist()
        compose.onNodeWithTag("asset_management_list").performScrollToKey("holding:asset-299")
        compose.onNodeWithText("表示検証銘柄 299").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("ZTEST299", opened) }
        assertBoundedCards()
    }

    @Test fun reenteringTheScreenRestoresScrollWithoutComposingTheWholePortfolio() {
        val visible = mutableStateOf(true)
        compose.setContent { KuroganeDividendTheme { if (visible.value) AssetManagementScreen(model) } }
        compose.onNodeWithTag("asset_management_list").performScrollToKey("holding:asset-150")
        compose.waitForIdle()
        val index = model.assetScrollIndex
        val offset = model.assetScrollOffset
        assertTrue(index > 100)
        compose.runOnIdle { visible.value = false }
        compose.runOnIdle { visible.value = true }
        compose.onNodeWithText("表示検証銘柄 150").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(index, model.assetScrollIndex)
            assertEquals(offset, model.assetScrollOffset)
        }
        assertBoundedCards()
    }

    @Test fun searchSurvivesRestorationAndMonthFilteringStillShowsTheEmptyState() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { KuroganeDividendTheme { AssetManagementScreen(model) } }
        compose.onNodeWithTag("asset_management_list").performScrollToNode(hasSetTextAction())
        compose.onNode(hasSetTextAction()).performTextInput("ZTEST299")
        compose.onNodeWithTag("asset_management_list").performScrollToKey("holding:asset-299")
        compose.onNodeWithText("表示検証銘柄 299").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("asset_management_list").performScrollToNode(hasSetTextAction())
        compose.onNode(hasSetTextAction()).assertTextEquals("ZTEST299")
        compose.runOnIdle { model.selectMonth(1) }
        compose.onNodeWithTag("asset_management_list").performScrollToNode(hasText("該当する保有銘柄はありません"))
        compose.onNodeWithText("該当する保有銘柄はありません").assertIsDisplayed()
        compose.runOnIdle { model.selectMonth(1) }
        compose.onNodeWithTag("asset_management_list").performScrollToKey("holding:asset-299")
        compose.onNodeWithText("表示検証銘柄 299").assertIsDisplayed()
    }
}
