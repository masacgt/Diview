package com.kurogane.dividend.notifications

import com.kurogane.dividend.model.Category
import com.kurogane.dividend.model.Holding
import com.kurogane.dividend.model.WatchlistStock
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

class NotificationPolicyTest {
    private val date = LocalDate.of(2026, 10, 5)
    private fun stock() = WatchlistStock("7203", "トヨタ", "東証", "輸送用機器", "jp", 999.0,
        dps = 100.0, currentYield = 99.0, targetYield = 4.0, targetPrice = 2500.0,
        dailyChange = "", isPositive = true, payoutMonths = listOf(10), payoutTiming = "", desc = "", alertEnabled = true)
    private fun plan(stocks: List<WatchlistStock> = listOf(stock()), prices: Map<String, Double> = mapOf("7203" to 2500.0),
        states: Map<String, PriceAlertState> = emptyMap(), day: LocalDate = date) = NotificationPolicy.priceAlerts(stocks, prices, states, day)

    @Test fun exactThresholdIsAnArrival() {
        val result = plan()
        assertEquals(1, result.hits.size)
        assertEquals(4.0, result.hits.single().yield, 0.0)
        assertTrue(result.states.getValue("7203").ready)
    }

    @Test fun ignoresCachedCurrentYieldAndUsesFetchedPrice() {
        assertTrue(plan(prices = mapOf("7203" to 3000.0)).hits.isEmpty())
    }

    @Test fun disabledAlertsNeverNotify() {
        assertTrue(plan(stocks = listOf(stock().copy(alertEnabled = false))).hits.isEmpty())
    }

    @Test fun continuouslyReadyStockDoesNotNotifyAgainTheNextDay() {
        val state = plan().states.mapValues { it.value.copy(notifiedDate = date.toString()) }
        assertTrue(plan(states = state).hits.isEmpty())
        assertTrue(plan(states = state, day = date.plusDays(1)).hits.isEmpty())
    }

    @Test fun crossingBackAboveTheThresholdRearmsOnALaterDay() {
        val sent = plan().states.mapValues { it.value.copy(notifiedDate = date.toString()) }
        val below = plan(prices = mapOf("7203" to 3000.0), states = sent).states
        assertTrue(plan(states = below).hits.isEmpty())
        assertEquals(1, plan(states = below, day = date.plusDays(1)).hits.size)
    }

    @Test fun changedTargetIsEvaluatedWithoutSendingTwiceInADay() {
        val sent = plan().states.mapValues { it.value.copy(notifiedDate = date.toString()) }
        assertTrue(plan(stocks = listOf(stock().copy(targetYield = 3.5)), states = sent).hits.isEmpty())
        assertEquals(1, plan(stocks = listOf(stock().copy(targetYield = 3.5)), states = sent, day = date.plusDays(1)).hits.size)
    }

    @Test fun missingAndInvalidQuotesPreservePreviousState() {
        val old = mapOf("7203" to PriceAlertState("4.0:100.0", true, date.toString()))
        listOf(emptyMap(), mapOf("7203" to 0.0), mapOf("7203" to -1.0),
            mapOf("7203" to Double.NaN), mapOf("7203" to Double.POSITIVE_INFINITY)).forEach { prices ->
            val result = plan(prices = prices, states = old)
            assertTrue(result.hits.isEmpty())
            assertEquals(old, result.states)
        }
    }

    @Test fun invalidTargetsAndDividendsDoNotGenerateAlerts() {
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, 101.0).forEach { target ->
            assertTrue(plan(stocks = listOf(stock().copy(targetYield = target))).hits.isEmpty())
        }
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { dividend ->
            assertTrue(plan(stocks = listOf(stock().copy(dps = dividend))).hits.isEmpty())
        }
    }

    @Test fun normalizesCodesAndRemovesDisabledAndDeletedDeliveryMarkers() {
        val previous = mapOf("DELETED" to PriceAlertState("old", true))
        val result = plan(stocks = listOf(stock().copy(code = " aapl "), stock().copy(code = "AAPL")),
            prices = mapOf("aapl" to 2500.0), states = previous)
        assertEquals(1, result.hits.size)
        assertEquals(setOf("AAPL"), result.states.keys)
    }

    @Test fun settingsAreOptInForBothKinds() {
        val settings = NotificationSettings()
        assertFalse(settings.enabled(NotificationKind.PRICE))
        assertFalse(settings.enabled(NotificationKind.DIVIDEND))
    }

    @Test fun delayIsComputedInJapanEvenWhenDeviceUsesAnotherTimezone() {
        val now = ZonedDateTime.parse("2026-10-04T23:00:00Z")
        assertEquals(30L, NotificationPolicy.nextDelay(now, NotificationPolicy.morning).toMinutes())
        assertEquals(570L, NotificationPolicy.nextDelay(now, NotificationPolicy.evening).toMinutes())
    }

    @Test fun exactOrPassedScheduledTimeMovesToTheNextDay() {
        val now = ZonedDateTime.parse("2026-10-05T08:30:00+09:00[Asia/Tokyo]")
        assertEquals(1440L, NotificationPolicy.nextDelay(now, LocalTime.of(8, 30)).toMinutes())
        assertEquals(1439L, NotificationPolicy.nextDelay(now.plusMinutes(1), LocalTime.of(8, 30)).toMinutes())
    }

    @Test fun delayedJobsCannotNotifyOvernight() {
        fun at(time: String) = ZonedDateTime.parse("2026-10-05T$time+09:00[Asia/Tokyo]")
        assertFalse(NotificationPolicy.withinWindow(at("08:29:59"), true))
        assertTrue(NotificationPolicy.withinWindow(at("08:30:00"), true))
        assertFalse(NotificationPolicy.withinWindow(at("12:00:00"), true))
        assertFalse(NotificationPolicy.withinWindow(at("17:29:59"), false))
        assertTrue(NotificationPolicy.withinWindow(at("17:30:00"), false))
        assertFalse(NotificationPolicy.withinWindow(at("21:00:00"), false))
        assertFalse(NotificationPolicy.withinWindow(at("03:00:00"), false))
    }

    @Test fun dividendReminderOnlyIncludesOwnedPayingSecuritiesForTheMonth() {
        val holding = Holding("h", "7203", "トヨタ", "輸送用機器", "東証", Category.JAPAN, 2500.0,
            shares = 100, acqPrice = 2000.0, dps = 100.0, yieldRate = 4.0, yoc = 5.0,
            nextPayoutTiming = "", nextPayoutAmount = "", dividendMonths = listOf(10, 10), dividendType = "", accountType = "NISA", dailyChange = "", isPositive = true)
        val holdings = listOf(holding, holding, holding.copy(code = "8306", shares = 0),
            holding.copy(code = "8316", dps = 0.0), holding.copy(code = "9432", dividendMonths = listOf(6)),
            holding.copy(code = "9433", dps = Double.NaN))
        assertEquals(listOf(holding), NotificationPolicy.dividendHoldings(holdings, date))
        assertTrue(NotificationPolicy.dividendHoldings(holdings, date.plusMonths(1)).isEmpty())
    }
}
