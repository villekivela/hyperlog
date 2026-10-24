package com.hyperlog.app.ui.settings

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
 * Renders of Settings → Claude key in each state (`docs/design/settings.md`). Skipped unless asked
 * for: `RENDERS=<dir> ./gradlew testDebugUnitTest --tests '*ClaudeKeyRenders*'`, with `DARK=1` for dark.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClaudeKeyRenders {
    @get:Rule
    val compose = createComposeRule()

    private val out = System.getenv("RENDERS")
    private val dark = System.getenv("DARK") == "1"

    private fun show(status: ClaudeKeyStatus) {
        assumeTrue(out != null)
        RuntimeEnvironment.setQualifiers(if (dark) "+night" else "+notnight")
        compose.setContent {
            HyperlogTheme {
                SettingsScreen(
                    gymCount = 2, exerciseCount = 173, onGyms = {}, onLibrary = {}, onBack = {},
                    startingWeights = StartingWeightsStatus(count = 80, invalid = false), onImportStartingWeights = {},
                    version = "0.4.0",
                    claudeKey = status,
                )
            }
        }
    }

    private fun save(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onAllNodes(isRoot())[0].captureToImage().asAndroidBitmap()
        File(out!!).mkdirs()
        File(out, "${if (dark) "dark" else "light"}-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun typed(check: KeyCheck, name: String) {
        show(ClaudeKeyStatus(check = check))
        compose.onNodeWithText("Claude key").performClick()
        compose.onNodeWithText("API key").performTextInput("sk-ant-api03-a1b2")
        save(name)
    }

    @Test
    fun noKey() {
        show(ClaudeKeyStatus())
        save("claude-key-none")
    }

    @Test
    fun entering() = typed(KeyCheck.Idle, "claude-key-entering")

    @Test
    fun refused() = typed(KeyCheck.Refused, "claude-key-refused")

    @Test
    fun saved() {
        show(ClaudeKeyStatus(ending = "a1b2", monthCost = 2_400_000))
        save("claude-key-saved")
    }
}
