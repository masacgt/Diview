package com.kurogane.dividend

import android.app.Activity
import android.os.Build
import android.os.Bundle
import com.kurogane.dividend.data.AutoBackupSnapshot
import com.kurogane.dividend.data.DividendHistoryOverride
import com.kurogane.dividend.data.DvmBackupKind
import com.kurogane.dividend.data.LocalImportStore
import com.kurogane.dividend.model.*
import com.kurogane.dividend.notifications.NotificationKind
import com.kurogane.dividend.notifications.NotificationScheduler
import com.kurogane.dividend.notifications.NotificationStore
import androidx.test.runner.AndroidJUnitRunner
import androidx.work.WorkManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** Normal JUnit runner unless explicitly invoked by the host's disposable-AVD guard. */
class ReleaseFixtureInstrumentation : AndroidJUnitRunner() {
    private lateinit var arguments: Bundle
    private var diagnostic = false
    override fun onCreate(arguments: Bundle?) {
        diagnostic = arguments?.getString("allowSyntheticFixture") == "true"
        this.arguments = arguments ?: Bundle()
        super.onCreate(arguments)
    }

    private val benefits = mapOf("1306" to listOf(ShareholderBenefit("検証優待", "1000")))
    private val climate = mapOf("1306" to StockClimate.DEFENSIVE)
    private val history = mapOf("1306" to listOf(DividendHistoryOverride(2025, 1000.0, 80.0, true, true)))

    override fun onStart() {
        if (!diagnostic) {
            super.onStart()
            return
        }
        try {
            require(Build.HARDWARE in setOf("ranchu", "goldfish")) { "Disposable emulator required" }
            require(arguments.getString("allowSyntheticFixture") == "true")
            val store = LocalImportStore(targetContext)
            when (arguments.getString("operation")) {
                "seed" -> {
                    val fixture = Holding(
                        id = "acceptance-fixture", code = "ZTEST0", name = "移行検証銘柄", sector = "検証", market = "東証",
                        category = Category.JAPAN, price = 2000.0, shares = 100, acqPrice = 1000.0,
                        dps = 80.0, yieldRate = 4.0, yoc = 8.0, nextPayoutTiming = "6月", nextPayoutAmount = "¥4,000",
                        dividendMonths = emptyList(), dividendType = "配当金", accountType = "特定口座",
                        dailyChange = "—", isPositive = true, taxFreeShares = 40
                    )
                    val count = arguments.getString("holdingCount")?.toInt() ?: 1
                    require(count in 0..2000)
                    store.saveHoldings(List(count) { index -> fixture.copy(id = "acceptance-$index", code = "ZTEST$index", name = "移行検証銘柄 $index") })
                    store.saveWatchlist(listOf(WatchlistStock(
                        code = "ZBACKUP", name = "無配の移行検証", market = "東証", sector = "検証", category = "jp",
                        price = 100.0, dps = 0.0, currentYield = 0.0, targetYield = 4.0, targetPrice = 0.0,
                        dailyChange = "—", isPositive = true, payoutMonths = emptyList(), payoutTiming = "配当月未取得", desc = "",
                        isNoDividendConfirmed = true
                    )))
                    DvmBackupKind.entries.forEach { store.saveBackup(it, "[]".toByteArray()) }
                    store.saveShareholderBenefits(benefits)
                    store.saveStockClimateOverrides(climate)
                    store.saveDividendHistoryOverrides(history)
                    check(targetContext.getSharedPreferences("display_preferences", 0).edit()
                        .putString("theme_mode", "dark").putBoolean("oled_black", true)
                        .putString("selected_accent", "blue").putString("tax_rate_mode", "net")
                        .putInt("composition_threshold", 7).commit())
                }
                "verify" -> {
                    check(store.loadShareholderBenefits() == benefits)
                    check(store.loadStockClimateOverrides() == climate)
                    check(store.loadDividendHistoryOverrides() == history)
                }
                "schedule-notifications", "verify-notifications", "disable-notifications" -> {
                    val notificationStore = NotificationStore(targetContext)
                    val disabled = arguments.getString("operation") == "disable-notifications"
                    if (arguments.getString("operation") != "verify-notifications") {
                        val settings = notificationStore.setEnabled(NotificationKind.PRICE, !disabled)
                        repeat(2) { NotificationScheduler.sync(targetContext, settings) }
                    }
                    check(notificationStore.settings().priceEnabled == !disabled)
                    val work = WorkManager.getInstance(targetContext)
                    val names = listOf(NotificationScheduler.PRICE_MORNING, NotificationScheduler.PRICE_EVENING)
                    val ids = names.map { name ->
                        val active = work.getWorkInfosForUniqueWork(name).get(15, TimeUnit.SECONDS).filterNot { it.state.isFinished }
                        check(active.size == if (disabled) 0 else 1)
                        active.singleOrNull()?.id?.toString().orEmpty()
                    }
                    sendStatus(1, Bundle().apply { putString("activeWorkIds", ids.joinToString(",")) })
                }
                "interrupt-snapshot" -> {
                    AutoBackupSnapshot(File(targetContext.filesDir, "diview_auto_backup.json"))
                        .write(JSONArray().put(JSONObject().put("id", "uncommitted")), JSONArray()) {
                            sendStatus(1, Bundle().apply { putString("phase", "atomic-open") })
                            Thread.sleep(30_000)
                            null
                        }
                }
                else -> error("Unknown diagnostic operation")
            }
            finish(Activity.RESULT_OK, Bundle().apply { putBoolean("completed", true) })
        } catch (failure: Exception) {
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("failureCategory", failure.javaClass.simpleName) })
        }
    }
}
