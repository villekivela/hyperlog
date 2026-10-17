package com.hyperlog.app.ui.today

import com.hyperlog.app.program.Position
import com.hyperlog.app.session.PastSessionItem
import org.junit.Assert.assertEquals
import org.junit.Test

/** The strip's marker for a day with finished sessions (SPEC, Today; design note, Today's calendar). */
class DayMarkerTest {
    private fun day(letter: Char, id: Long = 1) = PastSessionItem(id, id, id + 1, "Day $letter — Heavy", "Gym", emptyList(), programId = 1, position = Position(1, letter - 'A'))
    private fun adHoc(id: Long = 9) = PastSessionItem(id, id, id + 1, null, "Gym", emptyList())

    @Test
    fun oneSessionShowsItsLetterOrATick() {
        assertEquals("C", doneMarker(listOf(day('C'))))
        assertEquals(null, doneMarker(listOf(adHoc())))
    }

    @Test
    fun twoProgramDaysShowBothLettersInOrder() {
        assertEquals("CA", doneMarker(listOf(day('C', 1), day('A', 2))))
    }

    @Test
    fun moreThanTwoOrAnAdHocBesideShowTheFirstLetterAndAPlus() {
        assertEquals("A+", doneMarker(listOf(day('A', 1), day('B', 2), day('C', 3))))
        assertEquals("A+", doneMarker(listOf(day('A', 1), adHoc(2))))
        assertEquals("A+", doneMarker(listOf(adHoc(1), day('A', 2))))
    }

    @Test
    fun talkBackNamesEverySession() {
        assertEquals(listOf("Day C", "Day A"), doneNames(listOf(day('C', 1), day('A', 2))) { "ad hoc" })
        assertEquals(listOf("Day A", "ad hoc"), doneNames(listOf(day('A', 1), adHoc(2))) { "ad hoc" })
    }
}
