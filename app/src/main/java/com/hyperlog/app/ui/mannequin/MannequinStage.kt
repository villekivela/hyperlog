package com.hyperlog.app.ui.mannequin

import android.content.Context
import android.provider.Settings
import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.hyperlog.app.mannequin.MannequinClip
import com.hyperlog.app.mannequin.Mannequins
import com.hyperlog.app.mannequin.StageCamera
import com.hyperlog.app.ui.session.isDarkSurface
import com.hyperlog.app.ui.theme.CardShape
import com.hyperlog.app.ui.theme.ClayColours
import com.hyperlog.app.ui.theme.Tones
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal const val MannequinStageTag = "mannequin"

/**
 * The stage's width to its height. At the cards' margin in the sheet this keeps the height (and so
 * the mannequin's size) of the 4:3.4 stage the prototype drew inside the text margin.
 */
private const val StageAspect = 5f / 4f

/** How far a horizontal drag turns the mannequin, in degrees per dp. */
private const val DegreesPerDp = 0.5f

/**
 * What draws the mannequin into [view]: Filament on the phone ([FilamentPlayer]), a fake in tests,
 * which leave the pixels to the owner (SPEC, Technique videos → The mannequin). Every call comes
 * from the main thread.
 */
internal interface MannequinPlayer {
    val view: View

    /** Called once the loaded clip's first frame is on screen. */
    var onShown: () -> Unit

    /**
     * Loads a clip's `.glb` (a direct buffer), with the files it reads by name ([resources], each a
     * direct buffer), and frames the stage on [camera].
     */
    fun load(glb: ByteBuffer, resources: Map<String, ByteBuffer>, camera: StageCamera)

    /** Draws frames, looping the rep; [still] holds it at the bottom instead. */
    fun play(still: Boolean)

    /** Stops drawing frames. */
    fun pause()

    /** Turns the mannequin about its upright axis; positive turns its front to the right. */
    fun turn(degrees: Float)

    /** Back to the turn the clip opens on. */
    fun resetView()

    fun setColours(colours: ClayColours)

    /** Releases the engine. The player is not used again. */
    fun release()
}

internal fun interface MannequinPlayers {
    fun create(context: Context): MannequinPlayer
}

/** Makes the stage's player: Filament, unless a test provides another. */
internal val LocalMannequinPlayers = staticCompositionLocalOf<MannequinPlayers> { MannequinPlayers(::FilamentPlayer) }

/**
 * The exercise's mannequin doing one rep in a loop, on a stage the width of the sheet. A
 * horizontal drag turns it and a double-tap turns it back; vertical drags go on to the sheet. With
 * animations removed it holds still at the bottom of the rep. The stage shows while the clip
 * loads, then the mannequin fades in. It draws only while the app is in front, and its engine is
 * released when the stage leaves the screen with its sheet.
 */
@Composable
internal fun MannequinStage(clip: MannequinClip, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val players = LocalMannequinPlayers.current
    // Where the renderer can't start (its native library missing), the sheet shows as it does without a clip.
    val player = remember(players) {
        try {
            players.create(context)
        } catch (_: LinkageError) {
            null
        }
    } ?: return
    DisposableEffect(player) { onDispose { player.release() } }
    val still = remember { animationsRemoved(context) }
    var shown by remember(player, clip) { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(durationMillis = 300), label = "mannequin")
    val colours = if (isDarkSurface()) ClayColours.Dark else ClayColours.Light
    LaunchedEffect(player, clip) {
        player.onShown = { shown = true }
        val (glb, resources) = withContext(Dispatchers.IO) {
            context.readAsset(clip.file) to clip.resources.mapValues { (_, file) -> context.readAsset(file) }
        }
        player.load(glb, resources, clip.camera)
    }
    LaunchedEffect(player, colours) { player.setColours(colours) }
    LifecycleResumeEffect(player, still) {
        player.play(still)
        onPauseOrDispose { player.pause() }
    }
    Box(
        modifier
            .testTag(MannequinStageTag)
            .fillMaxWidth()
            .aspectRatio(StageAspect)
            .clip(CardShape)
            .background(Tones.sheetCard)
            // Only horizontal drags: a vertical one goes on to the sheet, which closes on it.
            .pointerInput(player) {
                detectHorizontalDragGestures { change, dx ->
                    change.consume()
                    player.turn(dx.toDp().value * DegreesPerDp)
                }
            }
            .pointerInput(player) { detectTapGestures(onDoubleTap = { player.resetView() }) },
    ) {
        AndroidView({ player.view }, Modifier.matchParentSize().graphicsLayer { this.alpha = alpha })
    }
}

/** An asset's bytes in a direct buffer, as Filament reads them. */
private fun Context.readAsset(file: String): ByteBuffer {
    val bytes = assets.open(file).use { it.readBytes() }
    return ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).apply { flip() }
}

/** The shipped clips, read from the assets once the caller is first composed; null until then. */
@Composable
internal fun rememberMannequins(): Mannequins? {
    val context = LocalContext.current
    return produceState<Mannequins?>(null) { value = withContext(Dispatchers.IO) { Mannequins.fromAssets(context) } }.value
}

/** Whether the system's animations are off (Settings → Accessibility → Remove animations). */
private fun animationsRemoved(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
