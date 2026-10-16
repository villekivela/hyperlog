package com.hyperlog.app.ui.session

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
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

/** A specimen of the RIR chip at the app's text sizes, for judging it. Run with RENDERS=<dir>. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RirChipRenders {
    @get:Rule
    val compose = createComposeRule()

    private val out = System.getenv("RENDERS")

    private fun render(dark: Boolean) {
        RuntimeEnvironment.setQualifiers(if (dark) "+night" else "+notnight")
        compose.setContent {
            HyperlogTheme {
                val t = MaterialTheme.typography
                val c = MaterialTheme.colorScheme
                Surface(color = c.surfaceContainer) { Column(Modifier.background(c.surfaceContainer).padding(12.dp).background(c.surfaceContainerLowest).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RirText("1 × 4–6 @ 1 · 2 × 6–8 @ 1", target = true, style = t.bodyMedium, color = c.onSurfaceVariant)
                    RirText("1 × 8–10 @ 0 · 3 × 10–12 @ 3 · 2 × 12–15 @ 10", target = true, style = t.bodyMedium, color = c.onSurfaceVariant)
                    RirText("82.5 × 8 @ 1 · 80 × 8 @ 2 · 60 × 12 @ 0 · 40 × 15 @ 4 · 20 × 20 @ 12", style = t.bodyMedium)
                    RirText("Every set: @ 2", target = true, style = t.bodyLarge)
                    RirText("Last time 97.5 kg × 6 @ 1, top of 4–6 → 100 kg", style = t.bodyLarge)
                    RirText("Set 2 was @ 0, target @ 1. Keep the weight and aim for 8 clean reps.", style = t.bodyLarge, modifier = Modifier.width(300.dp))
                    RirText("Tue 13 Oct · top set 82.5 × 8 @ 1 · 107 kg", style = t.bodyMedium, color = c.onSurfaceVariant)
                } }
            }
        }
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(out).mkdirs()
        File(out, "${if (dark) "dark" else "light"}-rir-chips.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun light() { assumeTrue(out != null); render(dark = false) }

    @Test
    fun dark() { assumeTrue(out != null); render(dark = true) }
}
