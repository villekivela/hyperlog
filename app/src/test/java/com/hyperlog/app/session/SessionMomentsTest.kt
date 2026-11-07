package com.hyperlog.app.session

import com.hyperlog.app.domain.LoggedSet
import org.junit.Assert.assertEquals
import org.junit.Test

/** What a finished session can say about itself (#272). */
class SessionMomentsTest {
    private fun set(kg: Double, reps: Int) = LoggedSet(kg, reps, 0, 1)

    @Test
    fun heavierIsUp() = assertEquals(LiftChange.Up, liftChange(listOf(set(105.0, 4)), listOf(set(100.0, 6))))

    @Test
    fun asHeavyForMoreRepsIsUp() = assertEquals(LiftChange.Up, liftChange(listOf(set(100.0, 7)), listOf(set(100.0, 6))))

    @Test
    fun theSameTopSetIsTheSame() = assertEquals(LiftChange.Same, liftChange(listOf(set(100.0, 6), set(90.0, 8)), listOf(set(100.0, 6))))

    @Test
    fun lighterIsDown() = assertEquals(LiftChange.Down, liftChange(listOf(set(95.0, 10)), listOf(set(100.0, 6))))

    @Test
    fun asHeavyForFewerRepsIsDown() = assertEquals(LiftChange.Down, liftChange(listOf(set(100.0, 5)), listOf(set(100.0, 6))))

    @Test
    fun theTopSetDecidesWhicheverPlaceItIn() = assertEquals(LiftChange.Up, liftChange(listOf(set(90.0, 8), set(105.0, 4)), listOf(set(100.0, 6), set(90.0, 8))))

    @Test
    fun nothingToCompareWithIsNoChange() = assertEquals(null, liftChange(listOf(set(100.0, 6)), emptyList()))
}
