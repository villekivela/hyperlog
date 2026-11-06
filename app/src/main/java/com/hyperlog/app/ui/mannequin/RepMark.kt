package com.hyperlog.app.ui.mannequin

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.hyperlog.app.R
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.mannequin.ExercisePoses
import com.hyperlog.app.mannequin.PoseShown
import com.hyperlog.app.mannequin.RepPose

/** The exercise library by id, so a mark can be drawn wherever only an exercise's id is known. */
internal val LocalExercises = staticCompositionLocalOf<Map<String, Exercise>> { emptyMap() }

internal const val RepMarkTag = "rep-mark"
internal const val RepMarkFallbackTag = "rep-mark-fallback"
internal const val RepStageTag = "rep-stage"

/** How much of a mark's edge stays clear, so a head or a foot never touches its tile. */
private const val Inset = 0.14f

/** The poses, read once however many rows draw one. */
private object Poses {
    private var loaded: ExercisePoses? = null

    fun of(context: Context): ExercisePoses = loaded ?: ExercisePoses.fromAssets(context).also { loaded = it }
}

/** A pose's joints as index pairs, worked out once, so drawing a row is a few lines and nothing else. */
private class RepFigure(pose: RepPose) {
    val start = pose.start
    val end = pose.end
    private val at = pose.joints.withIndex().associate { (i, name) -> name to i }

    private fun bones(vararg names: String) = names.map { at.getValue(it) }.toIntArray()

    private fun limbs(side: String) = bones(
        "spine_03", "upperarm_$side", "upperarm_$side", "lowerarm_$side", "lowerarm_$side", "hand_$side",
        "pelvis", "thigh_$side", "thigh_$side", "calf_$side", "calf_$side", "foot_$side",
    )

    val far = limbs(pose.far)
    val near = limbs(if (pose.far == "l") "r" else "l")
    val torso = bones("pelvis", "spine_03", "spine_03", "neck_01")
    val head = at.getValue("Head")
}

@Composable
private fun rememberFigure(exercise: Exercise?): Pair<RepFigure, Boolean>? {
    val context = LocalContext.current
    return remember(exercise) {
        exercise?.let { Poses.of(context).poseFor(it) }?.let { shown: PoseShown -> RepFigure(shown.pose) to shown.exact }
    }
}

/** [figure] on the canvas: its [points] in [ink], the far side fainter and the torso heavier. */
private fun DrawScope.drawPoints(figure: RepFigure, points: List<Int>, ink: Color) {
    val box = size.minDimension
    val inner = box * (1 - 2 * Inset)
    val left = (size.width - inner) / 2
    val top = (size.height - inner) / 2
    val stroke = box * 0.07f
    fun joint(i: Int) = Offset(left + points[i * 2] * inner / 1000f, top + points[i * 2 + 1] * inner / 1000f)
    fun bones(pairs: IntArray, color: Color, width: Float) {
        for (i in pairs.indices step 2) drawLine(color, joint(pairs[i]), joint(pairs[i + 1]), strokeWidth = width, cap = StrokeCap.Round)
    }
    bones(figure.far, ink.copy(alpha = ink.alpha * 0.45f), stroke)
    bones(figure.torso, ink, stroke * 1.5f)
    bones(figure.near, ink, stroke)
    drawCircle(ink, radius = stroke * 1.35f, center = joint(figure.head))
}

/** The rep: its end in full ink over its start as a ghost, so the mark shows how the lift moves. */
private fun DrawScope.drawRep(figure: RepFigure, ink: Color, ghost: Boolean) {
    if (ghost) drawPoints(figure, figure.start, ink.copy(alpha = 0.22f))
    drawPoints(figure, figure.end, ink)
}

/**
 * Hyperlog's signature (GLOSSARY, Rep mark): [exercise]'s rep on a tonal tile. One not yet posed
 * has its movement pattern's figure, muted and with no ghost, so it reads as a stand-in.
 */
@Composable
internal fun RepMark(exercise: Exercise?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val cs = MaterialTheme.colorScheme
    val figure = rememberFigure(exercise)
    val exact = figure?.second == true
    val ink = if (exact) cs.onSecondaryContainer else cs.onSurfaceVariant.copy(alpha = 0.6f)
    Canvas(
        // Its own layer: the mark is recorded once, and a list scrolling only moves it.
        modifier.size(size).graphicsLayer().clip(RoundedCornerShape(size * 0.3f)).background(cs.secondaryContainer)
            .testTag(if (exact) RepMarkTag else RepMarkFallbackTag),
    ) { figure?.let { drawRep(it.first, ink, ghost = exact) } }
}

/** [RepMark] for the exercise with this [id] in the library. */
@Composable
internal fun RepMark(id: String, modifier: Modifier = Modifier, size: Dp = 40.dp) = RepMark(LocalExercises.current[id], modifier, size)

/**
 * The band a short exercise leaves above its table: the rep, large, with its [cue]. A tap opens the
 * exercise. Under [StageMinimum] there isn't room for it to read, so nothing shows and the table
 * has the space.
 */
@Composable
internal fun RepStage(exercise: Exercise, cue: String?, onClick: () -> Unit) {
    val figure = rememberFigure(exercise) ?: return
    val cs = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxHeight < StageMinimum) return@BoxWithConstraints
        val figureSize = min(maxHeight - 16.dp, 200.dp)
        val details = stringResource(R.string.session_exercise_details)
        Row(
            Modifier.fillMaxSize().clickable(onClickLabel = details, onClick = onClick).padding(horizontal = 24.dp, vertical = 8.dp).testTag(RepStageTag),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val ink = if (figure.second) cs.onSurface else cs.onSurfaceVariant.copy(alpha = 0.6f)
            Canvas(Modifier.size(figureSize)) { drawRep(figure.first, ink, ghost = figure.second) }
            if (cue != null) {
                Spacer(Modifier.width(16.dp))
                Text(cue, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
        }
    }
}

private val StageMinimum = 96.dp
