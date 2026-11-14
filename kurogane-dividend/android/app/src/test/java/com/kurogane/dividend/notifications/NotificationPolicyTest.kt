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
        states: Map<String, PriceAlertState> = emptyMap(), day: LocalDate = date) = NotificationPolicy.priceAlerts(
        stocks, prices.mapValues { CachedNotificationPrice(it.value, day.toString()) }, states, day
    )

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

    @Test fun normalizesJapaneseCodesAndExcludesUsStocks() {
        val normalized = plan(stocks = listOf(stock().copy(code = " 7203 ")))
        assertEquals(1, normalized.hits.size)
        val usStock = stock().copy(code = "AAPL", category = "us")
        val result = plan(stocks = listOf(usStock), prices = mapOf("AAPL" to 2500.0))
        assertTrue(result.hits.isEmpty())
        assertTrue(result.eligibleCodes.isEmpty())
    }

    @Test fun normalizesPaddedStandardCodesAcrossQuotesAndPreviousStates() {
        val paddedStock = stock().copy(code = "72030")
        val quote = CachedNotificationPrice(2500.0, date.toString())
        val previous = mapOf("72030" to PriceAlertState("4.0:100.0", true))

        val result = NotificationPolicy.priceAlerts(
            listOf(paddedStock), emptyList(), mapOf("7203" to quote), previous, date
        )

        assertTrue(result.hits.isEmpty())
        assertEquals(setOf("7203"), result.eligibleCodes)
        assertEquals(setOf("7203"), result.states.keys)
    }

    @Test fun quoteWithoutDateOrMoreThanThreeTseSessionsOldCannotNotify() {
        val freshOnThursday = LocalDate.of(2026, 11, 5)
        val quote = CachedNotificationPrice(2500.0, "2026-10-30")
        val fresh = NotificationPolicy.priceAlerts(listOf(stock()), mapOf("7203" to quote), emptyMap(), freshOnThursday)
        assertEquals(1, fresh.hits.size) // Nov 3 is a JPX holiday; sessions are Nov 2, 4, and 5.
        val stale = NotificationPolicy.priceAlerts(listOf(stock()), mapOf("7203" to quote), emptyMap(), LocalDate.of(2026, 11, 6))
        assertTrue(stale.hits.isEmpty())
        assertTrue(stale.eligibleCodes.isEmpty())
        assertFalse(TseTradingCalendar.isFreshQuote(null, freshOnThursday))
        assertFalse(TseTradingCalendar.isFreshQuote("2026-11-06", freshOnThursday))
        assertFalse(TseTradingCalendar.isFreshQuote("bad-date", freshOnThursday))
        assertFalse(TseTradingCalendar.isFreshQuote("2026-11-01", freshOnThursday))
        assertFalse(TseTradingCalendar.isFreshQuote("2026-11-03", freshOnThursday))
    }

    @Test fun officialTseClosuresAndSubstituteHolidaysAreNotTradingSessions() {
        // JPX's published 2026 cash-market calendar, including exchange holidays and substitute days.
        listOf(
            "2026-01-01", "2026-01-02", "2026-01-03", "2026-01-12",
            "2026-02-11", "2026-02-23", "2026-03-20", "2026-04-29",
            "2026-05-03", "2026-05-04", "2026-05-05", "2026-05-06",
            "2026-07-20", "2026-08-11", "2026-09-21", "2026-09-22",
            "2026-09-23", "2026-10-12", "2026-11-03", "2026-11-23",
            "2026-12-31",
        ).forEach { date ->
            assertFalse("$date should be a JPX cash-market closure", TseTradingCalendar.isTradingSession(LocalDate.parse(date)))
        }
        assertTrue(TseTradingCalendar.isTradingSession(LocalDate.of(2026, 11, 4)))
    }

    @Test fun officialTseClosuresFor2027AndThreeSessionFreshnessAreCorrect() {
        // JPX published 2027 TSE cash-market holidays and exchange closures.
        listOf(
            "2027-01-01", "2027-01-02", "2027-01-03", "2027-01-11",
            "2027-02-11", "2027-02-23", "2027-03-21", "2027-03-22",
            "2027-04-29", "2027-05-03", "2027-05-04", "2027-05-05",
            "2027-07-19", "2027-08-11", "2027-09-20", "2027-09-23",
            "2027-10-11", "2027-11-03", "2027-11-23", "2027-12-31",
        ).forEach { date ->
            assertFalse("$date should be a JPX cash-market closure", TseTradingCalendar.isTradingSession(LocalDate.parse(date)))
        }

        assertTrue(TseTradingCalendar.isTradingSession(LocalDate.of(2027, 5, 6)))
        assertTrue(TseTradingCalendar.isFreshQuote("2027-04-28", LocalDate.of(2027, 5, 7)))
        assertFalse(TseTradingCalendar.isFreshQuote("2027-04-28", LocalDate.of(2027, 5, 10)))
    }

    @Test fun disabledStockDoesNotKeepOldState() {
        val previous = mapOf("7203" to PriceAlertState("old", true))
        val result = plan(stocks = listOf(stock().copy(alertEnabled = false)), states = previous)
        assertTrue(result.hits.isEmpty())
        assertTrue(result.states.isEmpty())
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
