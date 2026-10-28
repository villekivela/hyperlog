package com.hyperlog.app.ui.today

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.FreshDatabaseRule
import com.hyperlog.app.MainActivity
import com.hyperlog.app.await
import com.hyperlog.app.click
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.press
import com.hyperlog.app.program.ProgramParser
import com.hyperlog.app.program.TestPrograms
import com.hyperlog.app.ui.program.ProgramStore
import com.hyperlog.app.waitFor
import com.hyperlog.app.waitForDescription
import com.hyperlog.app.week.WeekdayPlan
import com.hyperlog.app.week.WeekdayPlans
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import com.hyperlog.app.data.SessionEntity

/** Today around a calendar of done and planned sessions (SPEC, Today). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class TodayCalendarTest {
    val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabaseRule()).around(compose)

    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val today = LocalDate.now()

    /** `Sat 17 Oct`, as the calendar and the cards name a date. */
    private fun short(d: LocalDate) =
        "${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}"

    private fun importProgram() {
        compose.waitFor("Today")
        val result = runBlocking { ProgramStore.get(app).import(TestPrograms.twoDays("Test")) }
        assertEquals(ProgramParser.Result.Valid::class, result::class)
        compose.waitFor("Day A")
    }

    private fun trainingDays(vararg days: DayOfWeek) = runBlocking {
        WeekdayPlans(HyperlogDatabase.get(app)).set(WeekdayPlan(days.associateWith { null }))
    }

    /** The calendar's cell for [date], once it shows. */
    private fun cell(date: LocalDate, state: String = "") =
        hasContentDescription(short(date) + state, substring = true)

    /** A finished ad-hoc session on [date], an hour long. */
    private fun adHocOn(date: LocalDate, hour: Int = 18) = runBlocking {
        val at = date.atTime(hour, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        HyperlogDatabase.get(app).sessions().insertSession(SessionEntity(startedAt = at, finishedAt = at + 3_600_000))
    }

    /** Swipes the calendar to [date]'s week. */
    private fun show(date: LocalDate) {
        val monday = { d: LocalDate -> d.minusDays(d.dayOfWeek.value - 1L) }
        val weeks = java.time.temporal.ChronoUnit.WEEKS.between(monday(today), monday(date)).toInt()
        repeat(kotlin.math.abs(weeks)) {
            compose.onNode(hasTestTag(CalendarTag)).performTouchInput { if (weeks > 0) swipeLeft() else swipeRight() }
            compose.waitForIdle()
        }
    }

    private fun finishDayA() {
        compose.onNodeWithText("Start session").performClick()
        compose.waitFor("Reps")
        compose.press("8", "0", "Next", "6", "Log set")
        compose.waitFor("70")
        compose.press("More")
        compose.click("Finish session")
        compose.click("Finish")
        compose.click("Done")
    }

    @Test
    fun todayOpensOnTheNextDayWithStartInsideItsCard() {
        importProgram()
        compose.onNodeWithText("Week 1 · Main").assertIsDisplayed()
        compose.onNodeWithText("Start session").assertIsDisplayed()
        compose.onNodeWithText("Ad-hoc session").assertIsDisplayed()
        // Today is the planned day, marked with its letter, and Today's own button isn't needed.
        compose.await(cell(today, ", planned Day A"))
        compose.onNode(hasTestTag(TodayButtonTag)).assertDoesNotExist()
    }

    @Test
    fun withTrainingDaysTheProgramStartsOnTheFirstOneAndTodayWithoutOneShowsItDated() {
        // Every weekday but today's: the program starts tomorrow.
        val days = DayOfWeek.entries.filter { it != today.dayOfWeek }.toTypedArray()
        trainingDays(*days)
        importProgram()
        val tomorrow = today.plusDays(1)
        // Today shows tomorrow's day, dated, with no Start: Start is for a day planned on today.
        compose.waitFor("Next · ${short(tomorrow)} · Week 1")
        compose.onNodeWithText("Start session").assertDoesNotExist()
        show(tomorrow)
        compose.await(cell(tomorrow, ", planned Day A"))
        compose.onNode(cell(tomorrow)).performClick()
        compose.waitFor("Next · ${short(tomorrow)} · Week 1")
        compose.onNodeWithText("Barbell Bench Press").assertIsDisplayed()
        // Off today: the header offers the way back.
        compose.onNode(hasTestTag(TodayButtonTag)).performClick()
        compose.waitFor("Next · ${short(tomorrow)} · Week 1")
    }

    @Test
    fun withNoTrainingDaysTheHeaderAsksForThem() {
        importProgram()
        compose.await(cell(today, ", planned Day A"))
        show(today.plusDays(1))
        // The words, or the pencil alone when the month needs the room (CalendarHeadTest).
        // On a Sunday tomorrow is another week's Monday, a rest day that offers the same prompt: take the head's.
        compose.onAllNodes(hasText("Set your training days").or(hasContentDescription("Set your training days"))).onFirst().performClick()
        compose.waitFor("Training days")
        compose.waitFor("Done")
    }

    @Test
    fun aSessionFinishedTodayShowsItsCardOpensInHistoryAndPointsToTheNextDay() {
        importProgram()
        finishDayA()
        compose.waitFor("Done · ${short(today)}", substring = true)
        compose.await(cell(today, ", done Day A"))
        // With no training days, the next day goes on tomorrow.
        val tomorrow = today.plusDays(1)
        // What's next comes first, over what was done.
        val next = compose.onNodeWithText("Next: Day B on ${short(tomorrow)}").fetchSemanticsNode().boundsInRoot
        val doneCard = compose.onNodeWithText("Done · ${short(today)}", substring = true).fetchSemanticsNode().boundsInRoot
        assertTrue("The Next link should sit above the done card", next.bottom <= doneCard.top)
        compose.click("Next: Day B on ${short(tomorrow)}")
        // On another date the head names the date and the week, as a planned day's does; the
        // block's name would wrap it onto a second line on the phone.
        compose.waitFor("Next · ${short(tomorrow)} · Week 1")
        compose.onNodeWithText("Leg Extension").assertIsDisplayed()
        // Start is only for a day planned on today.
        compose.onNodeWithText("Start session").assertDoesNotExist()
        compose.onNode(hasTestTag(TodayButtonTag)).performClick()
        compose.click("Open session")
        compose.waitFor("Barbell Bench Press")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitFor("Done · ${short(today)}", substring = true)
    }

    @Test
    fun aDayWithNothingIsARestDayPointingToTheNextSession() {
        importProgram()
        val yesterday = today.minusDays(1)
        show(yesterday)
        compose.onNode(cell(yesterday)).performClick()
        compose.waitFor("Rest day")
        compose.click("Next: Day A on ${short(today)}")
        compose.waitFor("Week 1 · Main")
    }

    @Test
    fun theCalendarSwipesBackToTheFirstSessionAndOnToTheLastPlannedWeek() {
        val longAgo = today.minusWeeks(10)
        runBlocking {
            val dao = HyperlogDatabase.get(app).sessions()
            val at = longAgo.atTime(18, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            dao.insertSession(com.hyperlog.app.data.SessionEntity(startedAt = at, finishedAt = at + 3_600_000))
        }
        trainingDays(DayOfWeek.MONDAY)
        importProgram()
        show(longAgo)
        // An ad-hoc session: done, with a tick for its letter.
        compose.await(cell(longAgo, ", done"))
        compose.onNode(hasTestTag(TodayButtonTag)).assertIsDisplayed()
        // Its week is the first: one more swipe stays on it.
        compose.onNode(hasTestTag(CalendarTag)).performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.await(cell(longAgo, ", done"))
        compose.onNode(hasTestTag(TodayButtonTag)).performClick()
        compose.onNode(hasTestTag(TodayButtonTag)).assertDoesNotExist()
        // One training day a week takes one day of each program week: Day A on the first Monday, Day B on the next.
        val firstMonday = generateSequence(today) { it.plusDays(1) }.first { it.dayOfWeek == DayOfWeek.MONDAY }
        show(firstMonday)
        compose.await(cell(firstMonday, ", planned Day A"))
        compose.onNode(hasTestTag(CalendarTag)).performTouchInput { swipeLeft() }
        compose.await(cell(firstMonday.plusWeeks(1), ", planned Day B"))
    }

    @Test
    fun slotLabelsAreCentredOnTheirRows() {
        importProgram()
        val label = compose.onAllNodes(hasTestTag(SlotLabelTag), useUnmergedTree = true)[0].fetchSemanticsNode().boundsInRoot
        val row = compose.onAllNodes(hasTestTag(SlotRowTag), useUnmergedTree = true)[0].fetchSemanticsNode().boundsInRoot
        assertEquals(row.center.y, label.center.y, 2f)
    }

    @Test
    fun whileASessionRunsTheNextDayOnALaterDateIsOnlyAPlan() {
        importProgram()
        finishDayA()
        // A second session today, left running.
        compose.onNode(androidx.compose.ui.test.hasText("Ad-hoc session").and(androidx.compose.ui.test.hasClickAction())).performClick()
        compose.waitFor("Add exercise")
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.click("Leave")
        compose.waitFor("Resume session")
        val tomorrow = today.plusDays(1)
        show(tomorrow)
        compose.onNode(cell(tomorrow)).performClick()
        compose.waitFor("Planned · ${short(tomorrow)} · Week 1")
        compose.onNode(hasTestTag(HeroCardTag)).assertDoesNotExist()
    }

    @Test
    fun backFromADoneDaysSessionReturnsToThatDayAndWeek() {
        val lastWeek = today.minusDays(8)
        adHocOn(lastWeek)
        importProgram()
        show(lastWeek)
        compose.onNode(cell(lastWeek)).performClick()
        compose.click("Open session")
        compose.waitForDescription("Back")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.await(cell(lastWeek, ", done").and(isSelected()))
        compose.waitFor("Done · ${short(lastWeek)}", substring = true)
    }

    @Test
    fun pagingTheStripSelectsTheSameWeekdayInTheWeekShown() {
        importProgram()
        compose.onNode(hasTestTag(CalendarTag)).performTouchInput { swipeLeft() }
        compose.await(cell(today.plusWeeks(1)).and(isSelected()))
        compose.onNode(hasTestTag(CalendarTag)).performTouchInput { swipeRight() }
        compose.onNode(hasTestTag(CalendarTag)).performTouchInput { swipeRight() }
        compose.await(cell(today.minusWeeks(1)).and(isSelected()))
    }

    @Test
    fun pastTheProgramsLastDayNothingIsPlannedAndTheLastDaySaysSo() {
        trainingDays(DayOfWeek.MONDAY)
        importProgram()
        // On Mondays alone each program week trains one day: the last is the second Monday.
        val lastMonday = generateSequence(today) { it.plusDays(1) }.first { it.dayOfWeek == DayOfWeek.MONDAY }.plusWeeks(1)
        show(lastMonday)
        compose.onNode(cell(lastMonday)).performClick()
        compose.waitFor("Last day · ${short(lastMonday)} · Week 2")
        val after = lastMonday.plusDays(1)
        compose.onNode(cell(after)).performClick()
        compose.waitFor("Nothing planned")
        compose.click("Last planned: Day B on ${short(lastMonday)}")
        compose.waitFor("Last day · ${short(lastMonday)} · Week 2")
    }

    @Test
    fun withNoTrainingDaysLaterDatesSayTheProgramGoesOnUndated() {
        importProgram()
        val later = today.plusDays(2)
        show(later)
        compose.onNode(cell(later)).performClick()
        compose.waitFor("Not planned yet")
        compose.onNodeWithText("Nothing planned").assertDoesNotExist()
        compose.onNodeWithText("Next: Day A on ${short(today)}").assertIsDisplayed()
        // The card's own button, after the header's.
        compose.onAllNodes(hasText("Set your training days").and(hasClickAction())).onLast().performClick()
        compose.waitFor("Training days")
    }

    @Test
    fun backFromADoneDaysSessionKeepsWhereTheDayWasScrolled() {
        val lastWeek = today.minusDays(8)
        (8..11).forEach { adHocOn(lastWeek, hour = it) }
        importProgram()
        show(lastWeek)
        compose.onNode(cell(lastWeek)).performClick()
        compose.waitFor("Open session")
        compose.onNode(hasTestTag(TodayDayListTag)).performScrollToIndex(7)
        compose.waitForIdle()
        val open = compose.onAllNodesWithText("Open session").fetchSemanticsNodes().last()
        val top = open.boundsInRoot.top
        compose.onAllNodesWithText("Open session").filter(hasAnyAncestor(hasTestTag(TodayDayListTag))).onLast().performClick()
        compose.waitForDescription("Back")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.await(cell(lastWeek, ", done").and(isSelected()))
        compose.waitForIdle()
        val tops = compose.onAllNodesWithText("Open session").fetchSemanticsNodes().map { it.boundsInRoot.top }
        assertTrue("The day opened at the top again: $tops, was $top", tops.any { abs(it - top) < 2f })
    }

    @Test
    fun theHeadNamesTheProgramWeekOfTheWeekShown() {
        importProgram()
        compose.waitFor("· Week 1", substring = true)
    }
}
