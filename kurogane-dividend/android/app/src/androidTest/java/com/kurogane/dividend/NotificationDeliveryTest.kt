package com.kurogane.dividend

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurogane.dividend.data.CloudflareMarketQuote
import com.kurogane.dividend.data.CloudflarePriceCatalogSnapshot
import com.kurogane.dividend.data.LocalImportStore
import com.kurogane.dividend.notifications.*
import com.kurogane.dividend.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZonedDateTime

@RunWith(AndroidJUnit4::class)
class NotificationDeliveryTest {
    private val app = ReviewApplication()
    private val store = NotificationStore(app)
    private val local = LocalImportStore(app)
    private var currentTime = ZonedDateTime.parse("2026-10-05T08:30:00+09:00[Asia/Tokyo]")
    private val clock = { currentTime }
    private var cacheReads = 0
    private var posted = 0
    private val names = listOf("local_import_store", "notification_preferences", "notification_delivery", "notification_price_cache")

    private fun queueDueDividendRightsEvent() {
        val event = DividendRightsEvent(
            id = "rights-event-7203-2026-10-30",
            code = "7203",
            name = "トヨタ",
            exDividendDate = LocalDate.of(2026, 10, 29),
            finalRightsDate = LocalDate.of(2026, 10, 28),
            recordDate = LocalDate.of(2026, 10, 30)
        )
        store.observeDividendRightsEvents(
            listOf(event),
            LocalDate.of(2026, 10, 30),
            ZonedDateTime.parse("2026-10-30T08:30:00+09:00[Asia/Tokyo]").toInstant().toEpochMilli()
        )
        store.observeDividendRightsEvents(
            listOf(event),
            LocalDate.of(2026, 10, 31),
            ZonedDateTime.parse("2026-10-31T08:30:00+09:00[Asia/Tokyo]").toInstant().toEpochMilli()
        )
        currentTime = ZonedDateTime.parse("2026-10-31T17:30:00+09:00[Asia/Tokyo]")
    }

