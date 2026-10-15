package com.hyperlog.app.ui.session

import androidx.compose.runtime.collectAsState
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.domain.PadKey
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.Programs
import com.hyperlog.app.program.RepRange
import com.hyperlog.app.program.Technique
import com.hyperlog.app.session.SessionLogger
import com.hyperlog.app.ui.theme.HyperlogTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A myo-reps set on the real session screen over a real [SessionLogger]: the owner's Leg Press
 * (target 3, at most 5), a mini-set lowered with the pad's − key around the 15 s breathing pause,
 * then logged with the pad's ✓ or the row's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiveMiniSetTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: HyperlogDatabase
    private lateinit var logger: SessionLogger
    private var now = 1_000_000L

    @Before
    fun open() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HyperlogDatabase::class.java).allowMainThreadQueries().build()
        ExerciseLibrary(db).sync(LibrarySeed.fromAssets(ApplicationProvider.getApplicationContext()))
        val programs = Programs(db, clock = { now })
        programs.import(
            """
            { "format": 1, "title": "T", "blocks": [ { "id": "b", "name": "B", "weekStart": 1, "weekEnd": 4, "days": [
              { "id": "a", "name": "Day A", "groups": [ { "slots": [ { "id": "s", "exercise": "leg_extension", "sets": [
                { "reps": "10-12", "rir": 0, "rest": 90, "technique": { "kind": "myo_reps", "miniSetReps": 3 } } ] } ] } ] } ] } ] }
            """,
        )
        logger = SessionLogger(db, clock = { now }, programs = programs).also { it.load() }
        logger.startDay(programs.active.first()!!, gymId = null)
        logger.press(PadKey.Digit(6)); logger.press(PadKey.Digit(0)); logger.done()
        logger.press(PadKey.Digit(1)); logger.press(PadKey.Digit(2))
        now += 5_000
        logger.done()
    }

    @After
    fun close() = db.close()

    private val actions = object : SessionActions {
        override fun press(key: PadKey) = runBlocking { logger.press(key) }
        override fun done() = runBlocking { logger.done() }
        override fun selectRir(value: Int) = runBlocking { logger.selectRir(value) }
        override fun focus(field: Field) = runBlocking { logger.focus(field) }
        override fun partialMode(on: Boolean) = runBlocking { logger.partialMode(on) }
        override fun pickWeight(kg: Double) = runBlocking { logger.pickWeight(kg) }
        override fun setPadVisible(visible: Boolean) = runBlocking { logger.setPadVisible(visible) }
        override fun logShown() = runBlocking { logger.logShown() }
        override fun endRest() = runBlocking { logger.endRest() }
        override fun finishSet() = runBlocking { logger.finishSet() }
        override fun addDrop(setIndex: Int) = runBlocking { logger.addDrop(setIndex) }
        override fun addExercise(exerciseId: String) = Unit
        override fun switchTo(sessionExerciseId: Long) = Unit
        override fun adjustRest(deltaMs: Long) = Unit
        override fun finish() = Unit
        override fun chooseMachine(itemId: Long) = Unit
        override fun addMachine(name: String, ladder: LadderSpec) = Unit
        override fun changeMachine() = Unit
        override fun saveMachine(item: com.hyperlog.app.gym.EquipmentItem) = Unit
        override fun setUpLadder(spec: LadderSpec) = Unit
        override fun skipLadderSetup() = Unit
        override fun takeLadderOffer() = Unit
        override fun declineLadderOffer() = Unit
        override fun notAtGym() = Unit
        override fun startHold() = Unit
        override fun stopHold() = Unit
        override fun setTechnique(setIndex: Int, technique: Technique?) = Unit
        override fun split(on: Boolean) = Unit
        override fun swap(sessionExerciseId: Long, exerciseId: String) = Unit
        override fun addSet(sessionExerciseId: Long) = Unit
        override fun removeSet(sessionExerciseId: Long, setIndex: Int) = Unit
        override fun setTarget(sessionExerciseId: Long, setIndex: Int, reps: RepRange, rir: Int?) = Unit
        override fun removeExercise(sessionExerciseId: Long) = Unit
        override fun move(sessionExerciseId: Long, by: Int) = Unit
        override fun tickWarmup(index: Int) = Unit
        override fun open(sessionExerciseId: Long) = Unit
        override fun closeView() = Unit
        override fun correct(sessionExerciseId: Long, set: Int, extension: Int?) = Unit
        override fun cancelCorrection() = Unit
    }

    private fun show() {
        compose.setContent {
            HyperlogTheme {
                val session = logger.state.collectAsState().value
                if (session != null) SessionScreen(session, emptyList(), actions, onLeave = {})
            }
        }
        compose.waitForIdle()
    }

    private fun str(id: Int) = ApplicationProvider.getApplicationContext<android.app.Application>().getString(id)

    private fun minus() = compose.onNodeWithContentDescription(str(com.hyperlog.app.R.string.pad_minus)).performClick()
    private fun padDone() = compose.onNodeWithTag(DoneKeyTag).performClick()
    private fun rowDone() = compose.onNodeWithContentDescription(str(com.hyperlog.app.R.string.check_log_follow)).performClick()

    private fun reps() = logger.state.value!!.current!!.sets.single().extensions.map { it.reps }

    private fun assertLoweredAndClosed() {
        compose.waitForIdle()
        assertEquals(listOf(2), reps())
        assertNull(logger.state.value!!.followUp)
    }

    @Test
    fun minusDuringTheBreathingPauseThenPadCheckAfterItEnds() {
        show()
        assertEquals(true, logger.state.value!!.breathing)
        minus()
        now += 20_000
        compose.mainClock.advanceTimeBy(20_000)
        padDone()
        assertLoweredAndClosed()
    }

    @Test
    fun minusAfterTheBreathingPauseEndsThenPadCheck() {
        show()
        now += 20_000
        compose.mainClock.advanceTimeBy(20_000)
        compose.waitForIdle()
        minus()
        padDone()
        assertLoweredAndClosed()
    }

    @Test
    fun minusThenTheRowsCheckWithThePadHidden() {
        show()
        minus()
        compose.onNodeWithContentDescription(str(com.hyperlog.app.R.string.pad_hide)).performClick()
        now += 20_000
        compose.mainClock.advanceTimeBy(20_000)
        rowDone()
        assertLoweredAndClosed()
    }

    @Test
    fun minusThenTheRowsCheckWithThePadShown() {
        show()
        minus()
        now += 20_000
        rowDone()
        assertLoweredAndClosed()
    }
}
