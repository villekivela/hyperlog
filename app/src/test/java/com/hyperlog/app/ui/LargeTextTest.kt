package com.hyperlog.app.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.hyperlog.app.program.Backoff
import com.hyperlog.app.program.PlannedSlot
import com.hyperlog.app.program.RepRange
import com.hyperlog.app.program.SetPrescription
import com.hyperlog.app.session.ActiveSession
import com.hyperlog.app.session.PastSession
import com.hyperlog.app.session.ExerciseSummary
import com.hyperlog.app.session.PastSessionItem
import com.hyperlog.app.session.SessionExercise
import com.hyperlog.app.navItem
import com.hyperlog.app.ui.coach.TileCard
import com.hyperlog.app.ui.gym.GymScreen
import com.hyperlog.app.ui.history.HistoryScreen
import com.hyperlog.app.ui.program.BlockChoice
import com.hyperlog.app.ui.program.SetSummary
import com.hyperlog.app.ui.session.PastSessionScreen
import com.hyperlog.app.ui.session.Recording
import com.hyperlog.app.ui.session.SessionScreen
import com.hyperlog.app.ui.session.SetTableTag
import com.hyperlog.app.ui.session.RirChipLoggedTag
import com.hyperlog.app.ui.session.RirChipTargetTag
import com.hyperlog.app.ui.theme.CardMargin
import com.hyperlog.app.ui.theme.ConnectedChoice
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.theme.HyperlogTheme
import com.hyperlog.app.ui.theme.NextAction
import com.hyperlog.app.ui.theme.NextCard
import com.hyperlog.app.ui.theme.SegmentedRow
import com.hyperlog.app.ui.today.SlotRows
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

