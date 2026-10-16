package com.hyperlog.app.ui.coach

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.hyperlog.app.coach.Workout
import com.hyperlog.app.coach.Workouts
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.gym.Gym
import com.hyperlog.app.gym.UsualGym
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.ProgramParser
import com.hyperlog.app.program.StarterProgram
import com.hyperlog.app.session.ExerciseSummary
import com.hyperlog.app.session.PastSessionItem
import com.hyperlog.app.ui.history.HistoryScreen
import com.hyperlog.app.ui.theme.HyperlogTheme
import com.hyperlog.app.ui.today.TodayScreen
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * Renders of the workout's states (#33) for docs/design/coach.md. Skipped unless asked for:
 * `RENDERS=<dir> ./gradlew testDebugUnitTest --tests '*WorkoutRenders*'`, with `DARK=1` for dark.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WorkoutRenders {
    @get:Rule
    val compose = createComposeRule()

    private val out = System.getenv("RENDERS")
    private val dark = System.getenv("DARK") == "1"

    private val library = LibrarySeed.parse(File("src/main/assets/exercises.json").readText())
    private val ids = library.exercises.map { it.id }.toSet()
    private val program = (ProgramParser.parse(File("src/main/assets/${StarterProgram.ASSET}").readText(), ids, library.merged) as ProgramParser.Result.Valid).program
    private val today = LocalDate.of(2026, 10, 15)
    private val names = library.exercises.associate { it.id to it.name }
    private val gyms = listOf(Gym(1, "Harbour", emptyList()), Gym(2, "Itis", emptyList()))
    private val usual = UsualGym(1, UsualGym.Why.Weekday)
    private val plain = ActiveProgram(1, program, Position(2, 1), today = today)
    private val lowest = listOf("Hamstrings" to 0.0, "Rear Delts" to 1.5, "Lats" to 4.0)

    private val hotel = (ProgramParser.workout(
        """
        { "workout": { "name": "Hotel dumbbells, 45 min",
          "summary": "Upper body with dumbbells only. Back first, since it's behind this week.",
          "groups": [
            { "slots": [ { "id": "row", "exercise": "chest_supported_db_row", "sets": [ { "count": 3, "reps": "8-12", "rir": 1, "rest": 120 } ] } ] },
            { "slots": [ { "id": "press", "exercise": "db_bench_press", "sets": [ { "count": 3, "reps": "8-10", "rir": 2, "rest": 120 } ] } ] },
            { "type": "superset", "slots": [
              { "id": "rear", "exercise": "lying_reverse_db_flye", "sets": [ { "count": 2, "reps": "12-15", "rir": 1, "rest": 60 } ] },
              { "id": "curl", "exercise": "db_bicep_curl", "sets": [ { "count": 2, "reps": "10-12", "rir": 1, "rest": 60 } ] } ] }
          ] } }
        """,
        ids,
    ) as ProgramParser.WorkoutResult.Valid).workout

    private val waiting = Workouts.Waiting(7, hotel, today, gymId = 2)

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
    private fun Coach(program: ActiveProgram? = plain, words: String = "", lowest: List<Pair<String, Double>> = this.lowest) = CoachScreen(
        program, TextFieldValue(words, TextRange(words.length)), onWords = {}, onBack = {},
        gyms = gyms, usualGym = usual, lowest = lowest,
    )

    private val build = { compose.onNodeWithText("Build me a workout").performClick(); Unit }

    @Test fun options() = render("coach-15-workout-options", before = build) { Coach() }

    @Test fun optionsPicked() = render("coach-16-workout-focus", before = {
        build()
        compose.onNodeWithText("Hard").performClick()
        compose.onNodeWithText("1 h 15").performClick()
        compose.onNodeWithText("Hamstrings · 0 sets").performClick()
        compose.onNodeWithText("Back").performScrollTo().performClick()
        compose.onNodeWithText("Glutes").performScrollTo().performClick()
    }) { Coach(words = "Hotel gym this week: dumbbells to 30 kg and a bench.") }

    @Test fun optionsNoProgram() = render("coach-17-workout-no-program", before = build) { Coach(program = null, lowest = emptyList()) }

    @Test fun review() = render("coach-18-workout-review") {
        WorkoutReviewScreen(hotel, names, onAdd = {}, onReject = {})
    }

    @Test fun todayWithProgram() = render("today-workout") {
        TodayScreen(
            onSettings = {}, sessionRunning = false, onResume = {}, onStart = { _, _, _ -> },
            program = plain, exerciseNames = names, library = library.exercises, workout = waiting, gyms = gyms, usualGym = usual,
        )
    }

    @Test fun todayNoProgram() = render("today-workout-no-program") {
        TodayScreen(
            onSettings = {}, sessionRunning = false, onResume = {}, onStart = { _, _, _ -> },
            program = null, exerciseNames = names, library = library.exercises, workout = waiting, gyms = gyms, usualGym = usual,
        )
    }

    @Test fun todayRemoved() = render("today-workout-removed", before = {
        compose.onNodeWithContentDescription("More for this workout").performClick()
        compose.onNodeWithText("Remove").performClick()
    }) {
        var shown by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Workouts.Waiting?>(waiting) }
        TodayScreen(
            onSettings = {}, sessionRunning = false, onResume = {}, onStart = { _, _, _ -> },
            program = plain, exerciseNames = names, library = library.exercises, workout = shown, gyms = gyms, usualGym = usual,
            onRemoveWorkout = { shown = null },
        )
    }

    @Test fun todayStartSheet() = render("today-workout-start", before = { compose.onNodeWithText("Start").performClick() }) {
        TodayScreen(
            onSettings = {}, sessionRunning = false, onResume = {}, onStart = { _, _, _ -> },
            program = plain, exerciseNames = names, library = library.exercises, workout = waiting, gyms = gyms, usualGym = usual,
        )
    }

    @Test fun todayRunning() = render("today-workout-running") {
        TodayScreen(
            onSettings = {}, sessionRunning = true, onResume = {}, onStart = { _, _, _ -> },
            program = plain, exerciseNames = names, library = library.exercises, gyms = gyms, usualGym = usual,
            runningDay = hotel.day(), runningWorkout = true,
        )
    }

    @Test fun history() = render("history-workout") {
        val zone = ZoneId.systemDefault()
        fun at(daysAgo: Long, hour: Int) = today.minusDays(daysAgo).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
        val sessions = listOf(
            PastSessionItem(3, at(0, 7), at(0, 8), hotel.name, "Itis", listOf(
                ExerciseSummary("Chest-Supported DB Row", List(3) { LoggedSet(26.0, 10, 0, 1) }),
                ExerciseSummary("DB Bench Press", List(3) { LoggedSet(30.0, 9, 0, 2) }),
            )),
            PastSessionItem(2, at(2, 18), at(2, 19), program.plannedDay(Position(2, 0))!!.day.name, "Harbour", listOf(
                ExerciseSummary("Barbell Bench Press", listOf(LoggedSet(92.5, 5, 0, 1), LoggedSet(82.5, 8, 0, 1))),
            )),
        )
        HistoryScreen(sessions, emptyList(), emptyList(), emptyList(), tab = 0, onTab = {}, onOpenSession = {}, onOpenExercise = {}, onBodyweight = {}, onLogBodyweight = {}, onBack = {})
    }

    @Test fun session() {
        assumeTrue(out != null)
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context, com.hyperlog.app.data.HyperlogDatabase::class.java).allowMainThreadQueries().build()
        val state = kotlinx.coroutines.runBlocking {
            com.hyperlog.app.library.ExerciseLibrary(db).sync(library)
            var now = today.atTime(7, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val programs = com.hyperlog.app.program.Programs(db, clock = { now })
            val logger = com.hyperlog.app.session.SessionLogger(db, clock = { now }, programs = programs).also { it.load() }
            // Last week's session of it, so this one has last time and a suggestion to show.
            val id = Workouts(db, clock = { now }).add(hotel, null)
            logger.startWorkout(id, null)
            fun type(t: String) = kotlinx.coroutines.runBlocking { t.forEach { logger.press(if (it == '.') com.hyperlog.app.domain.PadKey.Dot else com.hyperlog.app.domain.PadKey.Digit(it.digitToInt())) } }
            repeat(3) { i ->
                now += 200_000
                logger.focus(com.hyperlog.app.domain.Field.Weight); type("20"); logger.done(); type("${12 - i}"); logger.done()
            }
            logger.finish()
            now += 7 * 86_400_000L
            val again = Workouts(db, clock = { now }).add(hotel, null)
            logger.startWorkout(again, null)
            logger.state.value!!
        }
        render("session-workout") {
            com.hyperlog.app.ui.session.SessionScreen(state, library.exercises, com.hyperlog.app.ui.session.Noop, onLeave = {})
        }
        db.close()
    }
}
