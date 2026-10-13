package com.hyperlog.app.session

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.PadKey
import com.hyperlog.app.history.LiftGoals
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.Programs
import com.hyperlog.app.progression.Progression
import com.hyperlog.app.progression.Suggestion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * A lift goal never changes the suggestions (SPEC, Lift goals): the same weeks of bench, logged
 * with and without a 100 kg goal, hand the progression engine the same inputs and get the same
 * suggestions back.
 */
@RunWith(RobolectricTestRunner::class)
class LiftGoalSessionTest {
    /** Bench heavy on Day A and for volume on Day C, as the starter program has it. */
    private val program = """
        { "format": 1, "title": "Bench", "blocks": [
          { "id": "build", "name": "Build", "weekStart": 1, "weekEnd": 6, "days": [
            { "id": "a", "name": "Day A", "groups": [
              { "slots": [ { "id": "bench", "exercise": "barbell_bench_press", "sets": [ { "reps": "4-6", "rir": 1 }, { "reps": "6-8", "rir": 2, "backoff": {} } ] } ] } ] },
            { "id": "c", "name": "Day C", "groups": [
              { "slots": [ { "id": "bench_c", "exercise": "barbell_bench_press", "sets": [ { "count": 2, "reps": "8-10", "rir": 1 } ] } ] } ] } ] } ] }
    """

    /** One call to the engine: everything it reads, the ladder as its rungs. */
    private data class Call(val input: List<Any?>, val suggestion: Suggestion?)

    /** Runs three weeks of A and C bench, the goal set first or not, and returns every engine call. */
    private suspend fun weeks(goal: Boolean): List<Call> {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HyperlogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            ExerciseLibrary(db).sync(LibrarySeed.fromAssets(ApplicationProvider.getApplicationContext()))
            var now = 1_790_000_000_000L
            val programs = Programs(db, clock = { now })
            programs.import(program).let { assertTrue("$it", it is com.hyperlog.app.program.ProgramParser.Result.Valid) }
            if (goal) LiftGoals(db).set("barbell_bench_press", 100.0, by = LocalDate.of(2026, 12, 31))
            val calls = mutableListOf<Call>()
            val logger = SessionLogger(db, clock = { now }, programs = programs) { plan, today, history, ladder ->
                Progression.suggest(plan, today, history, ladder).also { calls += Call(listOf(plan, today, history, ladder.rungs), it) }
            }
            logger.load()
            suspend fun log(kg: String, reps: Int, rir: Int) {
                now += 200_000
                logger.focus(Field.Weight)
                kg.forEach { logger.press(if (it == '.') PadKey.Dot else PadKey.Digit(it.digitToInt())) }
                logger.done()
                "$reps".forEach { logger.press(PadKey.Digit(it.digitToInt())) }
                logger.selectRir(rir)
                logger.done()
            }
            val sessions = listOf(
                listOf(Triple("80", 6, 1), Triple("72.5", 8, 2)),
                listOf(Triple("70", 10, 1), Triple("70", 9, 0)),
                listOf(Triple("82.5", 5, 1), Triple("75", 7, 2)),
                listOf(Triple("72.5", 9, 1), Triple("72.5", 8, 1)),
                listOf(Triple("85", 4, 1), Triple("75", 8, 2)),
                listOf(Triple("72.5", 10, 1), Triple("72.5", 9, 1)),
            )
            for (sets in sessions) {
                now += 3 * 86_400_000L
                logger.startDay(programs.active.first()!!, gymId = null)
                for ((kg, reps, rir) in sets) log(kg, reps, rir)
                logger.finish()
            }
            // The next Day A, the suggestion the goal would most tempt.
            now += 3 * 86_400_000L
            logger.startDay(programs.active.first()!!, gymId = null)
            return calls
        } finally {
            db.close()
        }
    }

    @Test
    fun suggestionsAreIdenticalWithAndWithoutTheGoal() = runTest {
        val without = weeks(goal = false)
        val with = weeks(goal = true)

        assertTrue("the engine ran", without.size >= 12)
        assertTrue("it suggested", without.count { it.suggestion != null } >= 6)
        assertEquals(without.map { it.input }, with.map { it.input })
        assertEquals(without.map { it.suggestion }, with.map { it.suggestion })
    }
}
