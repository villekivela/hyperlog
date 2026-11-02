package com.hyperlog.app.ui.settings

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.coach.AboutMe
import com.hyperlog.app.coach.AgeBand
import com.hyperlog.app.coach.BodyArea
import com.hyperlog.app.coach.Progress
import com.hyperlog.app.coach.TrainingStyle
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.ui.theme.HyperlogTheme
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders of Settings → Training style in each state (`docs/design/training-style.md`), the whole
 * page on one tall image. Skipped unless asked for:
 * `RENDERS=<dir> ./gradlew testDebugUnitTest --tests '*TrainingStyleRenders*'`, with `DARK=1` for dark.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h4200dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TrainingStyleRenders {
    @get:Rule
    val compose = createComposeRule()

    private val out = System.getenv("RENDERS")
    private val dark = System.getenv("DARK") == "1"
    private val default = ApplicationProvider.getApplicationContext<android.content.Context>().assets.open("coach/how-i-train.md").bufferedReader().readText()

    private fun show(start: TrainingStyle, edited: Boolean = false) {
        assumeTrue(out != null)
        RuntimeEnvironment.setQualifiers(if (dark) "+night" else "+notnight")
        val library = LibrarySeed.fromAssets(ApplicationProvider.getApplicationContext()).exercises
        compose.setContent {
            var style by remember { mutableStateOf(start) }
            var own by remember { mutableStateOf(edited) }
            HyperlogTheme {
                TrainingStyleScreen(
                    style, own, library,
                    onTargets = { style = style.copy(targets = it) },
                    onAboutMe = { style = style.copy(aboutMe = it) },
                    onHowITrain = { style = style.copy(howITrain = it); own = true },
                    onDefaultHowITrain = { style = style.copy(howITrain = default); own = false },
                    onBack = {},
                )
            }
        }
    }

    private fun save(name: String, dialog: Boolean = false) {
        compose.waitForIdle()
        val node = if (dialog) compose.onAllNodes(isDialog())[0] else compose.onAllNodes(isRoot())[0]
        val bitmap = node.captureToImage().asAndroidBitmap()
        File(out!!).mkdirs()
        File(out, "${if (dark) "dark" else "light"}-training-style-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun defaults() {
        show(TrainingStyle(howITrain = default))
        save("defaults")
    }

    @Test
    fun customVolumeWrong() {
        show(TrainingStyle(howITrain = default))
        compose.onNodeWithText("Custom").performClick()
        val high = hasSetTextAction() and hasContentDescription("Volume, most sets")
        compose.onNode(high).performTextClearance()
        compose.onNode(high).performTextInput("50")
        save("custom-volume")
    }

    @Test
    fun howITrainEdited() {
        show(TrainingStyle(howITrain = "Heavy and simple. Three days a week, full body, and I stop a set when my form slips."), edited = true)
        save("edited")
        compose.onNodeWithText("Back to default").performScrollTo().performClick()
        save("default-dialog", dialog = true)
    }

    @Test
    fun unsaved() {
        show(TrainingStyle(howITrain = default))
        val field = hasSetTextAction() and hasContentDescription("How I train")
        compose.onNode(field).performScrollTo().performTextClearance()
        compose.onNode(field).performTextInput("Heavy and simple.")
        save("unsaved")
    }

    @Test
    fun aboutMeFilled() {
        show(
            TrainingStyle(
                howITrain = default,
                aboutMe = AboutMe(
                    age = AgeBand.Forties, progress = Progress.Months, comingBack = true,
                    aches = sortedMapOf(compareBy { it.ordinal }, BodyArea.Shoulders to "clicks on deep presses", BodyArea.Knees to "sore after lunges"),
                    avoid = listOf("barbell_squat"), favour = listOf("hack_squat", "bayesian_cable_curl"), anythingElse = "I train fasted in the mornings",
                ),
            ),
        )
        save("about-me")
    }

    @Test
    fun picker() {
        show(TrainingStyle(howITrain = default))
        compose.onNode(hasContentDescription("Add an exercise to avoid") or hasText("Add an exercise to avoid")).performScrollTo().performClick()
        compose.waitForIdle()
        save("picker", dialog = true)
    }
}
