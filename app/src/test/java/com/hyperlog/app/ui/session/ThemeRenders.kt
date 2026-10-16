package com.hyperlog.app.ui.session

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.hyperlog.app.domain.Extension
import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.domain.SetEntry
import com.hyperlog.app.domain.WeightLadder
import com.hyperlog.app.gym.Gym
import com.hyperlog.app.gym.UsualGym
import com.hyperlog.app.history.Bodyweight
import com.hyperlog.app.history.ExerciseTrend
import com.hyperlog.app.history.TrendLine
import com.hyperlog.app.history.TrendPoint
import com.hyperlog.app.history.WeekSets
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.PlannedSlot
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.ProgramParser
import com.hyperlog.app.program.RepRange
import com.hyperlog.app.program.SetPrescription
import com.hyperlog.app.program.StarterProgram
import com.hyperlog.app.program.Technique
import com.hyperlog.app.session.ActiveSession
import com.hyperlog.app.session.FollowUp
import com.hyperlog.app.session.SessionExercise
import com.hyperlog.app.ui.coach.CoachScreen
import com.hyperlog.app.ui.history.HistoryScreen
import com.hyperlog.app.ui.theme.HyperlogTheme
import com.hyperlog.app.ui.today.TodayScreen
import java.io.File
import java.time.LocalDate
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * THEME PROPOSALS (throwaway): Today, a session mid-set, Coach and History → Progress in the
 * proposal named by THEME. `RENDERS=<dir> THEME=cobalt [DARK=1] ./gradlew testDebugUnitTest --tests '*ThemeRenders*'`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThemeRenders {
    @get:Rule
    val compose = createComposeRule()

    private val out = System.getenv("RENDERS")
    private val dark = System.getenv("DARK") == "1"
    private val theme = System.getenv("THEME") ?: "cobalt"

    private val library = LibrarySeed.parse(File("src/main/assets/exercises.json").readText())
    private val program = (ProgramParser.parse(File("src/main/assets/${StarterProgram.ASSET}").readText(), library.exercises.map { it.id }.toSet(), library.merged) as ProgramParser.Result.Valid).program
    private val today = LocalDate.of(2026, 10, 15)
    private val names = library.exercises.associate { it.id to it.name }
    private val byId = library.exercises.associateBy { it.id }
    private val gyms = listOf(Gym(1, "Harbour", emptyList()), Gym(2, "Itis", emptyList()))
    private val usual = UsualGym(1, UsualGym.Why.Weekday)
    private val plain = ActiveProgram(1, program, Position(1, 1), today = today)

    private fun render(name: String, content: @Composable () -> Unit) {
        assumeTrue(out != null)
        RuntimeEnvironment.setQualifiers(if (dark) "+night" else "+notnight")
        compose.setContent { HyperlogTheme { content() } }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(out).mkdirs()
        File(out, "$theme-${if (dark) "dark" else "light"}-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun today() = render("1-today") {
        TodayScreen(
            onSettings = {}, sessionRunning = false, onResume = {}, onStart = { _, _, _ -> },
            gyms = gyms, usualGym = usual,
            program = plain, exerciseNames = names, library = library.exercises, lastTrained = today.minusDays(2),
        )
    }

    @Test fun session() = render("2-session") {
        val fly = byId.getValue("cable_flye")
        val drops = Technique.Dropset(2, 25)
        fun set(min: Int, max: Int, rir: Int, technique: Technique? = null) = SetPrescription(RepRange(min, max), rir, 180, technique = technique)
        val e = SessionExercise(
            id = 3, exercise = fly,
            sets = listOf(
                LoggedSet(25.0, 10, 0, 0, technique = Technique.Failure), LoggedSet(25.0, 9, 0, 1),
                LoggedSet(25.0, 10, 0, 1, technique = drops, extensions = listOf(Extension(Extension.Kind.Drop, 17.5, 7))),
            ),
            lastTime = listOf(LoggedSet(25.0, 10, 0, 1), LoggedSet(25.0, 9, 0, 0)), ladder = WeightLadder((1..40).map { it * 2.5 }), ladderKnown = true,
            prescription = listOf(set(8, 10, 0, Technique.Failure), set(8, 10, 1), set(8, 10, 1, drops), set(10, 12, 3)),
            slot = PlannedSlot("f", "3", 2, "cable_flye", emptyList(), null, emptyList()),
        )
        val session = ActiveSession(
            id = 1, startedAt = 0, exercises = listOf(e), currentId = e.id,
            entry = SetEntry("12.5", "6", "", null, Field.Reps, partialMode = false, fresh = true), padVisible = true,
            restEndsAt = null, restTotalMs = 180_000, warmupEdit = null, rampEntry = null, latestSet = 3L to 2,
        ).copy(followUp = FollowUp.Drop(1, 2), openSetId = 9)
        SessionScreen(session, listOf(fly), Noop, onLeave = {})
    }

    @Test fun coach() = render("3-coach") {
        val words = "Left elbow aches on curls since Monday, about 3/10. Pressing is fine."
        CoachScreen(plain, TextFieldValue(words, TextRange(words.length)), onWords = {}, onBack = {}, gyms = gyms, usualGym = usual)
    }

    @Test fun progress() = render("4-progress") {
        val start = today.minusWeeks(11)
        val bodyweights = (0..80 step 3).map { Bodyweight(today.minusDays(it.toLong()), 82.4 + (it % 7) * 0.15 + it * 0.012) }
        fun trend(id: String, kg: List<Double>, reps: Int): ExerciseTrend {
            val points = kg.mapIndexed { i, w -> TrendPoint(i.toLong(), start.plusWeeks(i.toLong()), LoggedSet(w, reps, 0, 1), w * (1 + reps / 30.0)) }
            return ExerciseTrend(byId.getValue(id), listOf(TrendLine(null, null, points)))
        }
        val exercises = listOf(
            trend("barbell_bench_press", listOf(90.0, 92.5, 92.5, 95.0, 95.0, 97.5, 97.5, 100.0, 100.0, 102.5), 6),
            trend("barbell_squat", listOf(120.0, 122.5, 125.0, 125.0, 127.5, 130.0, 130.0, 132.5), 5),
            trend("pendlay_row", listOf(80.0, 80.0, 82.5, 85.0, 85.0, 87.5, 87.5), 8),
            trend("deadlift", listOf(110.0, 112.5, 115.0, 117.5, 115.0, 120.0), 8),
        )
        val weeks = listOf(WeekSets(today.minusDays(3), mapOf("Chest" to 9.0, "Back" to 10.0, "Quads" to 6.0, "Hamstrings" to 6.0, "Shoulders" to 4.5, "Biceps" to 3.0, "Triceps" to 3.0)))
        HistoryScreen(null, weeks, exercises, bodyweights, tab = 1, onTab = {}, onOpenSession = {}, onOpenExercise = {}, onBodyweight = {}, onLogBodyweight = {}, onBack = {})
    }
}
