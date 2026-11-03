package com.hyperlog.app.ui.mannequin

import android.content.Context
import android.view.View
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.mannequin.GripClip
import com.hyperlog.app.mannequin.Mannequins
import com.hyperlog.app.mannequin.RepTimeline
import com.hyperlog.app.mannequin.StageCamera
import com.hyperlog.app.ui.session.ExerciseSheet
import com.hyperlog.app.ui.theme.ClayColours
import com.hyperlog.app.ui.theme.HyperlogTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.ByteBuffer

/**
 * A 2-grip exercise's sheet (owner, #127): "Set 1 · Wide" and "Set 2 · Narrow" under its mannequin, open on the
 * grip of the set being done, set 1 wide and set 2 narrow.
 */
@RunWith(RobolectricTestRunner::class)
class GripButtonsTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val pulldown = LibrarySeed.fromAssets(context).exercises.single { it.id == "2_grip_lat_pulldown" }
    private val wideCamera = StageCamera(listOf(0f, 1.1f, 0.4f), 1.2f, 62f, 8f)
    private val narrowCamera = StageCamera(listOf(0f, 1.0f, 0.4f), 1.1f, 62f, 8f)

    // Two shipped clips stand in for the two grips; which loaded shows by its camera.
    private val shipped = Mannequins.fromAssets(context).clips.associateBy { it.exerciseId }
    private val clip = shipped.getValue("lat_pulldown").copy(
        exerciseId = pulldown.id, equipment = pulldown.equipment,
        grips = listOf(GripClip("wide", shipped.getValue("lat_pulldown").file, wideCamera), GripClip("narrow", shipped.getValue("close_grip_lat_pulldown").file, narrowCamera)),
    )

    private class FakePlayer(context: Context) : MannequinPlayer {
        override val view = View(context)
        var camera: StageCamera? = null
        override var onShown: () -> Unit = {}
        override fun load(glb: ByteBuffer, resources: Map<String, ByteBuffer>, camera: StageCamera, tempo: RepTimeline) { this.camera = camera }
        override fun play(still: Boolean) {}
        override fun pause() {}
        override fun turn(degrees: Float) {}
        override fun resetView() {}
        override fun setBench(degrees: Int, hinge: List<Float>) {}
        override fun setColours(colours: ClayColours) {}
        override fun release() {}
    }

    private val players = mutableListOf<FakePlayer>()

    private val lifecycle = object : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }

    private fun sheet(set: Int?) {
        compose.setContent {
            CompositionLocalProvider(LocalMannequinPlayers provides MannequinPlayers { FakePlayer(it).also(players::add) }, LocalLifecycleOwner provides lifecycle) {
                HyperlogTheme { ExerciseSheet(pulldown, machine = null, onChangeMachine = null, mannequin = clip, set = set) }
            }
        }
        compose.waitForIdle()
    }

    /** The camera of the clip the stage loaded, once it has: it reads the clip off the main thread. */
    private fun loaded(): StageCamera? {
        compose.waitUntil(5_000) { players.lastOrNull()?.camera != null }
        return players.last().camera
    }

    @Test
    fun theFirstSetOpensOnTheWideGrip() {
        sheet(set = 0)
        compose.onNodeWithText("Set 1 · Wide").assertIsOn()
        compose.onNodeWithText("Set 2 · Narrow").assertIsOff()
        assertEquals(wideCamera, loaded())
    }

    @Test
    fun theSecondSetOpensOnTheNarrowGrip() {
        sheet(set = 1)
        compose.onNodeWithText("Set 2 · Narrow").assertIsOn()
        assertEquals(narrowCamera, loaded())
    }

    @Test
    fun aTapShowsTheOtherGrip() {
        sheet(set = 1)
        compose.onNodeWithText("Set 1 · Wide").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Set 1 · Wide").assertIsOn()
        compose.waitUntil(5_000) { players.lastOrNull()?.camera == wideCamera }
    }

    @Test
    fun outsideASessionItOpensOnTheFirstGrip() {
        sheet(set = null)
        compose.onNodeWithText("Set 1 · Wide").assertIsOn()
    }
}
