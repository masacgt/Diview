package com.kurogane.dividend

import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kurogane.dividend.data.*
import com.kurogane.dividend.model.*
import com.kurogane.dividend.viewmodel.DividendViewModel
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL

@RunWith(AndroidJUnit4::class)
class QualityFeatureTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var app: ReviewApplication
    private val stores = mutableListOf<ViewModelStore>()
    private val document = """{"security":{"code":"7203"},"financials":{"annual":[{"fiscal_year":"2026/03","sales":1000,"operating_profit":100,"equity_ratio_computed":45}]},"dividends":{"history_basis":"calendar_year_observed_distributions","records":[{"calendar_year":2025,"calendar_year_distribution_per_share":95}]},"valuation":{"history":[{"fiscal_year":"2026/03","eps_adjusted":250,"bps_adjusted":2000}]}}"""

    @Before fun prepare() {
        app = ReviewApplication()
        clear()
        seedReviewPortfolio(app)
    }
    private fun clear() = listOf("local_import_store", "display_preferences", "cloudflare_market_cache", "notification_preferences", "notification_delivery").forEach {
        app.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
    }
    @After fun cleanup() {
        instrumentation.runOnMainSync { stores.forEach(ViewModelStore::clear) }
        clear()
        app.cleanUp()
    }
    private fun model(): DividendViewModel {
        lateinit var vm: DividendViewModel
        val repo = CloudflareMarketRepository("https://example.com") { url -> object : HttpURLConnection(url) {
            override fun getResponseCode() = 200
            override fun getInputStream() = ByteArrayInputStream(document.toByteArray())
            override fun disconnect() {}
            override fun connect() {}
            override fun usingProxy() = false
        } }
        instrumentation.runOnMainSync {
            vm = DividendViewModel(app, false, repo, notificationScheduler = { _, _ -> })
            stores += ViewModelStore().apply { put("quality", vm) }
        }
        runBlocking { vm.awaitRestoration() }
        return vm
    }
    @Test fun allDomesticHoldingsAndWatchlistUseJapaneseNamesAndTse33Master() {
        val vm = model()
        val master = Tse33IndustryMaster.identities(app)
        val classified = app.assets.open("tse33_industry_master.tsv").bufferedReader().useLines { lines ->
            lines.drop(1).map { it.split('\t')[3] }.toSet()
        }
        assertEquals(33, classified.size)
        assertEquals(classified + "東証33業種未分類", master.values.map { it.industry }.toSet())
        for (holding in vm.holdings.value.filter { it.category == Category.JAPAN }) {
            assertEquals(master.getValue(holding.code).name, holding.name)
            assertEquals(master.getValue(holding.code).industry, holding.sector)
        }
        for (stock in vm.watchlist.value.filter { it.category == "jp" }) assertEquals(master.getValue(stock.code).industry, stock.sector)
        assertEquals("アップル", vm.holdings.value.first { it.code == "AAPL" }.name)
    }
    @Test fun cachedFxAndUsTaxChoiceSurviveRecreation() {
        app.getSharedPreferences("cloudflare_market_cache", Context.MODE_PRIVATE).edit().putString("fx", """{"base":"USD","quote":"JPY","rate":160,"date":"2026-01-01"}""").commit()
        val vm = model()
        val usd = vm.holdings.value.first { it.code == "AAPL" }
        assertEquals(160.0, usd.usdJpyRate!!, 0.0)
        instrumentation.runOnMainSync { vm.setUsTaxDeduction(false) }
        val restored = model()
        assertFalse(restored.usTaxDeduction.value)
        val restoredUsd = restored.holdings.value.first { it.code == "AAPL" }
        assertFalse(restoredUsd.usWithholdingEnabled)
        assertEquals(restoredUsd.annualDividend * .79685, restoredUsd.getEffectiveAnnualDividend("net"), .001)
    }
    @Test fun actualFinancialDataReplacesReferenceDpsAndCanBeRestoredOffline() = runBlocking {
        val vm = model()
        val data = vm.fetchFinancialData("7203", true)!!
        assertEquals("2026/03", data.period)
        assertEquals(95.0, data.dividends.single().dps, 0.0)
        assertEquals(95.0, vm.holdings.value.first { it.code == "7203" }.dps, 0.0)
        val restored = model()
        assertEquals(95.0, restored.holdings.value.first { it.code == "7203" }.dps, 0.0)
        assertEquals(95.0, restored.financialData.value.getValue("7203").dividends.single().dps, 0.0)
        assertEquals(45.0, restored.financialData.value.getValue("7203").profile.equityRatio, 0.0)
    }
    @Test fun lockPreferenceDefaultsOffAndPersistsWhenSet() {
        val vm = model()
        assertFalse(vm.biometricEnabled.value)
        instrumentation.runOnMainSync { vm.setBiometricEnabled(true) }
        assertTrue(model().biometricEnabled.value)
        instrumentation.runOnMainSync { vm.setBiometricEnabled(false) }
        assertFalse(model().biometricEnabled.value)
    }
}
