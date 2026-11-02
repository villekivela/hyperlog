package com.hyperlog.app.ui.mannequin

import android.content.Context
import android.view.View
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.mannequin.Mannequins
import com.hyperlog.app.mannequin.StageCamera
import com.hyperlog.app.ui.settings.SettingsScreen
import com.hyperlog.app.ui.settings.StartingWeightsStatus
import com.hyperlog.app.ui.theme.ClayColours
import com.hyperlog.app.ui.theme.HyperlogTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.ByteBuffer

/** Debug builds' gallery of every mannequin clip (SPEC, Technique videos → The mannequin). */
@RunWith(RobolectricTestRunner::class)
class MannequinGalleryTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val loaded = mutableListOf<Int>()
    private val factory = MannequinPlayers { c ->
        object : MannequinPlayer {
            override val view = View(c)
            override var onShown: () -> Unit = {}
            override fun load(glb: ByteBuffer, resources: Map<String, ByteBuffer>, camera: StageCamera) { loaded += glb.remaining() }
            override fun play(still: Boolean) {}
            override fun pause() {}
            override fun turn(degrees: Float) {}
            override fun resetView() {}
            override fun setColours(colours: ClayColours) {}
            override fun release() {}
        }
    }

    @Test
    fun theGalleryPlaysEveryClip() {
        val exercises = LibrarySeed.fromAssets(context).exercises
        compose.setContent {
            CompositionLocalProvider(LocalMannequinPlayers provides factory) {
                HyperlogTheme { MannequinGallery(Mannequins.fromAssets(context), exercises, onBack = {}) }
            }
        }
        // The clips come in the index's order, by exercise id.
        compose.onNodeWithText("Barbell Bench Press").assertExists()
        compose.onNodeWithText("1 of 8").assertExists()
        compose.onNodeWithTag(MannequinStageTag).assertExists()
        compose.waitUntil { loaded.isNotEmpty() }
        assertEquals(context.assets.open("mannequins/barbell_bench_press.glb").use { it.readBytes().size }, loaded.single())
    }

    private fun settings(onGallery: (() -> Unit)?) {
        compose.setContent {
            HyperlogTheme {
                SettingsScreen(
                    gymCount = 0, exerciseCount = 173, onGyms = {}, onLibrary = {}, onBack = {},
                    startingWeights = StartingWeightsStatus(count = 0, invalid = false), onImportStartingWeights = {},
                    onMannequinGallery = onGallery,
                )
            }
        }
    }

    @Test
    fun aDebugBuildOpensItFromSettings() {
        var opened = false
        settings { opened = true }
        compose.onNodeWithText("Mannequin gallery").performScrollTo().performClick()
        assertTrue(opened)
    }

    @Test
    fun aReleaseBuildHasNoGallery() {
        settings(null)
        compose.onAllNodesWithText("Mannequin gallery").assertCountEquals(0)
        compose.onAllNodesWithText("Debug").assertCountEquals(0)
    }
}
