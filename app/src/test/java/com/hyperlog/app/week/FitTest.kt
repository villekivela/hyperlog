package com.hyperlog.app.week

import com.hyperlog.app.gym.Gym
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.GripDemand
import com.hyperlog.app.library.MovementPattern
import com.hyperlog.app.program.Block
import com.hyperlog.app.program.Day
import com.hyperlog.app.program.PlannedDay
import com.hyperlog.app.program.PlannedSlot
import com.hyperlog.app.program.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.THURSDAY
import java.time.LocalDate

/** Each planned day against the training day it lands on (SPEC, Domain → Training days and the rotation → The fit). */
class FitTest {
    private val saturday = LocalDate.of(2026, 10, 31)

    private fun day(vararg exercises: String) = PlannedDay(
        Position(1, 2),
        Block("b", "Block", 1, 12, emptyList()),
        Day("c", "Day C", emptyList()),
        exercises.mapIndexed { i, e -> PlannedSlot("s$i", "${i + 1}", i, e, emptyList(), null, emptyList()) },
    )

    private fun exercise(id: String) = Exercise(id, id, Equipment.Machine, MovementPattern.Squat, GripDemand.None, listOf("quads"))

    @Test
    fun aDayMoreThanFiveMinutesOverItsTrainingDaysIsOverTime() {
        val fit = Fit.of(day(), saturday, WeekdayPlan(gyms = mapOf(SATURDAY to null), minutes = mapOf(SATURDAY to 50)), emptyList(), emptyList(), about = 58)
        assertEquals(TimeOver(about = 58, has = 50, weekday = SATURDAY), fit.time)
    }

    @Test
    fun exactlyFiveOverIsStillFine() {
        val fit = Fit.of(day(), saturday, WeekdayPlan(gyms = mapOf(SATURDAY to null), minutes = mapOf(SATURDAY to 50)), emptyList(), emptyList(), about = 55)
        assertNull(fit.time)
    }

    @Test
    fun aTrainingDayWithoutMinutesIsNeverChecked() {
        val fit = Fit.of(day(), saturday, WeekdayPlan(gyms = mapOf(SATURDAY to null)), emptyList(), emptyList(), about = 120)
        assertNull(fit.time)
    }

    @Test
    fun aDateThatIsNoTrainingDayHasNoFit() {
        val fit = Fit.of(day(), saturday, WeekdayPlan(gyms = mapOf(THURSDAY to null), minutes = mapOf(THURSDAY to 30)), emptyList(), emptyList(), about = 90)
        assertEquals(DayFit(null, null), fit)
    }

    @Test
    fun theSwapsCountIsTheStartSheetsListAtTheTrainingDaysGymWithSlotsNothingFitsToo() {
        val harbour = Gym(1, "Harbour", emptyList(), missing = setOf("leg_press", "hack_squat"))
        val library = listOf(exercise("leg_press"), exercise("hack_squat"), exercise("goblet_squat"))
        val fit = Fit.of(day("leg_press", "hack_squat", "goblet_squat"), saturday, WeekdayPlan(gyms = mapOf(SATURDAY to 1L)), listOf(harbour), library, about = 40)
        assertEquals(SwapCount(2, "Harbour"), fit.swaps)
    }

    @Test
    fun noSwapsAreCountedWithoutAGymOrWhenNothingIsMissing() {
        val harbour = Gym(1, "Harbour", emptyList(), missing = setOf("leg_press"))
        val library = listOf(exercise("leg_press"))
        val anyGym = Fit.of(day("leg_press"), saturday, WeekdayPlan(gyms = mapOf(SATURDAY to null)), listOf(harbour), library, about = 40)
        val nothingMissing = Fit.of(day("squat"), saturday, WeekdayPlan(gyms = mapOf(SATURDAY to 1L)), listOf(harbour), library, about = 40)
        assertNull(anyGym.swaps)
        assertNull(nothingMissing.swaps)
    }
}
