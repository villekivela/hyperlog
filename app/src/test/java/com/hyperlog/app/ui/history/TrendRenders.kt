package com.hyperlog.app.ui.history

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.history.ExerciseTrend
import com.hyperlog.app.history.TrendLine
import com.hyperlog.app.history.TrendPoint
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.GripDemand
import com.hyperlog.app.library.MovementPattern
import com.hyperlog.app.ui.theme.HyperlogTheme
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

/** An exercise's progress page, for the design note. Run with RENDERS=<dir>, DARK=1 for dark. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TrendRenders {
    @get:Rule
    val compose = createComposeRule()

    private val out = System.getenv("RENDERS")

    private val bench = Exercise("barbell_bench_press", "Barbell Bench Press", Equipment.Barbell, MovementPattern.HorizontalPush, GripDemand.Low, listOf("Chest"))

    @Test
    fun progress() {
        assumeTrue(out != null)
        val dark = System.getenv("DARK") == "1"
        RuntimeEnvironment.setQualifiers(if (dark) "+night" else "+notnight")
        val day = LocalDate.of(2026, 9, 1)
        val sets = listOf(
            LoggedSet(75.0, 8, 0, 3), LoggedSet(77.5, 8, 0, 2), LoggedSet(80.0, 6, 0, 1), LoggedSet(80.0, 5, 0, 0),
            LoggedSet(82.5, 5, 0, 1), LoggedSet(82.5, 6, 2, 0), LoggedSet(85.0, 4, 0, 0), LoggedSet(100.0, 3, 0, 2),
        )
        val points = sets.mapIndexed { i, s -> TrendPoint(i.toLong(), day.plusDays(4L * i), s, s.weightKg * (1 + s.fullReps / 30.0)) }
        compose.setContent {
            HyperlogTheme { ExerciseTrendScreen(ExerciseTrend(bench, listOf(TrendLine(null, null, points))), hasBodyweight = false, onOpenSession = {}, onBack = {}) }
        }
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(out).mkdirs()
        File(out, "${if (dark) "dark" else "light"}-exercise-progress.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
