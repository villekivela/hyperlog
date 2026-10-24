package com.hyperlog.app.ui.session

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.hyperlog.app.domain.LadderSpec.Companion.DefaultPlates
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.GripDemand
import com.hyperlog.app.library.MovementPattern
import com.hyperlog.app.program.RepRange
import com.hyperlog.app.program.SetPrescription
import com.hyperlog.app.progression.Record
import com.hyperlog.app.session.ActiveSession
import com.hyperlog.app.session.ExerciseSummary
import com.hyperlog.app.session.PastSession
import com.hyperlog.app.session.SessionExercise
import com.hyperlog.app.session.SessionSummary
import com.hyperlog.app.ui.theme.HyperlogTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A record set carries its mark wherever it shows, and Day done and Session done list the
 * session's records (SPEC, Records).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
class RecordMarkTest {
    @get:Rule
    val compose = createComposeRule()

    private val bench = Exercise("barbell_bench_press", "Barbell Bench Press", Equipment.Barbell, MovementPattern.HorizontalPush, GripDemand.Low, listOf("Chest"))
    private val top = SetPrescription(RepRange(4, 6), 1, 180)
    private val sets = listOf(LoggedSet(92.5, 5, 0, 1), LoggedSet(90.0, 7, 0, 1), LoggedSet(90.0, 5, 0, 1))
    private val records = listOf(Record.Estimate, Record.Reps, null)

    /** Set text as the screen writes it, with no break inside a set. */
    private val String.nb get() = replace(' ', '\u00A0')

    /** Words in text or in a symbol's description. */
    private fun says(words: String) = hasText(words, substring = true) or hasContentDescription(words, substring = true)

    private fun count(matcher: SemanticsMatcher, n: Int) = compose.onAllNodes(matcher, useUnmergedTree = true).assertCountEquals(n)

    private fun session(logged: Int) = ActiveSession(
        id = 1, startedAt = 0,
        exercises = listOf(
            SessionExercise(
                id = 1, exercise = bench, sets = sets.take(logged), lastTime = emptyList(), ladder = DefaultPlates.ladder(),
                ladderKnown = true, prescription = listOf(top, top, top), plates = DefaultPlates, records = records.take(logged),
            ),
        ),
        currentId = 1, entry = null, padVisible = true, restEndsAt = null, restTotalMs = 0,
    )

    @Test
    fun aRecordSetShowsItsMarkOnTheLoggingScreen() {
        compose.setContent { HyperlogTheme { SessionScreen(session(logged = 2).copy(entry = com.hyperlog.app.domain.SetEntry.prefill(sets[1])), listOf(bench), Noop, onLeave = {}) } }
        count(says("Estimate record"), 1)
        count(says("Rep record"), 1)
    }

    @Test
    fun dayDoneListsTheSessionsRecords() {
        compose.setContent { HyperlogTheme { SessionScreen(session(logged = 3), listOf(bench), Noop, onLeave = {}) } }
        val inDayDone = hasAnyAncestor(hasTestTag(DayDoneTag))
        count(inDayDone and hasText("Barbell Bench Press · " + "92.5 × 5".nb, substring = true) and says("Estimate record"), 1)
        count(inDayDone and hasText("Barbell Bench Press · " + "90 × 7".nb, substring = true) and says("Rep record"), 1)
        // The set that beat nothing isn't listed.
        count(inDayDone and hasText("90 × 5".nb, substring = true), 0)
    }

    private val summary = SessionSummary(1, 0, 3_600_000, listOf(ExerciseSummary(bench.name, sets, records)))

    @Test
    fun sessionDoneMarksTheSetsAndListsTheRecords() {
        compose.setContent { HyperlogTheme { SessionDoneScreen(summary, onResume = {}, onClose = {}) } }
        val listed = hasAnyAncestor(hasTestTag(RecordListTag))
        count(listed and hasText("Barbell Bench Press · " + "92.5 × 5".nb, substring = true) and says("Estimate record"), 1)
        count(listed and hasText("Barbell Bench Press · " + "90 × 7".nb, substring = true) and says("Rep record"), 1)
        // And the sets themselves, under the exercise.
        count(!listed and says("Estimate record"), 1)
        count(!listed and says("Rep record"), 1)
    }

    @Test
    fun historyMarksARecordSet() {
        val exercise = session(logged = 3).exercises.single()
        compose.setContent { HyperlogTheme { PastSessionScreen(PastSession(1, 0, 3_600_000, "Day A", null, listOf(exercise)), onOpen = {}, onBack = {}) } }
        count(says("Estimate record"), 1)
        count(says("Rep record"), 1)
    }
}
