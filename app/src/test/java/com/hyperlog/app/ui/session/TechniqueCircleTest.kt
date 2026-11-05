package com.hyperlog.app.ui.session

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.hyperlog.app.domain.LadderSpec.Companion.DefaultPlates
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.GripDemand
import com.hyperlog.app.library.MovementPattern
import com.hyperlog.app.program.HoldPosition
import com.hyperlog.app.program.RepRange
import com.hyperlog.app.program.SetPrescription
import com.hyperlog.app.program.Technique
import com.hyperlog.app.session.ActiveSession
import com.hyperlog.app.session.SessionExercise
import com.hyperlog.app.ui.theme.HyperlogTheme
import com.hyperlog.app.ui.theme.techniqueColor
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
 * A technique set's index circle is its technique's colour with the icon filling it and no set
 * number; a warm-up keeps `W` and a plain set its number (SPEC, Today → The index column).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TechniqueCircleTest {
    @get:Rule
    val compose = createComposeRule()

    private val bench = Exercise("barbell_bench_press", "Barbell Bench Press", Equipment.Barbell, MovementPattern.HorizontalPush, GripDemand.Low, listOf("Chest"))

    /** Set 1 plain, then one set of each technique (sets 2 to 6). */
    private val techniques = listOf(
        Technique.Dropset(), Technique.MyoReps(), Technique.LengthenedPartials(),
        Technique.IsoHold(seconds = 20, position = HoldPosition.Stretch), Technique.Failure,
    )

    private fun show(dark: Boolean) {
        RuntimeEnvironment.setQualifiers(if (dark) "+night" else "+notnight")
        val plan = listOf(SetPrescription(RepRange(4, 6), 1, 180)) + techniques.map { SetPrescription(RepRange(8, 10), 0, 120, technique = it) }
        val exercise = SessionExercise(
            id = 1, exercise = bench, sets = emptyList(), lastTime = emptyList(), ladder = DefaultPlates.ladder(), ladderKnown = true,
            prescription = plan, plates = DefaultPlates,
        )
        val session = ActiveSession(
            id = 1, startedAt = 0, exercises = listOf(exercise), currentId = 1, entry = null,
            padVisible = false, restEndsAt = null, restTotalMs = 0,
        )
        compose.setContent { HyperlogTheme { SessionScreen(session, listOf(bench), Noop, onLeave = {}) } }
    }

    private fun near(a: Color, b: Color) = kotlin.math.abs(a.red - b.red) + kotlin.math.abs(a.green - b.green) + kotlin.math.abs(a.blue - b.blue) < 0.06f

    private fun pixelsOf(number: Int): List<Color> {
        val image = compose.onNodeWithTag(indexTag("$number"), useUnmergedTree = true).captureToImage().toPixelMap()
        return (0 until image.width).flatMap { x -> (0 until image.height).map { y -> image[x, y] } }
    }

    private fun check(dark: Boolean) {
        show(dark)
        techniques.forEachIndexed { i, technique ->
            val number = i + 2
            val fill = techniqueColor(technique.kind, dark)
            val inFill = pixelsOf(number).count { near(it, fill) }
            assertTrue("${technique.kind} (dark=$dark) fills only $inFill pixels of its circle", inFill > 800)
            compose.onAllNodes(hasText("$number") and hasAnyAncestor(hasTestTag(indexTag("$number")))).assertCountEquals(0)
        }
    }

    @Test
    fun eachTechniqueSetIsItsColourWithNoNumberInLight() = check(dark = false)

    @Test
    fun eachTechniqueSetIsItsColourWithNoNumberInDark() = check(dark = true)

    @Test
    fun eachTechniqueSymbolIsCentredInItsCircle() {
        show(dark = false)
        val names = listOf("Drop set", "Myo-reps", "Lengthened partials", "Iso hold", "To failure")
        techniques.forEachIndexed { i, _ ->
            val tag = compose.onNodeWithTag(indexTag("${i + 2}"), useUnmergedTree = true).getBoundsInRoot()
            val icon = compose.onNode(hasContentDescription(names[i]), useUnmergedTree = true).getBoundsInRoot()
            // The circle sits at the column's start, IndexSize across, in the middle of the row.
            val dx = (icon.left + icon.right) / 2 - (tag.left + IndexSize / 2)
            val dy = (icon.top + icon.bottom) / 2 - (tag.top + tag.bottom) / 2
            assertTrue("${names[i]} is $dx across and $dy down from its circle's centre", abs(dx.value) <= 0.5f && abs(dy.value) <= 0.5f)
        }
    }

    @Test
    fun aPlainSetKeepsItsNumberAndNoTechniqueColour() {
        show(dark = false)
        compose.onAllNodes(hasText("1") and hasAnyAncestor(hasTestTag(indexTag("1")))).assertCountEquals(1)
        val colours = techniques.map { techniqueColor(it.kind, false) }
        assertTrue(pixelsOf(1).none { p -> colours.any { near(p, it) } })
    }
}
