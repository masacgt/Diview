package com.kurogane.dividend

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kurogane.dividend.data.*
import com.kurogane.dividend.model.*
import com.kurogane.dividend.ui.screens.*
import com.kurogane.dividend.ui.theme.KuroganeDividendTheme
import com.kurogane.dividend.viewmodel.DividendViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class OperationCompletionTest {
    @get:Rule val compose = createComposeRule()
    private val app = ReviewApplication()
    private val stores = mutableListOf<ViewModelStore>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val imagesToDelete = mutableListOf<Uri>()
    private val achievement = RoadmapAchievement(11, "STEP 11: 月25,000円", 25000, 21234, 10,
        "インフラ自給期", "週末の外食や趣味にかかる費用を自給", 20000)

    private fun clear() = listOf("local_import_store", "display_preferences", "cloudflare_market_cache", "notification_preferences", "notification_delivery").forEach {
        app.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Before fun prepare() { clear(); seedReviewPortfolio(app) }
    @After fun cleanup() {
        compose.runOnIdle { stores.forEach(ViewModelStore::clear) }
        imagesToDelete.forEach { context.contentResolver.delete(it, null, null) }
        clear()
        app.cleanUp()
    }
    private fun model(application: android.app.Application = app): DividendViewModel {
        lateinit var vm: DividendViewModel
        compose.runOnIdle {
            vm = DividendViewModel(application, false, CloudflareMarketRepository("https://example.com") { throw IOException("offline") }, notificationScheduler = { _, _ -> })
            stores += ViewModelStore().apply { put("operations", vm) }
        }
        runBlocking { vm.awaitRestoration() }
        return vm
    }

    @Test fun calculationAndExportSettingsSurviveRecreationAndReset() {
        val vm = model()
        compose.runOnIdle {
            vm.setCalculationBasis("cost")
            vm.setRoadmapTargetYield(4.5)
            vm.setCompositionThreshold(9)
            vm.setRoadmapShareTab(1)
            vm.setRoadmapShareMasked(true)
        }
        val restored = model()
        assertEquals("cost", restored.calculationBasis.value)
        assertEquals(4.5, restored.roadmapTargetYield.value, 0.0)
        assertEquals(9, restored.compositionThreshold.value)
        assertEquals(1, restored.roadmapShareTab.value)
        assertTrue(restored.roadmapShareMasked.value)
        runBlocking(Dispatchers.Main) { restored.resetLocalData() }
        assertEquals("eval", restored.calculationBasis.value)
        assertEquals(4.0, restored.roadmapTargetYield.value, 0.0)
        assertEquals(5, restored.compositionThreshold.value)
        assertEquals(0, restored.roadmapShareTab.value)
        assertFalse(restored.roadmapShareMasked.value)
    }

    @Test fun watchlistRegistrationNeverAddsHoldingsAndPersistsWithoutDuplicates() {
        val vm = model()
        val before = vm.holdings.value
        compose.runOnIdle { vm.removeWatchlistStock("7203") }
        runBlocking(Dispatchers.Main) {
            assertTrue(vm.registerWatchlistStock("7203"))
            assertTrue(vm.registerWatchlistStock("7203"))
        }
        assertEquals(before, vm.holdings.value)
        assertEquals(1, model().watchlist.value.count { it.code == "7203" })
        val watched = vm.watchlist.value
        runBlocking(Dispatchers.Main) {
            // 9999 is present in the bundled security catalog; use an absent code.
            assertTrue(runCatching { vm.registerWatchlistStock("0000") }.isFailure)
        }
        assertEquals(watched, vm.watchlist.value)
        assertEquals(before, vm.holdings.value)
    }

    @Test fun allocationValidatesAndPersistsAllAccounts() {
        val vm = model()
        val old = vm.holdings.value.first { it.code == "7203" }
        runBlocking(Dispatchers.Main) {
            assertFalse(vm.registerResolvedAllocations("7203", listOf(HoldingAllocation(-1, 100.0, "特定口座")), null))
            assertTrue(vm.registerResolvedAllocations("7203", listOf(
                HoldingAllocation(10, 2000.0, "新NISA成長枠"), HoldingAllocation(20, 3000.0, "特定口座")
            ), null))
        }
        val actual = model().holdings.value.first { it.code == "7203" }
        assertEquals(old.shares + 30, actual.shares)
        assertEquals(old.effectiveTaxFreeShares + 10, actual.effectiveTaxFreeShares)
        assertEquals((old.shares * old.acqPrice + 80000) / actual.shares, actual.acqPrice, .001)
    }

    @Test fun unearnedCertificateIsDisabledAndCannotBeExportedFromSavedChoice() {
        val vm = model()
        compose.runOnIdle { vm.setRoadmapShareTab(1) }
        compose.setContent { KuroganeDividendTheme { RoadmapShareSheet(vm, achievement.copy(completedSteps = 9), true, {}) } }
        compose.onNodeWithText("LV.1 踏破認定証").assertIsNotEnabled()
        compose.onNodeWithText("LEVEL 1 COMPLETE").assertDoesNotExist()
        compose.onNodeWithText("CURRENT ACTIVE QUEST").assertExists()
    }

    @Test fun roadmapAllocationSheetAppliesSelectedStockAndValidatesBeforeSaving() {
        val vm = model()
        val before = vm.holdings.value.first { it.code == "7203" }.shares
        val dismissed = java.util.concurrent.atomic.AtomicBoolean(false)
        compose.setContent { KuroganeDividendTheme { RoadmapAllocationSheet(vm) { dismissed.set(true) } } }
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("7203")
        compose.onNodeWithText("適用").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("選択中の銘柄").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasSetTextAction())[1].performScrollTo().performTextReplacement("-1")
        compose.onNodeWithText(app.getString(R.string.roadmap_text_40)).performScrollTo().performClick()
        compose.onNodeWithText(app.getString(R.string.roadmap_allocation_invalid)).assertExists()
        assertFalse(dismissed.get())
        assertEquals(before, vm.holdings.value.first { it.code == "7203" }.shares)
        compose.onAllNodes(hasSetTextAction())[1].performScrollTo().performTextReplacement("10")
        compose.onAllNodes(hasSetTextAction())[2].performScrollTo().performTextReplacement("1234")
        compose.onNodeWithText(app.getString(R.string.roadmap_text_40)).performScrollTo().performClick()
        compose.waitUntil(10_000) { dismissed.get() }
        assertEquals(before + 10, vm.holdings.value.first { it.code == "7203" }.shares)
    }

    @Test fun failedAllocationSaveDoesNotChangeSharesAndRetryAddsExactlyOnce() {
        val fail = java.util.concurrent.atomic.AtomicBoolean(false)
        val failingApp = object : android.app.Application() {
            init { attachBaseContext(context) }
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = app.filesDir
            override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
                val prefs = app.getSharedPreferences(name, mode)
                if (name != "local_import_store") return prefs
                return object : android.content.SharedPreferences by prefs {
                    override fun edit(): android.content.SharedPreferences.Editor {
                        val editor = prefs.edit()
                        return object : android.content.SharedPreferences.Editor by editor {
                            override fun putString(key: String?, value: String?): android.content.SharedPreferences.Editor {
                                editor.putString(key, value)
                                return this
                            }
                            override fun commit(): Boolean = if (fail.get()) false else editor.commit()
                        }
                    }
                }
            }
        }
        val vm = model(failingApp)
        val before = vm.holdings.value
        val allocations = listOf(HoldingAllocation(10, 1000.0, "特定口座"))
        fail.set(true)
        runBlocking(Dispatchers.Main) { assertTrue(runCatching { vm.registerResolvedAllocations("7203", allocations, null) }.isFailure) }
        assertEquals(before, vm.holdings.value)
        fail.set(false)
        runBlocking(Dispatchers.Main) { assertTrue(vm.registerResolvedAllocations("7203", allocations, null)) }
        assertEquals(before.first { it.code == "7203" }.shares + 10, model().holdings.value.first { it.code == "7203" }.shares)
    }

    @Test fun certificateShareOpensSystemChooserAndCanBeCancelled() {
        val vm = model()
        compose.runOnIdle { vm.setRoadmapShareTab(1); vm.setRoadmapShareMasked(true) }
        compose.setContent { KuroganeDividendTheme { RoadmapShareSheet(vm, achievement, false, {}) } }
        compose.onNodeWithText("LEVEL 1 COMPLETE").assertExists()
        compose.onNodeWithText("21,234", substring = true).assertDoesNotExist()
        compose.onNodeWithText("20,000", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("roadmap_export_action").performScrollTo().performClick()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun activities() = android.os.ParcelFileDescriptor.AutoCloseInputStream(
            automation.executeShellCommand("dumpsys activity activities")
        ).bufferedReader().use { it.readText() }
        compose.waitUntil(10_000) { activities().contains("ChooserActivity") }
        assertTrue(automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
        compose.waitForIdle()
    }

    private fun imageUris(): Set<Uri> = context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID),
        "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?", arrayOf("Diview-%"), null
    )!!.use { cursor -> buildSet {
        while (cursor.moveToNext()) add(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0)))
    } }

    @Test fun maskedCardIsActuallySavedAsPngAndCanBeSharedWithTemporaryReadAccess() {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= 29)
        val vm = model()
        val before = imageUris()
        compose.setContent { KuroganeDividendTheme { RoadmapShareSheet(vm, achievement, true, {}) } }
        compose.onNodeWithText("現在実績: ¥21,234", substring = true).assertExists()
        compose.onNode(isToggleable()).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("21,234", substring = true).assertDoesNotExist()
        compose.onNodeWithText("25,000", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("roadmap_export_action").performScrollTo().performSemanticsAction(SemanticsActions.OnClick) { click ->
            click()
            click() // Same frame: disabling the button alone does not prevent a second request.
        }
        compose.waitUntil(15_000) { imageUris().minus(before).isNotEmpty() }
        val added = imageUris().minus(before)
        imagesToDelete += added
        assertEquals(1, added.size)
        val uri = added.single()
        val bitmap = context.contentResolver.openInputStream(uri)!!.use(BitmapFactory::decodeStream)!!
        assertTrue(bitmap.width > 100 && bitmap.height > 100)
        val out = File(context.getExternalFilesDir(null), "operation-check-masked.png")
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val imageStore = RoadmapImageStore(context)
        val cache = imageStore.createPng(bitmap)
        try {
            val intent = imageStore.shareIntent(cache)
            assertEquals(Intent.ACTION_SEND, intent.action)
            assertEquals("image/png", intent.type)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals("content", intent.clipData!!.getItemAt(0).uri.scheme)
            assertEquals(cache.length(), context.contentResolver.openInputStream(intent.clipData!!.getItemAt(0).uri)!!.use { it.readBytes().size.toLong() })
            val destination = imageStore.createPng(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
            try {
                imageStore.writeDocument(cache, imageStore.shareIntent(destination).clipData!!.getItemAt(0).uri)
                assertArrayEquals(cache.readBytes(), destination.readBytes())
            } finally { destination.delete() }
            assertTrue(runCatching { imageStore.writeDocument(cache, Uri.parse("content://missing.provider/file")) }.isFailure)
            assertTrue(runCatching { imageStore.pendingFile("../private.png") }.isFailure)
        } finally { cache.delete(); bitmap.recycle() }
    }
}
