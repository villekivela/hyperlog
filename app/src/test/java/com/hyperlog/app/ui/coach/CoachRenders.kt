package com.hyperlog.app.ui.coach

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.hyperlog.app.coach.AcceptedChange
import com.hyperlog.app.coach.Change
import com.hyperlog.app.coach.Changes
import com.hyperlog.app.coach.Operation
import com.hyperlog.app.coach.Scope
import com.hyperlog.app.gym.Gym
import com.hyperlog.app.gym.UsualGym
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.ChangeRecord
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.ProgramParser
import com.hyperlog.app.program.StarterProgram
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

/**
 * Renders of Coach's states for docs/design/coach.md. Skipped unless asked for:
 * `RENDERS=<dir> ./gradlew testDebugUnitTest --tests '*CoachRenders*'`, with `DARK=1` for dark.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CoachRenders {
    @get:Rule
    val compose = createComposeRule()

    private val out = System.getenv("RENDERS")
    private val dark = System.getenv("DARK") == "1"

    private val library = LibrarySeed.parse(File("src/main/assets/exercises.json").readText())
    private val program = (ProgramParser.parse(File("src/main/assets/${StarterProgram.ASSET}").readText(), library.exercises.map { it.id }.toSet(), library.merged) as ProgramParser.Result.Valid).program
    private val today = LocalDate.of(2026, 10, 15)
    private val names = library.exercises.associate { it.id to it.name }
    private val gyms = listOf(Gym(1, "Harbour", emptyList()), Gym(2, "Itis", emptyList()))
    private val usual = UsualGym(1, UsualGym.Why.Weekday)

    private val hands = Change(
        "Hands need a week off grip work", Scope.Dates(today.minusDays(3), today.plusDays(12)),
        listOf(Operation.Swap("bench", "smith_machine_bench_press", "no grip")),
    )
    private val elbow = Change("Elbow: machine curls for the rest of Ramp-in", Scope.RestOfBlock, listOf(Operation.Swap("curl", "machine_preacher_curl")))

    private fun record(id: Long, change: Change) = ChangeRecord(
        AcceptedChange(id, change, today.minusDays(3), Position(1, 0), Position(1, 0), program.blockAt(1)!!.id), acceptedAt = id, revertedAt = null,
    )

    private val plain = ActiveProgram(1, program, Position(1, 1), today = today)
    private val withChanges = plain.copy(history = listOf(record(1, elbow), record(2, hands)))

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
    private fun Coach(
        program: ActiveProgram? = plain,
        words: String = "",
        replyReady: Boolean = false,
        errors: List<String>? = null,
    ) = CoachScreen(
        program, TextFieldValue(words, TextRange(words.length)), onWords = {}, onBack = {},
        gyms = gyms, usualGym = usual, replyReady = replyReady, pasteErrors = errors,
    )

    private val typed = "Left elbow aches on curls since Monday, about 3/10. Pressing is fine."

    @Test fun empty() = render("coach-01-empty") { Coach() }

    @Test fun inForce() = render("coach-02-in-force") { Coach(withChanges) }

    @Test fun words() = render("coach-03-words") { Coach(words = typed) }

    @Test fun asked() = render("coach-04-asked", before = { compose.onNodeWithText("Ask Claude").performClick() }) { Coach(words = typed) }

    @Test fun replyReady() = render("coach-05-reply-ready", before = { compose.onNodeWithText("Ask Claude").performClick() }) {
        Coach(words = typed, replyReady = true)
    }

    @Test fun badPaste() = render("coach-06-bad-paste") {
        Coach(words = typed, errors = listOf("operations[0].slot: unknown slot \"curls\"", "operations[1].percent: must be −50 to 50"))
    }

    @Test fun noProgram() = render("coach-07-no-program") { Coach(program = null, words = "My knee hurts on lunges.") }

    @Test fun review() = render("coach-08-review") {
        val diff = Changes.diff(program, plain.position, today, emptyList(), elbow)
        ChangeReviewScreen(ChangeReview(elbow, diff, program, program.blockAt(1)!!.name), names, onAccept = {}, onReject = {})
    }

    @Test fun easeBackIn() = render("coach-09-ease-back-in") { Coach(words = "I’ve had 11 days off. Ease me back in.") }

    @Test fun todayTimeOff() = render("today-time-off") {
        TodayScreen(
            onSettings = {}, sessionRunning = false, onResume = {}, onStart = { _, _, _ -> },
            program = plain, exerciseNames = names, library = library.exercises, lastTrained = today.minusDays(11),
        )
    }
}
