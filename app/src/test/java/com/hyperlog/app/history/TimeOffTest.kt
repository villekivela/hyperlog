package com.hyperlog.app.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/** Days off, as Today counts them for its time-off line (SPEC, Coaching → Time off on Today). */
class TimeOffTest {
    private val oct13 = LocalDate.of(2026, 10, 13) // a Tuesday

    @Test
    fun withoutAWeekdayPlanDaysOffAreTheDaysSinceTheLastSession() {
        assertEquals(14, TimeOff.daysOff(oct13.minusDays(14), oct13))
        assertEquals(11, TimeOff.daysSince(oct13.minusDays(11), oct13))
        assertNull(TimeOff.daysOff(null, oct13))
        assertNull(TimeOff.daysSince(null, oct13))
    }

    @Test
    fun withAWeekdayPlanTimeOffCountsFromTheFirstPlannedDayMissed() {
        // Training Tuesdays only, two weeks since the last session is one planned day missed a week ago.
        val tuesdays = setOf(DayOfWeek.TUESDAY)
        assertEquals(7, TimeOff.daysOff(oct13.minusDays(14), oct13, tuesdays))
        // Today itself isn't missed yet.
        assertEquals(0, TimeOff.daysOff(oct13.minusDays(7), oct13, tuesdays))
        // Monday, Wednesday and Friday, last trained on Friday 2 Oct: Monday 5 Oct was missed 8 days ago.
        val mwf = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        assertEquals(8, TimeOff.daysOff(LocalDate.of(2026, 10, 2), oct13, mwf))
    }

    @Test
    fun moreThanAWeekOffIsTimeOff() {
        assertFalse(TimeOff.isTimeOff(7))
        assertTrue(TimeOff.isTimeOff(8))
        assertFalse(TimeOff.isTimeOff(null))
    }
}
