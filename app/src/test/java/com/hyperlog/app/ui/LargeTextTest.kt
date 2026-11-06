package com.hyperlog.app.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.hyperlog.app.AppNavigationBar
import com.hyperlog.app.Screen
import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.LadderSpec.Companion.DefaultPlates
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.domain.SetEntry
import com.hyperlog.app.gym.Gym
import com.hyperlog.app.history.ExerciseTrend
import com.hyperlog.app.history.TrendLine
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.GripDemand
import com.hyperlog.app.library.MovementPattern
import com.hyperlog.app.program.RepRange
import com.hyperlog.app.program.SetPrescription
import com.hyperlog.app.session.ActiveSession
import com.hyperlog.app.session.PastSession
import com.hyperlog.app.session.SessionExercise
import com.hyperlog.app.navItem
import com.hyperlog.app.ui.gym.GymScreen
import com.hyperlog.app.ui.history.HistoryScreen
import com.hyperlog.app.ui.program.ProgramHeader
import com.hyperlog.app.ui.session.PastSessionScreen
import com.hyperlog.app.ui.session.Recording
import com.hyperlog.app.ui.session.SessionScreen
import com.hyperlog.app.ui.session.SetTableTag
import com.hyperlog.app.ui.session.RirChipLoggedTag
import com.hyperlog.app.ui.session.RirChipTargetTag
import com.hyperlog.app.ui.theme.CardMargin
import com.hyperlog.app.ui.theme.ConnectedChoice
import com.hyperlog.app.ui.theme.HyperlogTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val Scales = listOf(1.15f, 1.3f, 1.5f, 1.8f, 2f)

