package com.kurogane.dividend

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kurogane.dividend.data.*
import com.kurogane.dividend.model.*
import com.kurogane.dividend.viewmodel.DividendViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import com.kurogane.dividend.notifications.NotificationKind
import com.kurogane.dividend.notifications.NotificationStore

internal class ReviewApplication : AuditApplication()

@RunWith(AndroidJUnit4::class)
class PortfolioPersistenceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var application: ReviewApplication
    private val stores = mutableListOf<ViewModelStore>()

    @Before fun prepare() {
        application = ReviewApplication()
        listOf("local_import_store", "display_preferences", "cloudflare_market_cache", "notification_preferences", "notification_delivery").forEach {
            application.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @After fun cleanUp() {
        instrumentation.runOnMainSync { stores.forEach(ViewModelStore::clear) }
        listOf("local_import_store", "display_preferences", "cloudflare_market_cache", "notification_preferences", "notification_delivery").forEach {
            application.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private fun model(): DividendViewModel {
        lateinit var model: DividendViewModel
        instrumentation.runOnMainSync {
            model = DividendViewModel(application, false, CloudflareMarketRepository("https://example.com") { throw IOException("offline") }, notificationScheduler = { _, _ -> })
            stores += ViewModelStore().apply { put("review", model) }
        }
        runBlocking { model.awaitRestoration() }
        return model
    }

    private fun awaitSaved(predicate: () -> Boolean) {
        val until = System.currentTimeMillis() + 10_000
        while (!predicate() && System.currentTimeMillis() < until) Thread.sleep(20)
        assertTrue("Encrypted save did not complete", predicate())
    }

    @Test fun quickEditSurvivesViewModelRecreation() {
        val model = model()
        instrumentation.runOnMainSync { assertTrue(model.updateHolding("7203", 123, 1111.0, 2222.0)) }
        awaitSaved { LocalImportStore(application).loadHoldings()?.firstOrNull { it.code == "7203" }?.shares == 123 }
        val restored = model().holdings.value.first { it.code == "7203" }
        assertEquals(123, restored.shares)
        assertEquals(1111.0, restored.acqPrice, 0.0)
    }

    @Test fun unreadableWatchlistIsNotOverwrittenByStartupOrHoldingSave() {
        val prefs = application.getSharedPreferences("local_import_store", Context.MODE_PRIVATE)
        val broken = "invalid encrypted watchlist"
        prefs.edit().putString("watchlist", broken).commit()
        val vm = model()
        instrumentation.runOnMainSync { assertTrue(vm.updateHolding("7203", 137, 1100.0)) }
        awaitSaved { LocalImportStore(application).loadHoldings()?.firstOrNull { it.code == "7203" }?.shares == 137 }
        assertEquals(broken, prefs.getString("watchlist", null))
        instrumentation.runOnMainSync { vm.toggleWatchlistMembership(vm.holdings.value.first()) }
        assertTrue(vm.watchlist.value.isEmpty())
        assertEquals(broken, prefs.getString("watchlist", null))
        assertNotNull(vm.saveError.value)
    }

    @Test fun runtimeYieldUsesTheCachedPrice() {
        application.getSharedPreferences("cloudflare_market_cache", Context.MODE_PRIVATE).edit()
            .putString("prices", """{"7203":3000.0}""").commit()
        val holding = model().holdings.value.first { it.code == "7203" }
        assertEquals(3000.0, holding.price, 0.0)
        assertEquals(holding.dps / 3000.0 * 100.0, holding.yieldRate, 0.00001)
    }

    @Test fun rapidEditsPersistTheLatestValue() {
        val model = model()
        instrumentation.runOnMainSync { (1..100).forEach { model.updateHolding("7203", it, 1000.0) } }
        awaitSaved { LocalImportStore(application).loadHoldings()?.firstOrNull { it.code == "7203" }?.shares == 100 }
        assertEquals(100, model().holdings.value.first { it.code == "7203" }.shares)
    }

    @Test fun importsAreCommittedBeforeReturningAndAreIdempotent() = runBlocking {
        val model = model()
        val rows = listOf(CsvHoldingRow("130A", "新規銘柄", 100.0, 1000.0, 2000.0, "NISA", false))
        val fields = setOf("shares", "averageCost", "currentPrice", "account")
        model.importCsvRows(rows, fields)
        model.importCsvRows(rows, fields)
        val restored = LocalImportStore(application).loadHoldings()!!.filter { it.code == "130A" }
        assertEquals(1, restored.size)
        assertEquals(100, restored.single().shares)
    }

    @Test fun watchlistTargetsAndMembershipSurviveRecreation() {
        val model = model()
        instrumentation.runOnMainSync {
            model.updateTargetYield("7203", 0.5)
            model.toggleWatchlistStock("7203")
            model.toggleWatchlistMembership(model.holdings.value.first { it.code == "2914" })
        }
        awaitSaved { LocalImportStore(application).loadWatchlist()?.none { it.code == "2914" } == true }
        val restored = model().watchlist.value
        assertEquals(3.5, restored.first { it.code == "7203" }.targetYield, 0.0)
        assertTrue(restored.first { it.code == "7203" }.alertEnabled)
        assertFalse(restored.any { it.code == "2914" })
    }

    @Test fun ciphertextIsPrivateAndTamperingIsReported() {
        val store = LocalImportStore(application)
        val model = model()
        store.saveHoldings(model.holdings.value)
        val preferences = application.getSharedPreferences("local_import_store", Context.MODE_PRIVATE)
        val encrypted = preferences.getString("holdings", null)!!
        assertFalse(encrypted.contains("トヨタ"))
        preferences.edit().putString("holdings", "AAAA").commit()
        assertThrows(IllegalArgumentException::class.java) { store.loadHoldings() }
        assertNotNull(model().saveError.value)
    }

    @Test fun zeroQuantityAndMixedAccountsRoundTripThroughKeystore() {
        val model = model()
        val original = model.holdings.value.first().copy(shares = 0, taxFreeShares = 0)
        val store = LocalImportStore(application)
        store.saveHoldings(listOf(original))
        assertEquals(listOf(original), store.loadHoldings())
        val mixed = PortfolioMath.mergeAllocations(original, listOf(HoldingAllocation(100, 1000.0, "NISA"), HoldingAllocation(100, 3000.0, "特定口座")))
        store.saveHoldings(listOf(mixed))
        assertEquals(100, store.loadHoldings()!!.single().effectiveTaxFreeShares)
    }

    @Test fun resetPersistsAnEmptyPortfolioAndRemovesDrafts() = runBlocking {
        val model = model()
        val store = LocalImportStore(application)
        store.saveFeedbackDraft(org.json.JSONObject().put("message", "保存済みの下書き"))
        model.resetLocalData()
        assertTrue(model().holdings.value.isEmpty())
        assertTrue(model().watchlist.value.isEmpty())
        assertNull(store.loadFeedbackDraft())
    }

    @Test fun startupMaskAndTaxModeAreRestored() {
        val model = model()
        instrumentation.runOnMainSync { model.setMaskAtStartup(true); model.setTaxRateMode("net") }
        val restored = model()
        assertTrue(restored.isPrivacyActive.value)
        assertEquals("net", restored.taxRateMode.value)
    }

    @Test fun notificationOptInsAreSavedAndRestored() {
        val model = model()
        instrumentation.runOnMainSync {
            model.setNotificationEnabled(NotificationKind.PRICE, true)
            model.setNotificationEnabled(NotificationKind.DIVIDEND, true)
        }
        awaitSaved { NotificationStore(application).settings().let { it.priceEnabled && it.dividendEnabled } }
        val restored = model().notificationSettings.value
        assertTrue(restored.priceEnabled)
        assertTrue(restored.dividendEnabled)
        assertNotNull(LocalImportStore(application).loadWatchlist())
    }

    @Test fun resetRemovesNotificationOptInsAndDeliveryHistory() = runBlocking {
        val store = NotificationStore(application)
        store.setEnabled(NotificationKind.PRICE, true)
        store.setEnabled(NotificationKind.DIVIDEND, true)
        store.saveDividendMonth("2026-10")
        val model = model()
        model.resetLocalData()
        assertFalse(model.notificationSettings.value.priceEnabled)
        assertFalse(model.notificationSettings.value.dividendEnabled)
        assertNull(store.dividendMonth())
        assertFalse(model().notificationSettings.value.priceEnabled)
    }
}
