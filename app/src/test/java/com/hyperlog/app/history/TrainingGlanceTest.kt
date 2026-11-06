package com.hyperlog.app.history

import com.hyperlog.app.coach.NumberRange
import com.hyperlog.app.coach.VolumeStatus
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.GripDemand
import com.hyperlog.app.library.MovementPattern
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** What Progress opens on: the sets per muscle against targets (#270). */
class TrainingGlanceTest {
    private val monday = LocalDate.of(2026, 11, 2)
    private val range = NumberRange(8, 14)

    private fun weeks(vararg sets: Map<String, Double>) = sets.mapIndexed { i, s -> WeekSets(monday.minusWeeks(i.toLong()), s) }

    private fun glance(vararg sets: Map<String, Double>) = trainingGlance(weeks(*sets), emptyList(), today) { range }

    private val today = LocalDate.of(2026, 11, 5)

    private fun exercise(name: String) = Exercise(name, name, Equipment.Barbell, MovementPattern.entries.first(), GripDemand.None, listOf("Chest"))

    private fun point(date: LocalDate, kg: Double?) = TrendPoint(1, date, LoggedSet(80.0, 6, 0, 1), kg)

    /** A free weight's trend: its estimates by how many days before [today] each came. */
    private fun trend(name: String, vararg estimates: Pair<Long, Double?>) =
        ExerciseTrend(exercise(name), listOf(TrendLine(null, null, estimates.map { (ago, kg) -> point(today.minusDays(ago), kg) })))

    private fun moving(vararg trends: ExerciseTrend) = trainingGlance(emptyList(), trends.toList(), today) { range }.moving

    @Test
    fun aWeekPickedIsShownEvenWithNoSets() {
        val weeks = weeks(mapOf("Chest" to 9.0), emptyMap())
        val glance = trainingGlance(weeks, emptyList(), today, picked = monday.minusWeeks(1)) { range }
        assertEquals(monday.minusWeeks(1), glance.week)
        assertEquals(listOf("Chest" to 0.0), glance.muscles.map { it.muscle to it.sets })
    }

    @Test
    fun theMusclesFurthestShortComeFirst() {
        val glance = glance(mapOf("Chest" to 9.0, "Lats" to 2.0, "Quads" to 15.0, "Glutes" to 5.0))
        assertEquals(listOf("Lats", "Glutes", "Chest", "Quads"), glance.shortFirst.map { it.muscle })
    }

    /** Real weeks count secondaries at a half: listing them all read `0 of 22 on target`. */
    @Test
    fun aProgramsOwnMusclesAreTheOnesMeasured() {
        val weeks = weeks(mapOf("Chest" to 9.0, "Triceps Long Head" to 4.5))
        val glance = trainingGlance(weeks, emptyList(), today, only = setOf("Chest", "Lats")) { range }
        assertEquals(listOf("Chest", "Lats"), glance.muscles.map { it.muscle })
    }

    @Test
    fun aMuscleWithNoSetsIsApartFromTheRows() {
        val glance = glance(mapOf("Chest" to 9.0), mapOf("Lats" to 10.0))
        assertEquals(listOf("Chest"), glance.shortFirst.map { it.muscle })
        assertEquals(listOf("Lats"), glance.untrained.map { it.muscle })
    }

    @Test
    fun onlyAMuscleWithinItsRangeIsOnTarget() {
        assertEquals(1, glance(mapOf("Chest" to 9.0, "Lats" to 2.0, "Quads" to 15.0)).onTarget)
    }

    @Test
    fun theWeeksRunOldestFirstWithTheirWorkSets() {
        val weeks = listOf(WeekSets(monday, emptyMap(), 12), WeekSets(monday.minusWeeks(1), emptyMap(), 15))
        assertEquals(
            listOf(WeekTotal(monday.minusWeeks(1), 15), WeekTotal(monday, 12)),
            trainingGlance(weeks, emptyList(), today) { range }.weeks,
        )
    }

    @Test
    fun aLiftMovesFromItsEstimateAboutFourWeeksBeforeTheLatest() {
        val lift = moving(trend("Bench", 60L to 90.0, 35L to 100.0, 20L to 102.0, 2L to 105.0)).single()
        assertEquals(100.0, lift.fromKg, 0.0)
        assertEquals(105.0, lift.toKg, 0.0)
    }

    @Test
    fun aLiftWithLessHistoryMovesFromItsFirstEstimate() {
        val lift = moving(trend("Bench", 14L to 100.0, 2L to 104.0)).single()
        assertEquals(100.0, lift.fromKg, 0.0)
    }

    @Test
    fun theThreeThatMovedMostComeFirstEitherWay() {
        val lifts = moving(
            trend("Small", 14L to 100.0, 2L to 102.0),
            trend("Down", 14L to 100.0, 2L to 90.0),
            trend("Up", 14L to 100.0, 2L to 105.0),
            trend("Medium", 14L to 100.0, 2L to 103.0),
        )
        assertEquals(listOf("Down", "Up", "Medium"), lifts.map { it.exercise.name })
    }

    @Test
    fun aFlatLiftIsNotMoving() = assertEquals(emptyList<MovingLift>(), moving(trend("Bench", 14L to 100.0, 2L to 100.5)))

    @Test
    fun oneEstimateIsNotMovement() = assertEquals(emptyList<MovingLift>(), moving(trend("Bench", 2L to 100.0)))

    @Test
    fun aLiftNotDoneLatelyIsNotMoving() = assertEquals(emptyList<MovingLift>(), moving(trend("Bench", 60L to 100.0, 40L to 110.0)))

    @Test
    fun aSessionWithNoEstimateIsPassedOver() {
        assertEquals(104.0, moving(trend("Bench", 14L to 100.0, 8L to 104.0, 2L to null)).single().toKg, 0.0)
    }

    /** Two machines' estimates can't be compared (ADR 0002). */
    @Test
    fun anExerciseOnTwoMachinesIsNotMoving() {
        val line = { TrendLine("A", 1, listOf(point(today.minusDays(14), 100.0), point(today.minusDays(2), 110.0))) }
        assertEquals(emptyList<MovingLift>(), moving(ExerciseTrend(exercise("Row"), listOf(line(), line()))))
    }

    @Test
    fun aMuscleIsMeasuredAgainstItsRange() {
        val glance = glance(mapOf("Chest" to 9.0, "Lats" to 4.0, "Quads" to 15.0))
        assertEquals(monday, glance.week)
        assertEquals(
            listOf("Chest" to VolumeStatus.Within, "Lats" to VolumeStatus.Under, "Quads" to VolumeStatus.Over),
            glance.muscles.map { it.muscle to it.status },
        )
    }

    @Test
    fun aWeekWithNoSetsShowsTheLatestWeekThatHasThem() {
        val glance = glance(emptyMap(), emptyMap(), mapOf("Chest" to 9.0))
        assertEquals(monday.minusWeeks(2), glance.week)
        assertEquals(listOf("Chest" to 9.0), glance.muscles.map { it.muscle to it.sets })
    }

    @Test
    fun nothingInEightWeeksShowsNoWeek() {
        val glance = glance(emptyMap(), emptyMap())
        assertEquals(null, glance.week)
        assertEquals(emptyList<MuscleWeek>(), glance.muscles)
    }

    /** A muscle missed so far this week is what a glance is for: it stays, at 0, under its range. */
    @Test
    fun aMuscleTrainedInEarlierWeeksStaysAtNone() {
        val glance = glance(mapOf("Chest" to 9.0), mapOf("Lats" to 10.0, "Chest" to 8.0))
        assertEquals(
            listOf(Triple("Chest", 9.0, VolumeStatus.Within), Triple("Lats", 0.0, VolumeStatus.Under)),
            glance.muscles.map { Triple(it.muscle, it.sets, it.status) },
        )
    }
}