    @Before fun prepare() {
        currentTime = ZonedDateTime.parse("2026-10-05T08:30:00+09:00[Asia/Tokyo]")
        names.forEach { app.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
        local.saveWatchlist(listOf(WatchlistStock("7203", "トヨタ", "東証", "輸送用機器", "jp", 3000.0,
            dps = 100.0, currentYield = 3.33, targetYield = 4.0, targetPrice = 2500.0, dailyChange = "", isPositive = true,
            payoutMonths = listOf(10), payoutTiming = "", desc = "", alertEnabled = true)))
        local.saveHoldings(listOf(Holding("h", "7203", "トヨタ", "輸送用機器", "東証", Category.JAPAN, 2500.0,
            shares = 100, acqPrice = 2000.0, dps = 100.0, yieldRate = 4.0, yoc = 5.0, nextPayoutTiming = "",
            nextPayoutAmount = "", dividendMonths = listOf(10), dividendType = "", accountType = "NISA", dailyChange = "", isPositive = true)))
        NotificationPriceCache(app).merge(
            CloudflarePriceCatalogSnapshot(
                mapOf("7203" to CloudflareMarketQuote(2500.0, 100.0, quoteDate = currentTime.toLocalDate().toString())),
                currentTime.toLocalDate().toString()
            ),
            currentTime.toInstant().toEpochMilli()
        )
    }

    @After fun clean() { names.forEach { app.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() } }

    private fun engine(allowed: Boolean = true, publish: Boolean = true,
        load: suspend () -> Map<String, CachedNotificationPrice> = { cacheReads++; NotificationPriceCache(app).read() }) = NotificationDeliveryEngine(
        app, load, clock, { allowed }, { _, _, _ -> posted++; publish })

    @Test fun disabledOrUnpermittedNotificationsDoNotFetchOrSend() = runBlocking {
        engine().run(NotificationKind.PRICE)
        store.setEnabled(NotificationKind.PRICE, true)
        engine(allowed = false).run(NotificationKind.PRICE)
        assertEquals(0, cacheReads)
        assertEquals(0, posted)
        assertTrue(store.priceStates().isEmpty())
    }

    @Test fun deliveryMarkersSurviveRecreationAndPreventDuplicates() = runBlocking {
        store.setEnabled(NotificationKind.PRICE, true)
        engine().run(NotificationKind.PRICE)
        assertEquals(0, posted)
        currentTime = ZonedDateTime.parse("2026-10-05T17:30:00+09:00[Asia/Tokyo]")
        engine().run(NotificationKind.PRICE)
        engine().run(NotificationKind.PRICE)
        assertEquals(1, posted)
        assertEquals("2026-10-05", NotificationStore(app).priceStates().getValue("7203").notifiedDate)
    }

    @Test fun blockedPublicationDoesNotConsumeTheArrival() = runBlocking {
        store.setEnabled(NotificationKind.PRICE, true)
        engine(publish = false).run(NotificationKind.PRICE)
        assertTrue(store.priceStates().isEmpty())
        currentTime = ZonedDateTime.parse("2026-10-05T17:30:00+09:00[Asia/Tokyo]")
        engine(publish = false).run(NotificationKind.PRICE)
        assertTrue(store.priceStates().isEmpty())
        engine().run(NotificationKind.PRICE)
        assertEquals(2, posted)
        assertEquals("2026-10-05", store.priceStates().getValue("7203").notifiedDate)
    }

    @Test fun offlineNotificationUsesOnlyTheLocallySavedPriceCatalog() = runBlocking {
        store.setEnabled(NotificationKind.PRICE, true)
        engine().run(NotificationKind.PRICE)
        currentTime = ZonedDateTime.parse("2026-10-05T17:30:00+09:00[Asia/Tokyo]")
        engine().run(NotificationKind.PRICE)
        assertEquals(1, posted)
        assertEquals(2, cacheReads)
    }

    @Test fun offlineCachedPriceAlertPostsThroughAndroidNotificationManager() = runBlocking {
        val notifications = app.getSystemService(android.app.NotificationManager::class.java)
        val service = DiviewNotifications(app)
        notifications.cancel(DiviewNotifications.PRICE_ID)
        service.createChannels()
        store.setEnabled(NotificationKind.PRICE, true)
        try {
            assertTrue("Android price notifications must be permitted", service.canPost(DiviewNotifications.PRICE_CHANNEL))
            val cached = NotificationPriceCache(app).read()["7203"]
                ?: throw AssertionError("The local price cache must contain the seeded quote")
            assertEquals(2500.0, cached.price, 0.0)
            assertEquals("2026-10-05", cached.quoteDate)
            assertEquals(currentTime.toInstant().toEpochMilli(), cached.fetchedAtMillis)
            assertEquals("2026-10-05", cached.marketDataDate)
            assertEquals(null, cached.generation)

            val delivery = NotificationDeliveryEngine(app, now = clock)
            delivery.run(NotificationKind.PRICE)
            currentTime = ZonedDateTime.parse("2026-10-05T17:30:00+09:00[Asia/Tokyo]")
            delivery.run(NotificationKind.PRICE)

            val postedNotification = notifications.activeNotifications
                .firstOrNull { it.id == DiviewNotifications.PRICE_ID }
            assertNotNull("A real Android price alert should be active", postedNotification)
            val notification = postedNotification!!.notification
            assertTrue(notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)
                .toString().contains("目標利回り"))
            val text = (notification.extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)
                ?: notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)).toString()
            assertTrue(text.contains("7203"))
            assertTrue(text.contains("取得株価"))
            assertTrue(text.contains("2,500"))
            assertEquals("2026-10-05", store.priceStates().getValue("7203").notifiedDate)
        } finally {
            notifications.cancel(DiviewNotifications.PRICE_ID)
        }
    }

    @Test fun staleQuoteCancelsAQueuedAlertBeforeDelivery() = runBlocking {
        store.setEnabled(NotificationKind.PRICE, true)
        engine().run(NotificationKind.PRICE)
        assertEquals(1, store.pendingPriceAlerts().size)
        currentTime = ZonedDateTime.parse("2026-10-09T17:30:00+09:00[Asia/Tokyo]")
        engine().run(NotificationKind.PRICE)
        assertEquals(0, posted)
        assertTrue(store.pendingPriceAlerts().isEmpty())
    }

    @Test fun absentLocalQuoteDoesNotTriggerANetworkFallback() = runBlocking {
        app.getSharedPreferences("notification_price_cache", Context.MODE_PRIVATE).edit().clear().commit()
        store.setEnabled(NotificationKind.PRICE, true)
        engine().run(NotificationKind.PRICE)
        assertEquals(0, posted)
        assertEquals(1, cacheReads)
    }

    @Test fun disableDuringCacheReadStopsLatePublication() = runBlocking {
        store.setEnabled(NotificationKind.PRICE, true)
        engine(load = { store.setEnabled(NotificationKind.PRICE, false); mapOf("7203" to CachedNotificationPrice(2500.0, "2026-10-05")) }).run(NotificationKind.PRICE)
        assertEquals(0, posted)
        assertTrue(store.priceStates().isEmpty())
    }

    @Test fun deletedStockDuringCacheReadCannotBeNotified() = runBlocking {
        store.setEnabled(NotificationKind.PRICE, true)
        engine(load = { local.saveWatchlist(emptyList()); mapOf("7203" to CachedNotificationPrice(2500.0, "2026-10-05")) }).run(NotificationKind.PRICE)
        assertEquals(0, posted)
    }

    @Test fun resetDuringFetchClearsBothKindsAndStopsLatePublication() = runBlocking {
        store.setEnabled(NotificationKind.PRICE, true)
        store.setEnabled(NotificationKind.DIVIDEND, true)
        engine(load = { store.clear(); mapOf("7203" to CachedNotificationPrice(2500.0, "2026-10-05")) }).run(NotificationKind.PRICE)
        assertFalse(store.settings().priceEnabled)
        assertFalse(store.settings().dividendEnabled)
        assertEquals(0, posted)
        assertTrue(store.priceStates().isEmpty())
    }

    @Test fun cancellationIsPropagatedWithoutSending() = runBlocking {
        store.setEnabled(NotificationKind.PRICE, true)
        try { engine(load = { throw CancellationException("cancel") }).run(NotificationKind.PRICE); fail("Expected cancellation") }
        catch (_: CancellationException) { }
        assertEquals(0, posted)
    }

    @Test fun passedDividendRightsDateSendsOnceWithoutNetworkAccess() = runBlocking {
        store.setEnabled(NotificationKind.DIVIDEND, true)
        queueDueDividendRightsEvent()
        engine().run(NotificationKind.DIVIDEND)
        engine().run(NotificationKind.DIVIDEND)
        assertEquals(1, posted)
        assertEquals(0, cacheReads)
        assertTrue(store.pendingDividendRightsEvents().isEmpty())
    }

    @Test fun corruptPortfolioDoesNotBlockConfirmedDividendRightsNotification() = runBlocking {
        store.setEnabled(NotificationKind.DIVIDEND, true)
        queueDueDividendRightsEvent()
        app.getSharedPreferences("local_import_store", Context.MODE_PRIVATE).edit().putString("holdings", "invalid").commit()
        engine().run(NotificationKind.DIVIDEND)
        assertEquals(1, posted)
        assertEquals(0, cacheReads)
        assertTrue(store.pendingDividendRightsEvents().isEmpty())
    }

    @Test fun notificationDestinationsAreRestricted() {
        fun intent(url: String) = Intent(DiviewNotifications.ACTION_OPEN, Uri.parse(url))
        assertEquals("watchlist", DiviewNotifications.destination(intent("diview://notification/watchlist")))
        assertEquals("annual", DiviewNotifications.destination(intent("diview://notification/annual")))
        assertEquals("settings", DiviewNotifications.destination(intent("diview://notification/settings")))
        listOf("https://notification/settings", "diview://other/settings", "diview://notification/settings?x=1",
            "diview://notification/../../settings", "diview://notification/settings#x", "diview://notification/contact_feedback",
            "diview://user@notification/settings", "diview://notification:123/settings").forEach {
            assertNull(DiviewNotifications.destination(intent(it)))
        }
        assertNull(DiviewNotifications.destination(Intent("other", Uri.parse("diview://notification/settings"))))
    }
}
