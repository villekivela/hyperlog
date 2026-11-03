package com.hyperlog.app.ui.mannequin

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.mannequin.GripClip
import com.hyperlog.app.mannequin.Mannequins
import com.hyperlog.app.mannequin.RepTimeline
import com.hyperlog.app.mannequin.StageCamera
import com.hyperlog.app.ui.session.ExerciseSheet
import com.hyperlog.app.ui.theme.ClayColours
import com.hyperlog.app.ui.theme.HyperlogTheme
import java.io.File
import java.nio.ByteBuffer
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders of a 2-grip exercise's sheet with its Wide and Narrow buttons, for the design note. The
 * mannequin itself is Filament's and shows on the phone; here its stage is the empty card.
 * Skipped unless asked for: `RENDERS=<dir> ./gradlew testDebugUnitTest --tests '*GripRenders*'`,
 * with `DARK=1` for dark.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GripRenders {
    @get:Rule
    val compose = createComposeRule()

    private val out = System.getenv("RENDERS")
    private val dark = System.getenv("DARK") == "1"
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val pulldown = LibrarySeed.fromAssets(context).exercises.single { it.id == "2_grip_lat_pulldown" }
    private val shipped = Mannequins.fromAssets(context).clips.associateBy { it.exerciseId }
    private val clip = shipped.getValue("lat_pulldown").let { lat ->
        lat.copy(exerciseId = pulldown.id, equipment = pulldown.equipment, grips = listOf(GripClip("wide", lat.file, lat.camera), GripClip("narrow", lat.file, lat.camera)))
    }

    private class Blank(context: Context) : MannequinPlayer {
        override val view = View(context)
        override var onShown: () -> Unit = {}
        override fun load(glb: ByteBuffer, resources: Map<String, ByteBuffer>, camera: StageCamera, tempo: RepTimeline) {}
        override fun play(still: Boolean) {}
        override fun pause() {}
        override fun turn(degrees: Float) {}
        override fun resetView() {}
        override fun setBench(degrees: Int, hinge: List<Float>) {}
        override fun setColours(colours: ClayColours) {}
        override fun release() {}
    }

    private fun render(name: String, set: Int) {
        assumeTrue(out != null)
        RuntimeEnvironment.setQualifiers(if (dark) "+night" else "+notnight")
        compose.setContent {
            CompositionLocalProvider(LocalMannequinPlayers provides MannequinPlayers { Blank(it) }) {
                HyperlogTheme {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
                        Box(Modifier.padding(top = 32.dp)) { ExerciseSheet(pulldown, machine = null, onChangeMachine = null, mannequin = clip, set = set) }
                    }
                }
            }
        }
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(out).mkdirs()
        File(out, "${if (dark) "dark" else "light"}-grip-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun firstSet() = render("set-1-wide", 0)
    @Test fun secondSet() = render("set-2-narrow", 1)
}
