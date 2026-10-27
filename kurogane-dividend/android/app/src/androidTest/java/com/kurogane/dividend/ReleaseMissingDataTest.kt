package com.kurogane.dividend

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurogane.dividend.data.CloudflareMarketRepository
import com.kurogane.dividend.data.LocalImportStore
import com.kurogane.dividend.model.Category
import com.kurogane.dividend.model.Holding
import com.kurogane.dividend.ui.screens.AssetManagementScreen
import com.kurogane.dividend.ui.theme.KuroganeDividendTheme
import com.kurogane.dividend.viewmodel.DividendViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class ReleaseMissingDataTest {
    @get:Rule val compose = createComposeRule()
    private val app = AuditApplication()
    private val models = ViewModelStore()

    @After fun cleanup() {
        compose.runOnIdle { models.clear() }
        app.cleanUp()
    }

    @Test fun missingFundPriceAndDividendAreNotShownAsZeroLossOrNoDividend() {
        val holding = Holding(
            id = "fund", code = "FUND:TEST", name = "検証ファンド", sector = "投資信託", market = "投資信託",
            category = Category.FUND, price = 0.0, shares = 50_000, acqPrice = 20_000.0,
            dps = 0.0, yieldRate = 0.0, yoc = 0.0, nextPayoutTiming = "", nextPayoutAmount = "",
            dividendMonths = emptyList(), dividendType = "", accountType = "特定口座", dailyChange = "—", isPositive = true
        )
        LocalImportStore(app).saveHoldings(listOf(holding))
        lateinit var vm: DividendViewModel
        compose.runOnIdle {
            vm = DividendViewModel(app, false, CloudflareMarketRepository("https://example.com") { throw IOException() },
                notificationScheduler = { _, _ -> })
            models.put("missing", vm)
        }
        runBlocking { vm.awaitRestoration() }
        compose.setContent { KuroganeDividendTheme { AssetManagementScreen(vm) } }
        compose.waitUntil(10_000) { vm.assetManagementSummary.value.hasMissingValuation && vm.assetManagementSummary.value.hasMissingDividend }
        compose.onAllNodesWithText("評価額未取得")[0].assertIsDisplayed()
        compose.onAllNodesWithText("損益未取得")[0].assertIsDisplayed()
        compose.onAllNodesWithText("配当未取得")[0].assertIsDisplayed()
        compose.onAllNodesWithText("-100.0%", substring = true).assertCountEquals(0)
    }
}