/** Last as the table writes them, with no-break spaces inside a set. */
private const val ShortLast = "50\u00A0×\u00A010"
private const val LongLast = "50\u00A0×\u00A010 +\u00A05"

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
    fun theNextCardKeepsItsDayWholeAndClearOfItsButton() {
        val day = "Week 8 · Day A — Heavy bench, squat, pulldown"
        atEveryScale({ NextCard(NextAction("Go to Today", day) {}) }) { scale ->
            val title = compose.onNodeWithText(day, useUnmergedTree = true)
            val label = compose.onNodeWithText("Next", useUnmergedTree = true).getBoundsInRoot()
            val button = compose.onNodeWithText("Go to Today", useUnmergedTree = true)
            assertFalse("The day is cut short at $scale", title.cutShort())
            assertFalse("The button is cut short at $scale", button.cutShort())
            assertTrue("Next sits beside the day at $scale", label.bottom <= title.getBoundsInRoot().top)
            assertTrue("The day runs into the button at $scale", title.getBoundsInRoot().right + 8.dp <= button.getBoundsInRoot().left)
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
    private fun Session(lastTime: List<LoggedSet> = listOf(LoggedSet(50.0, 10, 5, 1))) {
        val exercise = SessionExercise(
            id = 1, exercise = bench, sets = emptyList(), lastTime = lastTime, ladder = DefaultPlates.ladder(), ladderKnown = true,
            prescription = List(lastTime.size) { SetPrescription(RepRange(4, 6), 1, 180) }, plates = DefaultPlates,
        )
        val entry = SetEntry("100", "6", "", 1, Field.Reps, partialMode = false, fresh = true)
        val session = ActiveSession(id = 1, startedAt = 0, exercises = listOf(exercise), currentId = 1, entry = entry, padVisible = true, restEndsAt = 1, restTotalMs = 180_000)
        SessionScreen(session, listOf(bench), Recording(mutableListOf()), onLeave = {})
    }

    @Test
    fun lastTimesSetStaysWhole() {
        atEveryScale({ Session() }) { scale ->
            compose.onNode(hasScrollToNodeAction() and hasAnyAncestor(hasTestTag(SetTableTag))).performScrollToNode(hasText(LongLast))
            assertFalse("Last time is cut short at $scale", compose.onNodeWithText(LongLast, useUnmergedTree = true).cutShort())
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

    /** The sweep of #268 found these on a real build, each from a size the issue hadn't listed. */
    @Test
    fun aSegmentedRowKeepsAllItsWords() {
        val title = "Starting weights from the old app"
        val supporting = "2 gyms · dumbbells and plates · tap to import again"
        atEveryScale({ SegmentedRow(title, supporting, Symbol.History) }) { scale ->
            assertFalse("The title is cut short at $scale", compose.onNodeWithText(title, useUnmergedTree = true).cutShort())
            assertFalse("The supporting line is cut short at $scale", compose.onNodeWithText(supporting, useUnmergedTree = true).cutShort())
        }
    }

    @Test
    fun aSessionsTitleInHistoryWraps() {
        val title = "Day B — Hinge, close-grip bench, leg curl"
        val session = PastSessionItem(1, 0, 3_600_000, title, "Kalliokoski", emptyList())
        atEveryScale({
            HistoryScreen(
                listOf(session), emptyList(), emptyList(), emptyList(), tab = 0, onTab = {}, onOpenSession = {}, onOpenExercise = {},
                onBodyweight = {}, onLogBodyweight = {}, onBack = {},
            )
        }) { scale -> assertFalse("The title is cut short at $scale", compose.onNodeWithText(title, useUnmergedTree = true).cutShort()) }
    }

    /** Round 1's score: a date centred on a wrapped title, and 5.5 sessions to a screen, down from 8. */
    @Test
    fun aSessionsDateSitsOnItsTitlesFirstLine() {
        val title = "Day B — Hinge, close-grip bench, leg curl"
        val session = PastSessionItem(1, 0, 3_600_000, title, "Kalliokoski", emptyList())
        show {
            HistoryScreen(
                listOf(session), emptyList(), emptyList(), emptyList(), tab = 0, onTab = {}, onOpenSession = {}, onOpenExercise = {},
                onBodyweight = {}, onLogBodyweight = {}, onBack = {},
            )
        }
        val name = compose.onNodeWithText(title, useUnmergedTree = true)
        val date = compose.onNodeWithText("Thu 1 Jan", useUnmergedTree = true)
        val nameBase = name.getBoundsInRoot().top + with(compose.density) { name.layout().firstBaseline.toDp() }
        val dateBase = date.getBoundsInRoot().top + with(compose.density) { date.layout().firstBaseline.toDp() }
        assertTrue("The date's baseline is ${dateBase - nameBase} off the title's", (dateBase - nameBase).value in -1f..1f)
    }

    @Test
    fun sevenSessionsFitAScreen() {
        val names = listOf("Barbell RDL", "Chest-Supported Machine Row", "Leg Extension", "Bayesian Cable Curl", "Hack Squat")
        val sessions = (1L..10L).map {
            val exercises = names.map { n -> ExerciseSummary(n, emptyList()) }
            PastSessionItem(it, it * 86_400_000, it * 86_400_000 + 3_600_000, "Day B — Hinge, close-grip bench, row $it", "Kalliokoski", exercises)
        }
        show {
            HistoryScreen(
                sessions, emptyList(), emptyList(), emptyList(), tab = 0, onTab = {}, onOpenSession = {}, onOpenExercise = {},
                onBodyweight = {}, onLogBodyweight = {}, onBack = {},
            )
        }
        val seventh = compose.onNodeWithText("Day B — Hinge, close-grip bench, row 7", useUnmergedTree = true).getBoundsInRoot()
        // The navigation bar takes the last 80 dp on the phone.
        // Its preview line and padding come under the title.
        assertTrue("The seventh session ends at ${seventh.bottom + 28.dp}", seventh.bottom + 28.dp <= 834.dp)
    }

    @Test
    fun aSessionsPreviewEndsOnAWholeNameAndACount() {
        val names = listOf("Barbell RDL", "Chest-Supported Machine Row", "Leg Extension", "Bayesian Cable Curl", "Hack Squat")
        val session = PastSessionItem(1, 0, 3_600_000, "Day B", "Kalliokoski", names.map { ExerciseSummary(it, emptyList()) })
        atEveryScale({
            HistoryScreen(
                listOf(session), emptyList(), emptyList(), emptyList(), tab = 0, onTab = {}, onOpenSession = {}, onOpenExercise = {},
                onBodyweight = {}, onLogBodyweight = {}, onBack = {},
            )
        }) { scale ->
            val preview = compose.onNode(hasText("more", substring = true), useUnmergedTree = true)
            assertFalse("The preview is cut at $scale", preview.cutShort())
            assertEquals("The preview wraps at $scale", 1, preview.layout().lineCount)
        }
    }

    /** Stacked, four phases pushed the weeks off screen; on one row of chips they stay in reach (#273). */
    @Test
    fun thePhasesStayOnOneRow() {
        val names = listOf("Ramp-in", "Build", "Intensify", "Deload + test")
        atEveryScale({ BlockChoice(names, 0) {} }) { scale ->
            val tops = names.take(2).map { compose.onNodeWithText(it, useUnmergedTree = true).getBoundsInRoot().top }
            assertEquals("Phases stack at $scale", tops[0].value, tops[1].value, 1f)
        }
    }

    @Test
    fun aCoachTilesLabelStaysWhole() {
        val label = "Different gym or none"
        atEveryScale({ TileCard(label, Symbol.History, RoundedCornerShape(8.dp), Modifier.width(180.dp)) {} }) { scale ->
            assertFalse("The tile is cut short at $scale", compose.onNodeWithText(label, useUnmergedTree = true).cutShort())
        }
    }

    @Test
    fun aLongExerciseNameOnTodayWraps() {
        val name = "Overhead Cable Triceps Extension"
        val slot = PlannedSlot("s", "5b", 0, "triceps", emptyList(), null, listOf(SetPrescription(RepRange(10, 12), 1, 90)))
        atEveryScale({ SlotRows(listOf(slot), mapOf("triceps" to name)) }) { scale ->
            assertFalse("The name is cut short at $scale", compose.onNodeWithText(name, useUnmergedTree = true).cutShort())
        }
    }

    @Test
    fun aPlannedSetNeverBreaksAcrossLines() {
        val sets = listOf(SetPrescription(RepRange(3, 5), 2, 180), SetPrescription(RepRange(6, 8), 2, 180, backoff = Backoff(0, 10)))
        atEveryScale({ SetSummary(sets, Modifier.width(240.dp)) }) { scale ->
            val layout = compose.onNode(hasText("×", substring = true), useUnmergedTree = true).layout()
            val text = layout.layoutInput.text.text
            (0 until layout.lineCount).forEach {
                val line = text.substring(layout.getLineStart(it), layout.getLineEnd(it)).trimEnd()
                assertFalse("`$line` ends mid-set at $scale", line.endsWith("×"))
            }
        }
    }

    /** Round 1's score: fitting each alone left Today larger than Program. */
    @Test
    fun theNavigationLabelsShareOneSize() {
        val labels = listOf("Today", "Program", "Coach", "History", "Settings")
        // Ink, not the style: autosized text keeps its style's size whatever it draws at. One size
        // means every label grows by the same factor from the first scale.
        var first = emptyMap<String, Float>()
        atEveryScale({ AppNavigationBar(Screen.Today) {} }) { scale ->
            val ink = labels.associateWith { compose.onNode(navItem(it), useUnmergedTree = true).layout().let { l -> l.getLineRight(0) - l.getLineLeft(0) } }
            if (first.isEmpty()) first = ink
            val growth = labels.map { ink.getValue(it) / first.getValue(it) }
            // Hinted glyph widths at the test's density wobble a few percent; fitted alone they differ by 50.
            assertTrue("Labels grew by $growth at $scale", growth.max() - growth.min() < 0.1f)
        }
    }

    /** One size for the whole column, the smallest any row needs, and never under the floor. */
    @Test
    fun theLastColumnSharesOneSizeAndHoldsItsFloor() {
        val sets = listOf(LoggedSet(50.0, 10, 0, 2), LoggedSet(50.0, 10, 5, 2))
        val table = hasScrollToNodeAction() and hasAnyAncestor(hasTestTag(SetTableTag))
        atEveryScale({ Session(sets) }) { scale ->
            // The first figure's own box: the same glyph, so the same width at the same size.
            val widths = listOf(ShortLast, LongLast).map {
                compose.onNode(table).performScrollToNode(hasText(it))
                compose.onNodeWithText(it, useUnmergedTree = true).layout().getBoundingBox(0).width
            }
            assertEquals("Last sizes differ at $scale: $widths", widths[0], widths[1], 0.01f)
            val long = compose.onNodeWithText(LongLast, useUnmergedTree = true)
            assertTrue("Last is ${long.layout().layoutInput.style.fontSize} at $scale", long.layout().layoutInput.style.fontSize.value >= 12f)
            assertFalse("Last is cut at $scale", long.cutShort())
        }
    }

    @Test
    fun theSetTablesHeadsHoldTheFloor() {
        atEveryScale({ Session() }) { scale ->
            listOf("Last", "kg", "Reps", "RIR").forEach {
                val head = compose.onAllNodes(hasText(it) and hasAnyAncestor(hasTestTag(SetTableTag)), useUnmergedTree = true)[0]
                val size = head.layout().layoutInput.style.fontSize.value
                assertTrue("$it is $size sp at $scale", size >= 11f)
            }
        }
    }

    @Test
    fun theNavigationLabelsKeepClearOfEachOther() {
        val labels = listOf("Today", "Program", "Coach", "History", "Settings")
        atEveryScale({ AppNavigationBar(Screen.Today) {} }) { scale ->
            labels.zipWithNext { a, b ->
                val gap = inkLeft(b) - inkRight(a)
                assertTrue("$a and $b are $gap apart at $scale", gap >= 4.dp)
            }
        }
    }

    private fun inkLeft(label: String) = compose.onNode(navItem(label), useUnmergedTree = true).let { it.getBoundsInRoot().left + with(compose.density) { it.layout().getLineLeft(0).toDp() } }

    private fun inkRight(label: String) = compose.onNode(navItem(label), useUnmergedTree = true).let { it.getBoundsInRoot().left + with(compose.density) { it.layout().getLineRight(0).toDp() } }

    @Test
    fun aGymsPlatesRowShowsEveryPlate() {
        show { GymScreen(Gym(1, "Kalliokoski", emptyList()), onRename = {}, onDelete = {}, onOpenItem = {}, onAddItem = {}, onBack = {}) }
        assertFalse("The plates are cut short", compose.onNodeWithText("8×20 2×15 2×10 2×5 2×2.5 2×1.25", useUnmergedTree = true).cutShort())
    }
}
