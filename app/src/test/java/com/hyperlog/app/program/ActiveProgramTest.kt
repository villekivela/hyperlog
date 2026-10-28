package com.hyperlog.app.program

import com.hyperlog.app.coach.Change
import com.hyperlog.app.coach.Operation
import com.hyperlog.app.coach.Scope
import com.hyperlog.app.week.RotationAnchor
import com.hyperlog.app.week.Rotations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.THURSDAY
import java.time.LocalDate

/** The program running hands out its turns resolved (GLOSSARY: Turn), whether it rotates or runs as a sequence. */
class ActiveProgramTest {
    private val mon26 = LocalDate.of(2026, 10, 26)
    private val thu29 = LocalDate.of(2026, 10, 29)
    private val fri30 = LocalDate.of(2026, 10, 30)
    private val sat31 = LocalDate.of(2026, 10, 31)
    private val monThuSat = setOf(MONDAY, THURSDAY, SATURDAY)

    /** Two weeks of the days [letters], one block. */
    private fun program(letters: String = "ABC") = Program(
        title = "Test",
        author = null,
        blocks = listOf(
            Block("b", "Block", 1, 2, letters.map { l ->
                Day(l.lowercase(), "Day $l", listOf(Group(false, listOf(Slot("s", "leg_extension", emptyList(), null, listOf(SetSpec(count = 3, reps = RepRange(8, 10), rir = 2)))))))
            }),
        ),
    )

    private val program = program()

    /** Rotating on Monday, Thursday and Saturday from Monday the 26th at its first day. */
    private fun rotating(today: LocalDate, history: List<ChangeRecord> = emptyList()): ActiveProgram {
        val rotation = Rotations.of(program, RotationAnchor(mon26, week = 1, day = 0), monThuSat, emptyList(), today)
        return ActiveProgram(1, program, rotation.next, history, today, rotation)
    }

    /** A turn as `"week.letter weekday-date status"`, for reading at a glance. */
    private fun Turn.label() = "${day.position.week}.${day.day.id.uppercase()} ${date.dayOfWeek.name.take(3)} ${date.dayOfMonth} ${status.name.lowercase()}"

    private fun rir(by: Int, scope: Scope) = Change("Easier", scope, listOf(Operation.AdjustRir(null, by)))

    private fun record(change: Change, active: ActiveProgram) = ChangeRecord(active.accept(change).copy(id = 1), acceptedAt = 1, revertedAt = null)

    @Test
    fun aSequenceGivesItsNextDayOnTodayOrTomorrowOnceTrainedToday() {
        val sequence = ActiveProgram(1, program, Position(1, 1), today = fri30)
        assertEquals(listOf("1.B FRI 30 ahead"), sequence.turns.map { it.label() })
        assertEquals("1.B FRI 30 ahead", sequence.next?.label())
        assertNull(sequence.trainToday)

        val trained = ActiveProgram(1, program, Position(1, 1), today = fri30, lastTrained = fri30)
        assertEquals(listOf("1.B SAT 31 ahead"), trained.turns.map { it.label() })

        assertEquals(emptyList<Turn>(), ActiveProgram(1, program, Position(3, 0), today = fri30).turns)
    }

    @Test
    fun aRotationGivesEveryMissedAndComingTurnByDate() {
        val active = rotating(thu29)
        assertEquals(
            listOf("1.A MON 26 missed", "1.B THU 29 ahead", "1.C SAT 31 ahead", "2.A MON 2 ahead"),
            active.turns.take(4).map { it.label() },
        )
        assertEquals("1.B THU 29 ahead", active.next?.label())
    }

    @Test
    fun aMissedTurnTrainedTodayIsReadForToday() {
        // Friday has no training day: Thursday's missed day can be trained today, with today's Changes.
        val bare = rotating(fri30)
        val today = rir(1, Scope.Dates(fri30, fri30))
        val active = rotating(fri30, listOf(record(today, bare)))
        val turn = active.trainToday!!
        assertEquals("1.B THU 29 missed", turn.label())
        assertEquals(3, turn.day.slots.single().sets.first().rir)
    }

    @Test
    fun aDatedChangeReadsOnTheRotationsDatesInItsReviewAndInForce() {
        val active = rotating(mon26)
        // Saturday's day is Day C, not the next day the old guess counted from.
        val saturday = rir(1, Scope.Dates(sat31, sat31))
        val review = active.diff(saturday)
        assertEquals(listOf("c"), review.days.map { it.day.day.id })

        val accepted = rotating(mon26, listOf(record(saturday, active)))
        val inForce = accepted.inForceDiff(accepted.changes.single())
        assertEquals(listOf("c"), inForce.days.map { it.day.day.id })
        // What the review and the Change in force show is what Today shows on Saturday.
        val today = accepted.turns.single { it.date == sat31 }.day
        assertEquals(today, review.days.single().day)
        assertEquals(today, inForce.days.single().day)
    }
}
