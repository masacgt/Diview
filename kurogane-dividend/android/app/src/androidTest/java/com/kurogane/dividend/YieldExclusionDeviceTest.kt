package com.kurogane.dividend

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurogane.dividend.data.CloudflareMarketRepository
import com.kurogane.dividend.data.DvmBackupExporter
import com.kurogane.dividend.data.DvmBackupImporter
import com.kurogane.dividend.data.DvmBackupKind
import com.kurogane.dividend.data.LocalImportStore
import com.kurogane.dividend.model.Category
import com.kurogane.dividend.model.Holding
import com.kurogane.dividend.ui.screens.AssetManagementScreen
import com.kurogane.dividend.ui.screens.StockDetailScreen
import com.kurogane.dividend.ui.theme.KuroganeDividendTheme
import com.kurogane.dividend.viewmodel.DividendViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/** Uses only isolated synthetic data; the user's portfolio is never edited. */
@RunWith(AndroidJUnit4::class)
class YieldExclusionDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val application = AuditApplication()
    private val models = ViewModelStore()

    @After fun cleanup() {
        compose.runOnIdle { models.clear() }
        application.cleanUp()
    }

    private fun holding(code: String, dps: Double) = Holding(
        id = code, code = code, name = "検証銘柄$code", sector = "検証", market = "東証",
        category = Category.JAPAN, price = 1000.0, shares = 100, acqPrice = 1000.0,
        dps = dps, yieldRate = dps / 10.0, yoc = dps / 10.0, nextPayoutTiming = "",
        nextPayoutAmount = "", dividendMonths = emptyList(), dividendType = "", accountType = "特定口座",
        dailyChange = "—", isPositive = true
    )

    private fun model(key: String): DividendViewModel {
        lateinit var vm: DividendViewModel
        compose.runOnIdle {
            vm = DividendViewModel(application, false, CloudflareMarketRepository("https://example.com") {
                throw IOException("offline fixture")
            }, notificationScheduler = { _, _ -> })
            models.put(key, vm)
        }
        runBlocking { vm.awaitRestoration() }
        return vm
    }

    @Test fun portableBackupImportRestoresExclusionAndAverage() {
        val store = LocalImportStore(application)
        store.saveHoldings(listOf(holding("7203", 40.0), holding("1306", 0.0)))
        val vm = model("backup")
        compose.runOnIdle { vm.setYieldExcluded("1306", true) }
        compose.waitUntil(10_000) { "1306" in store.loadYieldExcludedCodes() }
        val bytes = DvmBackupExporter.exportExtras(emptyMap(), emptyMap(), emptyMap(), yieldExcludedCodes = vm.yieldExcludedCodes.value)
        compose.runOnIdle { vm.setYieldExcluded("1306", false) }
        compose.waitUntil(10_000) { store.loadYieldExcludedCodes().isEmpty() }
        val preview = DvmBackupImporter.parse(DvmBackupKind.DIVIEW_EXTRAS.fileName, bytes)
        runBlocking { vm.importDvmBackups(listOf(preview)) }
        assertEquals(setOf("1306"), vm.yieldExcludedCodes.value)
        assertEquals(setOf("1306"), store.loadYieldExcludedCodes())
        compose.waitUntil(10_000) { kotlin.math.abs(vm.assetManagementSummary.value.averageYield - 4.0) < 0.0001 }
        val restored = model("backup-restored")
        assertEquals(setOf("1306"), restored.yieldExcludedCodes.value)
        compose.waitUntil(10_000) { kotlin.math.abs(restored.assetManagementSummary.value.averageYield - 4.0) < 0.0001 }
    }

    @Test fun switchUpdatesVisibleAverageAndSurvivesRecreation() {
        val store = LocalImportStore(application)
        store.saveHoldings(listOf(holding("7203", 40.0), holding("1306", 0.0)))
        val vm = model("first")
        compose.waitUntil(10_000) { kotlin.math.abs(vm.assetManagementSummary.value.averageYield - 2.0) < 0.0001 }
        val showDetail = mutableStateOf(false)
        compose.setContent { KuroganeDividendTheme {
            if (showDetail.value) StockDetailScreen("1306", vm) else AssetManagementScreen(vm)
        } }
        compose.onNodeWithText("平均配当利回り").assertIsDisplayed()
        compose.onNodeWithText("2.00").assertIsDisplayed()
        compose.runOnIdle { showDetail.value = true }
        compose.onAllNodes(hasScrollToIndexAction())[0].performScrollToIndex(6)
        val toggle = compose.onNodeWithContentDescription("平均配当利回りから除外")
        toggle.assertIsDisplayed().assertIsOff().performClick().assertIsOn()
        compose.waitUntil(10_000) { kotlin.math.abs(vm.assetManagementSummary.value.averageYield - 4.0) < 0.0001 }
        compose.waitUntil(10_000) { "1306" in store.loadYieldExcludedCodes() }
        compose.runOnIdle { showDetail.value = false }
        compose.onNodeWithText("4.00").assertIsDisplayed()
        val restored = model("restored")
        assertEquals(setOf("1306"), restored.yieldExcludedCodes.value)
        compose.waitUntil(10_000) { kotlin.math.abs(restored.assetManagementSummary.value.averageYield - 4.0) < 0.0001 }
        compose.runOnIdle { showDetail.value = true }
        compose.onAllNodes(hasScrollToIndexAction())[0].performScrollToIndex(6)
        compose.onNodeWithContentDescription("平均配当利回りから除外").assertIsOn().performClick().assertIsOff()
        compose.waitUntil(10_000) { kotlin.math.abs(vm.assetManagementSummary.value.averageYield - 2.0) < 0.0001 }
        compose.waitUntil(10_000) { store.loadYieldExcludedCodes().isEmpty() }
    }
}
