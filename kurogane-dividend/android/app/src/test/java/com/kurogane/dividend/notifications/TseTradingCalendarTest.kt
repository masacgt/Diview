package com.kurogane.dividend.notifications

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZonedDateTime

class TseTradingCalendarTest {
    @Test fun latestCompletedSessionWaitsUntilDailyClose() {
        val beforeClose = ZonedDateTime.parse("2026-11-19T15:59:00+09:00[Asia/Tokyo]")
        val afterClose = ZonedDateTime.parse("2026-11-19T16:00:00+09:00[Asia/Tokyo]")

        assertEquals(LocalDate.parse("2026-11-18"), TseTradingCalendar.latestCompletedSessionDate(beforeClose))
        assertEquals(LocalDate.parse("2026-11-19"), TseTradingCalendar.latestCompletedSessionDate(afterClose))
    }

    @Test fun latestCompletedSessionSkipsWeekendsAndPublicHolidays() {
        val saturday = ZonedDateTime.parse("2026-11-21T18:00:00+09:00[Asia/Tokyo]")
        val holidayTuesday = ZonedDateTime.parse("2026-11-24T15:00:00+09:00[Asia/Tokyo]")

        assertEquals(LocalDate.parse("2026-11-20"), TseTradingCalendar.latestCompletedSessionDate(saturday))
        assertEquals(LocalDate.parse("2026-11-20"), TseTradingCalendar.latestCompletedSessionDate(holidayTuesday))
    }

    @Test fun latestCompletedSessionUsesJapanLocalTime() {
        val utc = ZonedDateTime.parse("2026-11-19T07:01:00Z")

        assertEquals(LocalDate.parse("2026-11-19"), TseTradingCalendar.latestCompletedSessionDate(utc))
    }
}
