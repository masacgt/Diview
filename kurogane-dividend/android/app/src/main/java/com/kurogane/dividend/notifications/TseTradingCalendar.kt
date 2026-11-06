package com.kurogane.dividend.notifications

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.temporal.TemporalAdjusters
import kotlin.math.floor

/** Offline approximation of TSE cash-market sessions from Japanese public holidays. */
object TseTradingCalendar {
    const val MAX_QUOTE_AGE_SESSIONS = 3

    fun isFreshQuote(quoteDate: String?, today: LocalDate): Boolean {
        val quotedOn = quoteDate?.let { runCatching { LocalDate.parse(it).takeIf { parsed -> parsed.toString() == it } }.getOrNull() }
            ?: return false
        if (quotedOn.isAfter(today) || !isTradingSession(quotedOn)) return false
        var sessions = 0
        var date = quotedOn.plusDays(1)
        val holidaysByYear = mutableMapOf<Int, Set<LocalDate>>()
        while (!date.isAfter(today)) {
            val holidays = holidaysByYear.getOrPut(date.year) { publicHolidays(date.year) }
            if (isTradingSession(date, holidays)) {
                sessions++
                if (sessions > MAX_QUOTE_AGE_SESSIONS) return false
            }
            date = date.plusDays(1)
        }
        return true
    }

    fun isTradingSession(date: LocalDate): Boolean = isTradingSession(date, publicHolidays(date.year))

    private fun isTradingSession(date: LocalDate, holidays: Set<LocalDate>): Boolean {
        if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) return false
        if ((date.month == Month.JANUARY && date.dayOfMonth in 1..3) ||
            (date.month == Month.DECEMBER && date.dayOfMonth == 31)) return false
        return date !in holidays
    }

    /** Japanese national holidays, substitute holidays, and citizens' holidays. */
    private fun publicHolidays(year: Int): Set<LocalDate> {
        val holidays = mutableSetOf<LocalDate>()
        fun add(month: Int, day: Int) { holidays += LocalDate.of(year, month, day) }
        fun monday(month: Int, ordinal: Int) = LocalDate.of(year, month, 1)
            .with(TemporalAdjusters.dayOfWeekInMonth(ordinal, DayOfWeek.MONDAY))

        add(1, 1)
        holidays += monday(1, 2)
        add(2, 11)
        if (year >= 2020) add(2, 23)
        holidays += LocalDate.of(year, 3, springEquinoxDay(year))
        add(4, 29)
        add(5, 3)
        add(5, 4)
        add(5, 5)
        holidays += monday(7, 3)
        if (year >= 2016) add(8, 11)
        holidays += monday(9, 3)
        holidays += LocalDate.of(year, 9, autumnEquinoxDay(year))
        holidays += monday(10, 2)
        add(11, 3)
        add(11, 23)

        // The 2020/2021 Olympic schedule temporarily moved three holidays.
        when (year) {
            2020 -> {
                holidays.remove(monday(7, 3)); holidays.remove(monday(10, 2)); holidays.remove(LocalDate.of(year, 8, 11))
                holidays += LocalDate.of(year, 7, 23); holidays += LocalDate.of(year, 7, 24); holidays += LocalDate.of(year, 8, 10)
            }
            2021 -> {
                holidays.remove(monday(7, 3)); holidays.remove(monday(10, 2)); holidays.remove(LocalDate.of(year, 8, 11))
                holidays += LocalDate.of(year, 7, 22); holidays += LocalDate.of(year, 7, 23); holidays += LocalDate.of(year, 8, 8)
            }
        }

        holidays.filter { it.dayOfWeek == DayOfWeek.SUNDAY }.forEach { sunday ->
            var substitute = sunday.plusDays(1)
            while (substitute in holidays) substitute = substitute.plusDays(1)
            holidays += substitute
        }

        var date = LocalDate.of(year, 1, 2)
        val end = LocalDate.of(year, 12, 30)
        while (!date.isAfter(end)) {
            if (date.dayOfWeek != DayOfWeek.SUNDAY && date.minusDays(1) in holidays && date.plusDays(1) in holidays) {
                holidays += date
            }
            date = date.plusDays(1)
        }
        return holidays
    }

    private fun springEquinoxDay(year: Int): Int = floor(
        20.8431 + 0.242194 * (year - 1980) - floor((year - 1980) / 4.0)
    ).toInt()

    private fun autumnEquinoxDay(year: Int): Int = floor(
        23.2488 + 0.242194 * (year - 1980) - floor((year - 1980) / 4.0)
    ).toInt()
}
