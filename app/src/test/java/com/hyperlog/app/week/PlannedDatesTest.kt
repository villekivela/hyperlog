package com.hyperlog.app.week

import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.ProgramParser
import com.hyperlog.app.program.TestPrograms
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate

/** The program's coming days put on dates, as Today's calendar marks them (SPEC, Today → Planned dates). */
class PlannedDatesTest {
    private val program = (ProgramParser.parse(TestPrograms.twoDays("Test"), setOf("barbell_bench_press", "smith_machine_bench_press", "cable_lateral_raise", "leg_extension")) as ProgramParser.Result.Valid).program

    private val sat17 = LocalDate.of(2026, 10, 17)

    private fun at(position: Position, today: LocalDate = sat17) = ActiveProgram(1, program, position, today = today)

    /** The planned dates as `date to "week.day"`, for reading at a glance. */
    private fun dates(active: ActiveProgram?, days: Set<java.time.DayOfWeek>, trainedToday: Boolean = false) =
        PlannedDates.of(active, days, trainedToday).map { (date, day) -> date to "${day.position.week}.${day.day.name}" }

    @Test
    fun theNextDayGoesOnTodayAndTheRestOnTheTrainingDaysAfterIt() {
        // Two weeks of Day A and Day B, from week 1 Day B: three days left.
        assertEquals(
            listOf(
                sat17 to "1.Day B",
                LocalDate.of(2026, 10, 19) to "2.Day A",
                LocalDate.of(2026, 10, 21) to "2.Day B",
            ),
            dates(at(Position(1, 1)), setOf(MONDAY, WEDNESDAY, FRIDAY)),
        )
    }

    @Test
    fun aSessionFinishedTodayPutsTheNextDayOnTheNextTrainingDay() {
        assertEquals(
            listOf(
                LocalDate.of(2026, 10, 19) to "1.Day B",
                LocalDate.of(2026, 10, 21) to "2.Day A",
                LocalDate.of(2026, 10, 23) to "2.Day B",
            ),
            dates(at(Position(1, 1)), setOf(MONDAY, WEDNESDAY, FRIDAY), trainedToday = true),
        )
    }

    @Test(timeout = 5_000)
    fun withNoTrainingDaysOnlyTheNextDayIsMarked() {
        assertEquals(listOf(sat17 to "1.Day B"), dates(at(Position(1, 1)), emptySet()))
        // Trained today: the next day goes on tomorrow.
        assertEquals(listOf(LocalDate.of(2026, 10, 18) to "1.Day B"), dates(at(Position(1, 1)), emptySet(), trainedToday = true))
    }

    @Test
    fun aFinishedProgramOrNoProgramPlansNothing() {
        assertEquals(emptyList<Any>(), dates(at(Position(3, 0)), setOf(MONDAY)))
        assertEquals(emptyList<Any>(), dates(null, setOf(MONDAY)))
    }

    @Test
    fun theLastDayOfTheProgramIsTheLastPlanned() {
        assertEquals(listOf(sat17 to "2.Day B"), dates(at(Position(2, 1)), setOf(MONDAY, WEDNESDAY, FRIDAY)))
    }
}