/**
 * Screens at the owner's system text size (115 %) on a phone a little narrower than theirs: what
 * the phone pass (#58) found cut short stays whole.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
// Real text measuring, so cut-short text shows.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LargeTextTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(scale: Float = 1.15f, content: @Composable () -> Unit) = compose.setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) { HyperlogTheme { content() } }
    }

    /** [content] at each size the phone offers up to 200 %, running [check] at each. */
    private fun atEveryScale(content: @Composable () -> Unit, check: (Float) -> Unit) {
        var scale by mutableFloatStateOf(Scales.first())
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) { HyperlogTheme { content() } }
        }
        Scales.forEach {
            compose.runOnIdle { scale = it }
            compose.waitForIdle()
            check(it)
        }
    }

    private fun SemanticsNodeInteraction.layout(): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        return layouts.single()
    }

    /**
     * Ink past the text's box, or an ellipsis. Not `hasVisualOverflow`, which compares the paragraph's
     * width, the widest the text may be, and so calls any short line in a loose box cut short.
     */
    private fun SemanticsNodeInteraction.cutShort(): Boolean {
        val layout = layout()
        return layout.didOverflowHeight || (0 until layout.lineCount).any { layout.isLineEllipsized(it) || layout.getLineRight(it) > layout.size.width + 0.5f }
    }

    @Test
    fun aPastSessionsTitleWrapsRatherThanBeingCut() {
        val title = "Day C — Bench volume, leg press, pulldown"
        show { PastSessionScreen(PastSession(1, 0, 3_600_000, title, "Kalliokoski", emptyList()), onOpen = {}, onBack = {}) }
        assertFalse("The title is cut short", compose.onNodeWithText(title, useUnmergedTree = true).cutShort())
    }

    @Test
    fun aLongExerciseNameOnProgressWrapsRatherThanBeingCut() {
        val name = "Chest-Supported Machine Row"
        val exercise = Exercise("row", name, Equipment.Machine, MovementPattern.HorizontalPull, GripDemand.Low, listOf("Back"))
        val trend = ExerciseTrend(exercise, listOf(TrendLine(null, null, emptyList()), TrendLine(null, null, emptyList())))
        show {
            HistoryScreen(
                emptyList(), emptyList(), listOf(trend), emptyList(), tab = 1, onTab = {}, onOpenSession = {}, onOpenExercise = {},
                onBodyweight = {}, onLogBodyweight = {}, onBack = {},
            )
        }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(name))
        assertFalse("The name is cut short", compose.onNodeWithText(name, useUnmergedTree = true).cutShort())
    }

    @Test
    fun theNextLabelKeepsClearOfTheDayItNames() {
        val day = "Week 3 · Day B"
        atEveryScale({ ProgramHeader("Full body", null, day) }) { scale ->
            val label = compose.onNodeWithText("Next", useUnmergedTree = true)
            val next = compose.onNodeWithText(day, useUnmergedTree = true)
            assertEquals("Next splits at $scale", 1, label.layout().lineCount)
            val ink = label.getBoundsInRoot().left + with(compose.density) { label.layout().getLineRight(0).toDp() }
            assertTrue("Next is ${next.getBoundsInRoot().left - ink} from the day at $scale", next.getBoundsInRoot().left - ink >= 8.dp)
            assertFalse("The day is cut short at $scale", next.cutShort())
        }
    }

    @Test
    fun aConnectedChoiceKeepsEveryLabelWhole() {
        val labels = listOf("Ramp-in", "Build", "Intensify", "Deload + test")
        atEveryScale({ ConnectedChoice(labels, 0, {}, Modifier.padding(horizontal = CardMargin), contentPadding = PaddingValues(horizontal = 8.dp), fitLabels = true) }) { scale ->
            labels.forEach {
                val label = compose.onNodeWithText(it, useUnmergedTree = true)
                assertFalse("$it is cut short at $scale", label.cutShort())
                assertEquals("$it wraps at $scale", 1, label.layout().lineCount)
            }
        }
    }

    @Test
    fun theNavigationBarKeepsEveryLabelOnOneLine() {
        atEveryScale({ AppNavigationBar(Screen.Today) {} }) { scale ->
            listOf("Today", "Program", "Coach", "History", "Settings").forEach {
                val label = compose.onNode(navItem(it), useUnmergedTree = true)
                assertEquals("$it splits at $scale", 1, label.layout().lineCount)
                assertFalse("$it is cut short at $scale", label.cutShort())
            }
        }
    }

    private val bench = Exercise("bench", "Bench Press", Equipment.Barbell, MovementPattern.HorizontalPush, GripDemand.Low, listOf("Chest"))

    /** The logging screen with one exercise and rest over, so the Next row reads `Last exercise` beside the clock. */
    @Composable
    private fun Session() {
        val exercise = SessionExercise(
            id = 1, exercise = bench, sets = emptyList(), lastTime = listOf(LoggedSet(50.0, 10, 5, 1)), ladder = DefaultPlates.ladder(), ladderKnown = true,
            prescription = listOf(SetPrescription(RepRange(4, 6), 1, 180)), plates = DefaultPlates,
        )
        val entry = SetEntry("100", "6", "", 1, Field.Reps, partialMode = false, fresh = true)
        val session = ActiveSession(id = 1, startedAt = 0, exercises = listOf(exercise), currentId = 1, entry = entry, padVisible = true, restEndsAt = 1, restTotalMs = 180_000)
        SessionScreen(session, listOf(bench), Recording(mutableListOf()), onLeave = {})
    }

    @Test
    fun lastTimesSetStaysWhole() {
        atEveryScale({ Session() }) { scale ->
            compose.onNode(hasScrollToNodeAction() and hasAnyAncestor(hasTestTag(SetTableTag))).performScrollToNode(hasText("50 × 10 + 5"))
            assertFalse("Last time is cut short at $scale", compose.onNodeWithText("50 × 10 + 5", useUnmergedTree = true).cutShort())
        }
    }

    @Test
    fun theNextRowsNameStaysWhole() {
        atEveryScale({ Session() }) { scale ->
            assertFalse("The Next row is cut short at $scale", compose.onNodeWithText("Last exercise", useUnmergedTree = true).cutShort())
        }
    }

    @Test
    fun thePadsRirChipsStayInsideTheirKeys() {
        atEveryScale({ Session() }) { scale ->
            listOf("0", "1", "2", "3", "4+").forEach {
                val key = compose.onNodeWithContentDescription("RIR $it", useUnmergedTree = true).getBoundsInRoot()
                val chip = compose.onAllNodes((hasTestTag(RirChipTargetTag) or hasTestTag(RirChipLoggedTag)) and hasAnyAncestor(hasContentDescription("RIR $it")), useUnmergedTree = true)[0].getBoundsInRoot()
                val room = with(compose.density) { 4.dp }
                assertTrue("RIR $it is ${chip.right - chip.left} wide in ${key.right - key.left} at $scale", chip.left >= key.left + room && chip.right <= key.right - room)
            }
        }
    }

    @Test
    fun theSetTablesHeadsStayWhole() {
        atEveryScale({ Session() }) { scale ->
            listOf("Last", "kg", "Reps", "RIR").forEach {
                val head = compose.onAllNodes(hasText(it) and hasAnyAncestor(hasTestTag(SetTableTag)), useUnmergedTree = true)[0]
                assertEquals("$it splits at $scale", 1, head.layout().lineCount)
                assertFalse("$it is cut short at $scale", head.cutShort())
            }
        }
    }

    @Test
    fun aGymsPlatesRowShowsEveryPlate() {
        show { GymScreen(Gym(1, "Kalliokoski", emptyList()), onRename = {}, onDelete = {}, onOpenItem = {}, onAddItem = {}, onBack = {}) }
        assertFalse("The plates are cut short", compose.onNodeWithText("8×20 2×15 2×10 2×5 2×2.5 2×1.25", useUnmergedTree = true).cutShort())
    }
}
