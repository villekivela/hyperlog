package com.hyperlog.app.ui.mannequin

import android.content.Context
import android.provider.Settings
import android.view.View
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeRight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.mannequin.MannequinClip
import com.hyperlog.app.mannequin.Mannequins
import com.hyperlog.app.mannequin.RepTimeline
import com.hyperlog.app.mannequin.StageCamera
import com.hyperlog.app.program.RepRange
import com.hyperlog.app.program.SetPrescription
import com.hyperlog.app.ui.session.ExerciseSheet
import com.hyperlog.app.ui.theme.ClayColours
import com.hyperlog.app.ui.theme.HyperlogTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer

/**
 * The mannequin at the top of the prescription sheet (SPEC, Technique videos → The mannequin),
 * with a fake player standing in for Filament: the pixels are the owner's to check.
 */
@RunWith(RobolectricTestRunner::class)
class MannequinStageTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val library = LibrarySeed.fromAssets(context).exercises.associateBy { it.id }
    private val mannequins = Mannequins.fromAssets(context)
    private val squat = library.getValue("barbell_squat")

    private class FakePlayer(context: Context) : MannequinPlayer {
        override val view = View(context)
        var loaded: Pair<Int, StageCamera>? = null
        var resources: Map<String, Int>? = null
        var tempo: RepTimeline? = null
        var turned = 0f
        var resets = 0
        var playing = false
        var still: Boolean? = null
        var released = false
        override var onShown: () -> Unit = {}
        override fun load(glb: ByteBuffer, resources: Map<String, ByteBuffer>, camera: StageCamera, tempo: RepTimeline) {
            loaded = glb.remaining() to camera
            this.tempo = tempo
            this.resources = resources.mapValues { it.value.remaining() }
        }
        override fun play(still: Boolean) { playing = true; this.still = still }
        override fun pause() { playing = false }
        override fun turn(degrees: Float) { turned += degrees }
        override fun resetView() { resets++ }
        override fun setColours(colours: ClayColours) {}
        override fun release() { released = true }
    }

    private val players = mutableListOf<FakePlayer>()
    private val factory = MannequinPlayers { FakePlayer(it).also(players::add) }

    private val lifecycle = object : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }

    private fun sheet(clip: MannequinClip?) {
        compose.setContent {
            CompositionLocalProvider(LocalMannequinPlayers provides factory, LocalLifecycleOwner provides lifecycle) {
                HyperlogTheme { ExerciseSheet(squat, machine = null, onChangeMachine = null, mannequin = clip) }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun theSquatsSheetPlaysItsClipAtTheTop() {
        sheet(mannequins.clipFor(squat))
        val stage = compose.onNodeWithTag(MannequinStageTag).getBoundsInRoot()
        val name = compose.onNodeWithText("Barbell Back Squat").getBoundsInRoot()
        assertTrue("the stage sits above the name", stage.bottom <= name.top)
        compose.waitUntil { players.single().loaded != null }
        val glb = context.assets.open("mannequins/barbell_squat.glb").use { it.readBytes().size }
        assertEquals(glb to mannequins.clipFor(squat)!!.camera, players.single().loaded)
        val shared = context.assets.open("mannequins/mannequin.bin").use { it.readBytes().size }
        assertEquals(mapOf("mannequin.bin" to shared), players.single().resources)
        assertEquals(mannequins.clipFor(squat)!!.tempo, players.single().tempo)
        assertTrue(players.single().playing)
        assertEquals(false, players.single().still)
    }

    @Test
    fun anExerciseWithoutAClipShowsTheSheetAsBefore() {
        sheet(null)
        compose.onAllNodesWithTag(MannequinStageTag).assertCountEquals(0)
        assertTrue(players.isEmpty())
    }

    @Test
    fun whereTheRendererCantStartTheSheetShowsAsWithoutAClip() {
        // As on the JVM, where Filament's native library isn't there.
        val missing = MannequinPlayers { throw UnsatisfiedLinkError("no filament-jni in java.library.path") }
        compose.setContent {
            CompositionLocalProvider(LocalMannequinPlayers provides missing, LocalLifecycleOwner provides lifecycle) {
                HyperlogTheme { ExerciseSheet(squat, machine = null, onChangeMachine = null, mannequin = mannequins.clipFor(squat)) }
            }
        }
        compose.onNodeWithText("Barbell Back Squat").assertIsDisplayed()
        compose.onAllNodesWithTag(MannequinStageTag).assertCountEquals(0)
    }

    @Test
    fun aHorizontalDragTurnsItAndAVerticalOneIsLeftToTheSheet() {
        var sheetDragged = 0f
        compose.setContent {
            CompositionLocalProvider(LocalMannequinPlayers provides factory, LocalLifecycleOwner provides lifecycle) {
                HyperlogTheme {
                    Box(Modifier.draggable(rememberDraggableState { sheetDragged += it }, Orientation.Vertical)) {
                        MannequinStage(mannequins.clipFor(squat)!!)
                    }
                }
            }
        }
        compose.onNodeWithTag(MannequinStageTag).performTouchInput { swipeDown() }
        assertEquals(0f, players.single().turned)
        assertTrue("the sheet takes the vertical drag", sheetDragged > 0f)
        compose.onNodeWithTag(MannequinStageTag).performTouchInput { swipeRight() }
        assertTrue("a drag to the right turns it", players.single().turned > 0f)
    }

    private val longPlan = List(8) { SetPrescription(RepRange(6, 8), 2, 180) }

    @Test
    fun aSheetTallerThanTheRoomScrollsToItsVideoButton() {
        compose.setContent {
            CompositionLocalProvider(LocalMannequinPlayers provides factory, LocalLifecycleOwner provides lifecycle) {
                HyperlogTheme {
                    Box(Modifier.height(600.dp)) {
                        ExerciseSheet(squat, machine = null, onChangeMachine = null, plan = longPlan, mannequin = mannequins.clipFor(squat))
                    }
                }
            }
        }
        compose.onNodeWithText("Technique video").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w411dp-h914dp")
    fun aSheetThatFitsLeavesAVerticalDragToTheSheet() {
        var sheetDragged = 0f
        compose.setContent {
            CompositionLocalProvider(LocalMannequinPlayers provides factory, LocalLifecycleOwner provides lifecycle) {
                HyperlogTheme {
                    Box(Modifier.draggable(rememberDraggableState { sheetDragged += it }, Orientation.Vertical)) {
                        ExerciseSheet(squat, machine = null, onChangeMachine = null, mannequin = mannequins.clipFor(squat))
                    }
                }
            }
        }
        compose.onNodeWithText("Barbell Back Squat").performTouchInput { swipeDown() }
        assertTrue("the sheet takes the drag", sheetDragged > 0f)
    }

    @Test
    fun aDoubleTapResetsTheView() {
        sheet(mannequins.clipFor(squat))
        compose.onNodeWithTag(MannequinStageTag).performTouchInput { doubleClick() }
        assertEquals(1, players.single().resets)
    }

    @Test
    fun withAnimationsRemovedItHoldsStill() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        sheet(mannequins.clipFor(squat))
        assertEquals(true, players.single().still)
    }

    @Test
    fun itPlaysOnlyWhileTheAppIsInFrontAndReleasesTheEngineWhenTheSheetCloses() {
        var open by mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(LocalMannequinPlayers provides factory, LocalLifecycleOwner provides lifecycle) {
                HyperlogTheme { if (open) MannequinStage(mannequins.clipFor(squat)!!) }
            }
        }
        compose.waitForIdle()
        val player = players.single()
        assertTrue(player.playing)
        compose.runOnIdle { lifecycle.registry.currentState = Lifecycle.State.CREATED }
        compose.waitForIdle()
        assertFalse("paused behind another app", player.playing)
        compose.runOnIdle { lifecycle.registry.currentState = Lifecycle.State.RESUMED }
        compose.waitForIdle()
        assertTrue("playing again in front", player.playing)
        assertFalse(player.released)
        open = false
        compose.waitForIdle()
        assertTrue("the engine is released with the sheet", player.released)
    }
}
