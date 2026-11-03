package com.kurogane.dividend.model

import com.kurogane.dividend.data.CloudflareDividendEvent
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DividendMonthResolverTest {
    private val today = LocalDate.parse("2026-11-03")
    private fun event(date: String) = CloudflareDividendEvent(LocalDate.parse(date), 50.0)

    @Test fun emptyMonthsUseLatestTwelveMonthsOfHistoricalEvents() {
        val events = listOf(event("2019-03-27"), event("2025-03-28"), event("2026-03-30"), event("2026-09-29"))
        assertEquals(listOf(3, 9), DividendMonthResolver.resolve(emptyList(), events, today))
    }

    @Test fun olderHistoryCanProvideMonthsAndFutureEventsAreNotPastEvidence() {
        val events = listOf(event("2019-03-27"), event("2019-09-27"), event("2020-03-30"), event("2027-01-01"))
        assertEquals(listOf(3, 9), DividendMonthResolver.resolve(emptyList(), events, today))
        assertEquals(emptyList<Int>(), DividendMonthResolver.resolve(emptyList(), listOf(event("2027-01-01")), today))
    }

    @Test fun monthOnlyInputRemainsAvailableWithoutCreatingEvents() {
        assertEquals(listOf(3, 9), DividendMonthResolver.resolve(listOf(9, 3, 3, 13), emptyList(), today))
        assertEquals(emptyList<Int>(), DividendMonthResolver.resolve(emptyList(), emptyList(), today))
    }

    @Test fun explicitlyProvidedMonthsTakePriorityOverHistoricalEvents() {
        assertEquals(listOf(6, 12), DividendMonthResolver.resolve(listOf(6, 12), listOf(event("2026-03-30")), today))
    }

    @Test fun olderYearResponsesCannotEraseOrReplaceExistingRecentMonths() {
        assertEquals(listOf(3, 9), DividendMonthResolver.monthsFromResponse(listOf(3, 9), emptyList(), (2019..2025).toList(), today))
        assertEquals(listOf(3, 9), DividendMonthResolver.monthsFromResponse(listOf(3, 9), listOf(6), listOf(2024), today))
        assertEquals(emptyList<Int>(), DividendMonthResolver.monthsFromResponse(emptyList(), listOf(6), listOf(2024), today))
    }

    @Test fun fullRecentWindowCanUpdateOrClearMonthList() {
        assertEquals(listOf(6, 12), DividendMonthResolver.monthsFromResponse(listOf(3, 9), listOf(6, 12), listOf(2025, 2026), today))
        assertEquals(emptyList<Int>(), DividendMonthResolver.monthsFromResponse(listOf(3, 9), emptyList(), listOf(2025, 2026), today))
        assertEquals(listOf(3, 9), DividendMonthResolver.monthsFromResponse(emptyList(), listOf(3, 9), listOf(2026), today))
    }
}
