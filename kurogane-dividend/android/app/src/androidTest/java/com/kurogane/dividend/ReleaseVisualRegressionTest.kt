package com.kurogane.dividend

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kurogane.dividend.data.CloudflareMarketRepository
import com.kurogane.dividend.data.LocalImportStore
import com.kurogane.dividend.model.Category
import com.kurogane.dividend.model.Holding
import com.kurogane.dividend.ui.screens.*
import com.kurogane.dividend.ui.theme.KuroganeDividendTheme
import com.kurogane.dividend.viewmodel.DividendViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

/** Captures only synthetic data. Golden images remain in local evidence, never in the app. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ReleaseVisualRegressionTest {
    @get:Rule val compose = createComposeRule()
    private val app = AuditApplication()
    private val models = ViewModelStore()
    private data class Case(val screen: String, val theme: String, val scale: Float = 1f)

    @After fun clean() {
        compose.runOnIdle { models.clear() }
        app.cleanUp()
    }

    @Test fun captureNineScreensInThreeThemesAndLargeFontSamples() {
        LocalImportStore(app).saveHoldings(listOf(Holding(
            id = "visual", code = "ZTEST0", name = "表示検証銘柄", sector = "検証", market = "東証",
            category = Category.JAPAN, price = 2000.0, shares = 100, acqPrice = 1000.0, dps = 80.0,
            yieldRate = 4.0, yoc = 8.0, nextPayoutTiming = "6月", nextPayoutAmount = "¥4,000",
            dividendMonths = emptyList(), dividendType = "配当金", accountType = "特定口座",
            dailyChange = "—", isPositive = true, taxFreeShares = 40
        )))
        lateinit var vm: DividendViewModel
        compose.runOnIdle {
            vm = DividendViewModel(app, false, CloudflareMarketRepository("https://example.com") {
                throw IOException()
            }, notificationScheduler = { _, _ -> })
            models.put("visual", vm)
        }
        runBlocking { vm.awaitRestoration() }
        val selected = mutableStateOf(Case("assets", "light"))
        compose.setContent {
            val case = selected.value
            val density = LocalDensity.current
            key(case) {
                CompositionLocalProvider(LocalDensity provides Density(density.density, case.scale)) {
                    KuroganeDividendTheme(themeMode = if (case.theme == "light") "light" else "dark",
                        isOledBlack = case.theme == "oled") {
                        Box(Modifier.fillMaxSize()) {
                            when (case.screen) {
                                "assets" -> AssetManagementScreen(vm)
                                "annual" -> AnnualDividendScreen(vm)
                                "roadmap" -> RoadmapScreen(vm)
                                "watchlist" -> WatchlistScreen(vm)
                                "settings" -> SettingsScreen(vm)
                                "stock" -> StockDetailScreen("ZTEST0", vm)
                                "csv" -> CsvImportScreen(onBack = {})
                                "feedback" -> ContactFeedbackScreen(onBack = {}, viewModel = vm)
                                "terms" -> TermsPrivacyScreen(onBack = {})
                            }
                        }
                    }
                }
            }
        }
        val label = InstrumentationRegistry.getArguments().getString("visualLabel", "candidate")
        require(label.matches(Regex("[A-Za-z0-9_-]+")))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "release-visual/$label").apply { mkdirs() }
        val screens = listOf("assets", "annual", "roadmap", "watchlist", "settings", "stock", "csv", "feedback", "terms")
        val cases = listOf("light", "dark", "oled").flatMap { theme -> screens.map { Case(it, theme) } } +
            listOf(1.3f, 2f).flatMap { scale -> listOf("assets", "settings", "watchlist").map { Case(it, "dark", scale) } }
        for (case in cases) {
            compose.runOnIdle { selected.value = case }
            compose.mainClock.advanceTimeBy(3_000)
            compose.waitForIdle()
            val image = compose.onRoot().captureToImage().asAndroidBitmap()
            assertTrue(image.width > 0 && image.height > 0)
            File(directory, "${case.screen}_${case.theme}_${case.scale}.png").outputStream().use {
                assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        }
        assertEquals(33, directory.listFiles { file -> file.extension == "png" }!!.size)
    }
}
