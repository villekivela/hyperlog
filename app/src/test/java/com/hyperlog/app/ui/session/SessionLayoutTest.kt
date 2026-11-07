package com.hyperlog.app.ui.session

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.LadderSpec.Companion.DefaultPlates
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.domain.SetEntry
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.GripDemand
import com.hyperlog.app.library.MovementPattern
import com.hyperlog.app.program.RepRange
import com.hyperlog.app.program.SetPrescription
import com.hyperlog.app.program.Technique
import com.hyperlog.app.session.ActiveSession
import com.hyperlog.app.session.FollowUp
import com.hyperlog.app.session.LastSession
import com.hyperlog.app.session.SessionExercise
import com.hyperlog.app.ui.theme.HyperlogTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The logging screen's column: the cue, the table straight under it, then the last sessions where there's room (#269). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w427dp-h950dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SessionLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private val bench = Exercise("barbell_bench_press", "Barbell Bench Press", Equipment.Barbell, MovementPattern.HorizontalPush, GripDemand.Low, listOf("Chest"), note = "Shoulder blades back and down.")
    private val history = listOf(
        LastSession(LocalDate.of(2026, 11, 2), LoggedSet(97.5, 5, 0, 2)),
        LastSession(LocalDate.of(2026, 10, 29), LoggedSet(95.0, 5, 0, 1)),
        LastSession(LocalDate.of(2026, 10, 26), LoggedSet(92.5, 6, 0, 1)),
    )

    private fun exercise(sets: Int = 1, logged: Int = 0, note: String? = bench.note, last: List<LastSession> = history, technique: Technique? = null) = SessionExercise(
        1, bench.copy(note = note), List(logged) { LoggedSet(100.0, 5, 0, 1, technique = technique) }, emptyList(), DefaultPlates.ladder(), true,
        prescription = List(sets) { SetPrescription(RepRange(4, 6), 1, 180, technique = technique) }, plates = DefaultPlates, lastSessions = last,
        setIndices = List(logged) { it },
    )

    private val typing = SetEntry("100", "", "", 1, Field.Reps, partialMode = false, fresh = true)

    private fun session(e: SessionExercise, entry: SetEntry? = typing, pad: Boolean = true) =
        ActiveSession(1, 0, listOf(e), 1, entry, padVisible = pad, restEndsAt = null, restTotalMs = 180_000, rampEntry = entry)

    private var shown by mutableStateOf<ActiveSession?>(null)
    private var scale by mutableFloatStateOf(1.15f)

    /** [session] at [at] text size: set once, then swapped, as one screen can only be set once. */
    private fun show(session: ActiveSession, at: Float = 1.15f) {
        val first = shown == null
        compose.runOnIdle { shown = session; scale = at }
        if (first) compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                HyperlogTheme { shown?.let { key(it) { SessionScreen(it, listOf(bench), Recording(mutableListOf()), onLeave = {}) } } }
            }
        }
        compose.waitForIdle()
    }

    private fun SemanticsNodeInteraction.bounds() = getBoundsInRoot()
    private fun heads() = compose.onNode(hasText("kg") and hasAnyAncestor(hasTestTag(SetTableTag)), useUnmergedTree = true).bounds()

    /** What the table sits under: the cue, else the exercise's name. */
    private fun above(cue: Boolean): Dp =
        if (cue) compose.onNodeWithTag(CueBandTag, useUnmergedTree = true).bounds().bottom
        else compose.onNode(hasText(bench.name) and !hasAnyAncestor(hasTestTag(SetTableTag)), useUnmergedTree = true).bounds().bottom

    private fun assertUnder(state: String, cue: Boolean = true) {
        val gap = heads().top - above(cue)
        // The heads' own 32 dp box sets the text a little under; anything more is a gap.
        assertTrue("$state: the table is $gap under what's above it", gap <= 40.dp)
    }

    @Test
    fun aShortTableSitsUnderTheCueWithThePadUp() { show(session(exercise())); assertUnder("pad up") }

    @Test
    fun aShortTableSitsUnderTheCueWithThePadHidden() { show(session(exercise(), pad = false)); assertUnder("pad hidden") }

    @Test
    fun aShortTableSitsUnderTheCueWhileRestRuns() {
        show(session(exercise(sets = 2, logged = 1)).copy(restEndsAt = System.currentTimeMillis() + 95_000))
        assertUnder("rest running")
    }

    @Test
    fun aShortTableSitsUnderTheCueOverDayDone() { show(session(exercise(logged = 1), entry = null)); assertUnder("Day done") }

    @Test
    fun aShortTableSitsUnderTheCueInADrop() {
        val drops = exercise(logged = 1, technique = Technique.Dropset(2, 25))
        show(session(drops).copy(followUp = FollowUp.Drop(0, 2), openSetId = 1, openIndex = 0))
        assertUnder("drop")
    }

    @Test
    fun withNoCueTheTableSitsUnderTheName() { show(session(exercise(note = null))); assertUnder("no cue", cue = false) }

    /** The note is part of the exercise: it shows while the warm-ups fill the table, before any set. */
    @Test
    fun theNoteShowsWithFourWarmupsBeforeAnySet() {
        listOf(1.15f, 2f).forEach { scale ->
            show(session(exercise(sets = 3)), scale)
            compose.onAllNodesWithText("W", useUnmergedTree = true).fetchSemanticsNodes().size.let { assertTrue("$it warm-ups at $scale", it >= 4) }
            compose.onAllNodesWithTag(CueBandTag, useUnmergedTree = true).assertCountEquals(1)
        }
    }

    /** No warm-ups at the bar's weight, so the table is one row and leaves room. */
    private val light = SetEntry("20", "", "", 1, Field.Reps, partialMode = false, fresh = true)

    @Test
    fun withRoomTheLastSessionsShowUnderTheTable() {
        show(session(exercise(), entry = light))
        compose.onAllNodesWithText("Last sessions").assertCountEquals(1)
        assertEquals(3, compose.onAllNodesWithTag(LastSessionTag, useUnmergedTree = true).fetchSemanticsNodes().size)
        compose.onAllNodesWithText("Mon 2 Nov", substring = true).assertCountEquals(1)
    }

    @Test
    fun theLastSessionsNeverPushTheTableOrThePad() {
        show(session(exercise(last = emptyList())))
        val table = heads(); val pad = compose.onNodeWithTag(PadTag, useUnmergedTree = true).bounds()
        show(session(exercise()))
        assertEquals(table, heads())
        assertEquals(pad, compose.onNodeWithTag(PadTag, useUnmergedTree = true).bounds())
    }

    @Test
    fun aLongTableLeavesNoRoomForThem() {
        show(session(exercise(sets = 8)))
        compose.onAllNodesWithText("Last sessions").assertCountEquals(0)
        compose.onAllNodesWithTag(LastSessionTag, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun onlyWholeRowsShow() {
        listOf(2, 3, 4, 5).forEach { sets ->
            show(session(exercise(sets = sets)))
            val rows = compose.onAllNodesWithTag(LastSessionTag, useUnmergedTree = true).fetchSemanticsNodes()
            val next = compose.onNodeWithTag(NextRowTag, useUnmergedTree = true).bounds().top
            rows.forEach { assertTrue("A row at $sets sets runs under the pad", with(compose.density) { it.boundsInRoot.bottom.toDp() } <= next) }
        }
    }
}
