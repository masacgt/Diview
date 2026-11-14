package com.kurogane.dividend

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.kurogane.dividend.data.CloudflareMarketQuote
import com.kurogane.dividend.data.CloudflarePriceCatalogSnapshot
import com.kurogane.dividend.data.CloudflareMarketRepository
import com.kurogane.dividend.data.LocalImportStore
import com.kurogane.dividend.model.WatchlistStock
import com.kurogane.dividend.notifications.DiviewNotifications
import com.kurogane.dividend.notifications.DividendNotificationWorker
import com.kurogane.dividend.notifications.NotificationKind
import com.kurogane.dividend.notifications.NotificationPriceCache
import com.kurogane.dividend.notifications.NotificationStore
import com.kurogane.dividend.notifications.TseTradingCalendar
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Opt-in acceptance flow for the production R2 quote catalog and the actual offline WorkManager worker.
 * Invoke individual methods only on the disposable API 35 AVD with
 * -e allowLiveR2NotificationAcceptance true. The host controls the clock and network between phases.
 */
@RunWith(AndroidJUnit4::class)
class ProductionCatalogOfflineWorkerTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun requireExplicitDisposableAvdRun() {
        assumeTrue(
            "Live production acceptance requires an explicit opt-in",
            InstrumentationRegistry.getArguments().getString("allowLiveR2NotificationAcceptance") == "true"
        )
        assumeTrue("This acceptance flow is limited to an emulator", Build.HARDWARE in setOf("ranchu", "goldfish"))
    }

    @Test fun fetchProductionQuoteAndPersistTheDeviceCache() = runBlocking {
        requireExplicitDisposableAvdRun()
        val work = WorkManager.getInstance(context)
        work.cancelAllWork().result.get(30, TimeUnit.SECONDS)
        listOf("local_import_store", "notification_preferences", "notification_delivery", "notification_price_cache")
            .forEach { name -> context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit() }

        val snapshot = CloudflareMarketRepository().fetchTargetedPriceSnapshot(listOf("7203"))
        val quote = snapshot.quotes["7203"]
            ?: throw AssertionError("Production R2-backed quote catalog must contain 7203")
        assertNotNull("Quote must carry its market date", quote.quoteDate)
        assertTrue("Quote price must be positive", quote.price.isFinite() && quote.price > 0.0)
        LocalImportStore(context).saveWatchlist(
            listOf(
                WatchlistStock(
                    code = "7203",
                    name = "トヨタ自動車",
                    market = "東証",
                    sector = "輸送用機器",
                    category = "jp",
                    price = quote.price,
                    dps = 100.0,
                    currentYield = 100.0 / quote.price * 100.0,
                    targetYield = 0.1,
                    targetPrice = 0.0,
                    dailyChange = "",
                    isPositive = true,
                    payoutMonths = emptyList(),
                    payoutTiming = "",
                    desc = "",
                    alertEnabled = true
                )
            )
        )
        NotificationStore(context).setEnabled(NotificationKind.PRICE, true)
        val fetchedAtMillis = System.currentTimeMillis()
        assertTrue(
            "Production price cache must finish writing before the app process exits",
            NotificationPriceCache(context).mergeAndCommit(
                snapshot.copy(quotes = mapOf("7203" to quote)),
                fetchedAtMillis
            )
        )

        val cached = NotificationPriceCache(context).read()["7203"]
            ?: throw AssertionError("Production quote and provenance must remain in the device cache")
        assertEquals(quote.price, cached.price, 0.0)
        assertEquals(quote.quoteDate, cached.quoteDate)
        assertEquals(fetchedAtMillis, cached.fetchedAtMillis)
        assertEquals(snapshot.marketDataDate, cached.marketDataDate)
        assertEquals(snapshot.generation, cached.generation)
        assertTrue("Watchlist fixture must persist", LocalImportStore(context).loadWatchlist()?.single()?.alertEnabled == true)
        val notifications = DiviewNotifications(context)
        notifications.createChannels()
        assertTrue("Notification permission and channel must allow posting", notifications.canPost(DiviewNotifications.PRICE_CHANNEL))
    }

    @Test fun morningWorkerAppliesProductionQuoteFreshnessWithoutNetwork() {
        requireExplicitDisposableAvdRun()
        assertOffline()
        assertTrue("Run this phase inside the 08:30 notification window", com.kurogane.dividend.notifications.NotificationPolicy.withinWindow(ZonedDateTime.now(), true))

        val quote = NotificationPriceCache(context).read()["7203"]
            ?: throw AssertionError("Production quote must remain in the device cache")
        val isFresh = TseTradingCalendar.isFreshQuote(
            quote.quoteDate,
            LocalDate.now(com.kurogane.dividend.notifications.NotificationPolicy.japanZone)
        )
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(DiviewNotifications.PRICE_ID)
        val info = enqueuePriceWorker(morning = true)
        assertEquals(WorkInfo.State.SUCCEEDED, info.state)
        if (isFresh) {
            assertEquals(1, NotificationStore(context).pendingPriceAlerts().size)
            assertTrue(NotificationStore(context).priceStates().getValue("7203").ready)
        } else {
            assertTrue("Stale production prices must not queue notifications", NotificationStore(context).pendingPriceAlerts().isEmpty())
            assertFalse(NotificationStore(context).priceStates()["7203"]?.ready == true)
        }
        assertTrue(manager.activeNotifications.none { it.id == DiviewNotifications.PRICE_ID })
    }

    @Test fun eveningWorkerHonorsProductionQuoteFreshnessWithoutNetwork() {
        requireExplicitDisposableAvdRun()
        assertOffline()
        assertTrue("Run this phase inside the 17:30 notification window", com.kurogane.dividend.notifications.NotificationPolicy.withinWindow(ZonedDateTime.now(), false))

        val cached = NotificationPriceCache(context).read()["7203"]
            ?: throw AssertionError("Production quote must remain in the device cache")
        val isFresh = TseTradingCalendar.isFreshQuote(
            cached.quoteDate,
            LocalDate.now(com.kurogane.dividend.notifications.NotificationPolicy.japanZone)
        )
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(DiviewNotifications.PRICE_ID)
        val info = enqueuePriceWorker(morning = false)
        assertEquals(WorkInfo.State.SUCCEEDED, info.state)
        val active = awaitPriceNotification(manager)
        if (isFresh) {
            assertNotNull("Actual WorkManager worker must post an Android notification for a fresh quote", active)
            assertAlertNotification(active!!.notification)
            assertTrue(NotificationStore(context).pendingPriceAlerts().isEmpty())
            assertEquals(
                LocalDate.now(com.kurogane.dividend.notifications.NotificationPolicy.japanZone).toString(),
                NotificationStore(context).priceStates().getValue("7203").notifiedDate
            )
        } else {
            assertTrue("A stale production quote must not post an Android notification", active == null)
            assertTrue(NotificationStore(context).pendingPriceAlerts().isEmpty())
        }
    }

    @Test fun seedFreshLocalQuoteForOfflineWorkerDelivery() = runBlocking {
        requireExplicitDisposableAvdRun()
        context.getSystemService(NotificationManager::class.java).cancel(DiviewNotifications.PRICE_ID)
        listOf("local_import_store", "notification_preferences", "notification_delivery", "notification_price_cache")
            .forEach { name -> context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit() }
        var marketDate = LocalDate.now(com.kurogane.dividend.notifications.NotificationPolicy.japanZone)
        while (!TseTradingCalendar.isTradingSession(marketDate)) marketDate = marketDate.minusDays(1)
        seedWatchlistAndCache(price = 2_500.0, quoteDate = marketDate.toString())
    }

    @Test fun morningWorkerQueuesFreshLocalFixtureWithoutNetwork() {
        requireExplicitDisposableAvdRun()
        assertOffline()
        assertTrue("Run this phase inside the 08:30 notification window", com.kurogane.dividend.notifications.NotificationPolicy.withinWindow(ZonedDateTime.now(), true))
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(DiviewNotifications.PRICE_ID)
        val info = enqueuePriceWorker(morning = true)
        assertEquals(WorkInfo.State.SUCCEEDED, info.state)
        assertEquals(1, NotificationStore(context).pendingPriceAlerts().size)
        assertFalse(NotificationStore(context).priceStates()["7203"]?.ready == true)
        assertTrue(manager.activeNotifications.none { it.id == DiviewNotifications.PRICE_ID })
    }

    @Test fun eveningWorkerPostsFreshLocalFixtureWithoutNetwork() {
        requireExplicitDisposableAvdRun()
        assertOffline()
        assertTrue("Run this phase inside the 17:30 notification window", com.kurogane.dividend.notifications.NotificationPolicy.withinWindow(ZonedDateTime.now(), false))
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(DiviewNotifications.PRICE_ID)
        val info = enqueuePriceWorker(morning = false)
        assertEquals(WorkInfo.State.SUCCEEDED, info.state)
        assertTrue(DiviewNotifications(context).canPost(DiviewNotifications.PRICE_CHANNEL))
        val active = awaitPriceNotification(manager)
        assertNotNull("Actual WorkManager worker must post an Android notification from the local cache", active)
        assertAlertNotification(active!!.notification)
        assertTrue(NotificationStore(context).pendingPriceAlerts().isEmpty())
        assertEquals(
            LocalDate.now(com.kurogane.dividend.notifications.NotificationPolicy.japanZone).toString(),
            NotificationStore(context).priceStates().getValue("7203").notifiedDate
        )
    }

    private suspend fun seedWatchlistAndCache(price: Double, quoteDate: String) {
        LocalImportStore(context).saveWatchlist(
            listOf(
                WatchlistStock(
                    code = "7203",
                    name = "トヨタ自動車",
                    market = "東証",
                    sector = "輸送用機器",
                    category = "jp",
                    price = price,
                    dps = 100.0,
                    currentYield = 100.0 / price * 100.0,
                    targetYield = 0.1,
                    targetPrice = 0.0,
                    dailyChange = "",
                    isPositive = true,
                    payoutMonths = emptyList(),
                    payoutTiming = "",
                    desc = "",
                    alertEnabled = true
                )
            )
        )
        NotificationStore(context).setEnabled(NotificationKind.PRICE, true)
        val fetchedAtMillis = System.currentTimeMillis()
        val snapshot = CloudflarePriceCatalogSnapshot(
            mapOf("7203" to CloudflareMarketQuote(price, 100.0, quoteDate = quoteDate)),
            quoteDate
        )
        assertTrue(
            NotificationPriceCache(context).mergeAndCommit(snapshot, fetchedAtMillis)
        )
        val cached = NotificationPriceCache(context).read()["7203"]
            ?: throw AssertionError("Durably saved fixture quote must be readable")
        assertEquals(price, cached.price, 0.0)
        assertEquals(quoteDate, cached.quoteDate)
        assertEquals(fetchedAtMillis, cached.fetchedAtMillis)
        assertEquals(snapshot.marketDataDate, cached.marketDataDate)
        assertEquals(snapshot.generation, cached.generation)
        val notifications = DiviewNotifications(context)
        notifications.createChannels()
        assertTrue("Notification permission and channel must allow posting", notifications.canPost(DiviewNotifications.PRICE_CHANNEL))
    }

    private fun assertAlertNotification(notification: Notification) {
        val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        val body = (notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: notification.extras.getCharSequence(Notification.EXTRA_TEXT)).toString()
        assertTrue(title.contains("目標利回り"))
        assertTrue(body.contains("7203"))
        assertTrue(body.contains("取得株価"))
    }

    private fun awaitPriceNotification(manager: NotificationManager): StatusBarNotification? {
        val deadline = SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(5)
        var active = manager.activeNotifications.firstOrNull { it.id == DiviewNotifications.PRICE_ID }
        while (active == null && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(50)
            active = manager.activeNotifications.firstOrNull { it.id == DiviewNotifications.PRICE_ID }
        }
        return active
    }

    private fun assertOffline() {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        assertTrue("Disable airplane mode / all network interfaces before this worker phase", connectivity.activeNetwork == null)
    }

    private fun enqueuePriceWorker(morning: Boolean): WorkInfo {
        val request = OneTimeWorkRequestBuilder<DividendNotificationWorker>()
            .setInputData(workDataOf("kind" to NotificationKind.PRICE.name, "morning" to morning))
            .addTag("diview.offline-catalog-acceptance")
            .build()
        val work = WorkManager.getInstance(context)
        work.enqueue(request).result.get(15, TimeUnit.SECONDS)
        val deadline = SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(60)
        var info = work.getWorkInfoById(request.id).get(10, TimeUnit.SECONDS)
        while (!info.state.isFinished && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(200)
            info = work.getWorkInfoById(request.id).get(10, TimeUnit.SECONDS)
        }
        return info
    }
}
