package com.hyperlog.app.ui.session

import android.graphics.Bitmap
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.hyperlog.app.domain.LadderSpec
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
 * Renders of the whole bar and the whole machine, each on its own, for docs/design/logging-screen.md.
 * Skipped unless asked for: `RENDERS=<dir> ./gradlew testDebugUnitTest --tests '*WholeBarRenders*'`,
 * with `DARK=1` for dark.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WholeBarRenders {
    @get:Rule
    val compose = createComposeRule()

    private val out = System.getenv("RENDERS")

    private fun render(name: String, plates: LadderSpec.Plates, kg: Double, machine: Boolean) {
        assumeTrue(out != null)
        val dark = System.getenv("DARK") == "1"
        RuntimeEnvironment.setQualifiers(if (dark) "+night" else "+notnight")
        compose.setContent {
            HyperlogTheme {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    WholeBar(plates, kg, Modifier.padding(24.dp), machine = machine)
                }
            }
        }
        compose.waitForIdle()
        val bitmap = compose.onAllNodes(isRoot())[0].captureToImage().asAndroidBitmap()
        File(out).mkdirs()
        File(out, "${if (dark) "dark" else "light"}-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun wholeBar() = render("whole-bar", LadderSpec.DefaultPlates, 100.0, machine = false)

    @Test
    fun wholeMachine() = render("whole-machine", LadderSpec.Plates(30.0, listOf(20.0, 10.0)), 90.0, machine = true)
}
