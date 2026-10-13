package com.hyperlog.app.history

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.HyperlogDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/** Lift goals (SPEC, Lift goals): one target an exercise, optionally by a date. */
@RunWith(RobolectricTestRunner::class)
class LiftGoalsTest {
    private lateinit var db: HyperlogDatabase

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HyperlogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun close() = db.close()

    @Test
    fun anExerciseHoldsOneGoalUntilItIsCleared() = runTest {
        val goals = LiftGoals(db)
        assertNull(goals.of("barbell_bench_press"))

        goals.set("barbell_bench_press", 95.0, by = null)
        goals.set("barbell_bench_press", 100.0, by = LocalDate.of(2026, 12, 31))

        val bench = LiftGoal("barbell_bench_press", 100.0, LocalDate.of(2026, 12, 31))
        assertEquals(bench, goals.of("barbell_bench_press"))
        assertEquals(listOf(bench), goals.all.first())

        goals.clear("barbell_bench_press")
        assertNull(goals.of("barbell_bench_press"))
    }
}
