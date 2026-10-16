package com.hyperlog.app.ui.program

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.hyperlog.app.coach.AcceptedChange
import com.hyperlog.app.coach.Change
import com.hyperlog.app.coach.Operation
import com.hyperlog.app.coach.Scope
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.ChangeRecord
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.ProgramParser
import com.hyperlog.app.program.StarterProgram
import com.hyperlog.app.session.ExerciseSummary
import com.hyperlog.app.session.PastSessionItem
import com.hyperlog.app.ui.theme.HyperlogTheme
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
 * Renders of Program's states for docs/design/program-and-today.md, on the starter program.
 * Skipped unless asked for: `RENDERS=<dir> ./gradlew testDebugUnitTest --tests '*ProgramRenders*'`,
 * with `DARK=1` for dark.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProgramRenders {
    @get:Rule
    val compose = createComposeRule()

    private val out = System.getenv("RENDERS")
    private val dark = System.getenv("DARK") == "1"

    private val library = LibrarySeed.parse(File("src/main/assets/exercises.json").readText())
    private val program = (ProgramParser.parse(File("src/main/assets/${StarterProgram.ASSET}").readText(), library.exercises.map { it.id }.toSet(), library.merged) as ProgramParser.Result.Valid).program
    private val today = LocalDate.of(2026, 10, 15)
    private val names = library.exercises.associate { it.id to it.name }

    private val hands = Change(
        "Hands need a week off grip work", Scope.Dates(today.minusDays(3), today.plusDays(11)),
        listOf(Operation.Swap("bench", "smith_machine_bench_press", "no grip")),
    )
    private val elbow = Change("Elbow: machine curls for the rest of Build", Scope.RestOfBlock, listOf(Operation.Swap("curl", "machine_preacher_curl")))

    private fun record(id: Long, change: Change, revertedAt: Long? = null) = ChangeRecord(
        AcceptedChange(id, change, today.minusDays(3), Position(3, 0), Position(3, 0), program.blockAt(3)!!.id),
        acceptedAt = id, revertedAt = revertedAt,
    )

    private fun at(daysAgo: Long) = today.minusDays(daysAgo).atTime(17, 30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun session(id: Long, position: Position, daysAgo: Long, vararg sets: Pair<String, LoggedSet>) =
        PastSessionItem(
            id, at(daysAgo), at(daysAgo) + 3_600_000, program.plannedDay(position)!!.day.name, "Harbour",
            sets.map { (name, set) -> ExerciseSummary(name, listOf(set)) }, programId = 1, position = position,
        )

    /** Week 3 trained on A and C, B skipped; week 4's Day A trained, so Day B is next. */
    private val sessions = listOf(
        session(
            1, Position(3, 0), 9, "Barbell Bench Press" to LoggedSet(82.5, 5, 0, 1), "Hack Squat" to LoggedSet(120.0, 7, 0, 1),
            "Lat Pulldown" to LoggedSet(72.5, 8, 0, 1), "Cable Lateral Raise" to LoggedSet(10.0, 12, 0, 0),
        ),
        session(
            2, Position(3, 2), 5, "Barbell Bench Press" to LoggedSet(72.5, 9, 0, 1), "Leg Press" to LoggedSet(200.0, 11, 0, 1),
            "Lat Pulldown" to LoggedSet(70.0, 10, 0, 1),
        ),
        session(
            3, Position(4, 0), 2, "Barbell Bench Press" to LoggedSet(85.0, 5, 0, 1), "Hack Squat" to LoggedSet(122.5, 7, 0, 1),
            "Lat Pulldown" to LoggedSet(75.0, 8, 0, 1), "Cable Lateral Raise" to LoggedSet(10.0, 13, 0, 0),
        ),
    )

    private val running = ActiveProgram(1, program, Position(4, 1), today = today, history = listOf(record(1, elbow)))

    private fun render(name: String, before: () -> Unit = {}, content: @Composable () -> Unit) {
        assumeTrue(out != null)
        RuntimeEnvironment.setQualifiers(if (dark) "+night" else "+notnight")
        compose.setContent { HyperlogTheme { content() } }
        compose.waitForIdle()
        before()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        val bitmap = screen()
        File(out).mkdirs()
        File(out, "${if (dark) "dark" else "light"}-program-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /**
     * Every window drawn in order at its place, so a menu shows over the screen (a popup is a window
     * of its own, which a capture of one root leaves out).
     */
    private fun screen(): Bitmap {
        val main = compose.onAllNodes(isRoot())[0].captureToImage().asAndroidBitmap()
        val out = main.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(out)
        val global = Class.forName("android.view.WindowManagerGlobal")
        val instance = global.getMethod("getInstance").invoke(null)
        @Suppress("UNCHECKED_CAST")
        val views = global.getDeclaredField("mViews").apply { isAccessible = true }.get(instance) as List<android.view.View>
        views.drop(1).forEach { view ->
            val at = IntArray(2).also { view.getLocationOnScreen(it) }
            canvas.save()
            canvas.translate(at[0].toFloat(), at[1].toFloat())
            view.draw(canvas)
            canvas.restore()
        }
        return out
    }

    @Composable
    private fun Program(active: ActiveProgram = running) {
        ProgramScreen(active, null, onImport = {}, onCloseErrors = {}, onBack = {}, exerciseNames = names, sessions = sessions, starter = program)
    }

    private fun scrollTo(text: String) {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text, substring = true))
    }

    @Test fun current() = render("current") { Program() }

    @Test fun pastWeek() = render("past-week", before = {
        compose.onNodeWithText("3").performScrollTo().performClick()
        scrollTo("Skipped")
    }) { Program() }

    @Test fun futureBlock() = render("future-block", before = { compose.onNodeWithText("Intensify").performClick() }) { Program() }

    @Test fun deload() = render("deload", before = {
        compose.onNodeWithText("Deload + test").performClick()
        scrollTo("Day A — Heavy bench, squat, pulldown")
    }) { Program() }

    @Test fun ends() = render("ends", before = { scrollTo("Ends ") }) {
        Program(running.copy(position = Position(4, 0), history = listOf(record(1, elbow), record(2, hands))))
    }

    @Test fun menu() = render("menu", before = { compose.onNodeWithContentDescription("More").performClick() }) { Program() }

    @Test fun changes() = render("changes", before = { compose.onNodeWithText("Changes · ", substring = true).performClick() }) {
        val replacedOn = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val ended = record(2, hands, revertedAt = replacedOn)
        val ending = record(3, Change("Hands feel fine: back to the plan", Scope.RestOfProgram, emptyList(), replaces = listOf(2)))
        val lighter = record(4, Change("Lighter close-grip bench", Scope.RestOfProgram, listOf(Operation.AdjustLoad("close_grip_bench", -10, "elbow"))), revertedAt = replacedOn)
        Program(running.copy(history = listOf(record(1, elbow), ended, ending, lighter)))
    }

    @Test fun finished() = render("finished") { Program(running.copy(position = Position(program.weeks + 1, 0))) }
}
