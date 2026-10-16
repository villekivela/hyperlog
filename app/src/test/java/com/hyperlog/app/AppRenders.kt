package com.hyperlog.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.data.LoggedSetEntity
import com.hyperlog.app.data.SessionEntity
import com.hyperlog.app.data.SessionExerciseEntity
import com.hyperlog.app.update.BuildReleases
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * Renders of the whole app's screens that the screen-level renders (Session, Program, Coach,
 * Workout) don't cover: Today, Settings, the library, History, the ad-hoc session and Session done.
 * The whole bar is in `WholeBarRenders`.
 * Skipped unless asked for: `RENDERS=<dir> ./gradlew testDebugUnitTest --tests '*AppRenders*'`,
 * with `DARK=1` for dark.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppRenders {
    val compose = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabaseRule()).around(compose)

    private val out = System.getenv("RENDERS")
    private val dark = System.getenv("DARK") == "1"

    private fun launch(fakeRelease: String? = null) {
        assumeTrue(out != null)
        RuntimeEnvironment.setQualifiers(if (dark) "+night" else "+notnight")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, MainActivity::class.java).apply { fakeRelease?.let { putExtra(BuildReleases.FAKE_RELEASE, it) } }
        ActivityScenario.launch<MainActivity>(intent)
        compose.waitFor("Today")
    }

    private fun save(name: String) {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        val bitmap = compose.onAllNodes(isRoot())[0].captureToImage().asAndroidBitmap()
        File(out).mkdirs()
        File(out, "${if (dark) "dark" else "light"}-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val db get() = HyperlogDatabase.get(ApplicationProvider.getApplicationContext<Context>())

    private fun at(date: LocalDate) = date.atTime(18, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun session(date: LocalDate, exerciseId: String, vararg sets: Pair<Double, Int>) = runBlocking {
        val dao = db.sessions()
        val id = dao.insertSession(SessionEntity(startedAt = at(date), finishedAt = at(date) + 3_600_000))
        val exercise = dao.insertSessionExercise(SessionExerciseEntity(sessionId = id, exerciseId = exerciseId, position = 0))
        sets.forEachIndexed { i, (kg, reps) ->
            dao.insertSet(LoggedSetEntity(sessionExerciseId = exercise, position = i, weightKg = kg, fullReps = reps, partialReps = 0, rir = 1, loggedAt = at(date)))
        }
    }

    @Test
    fun todayStarter() {
        launch()
        save("today-starter")
    }

    @Test
    fun todayUpdateBanner() {
        launch(fakeRelease = "9.9.9")
        compose.waitFor("9.9.9", substring = true)
        save("today-update-banner")
    }

    @Test
    fun todayDay() {
        launch()
        compose.click("Run this program")
        compose.waitFor("Week 1 · Ramp-in")
        save("today-day")
    }

    @Test
    fun settings() {
        launch()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.waitFor("Exercise library")
        save("settings")
    }

    @Test
    fun library() {
        launch()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.click("Exercise library")
        compose.waitFor("Barbell Bench Press")
        save("library")
    }

    @Test
    fun exerciseEditor() {
        launch()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.click("Exercise library")
        compose.click("Barbell Bench Press")
        compose.waitFor("Save")
        save("exercise-editor")
    }

    @Test
    fun history() {
        launch()
        session(LocalDate.now().minusDays(5), "barbell_bench_press", 80.0 to 6, 80.0 to 5)
        session(LocalDate.now().minusDays(2), "barbell_bench_press", 82.5 to 6, 82.5 to 5)
        compose.openTab("History")
        compose.waitFor("Sessions")
        save("history")
    }

    @Test
    fun adhocAddExercise() {
        launch()
        compose.click("Start session")
        compose.waitFor("Add exercise")
        save("adhoc-add-exercise")
    }

    @Test
    fun sessionDone() {
        launch()
        compose.click("Start session")
        compose.click("Barbell Bench Press")
        compose.press("8", "0", "Next", "8", "RIR 1", "Log set")
        compose.press("More")
        compose.click("Finish session")
        compose.click("Finish")
        compose.waitFor("Session done")
        save("session-done")
    }
}
