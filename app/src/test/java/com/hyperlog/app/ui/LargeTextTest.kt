package com.hyperlog.app.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.hyperlog.app.AppNavigationBar
import com.hyperlog.app.Screen
import com.hyperlog.app.coach.NumberRange
import com.hyperlog.app.domain.Extension
import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.domain.LadderSpec.Companion.DefaultPlates
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.domain.SetEntry
import com.hyperlog.app.domain.WeightCheck
import com.hyperlog.app.domain.WeightLadder
import com.hyperlog.app.domain.WeightUnit
import com.hyperlog.app.domain.formatKg
import com.hyperlog.app.gym.EquipmentItem
import com.hyperlog.app.gym.Gym
import com.hyperlog.app.history.ExerciseTrend
import com.hyperlog.app.history.TrendLine
import com.hyperlog.app.history.WeekSets
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.GripDemand
import com.hyperlog.app.library.MovementPattern
import com.hyperlog.app.navItem
import com.hyperlog.app.program.Backoff
import com.hyperlog.app.program.PlannedSlot
import com.hyperlog.app.program.HoldPosition
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.RepRange
import com.hyperlog.app.program.SetPrescription
import com.hyperlog.app.program.Technique
import com.hyperlog.app.progression.Suggestion
import com.hyperlog.app.session.ActiveSession
import com.hyperlog.app.session.Correction
import com.hyperlog.app.session.ExerciseSummary
import com.hyperlog.app.session.PastSession
import com.hyperlog.app.session.FollowUp
import com.hyperlog.app.session.PastSessionItem
import com.hyperlog.app.session.PickedSet
import com.hyperlog.app.session.SessionExercise
import com.hyperlog.app.session.WarmupEdit
import com.hyperlog.app.ui.coach.TileCard
import com.hyperlog.app.ui.gym.GymScreen
import com.hyperlog.app.ui.history.HistoryScreen
import com.hyperlog.app.ui.program.BlockChoice
import com.hyperlog.app.ui.program.SetSummary
import com.hyperlog.app.ui.session.PadTag
import com.hyperlog.app.ui.session.PastSessionScreen
import com.hyperlog.app.ui.session.Recording
import com.hyperlog.app.ui.session.RirChipLoggedTag
import com.hyperlog.app.ui.session.RirChipTargetTag
import com.hyperlog.app.ui.session.SessionScreen
import com.hyperlog.app.ui.session.SetTableTag
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.theme.CardMargin
import com.hyperlog.app.ui.theme.ConnectedChoice
import com.hyperlog.app.ui.theme.HyperlogTheme
import com.hyperlog.app.ui.theme.NextAction
import com.hyperlog.app.ui.theme.NextCard
import com.hyperlog.app.ui.theme.SegmentedRow
import com.hyperlog.app.ui.theme.TextEdge
import com.hyperlog.app.ui.today.SlotRows
import com.hyperlog.app.ui.today.WeekRow
import java.time.LocalDate
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
private const val ShortLast = "50 × 10"
private const val LongLast = "50 × 10 + 5"

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
    /** A line ends inside a number: `50 × 1` over `0`. */
    private fun SemanticsNodeInteraction.splitsAFigure(): Boolean {
        val layout = layout()
        val text = layout.layoutInput.text.text
        fun figure(c: Char) = c.isDigit() || c == '.'
        return (1 until layout.lineCount).any { figure(text[layout.getLineStart(it) - 1]) && figure(text[layout.getLineStart(it)]) }
    }

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
        atEveryScale({ NextCard(NextAction(day, "Start")) }) { scale ->
            val title = compose.onNodeWithText(day, useUnmergedTree = true)
            val label = compose.onNodeWithText("Next", useUnmergedTree = true).getBoundsInRoot()
            val button = compose.onNodeWithText("Start", useUnmergedTree = true)
            assertFalse("The day is cut short at $scale", title.cutShort())
            assertFalse("The button is cut short at $scale", button.cutShort())
            assertTrue("Next sits beside the day at $scale", label.bottom <= title.getBoundsInRoot().top)
            // Beside the day while it can be, under it at large text so the day isn't squeezed.
            val beside = title.getBoundsInRoot().right + 8.dp <= button.getBoundsInRoot().left
            val under = title.getBoundsInRoot().bottom <= button.getBoundsInRoot().top
            assertTrue("The day runs into the button at $scale", if (scale >= 1.5f) under else beside)
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
    private fun Session(lastTime: List<LoggedSet> = listOf(LoggedSet(50.0, 10, 5, 1)), sets: List<LoggedSet> = emptyList()) {
        val exercise = SessionExercise(
            id = 1, exercise = bench, sets = sets, lastTime = lastTime, ladder = DefaultPlates.ladder(), ladderKnown = true,
            prescription = List(lastTime.size) { SetPrescription(RepRange(4, 6), 1, 180) }, plates = DefaultPlates,
        )
        val entry = SetEntry("100", "6", "", 1, Field.Reps, partialMode = false, fresh = true)
        val session = ActiveSession(id = 1, startedAt = 0, exercises = listOf(exercise), currentId = 1, entry = entry, padVisible = true, restEndsAt = 1, restTotalMs = 180_000)
        SessionScreen(session, listOf(bench), Recording(mutableListOf()), onLeave = {})
    }

    @Test
    fun lastTimesSetStaysWhole() {
        atEveryScale({ Session() }) { scale ->
            compose.onNode(hasScrollToNodeAction() and hasAnyAncestor(hasTestTag(SetTableTag))).performScrollToNode(hasLast(LongLast))
            assertFalse("Last time is cut short at $scale", compose.onNode(hasLast(LongLast), useUnmergedTree = true).cutShort())
            assertFalse("Last time splits a figure at $scale", compose.onNode(hasLast(LongLast), useUnmergedTree = true).splitsAFigure())
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

    /** Each line of [this] ends on a whole word. */
    private fun SemanticsNodeInteraction.assertWholeWords(what: String) {
        val layout = layout()
        val text = layout.layoutInput.text.text
        (0 until layout.lineCount).forEach {
            val end = layout.getLineEnd(it)
            assertFalse("$what splits a word: `${text.substring(layout.getLineStart(it), end)}`", end in 1 until text.length && text[end - 1].isLetter() && text[end].isLetter())
        }
    }

    /** Round 3: `Gastrocnemi/us`, `Target 10–16` over `sets` and `Sets last` over `week` at 200 %. */
    @Test
    @Config(qualifiers = "w427dp-h950dp-xxhdpi")
    fun progressHoldsItsWordsOnAPhonesScreen() {
        val monday = LocalDate.of(2026, 11, 2)
        val weeks = listOf(WeekSets(monday, emptyMap()), WeekSets(monday.minusWeeks(1), mapOf("Gastrocnemius" to 1.5, "Triceps Lateral Head" to 3.0, "Calves" to 13.0), 4))
        atEveryScale({
            HistoryScreen(
                emptyList(), weeks, emptyList(), emptyList(), tab = 1, onTab = {}, onOpenSession = {}, onOpenExercise = {},
                onBodyweight = {}, onLogBodyweight = {}, onBack = {}, today = LocalDate.of(2026, 11, 5), standing = NumberRange(10, 16),
            )
        }) { scale ->
            compose.onNodeWithText("Gastrocnemius", useUnmergedTree = true).assertWholeWords("Gastrocnemius at $scale")
            compose.onNodeWithText("Triceps Lateral Head", useUnmergedTree = true).assertWholeWords("Triceps at $scale")
            assertEquals("The target wraps at $scale", 1, compose.onNodeWithText("Target 10–16 sets", useUnmergedTree = true).layout().lineCount)
            assertEquals("The head wraps at $scale", 1, compose.onNodeWithText("Sets last week", useUnmergedTree = true).layout().lineCount)
            // Ticks that can't both fit leave the top one, not `1016`.
            val low = compose.onAllNodes(hasText("10"), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot
            val high = compose.onNode(hasText("16"), useUnmergedTree = true).getBoundsInRoot()
            if (low != null) assertTrue("10 and 16 touch at $scale", with(compose.density) { low.right.toDp() } + 4.dp <= high.left)
        }
    }

    /** Round 4: the day's letter clipped in its fixed 20 dp marker at 200 %. */
    @Test
    @Config(qualifiers = "w427dp-h950dp-xxhdpi")
    fun theCalendarsLettersStayWhole() {
        val monday = LocalDate.of(2026, 11, 2)
        val session = PastSessionItem(1, 0, 3_600_000, "Day A — Bench", null, emptyList(), programId = 1, position = Position(1, 0))
        atEveryScale({ WeekRow(monday, monday.plusDays(4), monday.plusDays(4), mapOf(monday to listOf(session)), emptyMap(), {}) }) { scale ->
            assertFalse("A is cut short at $scale", compose.onNodeWithText("A", useUnmergedTree = true).cutShort())
        }
    }

    /** The logging screen with [kg] logged as set 1 and in the entry box of set 2. */
    @Composable
    private fun WeightSession(kg: Double, ladder: WeightLadder) {
        val shown = formatKg(kg)
        val exercise = SessionExercise(
            id = 1, exercise = bench, sets = listOf(LoggedSet(kg, 5, 0, 1)), lastTime = listOf(LoggedSet(kg, 5, 0, 1)), ladder = ladder, ladderKnown = true,
            prescription = List(2) { SetPrescription(RepRange(4, 6), 1, 180) }, plates = DefaultPlates,
        )
        val entry = SetEntry(shown, "5", "", 1, Field.Reps, partialMode = false, fresh = false)
        val session = ActiveSession(id = 1, startedAt = 0, exercises = listOf(exercise), currentId = 1, entry = entry, padVisible = true, restEndsAt = null, restTotalMs = 0)
        SessionScreen(session, listOf(bench), Recording(mutableListOf()), onLeave = {})
    }

    /** [kg], in both the logged row and the entry box, whole at every size. */
    private fun assertWeightWhole(kg: Double, ladder: WeightLadder, lastCanGo: Boolean = false) {
        val shown = formatKg(kg)
        atEveryScale({ WeightSession(kg, ladder) }) { scale ->
            val nodes = compose.onAllNodes(hasText(shown) and hasAnyAncestor(hasTestTag(SetTableTag)), useUnmergedTree = true)
            val found = nodes.fetchSemanticsNodes().size
            assertEquals("$shown logged and in the entry box at $scale", 2, found)
            (0 until found).forEach {
                val layout = nodes[it].layout()
                val read = layout.layoutInput.text.text.substring(0, layout.getLineEnd(0, visibleEnd = true))
                assertEquals("Row $it reads short at $scale", shown, read)
                assertTrue("$shown runs past its box at $scale", layout.getLineRight(0) <= layout.size.width + 0.5f)
            }
            // The column gives way to the weight, and Last, beside it, still reads whole or not at all.
            val lasts = compose.onAllNodes(hasText("×", substring = true) and hasAnyAncestor(hasTestTag(SetTableTag)), useUnmergedTree = true)
            if (lasts.fetchSemanticsNodes().isEmpty()) {
                assertTrue("Last is gone at $scale", lastCanGo)
                return@atEveryScale
            }
            assertFalse("Last splits a figure at $scale", lasts[0].splitsAFigure())
            val last = lasts[0].layout()
            val written = last.layoutInput.text.text
            assertTrue("Last splits $shown at $scale", written.substring(0, last.getLineEnd(0, visibleEnd = true)).startsWith(shown))
            assertEquals("Last reads short at $scale", written.length, last.getLineEnd(last.lineCount - 1, visibleEnd = true))
            assertFalse("Last overflows at $scale", last.didOverflowHeight)
            assertTrue("Last runs past its box at $scale", (0 until last.lineCount).all { last.getLineRight(it) <= last.size.width + 0.5f })
        }
    }

    /** 12 sp, but for the half step at 200 % that keeps `50×10+5` to the row's two lines. */
    private fun lastFloorAt(scale: Float) = if (scale < 2f) 12f else 11.5f

    /** Last showing [set], however its spaces are written. */
    private fun hasLast(set: String) = SemanticsMatcher("Last reads $set") { node ->
        fun bare(text: String) = text.filterNot { it.isWhitespace() || it == '\u200B' }
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { bare(it.text) == bare(set) }
    }

    private val stepLadder = LadderSpec.Steps(0.0, 1.25, WeightUnit.Kg, 200).ladder()

    /** Round 5: `87.5` read `87.` at 200 %, in the logged row and the entry box. A weight that reads wrong is worse than a wrap. */
    @Test
    @Config(qualifiers = "w427dp-h950dp-xxhdpi")
    fun aDecimalWeightStaysWholeOnPlates() = assertWeightWhole(87.5, DefaultPlates.ladder())

    @Test
    @Config(qualifiers = "w427dp-h950dp-xxhdpi")
    fun aThreeDigitDecimalStaysWholeOnPlates() {
        assertWeightWhole(102.5, DefaultPlates.ladder())
    }

    @Test
    @Config(qualifiers = "w427dp-h950dp-xxhdpi")
    fun theLongestPlateWeightStaysWhole() = assertWeightWhole(122.5, DefaultPlates.ladder())

    @Test
    @Config(qualifiers = "w427dp-h950dp-xxhdpi")
    fun aFineStepWeightStaysWhole() = assertWeightWhole(101.25, stepLadder)

    @Test
    @Config(qualifiers = "w427dp-h950dp-xxhdpi")
    fun aSmallFineStepWeightStaysWhole() = assertWeightWhole(11.25, stepLadder)

    @Test
    @Config(qualifiers = "w410dp-h914dp-xxhdpi")
    fun aFineStepWeightStaysWholeOnANarrowerPhone() = assertWeightWhole(101.25, stepLadder, lastCanGo = true)

    @Test
    @Config(qualifiers = "w360dp-h800dp-xxhdpi")
    fun theLongestPlateWeightStaysWholeOnTheNarrowestPhone() = assertWeightWhole(122.5, DefaultPlates.ladder(), lastCanGo = true)

    /** On the build at 200 %, a 102.5 beside it left `50×10+5` three lines, and the `+5` went. */
    @Test
    @Config(qualifiers = "w427dp-h950dp-xxhdpi")
    fun aSetKeepsItsPartialsBesideAWideWeight() {
        val sets = listOf(LoggedSet(50.0, 10, 5, 1), LoggedSet(50.0, 10, 5, 1))
        atEveryScale({ Session(sets, listOf(LoggedSet(102.5, 5, 0, 1))) }) { scale ->
            val table = hasScrollToNodeAction() and hasAnyAncestor(hasTestTag(SetTableTag))
            compose.onNode(table).performScrollToNode(hasLast(LongLast))
            val last = compose.onAllNodes(hasLast(LongLast), useUnmergedTree = true)[0]
            val layout = last.layout()
            assertEquals("Last reads short at $scale", layout.layoutInput.text.text.length, layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
            assertFalse("Last is cut at $scale", last.cutShort())
            assertTrue("Last is ${layout.layoutInput.style.fontSize} at $scale", layout.layoutInput.style.fontSize.value >= lastFloorAt(scale))
        }
    }

    /** Its last line's ink ends under the box. A tall line's empty foot can pass the box; a digit's ink stops at the baseline. */
    private fun TextLayoutResult.inkBelowItsBox(): Boolean {
        val last = lineCount - 1
        val text = layoutInput.text.text
        val descends = text.substring(getLineStart(last)).any { it in "gjpqy,/()" }
        val em = with(layoutInput.density) { layoutInput.style.fontSize.toPx() }
        return getLineBaseline(last) + (if (descends) em * 0.2f else 0f) > size.height + 0.5f
    }

    /** Every text on screen that doesn't read whole, with what's wrong with it. */
    private fun faults(): List<String> {
        val texts = SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult)
        val all = compose.onAllNodes(texts, useUnmergedTree = true)
        val cut = all.fetchSemanticsNodes().indices.mapNotNull { i ->
            val layout = all[i].layout()
            val text = layout.layoutInput.text.text
            val lines = 0 until layout.lineCount
            fun inWord(c: Char) = c.isLetterOrDigit() || c == '.'
            when {
                text.isBlank() -> null
                lines.any { layout.isLineEllipsized(it) } -> "`$text` ellipsized"
                layout.getLineEnd(layout.lineCount - 1, visibleEnd = true) < text.trimEnd().length -> "`$text` loses its end"
                layout.inkBelowItsBox() -> "`$text` is cut at the foot"
                lines.any { layout.getLineRight(it) > layout.size.width + 0.5f } -> "`$text` runs past its box"
                (1 until layout.lineCount).any { inWord(text[layout.getLineStart(it) - 1]) && inWord(text[layout.getLineStart(it)]) } -> "`$text` splits inside a word or figure"
                else -> null
            }
        }
        // What the pad's own rows hold back: a text laid out whole in a row too short for it.
        val inPad = compose.onAllNodes(texts and hasAnyAncestor(hasTestTag(PadTag)), useUnmergedTree = true)
        val heldBack = inPad.fetchSemanticsNodes().indices.mapNotNull { i ->
            val layout = inPad[i].layout()
            val shown = inPad[i].fetchSemanticsNode().boundsInRoot
            "`${layout.layoutInput.text.text}` is clipped by its row".takeIf { shown.height < layout.size.height - 1f || shown.width < layout.size.width - 1f }
        }
        // Two values whole but touching read as one: `102.5` beside `12 + 6` was `102.512 + 6` on the build.
        val inTable = compose.onAllNodes(texts and hasAnyAncestor(hasTestTag(SetTableTag)), useUnmergedTree = true)
        val ink = inTable.fetchSemanticsNodes().indices.mapNotNull { i ->
            val layout = inTable[i].layout()
            val box = inTable[i].fetchSemanticsNode().boundsInRoot
            val lines = 0 until layout.lineCount
            val text = layout.layoutInput.text.text
            if (text.isBlank() || box.height <= 0f) null else Triple(text, box, lines.minOf { layout.getLineLeft(it) } + box.left to lines.maxOf { layout.getLineRight(it) } + box.left)
        }.sortedBy { it.third.first }
        val gap = with(compose.density) { 4.dp.toPx() }
        val touching = ink.flatMapIndexed { i, (text, box, edges) ->
            ink.drop(i + 1).filter { (_, other, _) -> box.top < other.bottom && other.top < box.bottom }.take(1)
                .filter { (_, _, next) -> next.first - edges.second < gap }.map { (next, _, _) -> "`$text` runs into `$next`" }
        }
        val pad = compose.onAllNodes(hasTestTag(PadTag), useUnmergedTree = true)
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val offScreen = listOfNotNull("The pad runs off the screen".takeIf { pad.fetchSemanticsNodes().any { it.boundsInRoot.bottom > root.bottom + 1f || it.boundsInRoot.top < root.top } })
        return (cut + heldBack + touching + offScreen).distinct()
    }

    private val sweepExercise = SessionExercise(
        id = 1, exercise = bench, sets = emptyList(),
        lastTime = listOf(LoggedSet(97.5, 5, 0, 1), LoggedSet(87.5, 8, 5, 1)), ladder = DefaultPlates.ladder(), ladderKnown = true,
        prescription = List(3) { SetPrescription(RepRange(4, 6), 1, 180) }, plates = DefaultPlates,
        suggestion = Suggestion(97.5, 5, Suggestion.Kind.Deload, "Deload week, last time's weight", "97.5 · deload week, 2 in reserve"),
    )

    private fun onWeight(kg: String) = SetEntry(kg, "", "", 1, Field.Weight, partialMode = false, fresh = true)
    private fun onReps(kg: String, partial: Boolean = false) = SetEntry(kg, "5", "", 1, Field.Reps, partialMode = partial, fresh = false)

    private fun sweepSession(exercise: SessionExercise = sweepExercise, entry: SetEntry? = onReps("102.5"), more: List<SessionExercise> = emptyList()) = ActiveSession(
        id = 1, startedAt = 0, exercises = listOf(exercise) + more, currentId = exercise.id, entry = entry, padVisible = true, restEndsAt = null, restTotalMs = 180_000, rampEntry = entry,
    )

    private fun SessionExercise.logged(vararg done: LoggedSet) = copy(sets = done.toList(), setIndices = done.indices.toList())

    /** A set with its technique, one follow-up logged and another to come. */
    private fun followed(technique: Technique, kind: Extension.Kind, kg: Double, reps: Int) = sweepExercise.logged(
        LoggedSet(102.5, 5, 0, 1, technique = technique, extensions = listOf(Extension(kind, kg, reps))),
    ).copy(
        lastTime = listOf(LoggedSet(102.5, 5, 0, 1, technique = technique, extensions = listOf(Extension(kind, kg, reps), Extension(kind, kg, reps)))),
        prescription = listOf(SetPrescription(RepRange(4, 6), 1, 180, technique = technique)) + List(2) { SetPrescription(RepRange(4, 6), 1, 180) },
    )

    /** Each state of the logging screen, its strips and its fixed-height rows. */
    private fun sessionStates(): Map<String, ActiveSession> {
        val drop = followed(Technique.Dropset(2, 25), Extension.Kind.Drop, 77.5, 8)
        val mini = followed(Technique.MyoReps(3), Extension.Kind.MiniSet, 102.5, 3)
        val hold = followed(Technique.IsoHold(20, HoldPosition.Stretch), Extension.Kind.Hold, 102.5, 20)
        val fine = sweepExercise.copy(ladder = LadderSpec.Steps(0.0, 1.25, WeightUnit.Kg, 200).ladder(), plates = null)
        val stack = sweepExercise.copy(
            exercise = bench.copy(equipment = Equipment.Machine), plates = null, ladder = LadderSpec.DefaultPinStack.ladder(),
            machine = EquipmentItem(gymId = 1, name = "Chest press", ladder = LadderSpec.DefaultPinStack.copy(builtIn = listOf(2.5, 2.5))), pinPlates = listOf(1.25, 2.5),
        )
        val slot = PlannedSlot("s", "5a", 0, "bench", emptyList(), null, emptyList())
        val partner = SessionExercise(2, bench.copy(id = "rdl", name = "Barbell Romanian Deadlift"), emptyList(), emptyList(), DefaultPlates.ladder(), true, slot = slot.copy(id = "t", label = "5b", exercise = "rdl"))
        val done = LoggedSet(102.5, 5, 0, 1)
        return buildMap {
            listOf("102.5", "197.5", "207.5", "212.5").forEach { put("weight strip at $it", sweepSession(entry = onWeight(it))) }
            put("weight strip on a 1.25 step", sweepSession(fine, onWeight("101.25")))
            listOf("2.2× last time", "2.2× last set", "1/3 of last time", "Not on this equipment").forEach {
                put("check strip: $it", sweepSession(entry = onWeight("212.5")).copy(weightCheck = WeightCheck(it, listOf(197.5, 207.5, 212.5))))
            }
            put("RIR and F/P row", sweepSession())
            put("partials", sweepSession(entry = onReps("102.5", partial = true)))
            put("split reps", sweepSession(entry = SetEntry("102.5", "12", "", 1, Field.Right, partialMode = false, fresh = false, right = "10")))
            put("plate line", sweepSession(entry = onWeight("212.5")))
            put("plate line, off the plates", sweepSession(entry = onWeight("212.6")))
            put("stack line", sweepSession(stack, onWeight("85")))
            put("stack line, off the stack", sweepSession(stack, onWeight("87.5")))
            put("warm-up on the reps", sweepSession().copy(warmupEdit = WarmupEdit(1, 1, onReps("60"))))
            put("warm-up on the weight", sweepSession().copy(warmupEdit = WarmupEdit(1, 1, onWeight("102.5"))))
            put("correcting a set", sweepSession(sweepExercise.logged(done)).copy(correction = Correction(1, 0, null, null, onReps("102.5"))))
            put("correcting a drop", sweepSession(drop).copy(correction = Correction(1, 0, 0, Extension.Kind.Drop, onReps("77.5"))))
            put("correcting a mini-set", sweepSession(mini).copy(correction = Correction(1, 0, 0, Extension.Kind.MiniSet, onReps("102.5"))))
            put("correcting a hold", sweepSession(hold).copy(correction = Correction(1, 0, 0, Extension.Kind.Hold, onReps("102.5"))))
            put("follow-up drop", sweepSession(drop, onReps("77.5")).copy(followUp = FollowUp.Drop(1, 2), openSetId = 1, openIndex = 0))
            put("follow-up mini-set", sweepSession(mini, onReps("102.5")).copy(followUp = FollowUp.MiniSet(1, 3), openSetId = 1, openIndex = 0))
            put("follow-up hold", sweepSession(hold, onReps("102.5")).copy(followUp = FollowUp.Hold(20, HoldPosition.Stretch), openSetId = 1, openIndex = 0))
            put("follow-up hold running", sweepSession(hold, onReps("102.5")).copy(followUp = FollowUp.Hold(20, HoldPosition.Stretch), openSetId = 1, openIndex = 0, holdStartedAt = 0))
            put("drop rows done", sweepSession(drop.logged(drop.sets.single(), done)))
            put("drop rows to come", sweepSession(drop.logged()))
            put("picked set", sweepSession().copy(pickedSet = PickedSet(1, 2, onReps("102.5"))))
            put("logged partials and split reps", sweepSession(sweepExercise.logged(LoggedSet(102.5, 12, 6, 1), LoggedSet(102.5, 12, 0, 1, rightReps = 10))))
            put("three-digit reps", sweepSession(entry = SetEntry("102.5", "100", "20", 1, Field.Reps, partialMode = true, fresh = false)))
            put("bench row", sweepSession(sweepExercise.copy(exercise = bench.copy(adjustableBench = true), lastBenchAngle = 30)))
            put("which machine", sweepSession(sweepExercise.copy(needsMachine = true), entry = null).copy(gym = Gym(1, "Hyperlog Fitness Centre", listOf(EquipmentItem(id = 1, gymId = 1, name = "Plate-loaded Chest Press Machine With Long Name", ladder = LadderSpec.DefaultPinStack)))))
            val cued = bench.copy(id = "2_grip_lat_pulldown", note = "Two grips: wide overhand (1.5× shoulder width) and underhand (shoulder width).")
            put("rep stage with its cue", sweepSession(sweepExercise.copy(exercise = cued).logged(done, done), onWeight("102.5")))
            put("pad hidden", sweepSession().copy(padVisible = false))
            put("rest running", sweepSession(sweepExercise.logged(done)).copy(restEndsAt = System.currentTimeMillis() + 95_000))
            put("superset chips", sweepSession(sweepExercise.copy(slot = slot), more = listOf(partner)))
            put("swapped chip", sweepSession(sweepExercise.copy(slot = slot.copy(inPlaceOf = "rdl")), more = listOf(partner)))
            put("day done", sweepSession(sweepExercise.logged(done, done, done), entry = null))
        }
    }

    /** One clipping fault a round was the wrong pace: every state of the screen, at every size, in one pass. */
    @Test
    @Config(qualifiers = "w427dp-h950dp-xxhdpi")
    fun everySessionStateReadsWhole() = assertEveryStateWhole()

    @Test
    @Config(qualifiers = "w360dp-h800dp-xxhdpi")
    fun everySessionStateReadsWholeOnASmallPhone() = assertEveryStateWhole()

    /** A list composes only the rows on screen: a screen this tall has them all, none scrolled away unread. */
    @Test
    @Config(qualifiers = "w427dp-h2000dp-xxhdpi")
    fun everyRowOfEveryStateReadsWhole() = assertEveryStateWhole()

    private fun assertEveryStateWhole() {
        val states = sessionStates()
        var state by mutableStateOf(states.keys.first())
        val found = mutableListOf<String>()
        atEveryScale({ key(state) { SessionScreen(states.getValue(state), listOf(bench), Recording(mutableListOf()), onLeave = {}) } }) { scale ->
            states.keys.forEach {
                compose.runOnIdle { state = it }
                compose.waitForIdle()
                faults().forEach { fault -> found += "$scale, $it: $fault" }
            }
        }
        assertTrue("${found.size} faults:\n${found.joinToString("\n")}", found.isEmpty())
    }

    /** Round 4: at 200 % `97.5 × 5` almost touched the kg column. */
    @Test
    @Config(qualifiers = "w427dp-h950dp-xxhdpi")
    fun theLastColumnKeepsClearOfTheKgColumn() {
        val heavy = hasLast("97.5 × 5")
        atEveryScale({ Session(listOf(LoggedSet(97.5, 5, 0, 1)), listOf(LoggedSet(100.0, 5, 0, 1))) }) { scale ->
            val table = hasScrollToNodeAction() and hasAnyAncestor(hasTestTag(SetTableTag))
            compose.onNode(table).performScrollToNode(heavy)
            val last = compose.onNode(heavy, useUnmergedTree = true)
            val lines = 0 until last.layout().lineCount
            val lastInk = last.getBoundsInRoot().left + with(compose.density) { lines.maxOf { last.layout().getLineRight(it) }.toDp() }
            val kg = compose.onNode(hasText("100") and hasAnyAncestor(hasTestTag(SetTableTag)), useUnmergedTree = true)
            val kgInk = kg.getBoundsInRoot().left + with(compose.density) { kg.layout().getLineLeft(0).toDp() }
            assertTrue("Last ends ${kgInk - lastInk} from the 100 at $scale", kgInk - lastInk >= 8.dp)
        }
    }

    /** Round 2: `0 of 14 on target` ended 12 dp past the rows. */
    @Test
    fun theOnTargetCountEndsWithTheRows() {
        val week = WeekSets(LocalDate.of(2026, 11, 2), mapOf("Rear Delts" to 9.0), 9)
        atEveryScale({
            HistoryScreen(
                emptyList(), listOf(week), emptyList(), emptyList(), tab = 1, onTab = {}, onOpenSession = {}, onOpenExercise = {},
                onBodyweight = {}, onLogBodyweight = {}, onBack = {}, today = LocalDate.of(2026, 11, 5),
            )
        }) { scale ->
            val count = compose.onNodeWithText("1 of 1 on target", useUnmergedTree = true).getBoundsInRoot()
            assertTrue("The count ends at ${count.right} at $scale", count.right <= 410.dp - TextEdge)
        }
    }

    @Test
    fun aMusclesFigureStaysOnOneLine() {
        val week = WeekSets(LocalDate.of(2026, 11, 2), mapOf("Rear Delts" to 1.5), 2)
        atEveryScale({
            HistoryScreen(
                emptyList(), listOf(week), emptyList(), emptyList(), tab = 1, onTab = {}, onOpenSession = {}, onOpenExercise = {},
                onBodyweight = {}, onLogBodyweight = {}, onBack = {}, today = LocalDate.of(2026, 11, 5),
            )
        }) { scale -> assertEquals("1.5 splits at $scale", 1, compose.onNodeWithText("1.5", useUnmergedTree = true).layout().lineCount) }
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
                assertFalse("`$line` ends mid-set at $scale", line.endsWith("×") || line.endsWith("back-off"))
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
                compose.onNode(table).performScrollToNode(hasLast(it))
                compose.onNode(hasLast(it), useUnmergedTree = true).layout().getBoundingBox(0).width
            }
            assertEquals("Last sizes differ at $scale: $widths", widths[0], widths[1], 0.01f)
            val long = compose.onNode(hasLast(LongLast), useUnmergedTree = true)
            assertTrue("Last is ${long.layout().layoutInput.style.fontSize} at $scale", long.layout().layoutInput.style.fontSize.value >= lastFloorAt(scale))
            assertFalse("Last is cut at $scale", long.cutShort())
        }
    }

    /**
     * Round 2: `RI` at 200 %. At the test's usual density hinting narrows the glyphs, so this runs at
     * the phone's: every head's whole text is laid out, with room around it.
     */
    @Test
    @Config(qualifiers = "w410dp-h914dp-xxhdpi")
    fun theSetTablesHeadsAreWholeOnAPhonesScreen() {
        atEveryScale({ Session() }) { scale ->
            listOf("Last", "kg", "Reps", "RIR").forEach {
                val head = compose.onAllNodes(hasText(it) and hasAnyAncestor(hasTestTag(SetTableTag)), useUnmergedTree = true)[0]
                val layout = head.layout()
                assertEquals("$it loses its end at $scale", it.length, layout.getLineEnd(0))
                assertTrue("$it runs past its box at $scale", layout.getLineRight(0) <= layout.size.width + 0.5f)
            }
            val reps = compose.onAllNodes(hasText("Reps") and hasAnyAncestor(hasTestTag(SetTableTag)), useUnmergedTree = true)[0].getBoundsInRoot()
            val rir = compose.onAllNodes(hasText("RIR") and hasAnyAncestor(hasTestTag(SetTableTag)), useUnmergedTree = true)[0].getBoundsInRoot()
            assertTrue("RIR is ${rir.left - reps.right} from Reps at $scale", rir.left >= reps.right)
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
