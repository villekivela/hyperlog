package com.hyperlog.app.ui.session

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
import com.hyperlog.app.session.SessionExercise
import com.hyperlog.app.ui.theme.HyperlogTheme
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What TalkBack reads on the set table: every value with its set and unit (#268). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class SetTableLabelsTest {
    @get:Rule
    val compose = createComposeRule()

    private val bench = Exercise("barbell_bench_press", "Barbell Bench Press", Equipment.Barbell, MovementPattern.HorizontalPush, GripDemand.Low, listOf("Chest"))
    private val plan = List(3) { SetPrescription(RepRange(4, 6), 1, 180) }

    private fun show(
        sets: List<LoggedSet>, weight: String = "100", reps: String = "6", lastTime: List<LoggedSet> = emptyList(), plan: List<SetPrescription>? = this.plan,
    ) {
        val exercise = SessionExercise(
            id = 1, exercise = bench, sets = sets, lastTime = lastTime, ladder = DefaultPlates.ladder(), ladderKnown = true,
            prescription = plan, plates = DefaultPlates,
        )
        val entry = SetEntry(weight, reps, "", 1, Field.Reps, partialMode = false, fresh = true)
        val session = ActiveSession(
            id = 1, startedAt = 0, exercises = listOf(exercise), currentId = 1, entry = entry,
            padVisible = true, restEndsAt = null, restTotalMs = 0,
        )
        compose.setContent { HyperlogTheme { SessionScreen(session, listOf(bench), Recording(mutableListOf()), onLeave = {}) } }
    }

    private fun reads(label: String) = compose.onNodeWithContentDescription(label, useUnmergedTree = true).assertExists()

    @Test
    fun theSetBeingTypedReadsItsWeightAndRepsWithSetAndUnit() {
        show(emptyList())
        reads("Set 1 weight, 100 kg")
        reads("Set 1 reps, 6")
    }

    @Test
    fun anEmptyCellSaysSoAndGivesTheTarget() {
        show(emptyList(), weight = "", reps = "")
        reads("Set 1 weight, empty")
        reads("Set 1 reps, empty, target 4–6")
    }

    @Test
    fun aLoggedSetReadsItsValuesAndRir() {
        show(listOf(LoggedSet(50.0, 10, 0, 2)))
        reads("Set 1 weight, 50 kg")
        reads("Set 1 reps, 10")
        reads("Set 1, 2 RIR")
    }

    @Test
    fun aSetToComeReadsItsPlanAndTargetRir() {
        show(emptyList())
        reads("Set 2 reps, 4–6")
        reads("Set 2, target 1 RIR")
    }

    @Test
    fun lastTimeReadsWithItsSetAndUnit() {
        show(emptyList(), lastTime = listOf(LoggedSet(50.0, 10, 5, 2)))
        reads("Set 1 last time, 50 kg × 10 + 5")
    }

    @Test
    fun aDropReadsWhichDropOfWhichSet() {
        show(
            listOf(LoggedSet(25.0, 9, 0, 1, technique = Technique.Dropset(), extensions = listOf(Extension(Extension.Kind.Drop, 17.5, 6)))),
            plan = listOf(SetPrescription(RepRange(8, 10), 0, 120, technique = Technique.Dropset())),
        )
        reads("Drop 1 of set 1 weight, 17.5 kg")
        reads("Drop 1 of set 1 reps, 6")
    }

    /** Android leaves a zero-size node out of the tree, so an empty cell's label needs a size. */
    @Test
    fun anEmptyCellWithNoTargetStillHasALabelTalkBackCanReach() {
        show(emptyList(), weight = "", reps = "", plan = null)
        reads("Set 1 reps, empty").assertWidthIsAtLeast(1.dp)
    }

    /** The rule itself: nothing on a row is a figure on its own. */
    @Test
    fun noValueOnTheTableIsABareFigure() {
        show(listOf(LoggedSet(50.0, 10, 0, 2)), lastTime = listOf(LoggedSet(50.0, 10, 0, 2)))
        val bare = compose.onAllNodes(hasBareFigure() and hasAnyAncestor(hasTestTag(SetTableTag)), useUnmergedTree = true).fetchSemanticsNodes()
            .map { it.config[SemanticsProperties.Text].joinToString() }
        assertEquals(emptyList<String>(), bare)
    }

    private fun hasBareFigure() = SemanticsMatcher("a figure with no description") { node ->
        val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString().orEmpty()
        text.any(Char::isDigit) && node.config.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty() &&
            SemanticsProperties.HideFromAccessibility !in node.config
    }
}
