package com.hyperlog.app.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** A check-in keeps what opened it, as a kind (SPEC, Coaching → Check-in), and an older one reads back as it was. */
class CheckInKindTest {
    @get:Rule val folder = TemporaryFolder()

    private val frame = CheckInFrame("Context", emptyList())

    private fun roundTrip(checkIn: CheckIn): CheckIn {
        val store = CheckInStore(folder.newFile())
        store.save(checkIn)
        return store.load()!!
    }

    @Test
    fun eachKindIsKeptWithWhatItNeeds() {
        val fit = About(slot = "curl", date = "2026-10-31")
        val line = About(pattern = LinePattern.SameSwap, slot = "row", exercise = "seal_row", swappedFor = "db_row")
        listOf(
            CheckIn("fit", null, frame, Opening.Fit, about = fit),
            CheckIn("line", null, frame, Opening.CoachLine, about = line),
            CheckIn("block", null, frame, Opening.BlockReview),
            CheckIn("program", null, frame, Opening.ProgramReview),
            CheckIn("new", null, frame, Opening.NewProgramReview),
        ).forEach { assertEquals(it, roundTrip(it)) }
    }

    @Test
    fun aCheckInKeptBeforeKindsReadsBackUnchanged() {
        val file = folder.newFile()
        file.writeText("""{"opening":"I'm back.","frame":{"context":"C","tools":[]},"from":"EaseBackIn"}""")
        val read = CheckInStore(file).load()!!
        assertEquals(Opening.EaseBackIn, read.from)
        assertNull(read.about)
        file.writeText("""{"opening":"Something else.","tile":"SomethingElse","frame":{"context":"C","tools":[]}}""")
        assertEquals(Opening.Tile, CheckInStore(file).load()!!.from)
    }
}
