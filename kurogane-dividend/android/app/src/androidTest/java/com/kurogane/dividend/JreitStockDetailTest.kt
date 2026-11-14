package com.kurogane.dividend

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurogane.dividend.data.CloudflareMarketRepository
import com.kurogane.dividend.data.LocalImportStore
import com.kurogane.dividend.model.Category
import com.kurogane.dividend.model.Holding
import com.kurogane.dividend.model.WatchlistStock
import com.kurogane.dividend.ui.screens.StockDetailScreen
import com.kurogane.dividend.ui.theme.KuroganeDividendTheme
import com.kurogane.dividend.viewmodel.DividendViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection

@RunWith(AndroidJUnit4::class)
class JreitStockDetailTest {
    @get:Rule val compose = createComposeRule()
    private val app = AuditApplication()
    private val stores = ViewModelStore()

    @After fun cleanup() {
        compose.runOnIdle { stores.clear() }
        app.cleanUp()
    }

    private fun open(code: String, sector: String, offline: Boolean = false, watchlist: Boolean = false, dpsValue: Double = 80.0) {
        val store = LocalImportStore(app)
        if (watchlist) store.saveWatchlist(listOf(WatchlistStock(
            code = code, name = "検証銘柄", market = "東証", sector = sector, category = "jp",
            price = 2000.0, dps = 80.0, currentYield = 4.0, targetYield = 4.0, targetPrice = 0.0,
            dailyChange = "—", isPositive = true, payoutMonths = emptyList(), payoutTiming = "—", desc = ""
        ))) else store.saveHoldings(listOf(Holding(
            id = code, code = code, name = "検証銘柄", sector = sector, market = "東証",
            category = Category.JAPAN, price = 2000.0, shares = 1, acqPrice = 1000.0,
            dps = dpsValue, yieldRate = 4.0, yoc = 8.0, nextPayoutTiming = "—", nextPayoutAmount = "—",
            dividendMonths = emptyList(), dividendType = "配当金", accountType = "特定口座", dailyChange = "—", isPositive = true
        )))
        val repo = CloudflareMarketRepository("https://example.com", connectionFactory = { url ->
            if (offline || url.path != "/api/v1/security-categories") throw IOException("offline")
            object : HttpURLConnection(url) {
                override fun getResponseCode() = 200
                override fun getInputStream() = ByteArrayInputStream("""{"categories":{"8951":"013","9282":"013","1306":"014","7203":"011"}}""".toByteArray())
                override fun connect() = Unit
                override fun disconnect() = Unit
                override fun usingProxy() = false
            }
        })
        lateinit var vm: DividendViewModel
        compose.runOnIdle {
            vm = DividendViewModel(app, false, repo, notificationScheduler = { _, _ -> })
            stores.put(code, vm)
        }
        runBlocking { vm.awaitRestoration() }
        compose.setContent { KuroganeDividendTheme { StockDetailScreen(code, vm) } }
    }

    @Test fun reitUsesDedicatedExplanationsWithoutOrdinaryPayoutThreshold() {
        open("8951", "東証33業種未分類")
        val list = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
        list.performScrollToNode(hasText("参考情報"))
        compose.onNodeWithText("参考情報").assertIsDisplayed()
        list.performScrollToNode(hasText("J-REITの指標を参考表示します", substring = true))
        compose.onAllNodesWithText("J-REITの指標を参考表示します", substring = true)[0].assertIsDisplayed()
        compose.onNodeWithText("適正目安 30〜50%").assertDoesNotExist()
        list.performScrollToNode(hasText("配当指標の解説"))
        compose.onNodeWithText("配当指標の解説").assertIsDisplayed()
        list.performScrollToNode(hasText("帳簿上の純資産に対する投資口価格", substring = true))
        compose.onNodeWithText("帳簿上の純資産に対する投資口価格", substring = true).assertIsDisplayed()
    }

    @Test fun unidentifiedProductDoesNotReceiveStockScore() {
        open("9999", "東証33業種未分類", offline = true)
        val list = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
        list.performScrollToNode(hasText("分類未確認"))
        compose.onNodeWithText("分類未確認").assertIsDisplayed()
        list.performScrollToNode(hasText("銘柄の種類を取得できないため", substring = true))
        compose.onNodeWithText("適正目安 30〜50%").assertDoesNotExist()
    }

    @Test fun ordinaryStockRetainsItsExistingHealthDisplay() {
        open("7203", "輸送用機器")
        val list = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
        list.performScrollToNode(hasText("適正目安 30〜50%"))
        compose.onNodeWithText("適正目安 30〜50%").assertIsDisplayed()
    }

    @Test fun infrastructureFundIsNotTreatedAsJreit() {
        open("9282", "東証33業種未分類")
        val list = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
        list.performScrollToNode(hasText("適正目安 30〜50%"))
        compose.onNodeWithText("適正目安 30〜50%").assertIsDisplayed()
        compose.onAllNodesWithText("J-REITの指標を参考表示します", substring = true).assertCountEquals(0)
    }

    @Test fun watchlistedReitUsesTheDedicatedDisplayWithoutAnInternetConnection() {
        open("8984", "東証33業種未分類", offline = true, watchlist = true)
        val list = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
        list.performScrollToNode(hasText("参考情報"))
        compose.onNodeWithText("参考情報").assertIsDisplayed()
        list.performScrollToNode(hasText("J-REITの指標を参考表示します", substring = true))
        compose.onAllNodesWithText("J-REITの指標を参考表示します", substring = true)[0].assertIsDisplayed()
    }

    @Test fun missingDividendShowsUnavailableInBuySignalInsteadOfZero() {
        open("8951", "東証33業種未分類", offline = true, dpsValue = 0.0)
        val list = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
        list.performScrollToNode(hasText("配当利回り 未取得"))
        compose.onNodeWithText("配当利回り 未取得").assertIsDisplayed()
        compose.onNodeWithText("配当利回り 0.00%").assertDoesNotExist()
    }
}
