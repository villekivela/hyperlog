package com.hyperlog.app.ui.coach

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.hyperlog.app.coach.CheckIn
import com.hyperlog.app.coach.CheckInFailure
import com.hyperlog.app.coach.CheckInFrame
import com.hyperlog.app.coach.CheckInStep
import com.hyperlog.app.coach.CheckInView
import com.hyperlog.app.coach.Tile
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

/**
 * Renders of the check-in's failures and problems for docs/design/coach.md, which the scripted
 * coach doesn't play. Skipped unless asked for:
 * `RENDERS=<dir> ./gradlew testDebugUnitTest --tests '*CheckInRenders*'`, with `DARK=1` for dark.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CheckInRenders {
    @get:Rule
    val compose = createComposeRule()

    private val out = System.getenv("RENDERS")
    private val dark = System.getenv("DARK") == "1"

    private fun render(name: String, content: @Composable () -> Unit) {
        assumeTrue(out != null)
        RuntimeEnvironment.setQualifiers(if (dark) "+night" else "+notnight")
        compose.setContent { HyperlogTheme { content() } }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(out).mkdirs()
        File(out, "${if (dark) "dark" else "light"}-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val tired = CheckIn(Tile.Tired.words, Tile.Tired, CheckInFrame("Context", emptyList()))

    @Composable
    private fun Step(step: CheckInStep, pasteErrors: List<String>? = null) = CheckInScreen(
        CheckInView(tired, step), null,
        onTile = {}, onAnswer = {}, onLeave = {}, onBack = {}, onClose = {}, onTellMeMore = {}, onTryAgain = {},
        pasteErrors = pasteErrors,
    )

    @Test fun problems() = render("checkin-08-problems") {
        Step(
            CheckInStep.Problems(
                listOf(
                    "operations[0].slot: No slot \"no_such_slot\" in the program.",
                    "operations[0].exercise: No exercise \"no_such_exercise\" in the library.",
                ),
            ),
        )
    }

    @Test fun noConnection() = render("checkin-09-no-connection") { Step(CheckInStep.Failed(CheckInFailure.NoConnection)) }

    @Test fun keyRefused() = render("checkin-10-key-refused") { Step(CheckInStep.Failed(CheckInFailure.KeyRefused)) }

    @Test fun spendLimit() = render("checkin-11-spend-limit") { Step(CheckInStep.Failed(CheckInFailure.SpendLimit)) }

    @Test fun declined() = render("checkin-12-declined") { Step(CheckInStep.Failed(CheckInFailure.Refused)) }

    @Test fun badPaste() = render("checkin-13-bad-paste") {
        Step(
            CheckInStep.Failed(CheckInFailure.KeyRefused),
            pasteErrors = listOf("operations[0].rir: 6 is more than 5.", "scope.to: Before scope.from."),
        )
    }
}
