package com.hyperlog.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class CalendarDaysTest {
    private val helsinki = ZoneId.of("Europe/Helsinki")

    @Test
    fun elevenDaysBeforeLateOnThirdOfNovemberIsTheTwentyThirdOfOctoberAcrossTheClocksGoingBack() {
        // Clocks went back on 25 October 2026, so that day has 25 hours.
        val now = ZonedDateTime.of(2026, 11, 3, 23, 30, 0, 0, helsinki).toInstant()
        val then = Instant.ofEpochMilli(daysBefore(now, 11, helsinki)).atZone(helsinki)
        assertEquals(LocalDate.of(2026, 10, 23), then.toLocalDate())
        assertEquals(23, then.hour)
    }

    @Test
    fun elevenDaysBeforeEarlyOnNinthOfAprilIsTheTwentyNinthOfMarchAcrossTheClocksGoingForward() {
        // Clocks went forward on 29 March 2026, so that day has 23 hours.
        val now = ZonedDateTime.of(2026, 4, 9, 0, 30, 0, 0, helsinki).toInstant()
        val then = Instant.ofEpochMilli(daysBefore(now, 11, helsinki)).atZone(helsinki)
        assertEquals(LocalDate.of(2026, 3, 29), then.toLocalDate())
        assertEquals(0, then.hour)
    }
}
