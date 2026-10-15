package com.hyperlog.app.ui.session

import android.graphics.Bitmap
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.hyperlog.app.domain.Extension
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
import com.hyperlog.app.session.SessionExercise
import com.hyperlog.app.session.WarmupEdit
import com.hyperlog.app.ui.theme.HyperlogTheme
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The active row has no highlight (docs/design/logging-screen.md, Owner decisions): its kg and
 * reps are outlined cells, the focused one in primary, and its index circle is filled with
 * primary. Checked on the pixels of a render, in light and dark.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ActiveRowTest {
    @get:Rule
    val compose = createComposeRule()

    private val bench = Exercise("barbell_bench_press", "Barbell Bench Press", Equipment.Barbell, MovementPattern.HorizontalPush, GripDemand.Low, listOf("Chest"))

    private class Shot(val bitmap: Bitmap, val cs: ColorScheme, val density: Float)

    private fun shoot(session: ActiveSession, dark: Boolean): Shot {
        RuntimeEnvironment.setQualifiers(if (dark) "+night" else "+notnight")
        var scheme: ColorScheme? = null
        compose.setContent {
            HyperlogTheme {
                scheme = MaterialTheme.colorScheme
                SessionScreen(session, listOf(bench), StillActions, onLeave = {})
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        return Shot(compose.onRoot().captureToImage().asAndroidBitmap(), scheme!!, compose.density.density)
    }

    private fun Shot.at(x: Dp, y: Dp): Color = Color(bitmap.getPixel((x.value * density).toInt(), (y.value * density).toInt()))

    private fun assertColor(what: String, expected: Color, actual: Color) {
        val close = abs(expected.red - actual.red) < 0.04f && abs(expected.green - actual.green) < 0.04f && abs(expected.blue - actual.blue) < 0.04f
        assertTrue("$what: expected $expected but was $actual", close)
    }

    private fun index(label: String, n: Int = 0): DpRect = compose.onAllNodesWithTag(indexTag(label), useUnmergedTree = true)[n].getBoundsInRoot()

    /** The cell whose tap types into [field] ("kg" or "Reps"), the [n]th on screen. */
    private fun cell(field: String, n: Int = 0): DpRect = compose.onAllNodes(
        SemanticsMatcher("taps into $field") { it.config.getOrNull(SemanticsActions.OnClick)?.label == field },
    )[n].getBoundsInRoot()

    private fun mid(r: DpRect) = (r.top + r.bottom) / 2

    /** No fill beside the index circle, which is primary; the focused cell is outlined in primary, the other in outline. */
    private fun Shot.assertActive(what: String, circle: DpRect?, row: DpRect, focused: DpRect, other: DpRect?) {
        circle?.let { assertColor("$what: index circle", cs.primary, at(it.left + 4.dp, mid(it))) }
        assertColor("$what: no pill left of the index column", cs.surface, at(row.left - 6.dp, mid(row)))
        assertColor("$what: no pill right of the index column", cs.surface, at(row.right + 8.dp, mid(row)))
        assertColor("$what: focused cell", cs.primary, at(focused.left + 1.dp, mid(focused)))
        other?.let { assertColor("$what: other cell", cs.outline, at(it.left + 0.5.dp, mid(it))) }
    }

    private val top = SetPrescription(RepRange(4, 6), 1, 180)

    private fun session(exercise: SessionExercise, entry: SetEntry, followUp: FollowUp? = null, warmupEdit: WarmupEdit? = null) = ActiveSession(
        id = 1, startedAt = 0, exercises = listOf(exercise), currentId = 1, entry = entry, padVisible = true,
        restEndsAt = null, restTotalMs = 0, followUp = followUp, openSetId = followUp?.let { 7L }, warmupEdit = warmupEdit,
        rampEntry = entry.takeIf { warmupEdit != null },
    )

    private fun exercise(sets: List<LoggedSet>, plan: List<SetPrescription>) = SessionExercise(
        id = 1, exercise = bench, sets = sets, lastTime = emptyList(), ladder = DefaultPlates.ladder(), ladderKnown = true,
        prescription = plan, plates = DefaultPlates,
    )

    private fun bothThemes(block: (dark: Boolean) -> Unit) = listOf(false, true).forEach(block)

    @Test
    fun aPlainActiveSetIsMarkedByItsCellsAndAPrimaryCircle() = bothThemes { dark ->
        val shot = shoot(session(exercise(emptyList(), listOf(top)), SetEntry("100", "", "", 1, Field.Reps, partialMode = false, fresh = true)), dark)
        val circle = index("1")
        shot.assertActive("plain set, dark=$dark", circle, circle, focused = cell("Reps"), other = cell("kg"))
    }

    @Test
    fun anActiveDropIsMarkedOnlyByItsCellsAndTheSetsCircleStaysPrimary() = bothThemes { dark ->
        val drops = Technique.Dropset(2, 25)
        val set = LoggedSet(25.0, 9, 0, 1, technique = drops, extensions = listOf(Extension(Extension.Kind.Drop, 17.5, 6)))
        val shot = shoot(
            session(exercise(listOf(set), listOf(SetPrescription(RepRange(8, 10), 1, 120, technique = drops))), SetEntry("12.5", "6", "", null, Field.Reps, partialMode = false, fresh = true), FollowUp.Drop(1, 2)),
            dark,
        )
        val circle = index("1")
        assertColor("drop set, dark=$dark: the set's circle", shot.cs.primary, shot.at(circle.left + 4.dp, mid(circle)))
        val reps = cell("Reps")
        val row = DpRect(circle.left, reps.top, circle.right, reps.bottom)
        shot.assertActive("active drop, dark=$dark", null, row, focused = reps, other = cell("kg"))
        // The tall pill runs whole through the active drop's index column.
        assertColor("drop set, dark=$dark: tall pill", shot.cs.tertiaryContainer, shot.at(circle.left + 4.dp, mid(reps)))
    }

    @Test
    fun anActiveMiniSetIsMarkedOnlyByItsCellsAndTheSetsCircleStaysPrimary() = bothThemes { dark ->
        val myo = Technique.MyoReps(4)
        val set = LoggedSet(60.0, 12, 0, 0, technique = myo, extensions = listOf(Extension(Extension.Kind.MiniSet, 60.0, 4)))
        val shot = shoot(
            session(exercise(listOf(set), listOf(SetPrescription(RepRange(10, 12), 0, 90, technique = myo))), SetEntry("60", "4", "", null, Field.Reps, partialMode = false, fresh = true), FollowUp.MiniSet(1, 4)),
            dark,
        )
        val circle = index("1")
        assertColor("myo set, dark=$dark: the set's circle", shot.cs.primary, shot.at(circle.left + 4.dp, mid(circle)))
        val reps = cell("Reps")
        // A mini-set's weight is the set's, so it is no cell.
        shot.assertActive("active mini-set, dark=$dark", null, DpRect(circle.left, reps.top, circle.right, reps.bottom), focused = reps, other = null)
    }

    @Test
    fun aFinishedMyoRepsSetsPillIsOneSmoothShapeDownItsLeftEdge() = bothThemes { dark ->
        val myo = Technique.MyoReps(4)
        val minis = List(5) { Extension(Extension.Kind.MiniSet, 60.0, 4) }
        val set = LoggedSet(60.0, 12, 0, 0, technique = myo, extensions = minis)
        val shot = shoot(
            session(exercise(listOf(set), listOf(SetPrescription(RepRange(10, 12), 0, 90, technique = myo))), SetEntry("60", "", "", null, Field.Reps, partialMode = false, fresh = true)),
            dark,
        )
        val circle = index("1")
        // Down the pill's left edge, from below the circle to its last mini-set, every pixel is the pill's.
        val end = mid(circle) + RowHeight / 2 + SubRowHeight * 4f
        var y = circle.bottom
        while (y < end) {
            assertColor("myo set, dark=$dark: pill's left edge at y=$y", shot.cs.tertiaryContainer, shot.at(circle.left + 1.dp, y))
            y += 1.dp
        }
    }

    @Test
    fun aRangePlaceholderFitsInsideItsCell() {
        shoot(session(exercise(emptyList(), listOf(SetPrescription(RepRange(10, 12), 0, 90))), SetEntry("60", "", "", 0, Field.Reps, partialMode = false, fresh = true)), dark = true)
        val cell = cell("Reps")
        val text = compose.onNode(hasText("10–12") and hasAnyAncestor(hasClickAction()), useUnmergedTree = true)
        val results = mutableListOf<TextLayoutResult>()
        text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        val layout = results.single()
        assertTrue("the placeholder overflows", !layout.hasVisualOverflow)
        val line = (layout.getLineRight(0) - layout.getLineLeft(0)) / compose.density.density
        val room = (cell.right - cell.left).value - 2 * 8
        assertTrue("10–12 is $line dp wide, more than the $room dp inside the cell's padding", line <= room)
    }

    @Test
    fun aWarmUpOnThePadIsMarkedTheSameWay() = bothThemes { dark ->
        val entry = SetEntry("100", "", "", 1, Field.Reps, partialMode = false, fresh = true)
        val shot = shoot(
            session(exercise(emptyList(), listOf(top)), entry, warmupEdit = WarmupEdit(1, 1, SetEntry("60", "5", "", null, Field.Weight, partialMode = false, fresh = true))),
            dark,
        )
        val circle = index("W", 1)
        shot.assertActive("warm-up, dark=$dark", circle, circle, focused = cell("kg"), other = cell("Reps"))
    }
}

private object StillActions : SessionActions {
    override fun press(key: com.hyperlog.app.domain.PadKey) = Unit
    override fun done() = Unit
    override fun selectRir(value: Int) = Unit
    override fun focus(field: Field) = Unit
    override fun partialMode(on: Boolean) = Unit
    override fun pickWeight(kg: Double) = Unit
    override fun setPadVisible(visible: Boolean) = Unit
    override fun addExercise(exerciseId: String) = Unit
    override fun switchTo(sessionExerciseId: Long) = Unit
    override fun adjustRest(deltaMs: Long) = Unit
    override fun endRest() = Unit
    override fun finish() = Unit
    override fun chooseMachine(itemId: Long) = Unit
    override fun addMachine(name: String, ladder: com.hyperlog.app.domain.LadderSpec) = Unit
    override fun changeMachine() = Unit
    override fun saveMachine(item: com.hyperlog.app.gym.EquipmentItem) = Unit
    override fun setUpLadder(spec: com.hyperlog.app.domain.LadderSpec) = Unit
    override fun skipLadderSetup() = Unit
    override fun takeLadderOffer() = Unit
    override fun declineLadderOffer() = Unit
    override fun notAtGym() = Unit
    override fun finishSet() = Unit
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
