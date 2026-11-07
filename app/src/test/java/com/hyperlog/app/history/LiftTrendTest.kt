package com.hyperlog.app.history

import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.GripDemand
import com.hyperlog.app.library.MovementPattern
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The lift trend says what Progress's Moving and the coach line already say, from the sessions they count. */
class LiftTrendTest {
    private val today = LocalDate.of(2026, 11, 7)
    private val bench = Exercise("barbell_bench_press", "Barbell Bench Press", Equipment.Barbell, MovementPattern.HorizontalPush, GripDemand.Low, listOf("Chest"))

    private fun point(id: Long, daysAgo: Long, kg: Double?) = TrendPoint(id, today.minusDays(daysAgo), LoggedSet(100.0, 5, 0, 1), kg)

    @Test
    fun aLiftMovingOnProgressMovesTheSameHere() {
        val points = listOf(point(1, 30, 100.0), point(2, 20, 102.0), point(3, 10, 104.0), point(4, 2, 105.0))
        val progress = trainingGlance(emptyList(), listOf(ExerciseTrend(bench, listOf(TrendLine(null, null, points)))), today) { com.hyperlog.app.coach.NumberRange(10, 16) }.moving.single()
        val trend = LiftTrend.of(points, eased = emptySet(), today) as LiftTrend.Moved
        assertEquals(progress.fromKg, trend.fromKg, 0.0)
        assertEquals(progress.toKg, trend.toKg, 0.0)
    }

    @Test
    fun aLiftThatHasntMovedSaysSoInTheCoachLinesCount() {
        val points = listOf(point(1, 40, 100.0), point(2, 30, 100.0), point(3, 20, 100.0), point(4, 10, 100.0))
        assertEquals(LiftTrend.Stalled(3), LiftTrend.of(points, eased = emptySet(), today))
    }

    @Test
    fun underAFullPercentWithNoStallItHeldSteady() {
        val points = listOf(point(1, 14, 100.0), point(2, 2, 100.5))
        assertEquals(LiftTrend.Steady(100.5), LiftTrend.of(points, eased = emptySet(), today))
    }

    @Test
    fun underTwoComparableSessionsThereIsNone() {
        assertNull(LiftTrend.of(listOf(point(1, 2, 100.0)), eased = emptySet(), today))
        assertNull(LiftTrend.of(listOf(point(1, 9, 100.0), point(2, 2, null)), eased = emptySet(), today))
    }

    /** A deload or an eased day isn't a session to compare with, as the coach line and round 4's lift after a deload have it. */
    @Test
    fun aDeloadOrEasedDayIsntCounted() {
        val points = listOf(point(1, 16, 100.0), point(2, 9, 100.0), point(3, 2, 104.0))
        assertNull(LiftTrend.of(points.take(2) + point(3, 2, 100.0), eased = setOf(2L, 3L), today))
        assertEquals(LiftTrend.Moved(100.0, 104.0), LiftTrend.of(points, eased = setOf(2L), today))
    }

    @Test
    fun aLiftNotDoneInFourWeeksHasNone() {
        assertNull(LiftTrend.of(listOf(point(1, 60, 100.0), point(2, 40, 104.0)), eased = emptySet(), today))
    }
}
