package com.hyperlog.app.ui.today

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.hyperlog.app.ui.theme.HyperlogTheme
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * The calendar's head at the phone's larger text (font size 115 %): the month is never cut
 * short, and the training days stay one tap away (SPEC, Today; design note, Today's calendar).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
// Real text measuring, so a cut-short month shows.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CalendarHeadTest {
    @get:Rule
    val compose = createComposeRule()

    /** The longest month name. */
    private val september = LocalDate.of(2026, 9, 14)

    private fun head(showToday: Boolean, fontScale: Float = 1.15f, monday: LocalDate = september, week: Int? = null) = compose.setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            HyperlogTheme {
                CalendarHead(monday, showToday = showToday, onToday = {}, noWeekdayPlan = true, onWeekdayPlan = {}, week = week)
            }
        }
    }

    private fun SemanticsNodeInteraction.cutShort(): Boolean {
        val layouts = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        return layouts.single().let { it.hasVisualOverflow || it.isLineEllipsized(0) }
    }

    @Test
    fun offTodayTheMonthKeepsItsWholeNameBesideTodayAndThePencil() {
        head(showToday = true)
        assertFalse("The month is cut short", compose.onNodeWithText("September 2026").cutShort())
        compose.onNodeWithText("Today").assertHasClickAction()
        compose.onNodeWithContentDescription("Set your training days").assertHasClickAction()
    }

    @Test
    fun onTodayTheMonthKeepsItsWholeNameBesideTheTrainingDaysPrompt() {
        head(showToday = false)
        assertFalse("The month is cut short", compose.onNodeWithText("September 2026").cutShort())
        compose.onNodeWithContentDescription("Set your training days").assertHasClickAction()
    }

    @Test
    fun withRoomThePromptKeepsItsWords() {
        head(showToday = false, fontScale = 1f, monday = LocalDate.of(2026, 10, 12))
        compose.onNodeWithText("Set your training days").assertHasClickAction()
    }

    @Test
    fun theProgramWeekFollowsTheMonth() {
        head(showToday = false, fontScale = 1f, monday = LocalDate.of(2026, 10, 12), week = 7)
        compose.onNodeWithText("October 2026 · Week 7").assertExists()
    }

    @Test
    @Config(qualifiers = "w426dp-h952dp")
    fun onThePhonesWidthTheLongestMonthAbbreviatesToKeepItsWeekBesideTodayAndThePencil() {
        head(showToday = true, week = 12)
        val head = compose.onNodeWithText("Week 12", substring = true)
        assertFalse("The head is cut short", head.cutShort())
        // Off today the whole name and the week don't both fit beside Today and the pencil: the
        // month abbreviates before the week goes, and nothing is cut.
        compose.onNodeWithText("Sep 2026 · Week 12").assertExists()
        compose.onNodeWithContentDescription("Set your training days").assertHasClickAction()
    }
}
