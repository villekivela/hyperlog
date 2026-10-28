package com.hyperlog.app.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.hyperlog.app.coach.CoachLine
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.ProgramParser
import com.hyperlog.app.program.StarterProgram
import com.hyperlog.app.ui.theme.HyperlogTheme
import com.hyperlog.app.ui.today.TodayScreen
import com.hyperlog.app.week.RotationAnchor
import com.hyperlog.app.week.Rotations
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

/**
 * Renders of the coach line on Today (SPEC, Coaching → The coach line), for the pull request of
 * #89. Skipped unless asked for: `RENDERS=<dir> ./gradlew testDebugUnitTest --tests
 * '*CoachLineRenders*'`, with `DARK=1` for dark.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CoachLineRenders {
    @get:Rule
    val compose = createComposeRule()

    private val out = System.getenv("RENDERS")
    private val dark = System.getenv("DARK") == "1"
    private val library = LibrarySeed.parse(File("src/main/assets/exercises.json").readText())
    private val ids = library.exercises.map { it.id }.toSet()
    private val names = library.exercises.associate { it.id to it.name }
    private val program = (ProgramParser.parse(File("src/main/assets/${StarterProgram.ASSET}").readText(), ids, library.merged) as ProgramParser.Result.Valid).program
    private val monday = LocalDate.of(2026, 10, 26)
    private val days = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY)
    private val plan = WeekdayPlan(gyms = days.associateWith { null })
    private val rotation = Rotations.of(program, RotationAnchor(monday, 1, 0), days, emptyList(), monday)
    private val active = ActiveProgram(1, program, rotation.next, today = monday, rotation = rotation)

    private fun render(name: String, content: @Composable () -> Unit) {
        assumeTrue(out != null)
        RuntimeEnvironment.setQualifiers(if (dark) "+night" else "+notnight")
        compose.setContent { HyperlogTheme { content() } }
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(out).mkdirs()
        File(out, "${if (dark) "dark" else "light"}-coach-line-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun today(line: CoachLine, lastTrained: LocalDate? = null) = render(line::class.simpleName!!.lowercase() + if (lastTrained != null) "-time-off" else "") {
        TodayScreen(
            sessionRunning = false, onResume = {}, onStart = { _, _, _ -> },
            program = active, exerciseNames = names, library = library.exercises,
            weekdayPlan = plan, lastTrained = lastTrained, coachLine = line,
        )
    }

    @Test
    fun stall() = today(CoachLine.Stall("barbell_bench_press", sessions = 3, last = monday))

    @Test
    fun dayMissed() = today(CoachLine.DayMissed("a", "Day A", missed = 3, of = 4, last = monday))

    @Test
    fun grinding() = today(CoachLine.Grinding("leg_press", sessions = 3, last = monday))

    @Test
    fun sameSwap() = today(CoachLine.SameSwap("a2", "incline_machine_press", "machine_chest_press", sessions = 3, last = monday))

    /** Under the time-off banner, the louder of the two. */
    @Test
    fun underTimeOff() = today(CoachLine.Stall("barbell_bench_press", sessions = 4, last = monday), lastTrained = monday.minusDays(11))
}
