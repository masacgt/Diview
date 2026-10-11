package com.kurogane.dividend

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import java.io.IOException
import java.time.ZonedDateTime

@RunWith(AndroidJUnit4::class)
class NotificationDeliveryTest {
    private val app = ReviewApplication()
    private val store = NotificationStore(app)
    private val local = LocalImportStore(app)
    private val clock = { ZonedDateTime.parse("2026-10-05T08:30:00+09:00[Asia/Tokyo]") }
    private var fetches = 0
    private var posted = 0
    private val names = listOf("local_import_store", "notification_preferences", "notification_delivery")

    @Before fun prepare() {
        names.forEach { app.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
        local.saveWatchlist(listOf(WatchlistStock("7203", "トヨタ", "東証", "輸送用機器", "jp", 3000.0,
            dps = 100.0, currentYield = 3.33, targetYield = 4.0, targetPrice = 2500.0, dailyChange = "", isPositive = true,
            payoutMonths = listOf(10), payoutTiming = "", desc = "", alertEnabled = true)))
        local.saveHoldings(listOf(Holding("h", "7203", "トヨタ", "輸送用機器", "東証", Category.JAPAN, 2500.0,
            shares = 100, acqPrice = 2000.0, dps = 100.0, yieldRate = 4.0, yoc = 5.0, nextPayoutTiming = "",
            nextPayoutAmount = "", dividendMonths = listOf(10), dividendType = "", accountType = "NISA", dailyChange = "", isPositive = true)))
    }

    @After fun clean() { names.forEach { app.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() } }

    private fun engine(allowed: Boolean = true, publish: Boolean = true,
        fetch: suspend () -> Map<String, Double> = { fetches++; mapOf("7203" to 2500.0) }) = NotificationDeliveryEngine(
        app, fetch, clock, { allowed }, { _, _, _ -> posted++; publish })

    @Test fun disabledOrUnpermittedNotificationsDoNotFetchOrSend() = runBlocking {
        engine().run(NotificationKind.PRICE)
        store.setEnabled(NotificationKind.PRICE, true)
        engine(allowed = false).run(NotificationKind.PRICE)
        assertEquals(0, fetches)
        assertEquals(0, posted)
        assertTrue(store.priceStates().isEmpty())
    }

    @Test fun deliveryMarkersSurviveRecreationAndPreventDuplicates() = runBlocking {
        store.setEnabled(NotificationKind.PRICE, true)
        engine().run(NotificationKind.PRICE)
        engine().run(NotificationKind.PRICE)
        assertEquals(1, posted)
        assertEquals("2026-10-05", NotificationStore(app).priceStates().getValue("7203").notifiedDate)
    }

    @Test fun blockedPublicationDoesNotConsumeTheArrival() = runBlocking {
        store.setEnabled(NotificationKind.PRICE, true)
        engine(publish = false).run(NotificationKind.PRICE)
        assertTrue(store.priceStates().isEmpty())
        engine().run(NotificationKind.PRICE)
        assertEquals(2, posted)
        assertEquals("2026-10-05", store.priceStates().getValue("7203").notifiedDate)
    }

    @Test fun networkFailureDoesNotNotifyUsingCachedQuotes() = runBlocking {
        store.setEnabled(NotificationKind.PRICE, true)
        try { engine(fetch = { throw IOException("offline") }).run(NotificationKind.PRICE); fail("Expected network failure") }
        catch (_: IOException) { }
        assertEquals(0, posted)
        assertTrue(store.priceStates().isEmpty())
    }

    @Test fun disableDuringFetchStopsLatePublication() = runBlocking {
        store.setEnabled(NotificationKind.PRICE, true)
        engine(fetch = { store.setEnabled(NotificationKind.PRICE, false); mapOf("7203" to 2500.0) }).run(NotificationKind.PRICE)
        assertEquals(0, posted)
        assertTrue(store.priceStates().isEmpty())
    }

    @Test fun deletedStockDuringFetchCannotBeNotified() = runBlocking {
        store.setEnabled(NotificationKind.PRICE, true)
        engine(fetch = { local.saveWatchlist(emptyList()); mapOf("7203" to 2500.0) }).run(NotificationKind.PRICE)
        assertEquals(0, posted)
    }

    @Test fun resetDuringFetchClearsBothKindsAndStopsLatePublication() = runBlocking {
        store.setEnabled(NotificationKind.PRICE, true)
        store.setEnabled(NotificationKind.DIVIDEND, true)
        engine(fetch = { store.clear(); mapOf("7203" to 2500.0) }).run(NotificationKind.PRICE)
        assertFalse(store.settings().priceEnabled)
        assertFalse(store.settings().dividendEnabled)
        assertEquals(0, posted)
        assertTrue(store.priceStates().isEmpty())
    }

    @Test fun cancellationIsPropagatedWithoutSending() = runBlocking {
        store.setEnabled(NotificationKind.PRICE, true)
        try { engine(fetch = { throw CancellationException("cancel") }).run(NotificationKind.PRICE); fail("Expected cancellation") }
        catch (_: CancellationException) { }
        assertEquals(0, posted)
    }

    @Test fun dividendMonthSendsOnceWithoutNetworkAccess() = runBlocking {
        store.setEnabled(NotificationKind.DIVIDEND, true)
        engine().run(NotificationKind.DIVIDEND)
        engine().run(NotificationKind.DIVIDEND)
        assertEquals(1, posted)
        assertEquals(0, fetches)
        assertEquals("2026-10", store.dividendMonth())
    }

    @Test fun corruptEncryptedPortfolioCannotProduceANotification() = runBlocking {
        store.setEnabled(NotificationKind.DIVIDEND, true)
        app.getSharedPreferences("local_import_store", Context.MODE_PRIVATE).edit().putString("holdings", "invalid").commit()
        try { engine().run(NotificationKind.DIVIDEND); fail("Expected corrupt data to fail") }
        catch (_: IllegalArgumentException) { }
        assertEquals(0, posted)
        assertNull(store.dividendMonth())
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
