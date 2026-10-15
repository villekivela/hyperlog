package com.hyperlog.app.rest

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.domain.PadKey
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.Programs
import com.hyperlog.app.session.SessionLogger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** What the rest notification shows (SPEC, Rest on the lock screen, and Rest rules). */
@RunWith(RobolectricTestRunner::class)
class RestNoticeTest {
    private lateinit var db: HyperlogDatabase
    private lateinit var programs: Programs
    private var now = 1_000_000L

    @Before
    fun open() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HyperlogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        ExerciseLibrary(db).sync(LibrarySeed.fromAssets(ApplicationProvider.getApplicationContext()))
        programs = Programs(db, clock = { now })
    }

    @After
    fun close() = db.close()

    private fun sets(count: Int, rest: Int, technique: String? = null) =
        """[ { "count": $count, "reps": "8-10", "rir": 1, "rest": $rest${technique?.let { ", \"technique\": $it" } ?: ""} } ]"""

    /** Day A: 1 bench (2 sets, 3:00, [benchTechnique]), 2a curl + 2b French press (2 sets each). */
    private suspend fun program(benchTechnique: String? = null) {
        programs.import(
            """
            { "format": 1, "title": "T", "blocks": [ { "id": "b", "name": "B", "weekStart": 1, "weekEnd": 4, "days": [
              { "id": "a", "name": "Day A", "groups": [
                { "slots": [ { "id": "bench", "exercise": "db_bench_press", "sets": ${sets(2, 180, benchTechnique)} } ] },
                { "type": "superset", "slots": [
                  { "id": "curl", "exercise": "db_bicep_curl", "sets": ${sets(2, 60)} },
                  { "id": "french", "exercise": "db_french_press", "sets": ${sets(2, 90)} } ] } ] } ] } ] }
            """,
        )
    }

    private suspend fun logger() = SessionLogger(db, clock = { now }, programs = programs).also {
        it.load()
        it.startDay(programs.active.first()!!, gymId = null)
    }

    private suspend fun SessionLogger.type(text: String) = text.forEach {
        press(if (it == '.') PadKey.Dot else PadKey.Digit(it.digitToInt()))
    }

    /** Logs the active set at [kg] × [reps], never as a double tap. */
    private suspend fun SessionLogger.log(kg: String, reps: String = "8") {
        now += 5_000
        type(kg); done()
        type(reps); done()
    }

    /** ✓ on a drop as prefilled. */
    private suspend fun SessionLogger.dropAsPrefilled() {
        now += 5_000
        done()
    }

    private val SessionLogger.notice get() = state.value!!.restNotice()

    @Test
    fun restAfterASetShowsTheNextSetsTarget() = runTest {
        program()
        val logger = logger()
        assertNull(logger.notice)
        logger.log("20")

        assertEquals(
            RestNotice(
                RestNotice.Kind.Rest, endsAt = now + 180_000, totalMs = 180_000,
                next = NextSet("DB Bench Press", set = 2, weightKg = 20.0, reps = "8–10", rir = 1),
            ),
            logger.notice,
        )
    }

    @Test
    fun noRestBetweenSupersetPartnersAndFullRestAfterTheRound() = runTest {
        program()
        val logger = logger()
        logger.log("20"); logger.log("20")
        logger.log("12")
        assertNull(logger.notice)

        logger.log("10")
        // Full rest after the round, the French press's 1:30, before the curl's second set.
        val notice = logger.notice!!
        assertEquals(RestNotice.Kind.Rest, notice.kind)
        assertEquals(now + 90_000, notice.endsAt)
        assertEquals("DB Curl", notice.next!!.exercise)
        assertEquals(2, notice.next.set)
    }

    @Test
    fun nothingCountsDownOnceTheDayIsDone() = runTest {
        program()
        val logger = logger()
        repeat(2) { logger.log("20") }
        repeat(2) { logger.log("12"); logger.log("10") }
        assertNull(logger.notice)
    }

    @Test
    fun noRestBetweenASetAndItsDrops() = runTest {
        program(benchTechnique = """{ "kind": "dropset", "drops": 2, "percent": 25 }""")
        val logger = logger()
        logger.log("20")
        assertNull(logger.notice)
        logger.dropAsPrefilled()
        assertNull(logger.notice)
        logger.dropAsPrefilled()
        assertEquals(RestNotice.Kind.Rest, logger.notice!!.kind)
        assertEquals(now + 180_000, logger.notice!!.endsAt)
    }

    @Test
    fun aMyoMiniSetIsABreathingCountdownToTheMiniSet() = runTest {
        program(benchTechnique = """{ "kind": "myo_reps", "miniSetReps": 4 }""")
        val logger = logger()
        logger.log("20", "12")

        assertEquals(
            RestNotice(
                RestNotice.Kind.Breathing, endsAt = now + 15_000, totalMs = 15_000,
                next = NextSet("DB Bench Press", set = 1, miniSet = 1, weightKg = 20.0, reps = "4"),
            ),
            logger.notice,
        )
    }

    @Test
    fun endingRestTakesTheNoticeAway() = runTest {
        program()
        val logger = logger()
        logger.log("20")
        logger.endRest()
        assertNull(logger.notice)
    }
}
