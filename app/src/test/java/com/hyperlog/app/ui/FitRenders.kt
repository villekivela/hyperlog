package com.hyperlog.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.hyperlog.app.coach.ProgramFormat
import com.hyperlog.app.coach.ProgramProposal
import com.hyperlog.app.gym.Gym
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.ProgramParser
import com.hyperlog.app.program.StarterProgram
import com.hyperlog.app.ui.coach.ProgramReviewScreen
import com.hyperlog.app.ui.coach.ReviewFit
import com.hyperlog.app.ui.program.ProgramScreen
import com.hyperlog.app.ui.theme.HyperlogTheme
import com.hyperlog.app.ui.today.HeroCard
import com.hyperlog.app.ui.today.PlannedCard
import com.hyperlog.app.ui.today.aboutMinutes
import com.hyperlog.app.week.Fit
import com.hyperlog.app.week.RotationAnchor
import com.hyperlog.app.week.Rotations
import com.hyperlog.app.week.WeekdayPlan
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
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
 * Renders of the fit lines (SPEC, The fit), for the pull request of #88. Skipped unless asked for:
 * `RENDERS=<dir> ./gradlew testDebugUnitTest --tests '*FitRenders*'`, with `DARK=1` for dark.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FitRenders {
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

    /** The first slot of every day is missing at Harbour; Monday has 30 minutes. */
    private val harbour = Gym(1, "Harbour", emptyList(), missing = program.blocks.first().days.flatMap { d -> d.groups.flatMap { g -> g.slots.map { it.exercise } } }.take(3).toSet())
    private val plan = WeekdayPlan(
        gyms = mapOf(DayOfWeek.MONDAY to 1L, DayOfWeek.THURSDAY to 1L, DayOfWeek.SATURDAY to null),
        minutes = mapOf(DayOfWeek.MONDAY to 30, DayOfWeek.THURSDAY to 45),
    )
    private val rotation = Rotations.of(program, RotationAnchor(monday, 1, 0), days, emptyList(), monday)
    private val active = ActiveProgram(1, program, rotation.next, today = monday, rotation = rotation)

    private fun render(name: String, content: @Composable () -> Unit) {
        assumeTrue(out != null)
        RuntimeEnvironment.setQualifiers(if (dark) "+night" else "+notnight")
        compose.setContent { HyperlogTheme { content() } }
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(out).mkdirs()
        File(out, "${if (dark) "dark" else "light"}-fit-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun fit(day: com.hyperlog.app.program.PlannedDay, date: LocalDate) = Fit.of(day, date, plan, listOf(harbour), library.exercises, aboutMinutes(day, library.exercises, emptyList()))

    @Test
    fun todayCards() = render("today-cards") {
        val a = program.plannedDay(com.hyperlog.app.program.Position(1, 0))!!
        val b = program.plannedDay(com.hyperlog.app.program.Position(1, 1))!!
        Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainer)) {
            Spacer(Modifier.height(16.dp))
            HeroCard(a, date = null, onStart = {}, library = library.exercises, finished = emptyList(), fit = fit(a, monday), onFitTime = {})
            Spacer(Modifier.height(16.dp))
            PlannedCard(b, monday.plusDays(3), library = library.exercises, finished = emptyList(), fit = fit(b, monday.plusDays(3)), onFitTime = {})
            Spacer(Modifier.height(16.dp))
        }
    }

    @Test
    fun program() = render("program") {
        ProgramScreen(
            active, null, onImport = {}, onCloseErrors = {}, onBack = {}, exerciseNames = names,
            weekdayPlan = plan, gyms = listOf(harbour), library = library.exercises,
        )
    }

    @Test
    fun review() {
        val file = ProgramFormat.example(library.exercises).toString()
        val reply = """{"program": $file, "swaps": [], "newExercises": []}"""
        val proposal = (ProgramProposal.read(Json.parseToJsonElement(reply).jsonObject, ids, names) as ProgramProposal.Result.Valid).proposal
        val first = proposal.program.blocks.first().days.first().groups.first().slots.first().exercise
        val gym = harbour.copy(missing = setOf(first))
        render("review") {
            ProgramReviewScreen(proposal, names, null, onStart = {}, onReject = {}, fit = ReviewFit(plan.copy(minutes = mapOf(DayOfWeek.MONDAY to 10)), listOf(gym), library.exercises, monday))
        }
    }
}
