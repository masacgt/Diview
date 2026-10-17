package com.kurogane.dividend

import android.os.SystemClock
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kurogane.dividend.data.CloudflareMarketRepository
import com.kurogane.dividend.data.DividendHistoryOverride
import com.kurogane.dividend.data.LocalImportStore
import com.kurogane.dividend.model.ShareholderBenefit
import com.kurogane.dividend.model.StockClimate
import com.kurogane.dividend.viewmodel.DividendViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class ReleaseStartupRegressionTest {
    @Test fun encryptedOverridesAreReadyBeforeRestorationCompletes() {
        val app = AuditApplication()
        val models = ViewModelStore()
        val benefits = mapOf("1306" to listOf(ShareholderBenefit("検証優待", "1000")))
        val climate = mapOf("1306" to StockClimate.DEFENSIVE)
        val history = mapOf("1306" to listOf(DividendHistoryOverride(2025, 1000.0, 80.0, true, true)))
        val store = LocalImportStore(app)
        store.saveShareholderBenefits(benefits)
        store.saveStockClimateOverrides(climate)
        store.saveDividendHistoryOverrides(history)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var constructorMs = 0L
        lateinit var vm: DividendViewModel
        val started = SystemClock.elapsedRealtime()
        try {
            instrumentation.runOnMainSync {
                val construction = SystemClock.elapsedRealtime()
                vm = DividendViewModel(app, false, CloudflareMarketRepository("https://example.com") {
                    throw IOException()
                }, notificationScheduler = { _, _ -> })
                constructorMs = SystemClock.elapsedRealtime() - construction
                models.put("startup", vm)
            }
            runBlocking { vm.awaitRestoration() }
            assertTrue(vm.localDataRestored.value)
            assertEquals(benefits, vm.shareholderBenefits.value)
            assertEquals(climate, vm.stockClimateOverrides.value)
            assertEquals(history, vm.dividendHistoryOverrides.value)
            android.util.Log.i("ReleaseAcceptance", "Startup constructorMs=$constructorMs restorationMs=${SystemClock.elapsedRealtime() - started}")
        } finally {
            instrumentation.runOnMainSync { models.clear() }
            app.cleanUp()
        }
    }
}
