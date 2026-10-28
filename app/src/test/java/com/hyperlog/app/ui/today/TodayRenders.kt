package com.hyperlog.app.ui.today

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.gym.Gym
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.ProgramParser
import com.hyperlog.app.program.StarterProgram
import com.hyperlog.app.session.ExerciseSummary
import com.hyperlog.app.session.PastSessionItem
import com.hyperlog.app.session.date
import com.hyperlog.app.ui.theme.HyperlogTheme
import com.hyperlog.app.week.WeekdayPlan
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/**
 * Renders of Today's calendar and the day picked on it, for docs/design/program-and-today.md.
 * Skipped unless asked for: `RENDERS=<dir> ./gradlew testDebugUnitTest --tests '*TodayRenders*'`,
 * with `DARK=1` for dark.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TodayRenders {
    @get:Rule
    val compose = createComposeRule()

    private val out = System.getenv("RENDERS")
    private val dark = System.getenv("DARK") == "1"

    private val library = LibrarySeed.parse(File("src/main/assets/exercises.json").readText())
    private val program = (ProgramParser.parse(File("src/main/assets/${StarterProgram.ASSET}").readText(), library.exercises.map { it.id }.toSet(), library.merged) as ProgramParser.Result.Valid).program
    private val names = library.exercises.associate { it.id to it.name }
    private val sat17 = LocalDate.of(2026, 10, 17)
    private val gyms = listOf(Gym(1, "Kalliokoski", emptyList()), Gym(2, "Itis", emptyList()))
    private val mwf = WeekdayPlan(mapOf(DayOfWeek.MONDAY to 1L, DayOfWeek.WEDNESDAY to 1L, DayOfWeek.FRIDAY to 2L))

    private fun at(date: LocalDate, hour: Int, minute: Int = 0) = date.atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** A finished session of the program's day [day] in [week], an hour and some long. */
    private fun done(id: Long, date: LocalDate, week: Int, day: Int, gym: String) = PastSessionItem(
        id, at(date, 17), at(date, 18, 4), program.plannedDay(Position(week, day))!!.day.name, gym,
        listOf(
            ExerciseSummary("Barbell Bench Press", listOf(LoggedSet(92.5, 5, 0, 1), LoggedSet(82.5, 7, 0, 1), LoggedSet(82.5, 6, 0, 0))),
            ExerciseSummary("High-Bar Back Squat", listOf(LoggedSet(110.0, 6, 0, 2), LoggedSet(100.0, 8, 0, 1))),
            ExerciseSummary("2-Grip Lat Pulldown", listOf(LoggedSet(70.0, 9, 0, 1), LoggedSet(70.0, 8, 0, 0))),
        ),
        programId = 1, position = Position(week, day),
    )

    private val adHoc = PastSessionItem(9, at(sat17.minusDays(8), 7), at(sat17.minusDays(8), 7, 40), null, "Itis", listOf(
        ExerciseSummary("Seated DB Arnold Press", listOf(LoggedSet(18.0, 10, 0, 1), LoggedSet(18.0, 9, 0, 0))),
    ))

    /** Week 6 done Monday to Friday, an ad-hoc session the Friday before. */
    private val week6 = listOf(
        done(1, sat17.minusDays(5), 6, 0, "Kalliokoski"),
        done(2, sat17.minusDays(3), 6, 1, "Kalliokoski"),
        done(3, sat17.minusDays(1), 6, 2, "Itis"),
        adHoc,
    )

    private fun render(name: String, before: () -> Unit = {}, content: @Composable () -> Unit) {
        assumeTrue(out != null)
        RuntimeEnvironment.setQualifiers(if (dark) "+night" else "+notnight")
        compose.setContent { HyperlogTheme { content() } }
        compose.waitForIdle()
        before()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(out).mkdirs()
        File(out, "${if (dark) "dark" else "light"}-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Composable
    private fun Today(position: Position = Position(7, 0), sessions: List<PastSessionItem> = week6, plan: WeekdayPlan = mwf) {
        TodayScreen(
            sessionRunning = false, onResume = {}, onStart = { _, _, _ -> },
            program = ActiveProgram(1, program, position, today = sat17), exerciseNames = names, library = library.exercises,
            gyms = gyms, weekdayPlan = plan, sessions = sessions, lastTrained = sessions.maxOfOrNull { it.date() },
        )
    }

    /** Week 7's Day A finished today. */
    private val trainedToday = week6 + done(4, sat17, 7, 0, "Kalliokoski")

    private fun pick(date: LocalDate) {
        compose.onNode(hasContentDescription(shortDate(date), substring = true)).performClick()
        compose.waitForIdle()
    }

    @Test fun hero() = render("today-calendar-hero") { Today() }

    @Test fun done() = render("today-calendar-done") { Today(Position(7, 1), trainedToday) }

    @Test fun next() = render("today-calendar-next", before = { compose.onNodeWithText("Next: Day B on Mon 19 Oct").performClick() }) {
        Today(Position(7, 1), trainedToday)
    }

    @Test fun planned() = render("today-calendar-planned", before = {
        compose.onNode(hasTestTag(CalendarTag)).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        pick(LocalDate.of(2026, 10, 21))
    }) { Today(Position(7, 1), trainedToday) }

    @Test fun rest() = render("today-calendar-rest", before = { pick(LocalDate.of(2026, 10, 15)) }) { Today() }

    @Test fun pastDone() = render("today-calendar-past-done", before = { pick(LocalDate.of(2026, 10, 14)) }) { Today() }

    @Test fun lastWeek() = render("today-calendar-last-week", before = {
        compose.onNode(hasTestTag(CalendarTag)).performTouchInput { swipeRight() }
        compose.waitForIdle()
        pick(LocalDate.of(2026, 10, 9))
    }) { Today() }

    @Test fun noTrainingDays() = render("today-calendar-no-training-days") { Today(plan = WeekdayPlan.None) }

    /** Week 12's Day B, a deload day, today: the Deload tag under its name. */
    @Test fun deload() = render("today-calendar-deload") { Today(Position(12, 1)) }
}
