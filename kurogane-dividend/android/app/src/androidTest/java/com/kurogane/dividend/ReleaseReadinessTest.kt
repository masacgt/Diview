package com.kurogane.dividend

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kurogane.dividend.data.CloudflareMarketRepository
import com.kurogane.dividend.data.CloudflareSearchHit
import com.kurogane.dividend.ui.components.SearchSheet
import com.kurogane.dividend.ui.components.AddHoldingSheet
import com.kurogane.dividend.ui.components.ExDividendMatrixDialog
import com.kurogane.dividend.ui.screens.*
import com.kurogane.dividend.ui.theme.KuroganeDividendTheme
import com.kurogane.dividend.viewmodel.DividendViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TestRule
import org.junit.runners.model.Statement
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class ReleaseReadinessTest {
    // Change the actual device configuration before launching the Compose host;
    // dialogs use the host configuration rather than a LocalDensity override.
    @get:Rule(order = 0) val enlargedDisplay = TestRule { base, description ->
        object : Statement() {
            override fun evaluate() {
                if (description.methodName != "screensRemainScrollableInBothThemesWithLargeFontAndDisplay") {
                    base.evaluate(); return
                }
                val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
                fun shell(command: String): String = android.os.ParcelFileDescriptor.AutoCloseInputStream(
                    automation.executeShellCommand(command)
                ).bufferedReader().use { it.readText().trim() }
                val originalFont = shell("settings get system font_scale").toFloatOrNull() ?: 1f
                val densities = shell("wm density")
                val override = Regex("Override density: (\\d+)").find(densities)?.groupValues?.get(1)?.toInt()
                val physical = Regex("Physical density: (\\d+)").find(densities)!!.groupValues[1].toInt()
                try {
                    shell("settings put system font_scale 2.0")
                    shell("wm density ${((override ?: physical) * 1.15f).roundToInt()}")
                    base.evaluate()
                } finally {
                    shell("settings put system font_scale $originalFont")
                    shell("wm density ${override ?: "reset"}")
                }
            }
        }
    }
    @get:Rule(order = 1) val compose = createComposeRule()
    private val stores = ViewModelStore()
    private val app = ReviewApplication()

    @After fun cleanup() {
        compose.runOnIdle { stores.clear() }
        listOf("local_import_store", "display_preferences", "cloudflare_market_cache", "notification_preferences", "notification_delivery").forEach {
            app.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test fun failedSearchCanRetryAndReplaceLocalFallback() {
        val attempts = AtomicInteger()
        compose.setContent { KuroganeDividendTheme { SearchSheet(true, {}, {}, {
            if (attempts.incrementAndGet() == 1) throw IOException("offline")
            listOf(CloudflareSearchHit("7203", "再試行後の検索結果"))
        }) } }
        compose.onNode(hasSetTextAction()).performTextInput("7203")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("再試行").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("再試行").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("再試行後の検索結果").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("再試行後の検索結果").assertIsDisplayed()
        assertEquals(2, attempts.get())
    }

    @Test fun successfulEmptySearchExplainsHowToChangeConditions() {
        compose.setContent { KuroganeDividendTheme { SearchSheet(true, {}, {}, { emptyList() }) } }
        compose.onNode(hasSetTextAction()).performTextInput("9999")
        val message = app.getString(R.string.search_empty)
        compose.waitUntil(10_000) { compose.onAllNodesWithText(message).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(message).assertIsDisplayed()
    }

    private fun model(): DividendViewModel {
        lateinit var model: DividendViewModel
        compose.runOnIdle {
            model = DividendViewModel(app, false, CloudflareMarketRepository("https://example.com") { throw IOException("offline") }, notificationScheduler = { _, _ -> })
            stores.put("quality", model)
        }
        runBlocking { model.awaitRestoration() }
        return model
    }

    @Test fun settingsExposeSwitchSelectionAndAccessibleSliderActions() {
        val vm = model()
        compose.setContent { KuroganeDividendTheme { SettingsScreen(vm) } }
        val oled = app.getString(R.string.settings_text_3)
        compose.onNodeWithText(oled).performScrollTo()
        val switch = compose.onNode(hasText(oled) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        switch.assertIsOff().performClick().assertIsOn()
        assertTrue(vm.isOledBlack.value)
        val slider = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
        slider.performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { assertTrue(it(8f)) }
        slider.assertRangeInfoEquals(androidx.compose.ui.semantics.ProgressBarRangeInfo(8f, 3f..10f, 6))
    }

    @Test fun screensRemainScrollableInBothThemesWithLargeFontAndDisplay() {
        val vm = model()
        val screen = mutableIntStateOf(0)
        val light = mutableStateOf(false)
        compose.setContent {
            Box(Modifier.safeDrawingPadding()) {
                KuroganeDividendTheme(themeMode = if (light.value) "light" else "dark") {
                    when (screen.intValue) {
                        0 -> AssetManagementScreen(vm)
                        1 -> AnnualDividendScreen(vm)
                        2 -> RoadmapScreen(vm)
                        3 -> WatchlistScreen(vm)
                        4 -> SettingsScreen(vm)
                        5 -> StockDetailScreen("7203", vm)
                        6 -> CsvImportScreen()
                        7 -> ContactFeedbackScreen({}, vm)
                        8 -> TermsPrivacyScreen({})
                        9 -> SearchSheet(true, {}, {}, { emptyList() })
                        10 -> AddHoldingSheet(true, "8316", {}, onResolveSecurity = { null }, onRegisterCollective = { _, _ -> false })
                        11 -> ExDividendMatrixDialog(true, {})
                        12 -> RoadmapShareSheet(vm, com.kurogane.dividend.model.RoadmapAchievement(
                            11, "STEP 11: 月25,000円", 25000, 22000, 10, "インフラ自給期", "週末の外食や趣味にかかる費用を自給", 20000
                        ), true, {})
                        13 -> RoadmapAllocationSheet(vm, {})
                    }
                }
            }
        }
        val names = listOf("asset", "annual", "roadmap", "watchlist", "settings", "stock", "csv", "feedback", "terms", "search", "add", "matrix", "roadmap-share", "roadmap-allocation")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val images = File(context.getExternalFilesDir(null), "release-quality").apply { mkdirs() }
        for (isLight in listOf(false, true)) {
            compose.runOnIdle {
                vm.assetScrollIndex = 0; vm.assetScrollOffset = 0
                vm.annualScrollIndex = 0; vm.annualScrollOffset = 0
                vm.roadmapScrollIndex = 0; vm.roadmapScrollOffset = 0
                vm.watchlistScrollIndex = 0; vm.watchlistScrollOffset = 0
                light.value = isLight
            }
            names.forEachIndexed { index, name ->
                compose.runOnIdle { screen.intValue = index }
                compose.mainClock.advanceTimeBy(1_000)
                compose.waitForIdle()
                assertTrue("$name must expose content", compose.onAllNodes(hasText("", substring = true)).fetchSemanticsNodes().isNotEmpty())
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                // UiAutomation captures the displayed frame, which is drawn after
                // Compose semantics and Android's dialog-window transitions settle.
                android.os.SystemClock.sleep(500)
                InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
                    File(images, "${if (isLight) "light" else "dark"}-$name-large.png").outputStream().use {
                        assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
                    }
                }
                if (index < 9) compose.onAllNodes(hasScrollAction()).onFirst().performTouchInput { swipeUp() }
                if (index == 12) compose.onNodeWithTag("roadmap_export_action").performScrollTo().assertIsDisplayed()
                if (index == 13) compose.onNodeWithText(app.getString(R.string.roadmap_text_40)).performScrollTo().assertIsDisplayed()
            }
        }
    }
}
