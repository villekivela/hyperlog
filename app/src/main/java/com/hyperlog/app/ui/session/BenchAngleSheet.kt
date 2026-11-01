package com.hyperlog.app.ui.session

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.domain.BenchAngle
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon
import com.hyperlog.app.ui.theme.TextEdge
import com.hyperlog.app.ui.theme.figureDisplay
import com.hyperlog.app.ui.theme.figureLarge

internal const val BenchSheetTag = "bench-sheet"

/** The phone's tilt from flat, whole degrees; null until the sensor reads. [available] is false with no sensor to read. */
internal class Tilt(val reading: Int?, val available: Boolean)

/**
 * Reads the phone's tilt while it is on screen: the gravity sensor, else the accelerometer, its
 * vector smoothed so the number holds still on a bench.
 */
@Composable
internal fun rememberTilt(): Tilt {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager? }
    val sensor = remember { manager?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    var reading by remember { mutableStateOf<Int?>(null) }
    DisposableEffect(sensor) {
        val smooth = FloatArray(3)
        var started = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                for (i in 0..2) smooth[i] = if (started) smooth[i] + SMOOTHING * (event.values[i] - smooth[i]) else event.values[i]
                started = true
                reading = BenchAngle.tilt(smooth[0], smooth[1], smooth[2]) ?: reading
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) manager?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { manager?.unregisterListener(listener) }
    }
    return Tilt(reading, sensor != null)
}

/** How much of each new reading the smoothed vector takes, as the old app did. */
private const val SMOOTHING = 0.18f

/**
 * [BenchAngleSheet] for an exercise whose angle in its session is [angle] and whose last session's was
 * [last]. In a running session ([inSession]) it reads the phone's tilt, and once an angle is set
 * today that angle is the target. A past session's sheet is typed and compares with the session
 * before it, starting from [angle].
 */
@Composable
internal fun BenchAngleSheetFor(name: String, last: Int?, angle: Int?, inSession: Boolean, onSave: (Int) -> Unit, onClose: () -> Unit) {
    val tilt = if (inSession) rememberTilt() else Tilt(null, available = false)
    val againstToday = inSession && angle != null
    BenchAngleSheet(
        name, target = if (againstToday) angle else last, againstToday = againstToday, start = angle, reading = tilt.reading,
        measuring = inSession && tilt.available, onSave = onSave, onClose = onClose,
    )
}

/**
 * The bench angle's sheet (SPEC, Today → Bench angle): a padded bench side on, its backrest at the
 * angle with the phone on it and [target]'s backrest dashed behind; the angle; how far it is from
 * [target] (the angle set today when [againstToday], else last time's); a dot on a ±15° line; and the logging pad's
 * keys. While [measuring], the live [reading] fills the number until a key is typed; otherwise
 * [start], the angle already set. − and + step what shows by a degree. ✓ saves what shows.
 */
@Composable
internal fun BenchAngleSheet(
    name: String,
    target: Int?,
    againstToday: Boolean,
    start: Int?,
    reading: Int?,
    measuring: Boolean,
    onSave: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var typed by remember { mutableStateOf("") }
    val value = when {
        typed.isNotEmpty() -> BenchAngle.typed(typed)
        measuring -> reading
        else -> start
    }
    val tooSteep = typed.isNotEmpty() && value == null
    // A typed angle past 90 still draws, held at 90, so the backrest never vanishes.
    val drawn = value ?: typed.toIntOrNull()?.coerceIn(0, BenchAngle.MAX)
    val near = value != null && target != null && BenchAngle.near(value, target)
    val onTarget = onTargetColor()
    val ink = when {
        tooSteep -> cs.error
        near -> onTarget
        else -> cs.onSurface
    }
    // How far from the target, large enough to read from the pin; with no angle, the target itself.
    val gap = target?.let { t -> value?.let { offTarget(it, t, againstToday) } ?: targetOnly(t, againstToday) }
    val source = when {
        tooSteep -> stringResource(R.string.bench_too_steep, BenchAngle.MAX)
        typed.isNotEmpty() -> stringResource(R.string.bench_typed)
        measuring && reading != null -> stringResource(R.string.bench_from_phone)
        measuring -> stringResource(R.string.bench_waiting)
        value == null -> stringResource(R.string.bench_type)
        else -> null
    }
    Column(Modifier.fillMaxWidth().testTag(BenchSheetTag).navigationBarsPadding().padding(top = 8.dp, bottom = 16.dp)) {
        Text(
            stringResource(R.string.bench_title),
            Modifier.padding(horizontal = TextEdge).semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            name,
            Modifier.padding(horizontal = TextEdge),
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Column(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp).semantics(mergeDescendants = true) {},
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PaddedBench(drawn, target, ink, Modifier.size(BenchArtWidth, BenchArtHeight))
            // A typed angle past 90 stays on screen, in the error colour, so the slip shows.
            Text(
                when {
                    value != null -> "$value°"
                    tooSteep -> "$typed°"
                    else -> "–"
                },
                Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.figureDisplay,
                color = ink,
            )
            gap?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.figureLarge,
                    color = when {
                        value == null -> cs.onSurfaceVariant
                        near -> onTarget
                        else -> cs.onSurface
                    },
                    textAlign = TextAlign.Center,
                )
            }
            source?.let {
                Text(
                    it,
                    Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (tooSteep) cs.error else cs.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (target != null) OffTarget(drawn, target, ink, onTarget, Modifier.padding(top = 16.dp).size(BenchArtWidth, 24.dp))
        }
        BenchKeypad(
            canSave = value != null,
            saveDescription = value?.let { stringResource(R.string.bench_save, it) } ?: stringResource(R.string.bench_save_none),
            onDigit = { if (typed.length < 2) typed += it },
            onDelete = { typed = typed.dropLast(1) },
            onStep = { by ->
                // From what shows: typed, else the live reading, else the angle set, else the target.
                val from = typed.toIntOrNull() ?: value ?: start ?: target ?: 0
                typed = (from + by).coerceIn(0, BenchAngle.MAX).toString()
            },
            onSave = { value?.let(onSave) },
            onClose = onClose,
            modifier = Modifier.padding(horizontal = PadEdge),
        )
    }
}

/** The drawing and the off-target line share one width, so the line sits under the bench. */
private val BenchArtWidth = 248.dp
private val BenchArtHeight = 144.dp

/** The logging pad's edge, so the keys sit where the pad's do under the sheet. */
private val PadEdge = 8.dp

/** The trend scale's up colour for an angle on target, darkened in light mode ([Direction.upOnLight]). */
@Composable
internal fun onTargetColor(): Color = if (isDarkSurface()) Direction.Up.color else Direction.upOnLight

/** `2° over 30°`, `3° under 30°`, `On target, 30°`; against the angle set today, `… set today`. */
@Composable
private fun offTarget(angle: Int, target: Int, againstToday: Boolean): String = when {
    BenchAngle.near(angle, target) -> stringResource(if (againstToday) R.string.bench_on_target_today else R.string.bench_on_target, target)
    angle > target -> stringResource(if (againstToday) R.string.bench_over_today else R.string.bench_over, angle - target, target)
    else -> stringResource(if (againstToday) R.string.bench_under_today else R.string.bench_under, target - angle, target)
}

/** The target alone, while there is no angle: `30° set today`, `Last time 30°`. */
@Composable
private fun targetOnly(target: Int, againstToday: Boolean): String =
    stringResource(if (againstToday) R.string.bench_today else R.string.bench_last, target)

/**
 * The logging pad's grid on its own keys: digits, ⌫, − and + in the fourth column, close (leave
 * unsaved) at the bottom left and ✓ at the bottom right, so a thumb that knows the pad knows this.
 * ✓ is filled primary, dimmed like the pad's while there is nothing to save.
 */
@Composable
private fun BenchKeypad(
    canSave: Boolean,
    saveDescription: String,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onStep: (Int) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val cs = MaterialTheme.colorScheme
    @Composable
    fun RowScope.Digit(d: Char) = TextKey("$d", "$d") { onDigit(d) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(KeyGap)) {
        Row(horizontalArrangement = Arrangement.spacedBy(KeyGap)) { Digit('1'); Digit('2'); Digit('3'); SymbolKey(Symbol.Backspace, R.string.bench_delete, onDelete) }
        Row(horizontalArrangement = Arrangement.spacedBy(KeyGap)) { Digit('4'); Digit('5'); Digit('6'); SymbolKey(Symbol.Remove, R.string.bench_minus) { onStep(-1) } }
        Row(horizontalArrangement = Arrangement.spacedBy(KeyGap)) { Digit('7'); Digit('8'); Digit('9'); SymbolKey(Symbol.Add, R.string.bench_plus) { onStep(1) } }
        Row(horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
            SymbolKey(Symbol.Close, R.string.bench_close, onClose)
            Digit('0')
            Spacer(Modifier.weight(1f))
            Key(
                saveDescription,
                modifier = if (canSave) Modifier else Modifier.semantics { disabled() },
                container = if (canSave) cs.primary else cs.surfaceContainerHigh,
                content = if (canSave) cs.onPrimary else cs.outline,
                onClick = { if (canSave) onSave() },
            ) { SymbolIcon(Symbol.Check, null) }
        }
    }
}

/**
 * A bench side on: a beam on two feet and an upright under the seat, a short seat pad, and the
 * long backrest pad at [angle] with the phone lying on it; [target]'s backrest dashed behind it.
 * The hinge stays put, so the backrest swings about it as the angle changes.
 */
@Composable
private fun PaddedBench(angle: Int?, target: Int?, ink: Color, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val frame = cs.onSurfaceVariant
    val pad = cs.surfaceContainerHighest
    val ghost = cs.outline
    Canvas(modifier.clearAndSetSemantics {}) {
        val t = 14.dp.toPx() // the pads' thickness
        val seat = 64.dp.toPx()
        val back = 104.dp.toPx()
        val floor = size.height - 3.dp.toPx()
        val post = 26.dp.toPx()
        // Centred for a flat bench; upright, the backrest stands over the hinge, so the height holds it.
        val hinge = Offset((size.width - seat - back) / 2 + back, floor - post - t / 2)
        val corner = CornerRadius(t / 2, t / 2)
        val edge = 2.dp.toPx()
        val bar = 4.dp.toPx()
        // The frame: a beam on two feet, and the upright under the seat.
        val beamY = floor - 8.dp.toPx()
        val beamStart = hinge.x - back * 0.6f
        val beamEnd = hinge.x + seat - 4.dp.toPx()
        drawLine(frame, Offset(beamStart, beamY), Offset(beamEnd, beamY), bar, StrokeCap.Round)
        val foot = Size(16.dp.toPx(), 6.dp.toPx())
        listOf(beamStart, beamEnd).forEach { x ->
            drawRoundRect(frame, Offset(x - foot.width / 2, floor - foot.height), foot, CornerRadius(foot.height / 2))
        }
        drawLine(frame, Offset(hinge.x + 20.dp.toPx(), beamY), Offset(hinge.x + 20.dp.toPx(), hinge.y), bar, StrokeCap.Round)
        // The seat, right of the hinge.
        val seatTop = Offset(hinge.x + 2.dp.toPx(), hinge.y - t / 2)
        drawRoundRect(pad, seatTop, Size(seat, t), corner)
        drawRoundRect(frame, seatTop, Size(seat, t), corner, style = Stroke(edge))
        // A backrest pad pointing back from the hinge, turned up by its angle.
        fun backrest(degrees: Int, draw: DrawScope.(topLeft: Offset, size: Size) -> Unit) =
            rotate(degrees.toFloat(), hinge) { draw(Offset(hinge.x - back - 2.dp.toPx(), hinge.y - t / 2), Size(back, t)) }
        target?.let {
            backrest(it) { topLeft, s ->
                drawRoundRect(ghost, topLeft, s, corner, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
            }
        }
        angle?.let {
            backrest(it) { topLeft, s ->
                drawRoundRect(pad, topLeft, s, corner)
                drawRoundRect(ink, topLeft, s, corner, style = Stroke(edge))
                // The phone, lying face up on the pad towards its top end.
                val phone = Size(back * 0.46f, 6.dp.toPx())
                drawRoundRect(ink, Offset(topLeft.x + back * 0.14f, topLeft.y - phone.height - 1.dp.toPx()), phone, CornerRadius(3.dp.toPx()))
            }
        }
    }
}

/**
 * How far [angle] is from [target]: a dot on a line of ±15° whose centre tick is the target, the
 * ±3° that read as on target tinted round the tick.
 */
@Composable
private fun OffTarget(angle: Int?, target: Int, ink: Color, onTarget: Color, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val line = cs.outlineVariant
    val tick = cs.onSurfaceVariant
    Canvas(modifier.clearAndSetSemantics {}) {
        val cy = size.height / 2
        val unit = size.width / 2 / OFF_RANGE
        val track = 4.dp.toPx()
        drawLine(line, Offset(track / 2, cy), Offset(size.width - track / 2, cy), track, StrokeCap.Round)
        val zone = BenchAngle.NEAR * unit
        drawRoundRect(onTarget.copy(alpha = 0.35f), Offset(size.width / 2 - zone, cy - 4.dp.toPx()), Size(zone * 2, 8.dp.toPx()), CornerRadius(4.dp.toPx()))
        drawLine(tick, Offset(size.width / 2, cy - 8.dp.toPx()), Offset(size.width / 2, cy + 8.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
        angle?.let { a ->
            val d = (a - target).coerceIn(-OFF_RANGE.toInt(), OFF_RANGE.toInt())
            val r = 8.dp.toPx()
            val x = (size.width / 2 + d * unit).coerceIn(r, size.width - r)
            drawCircle(ink, r, Offset(x, cy))
        }
    }
}

/** The degrees either side of the target the off-target line spans. */
private const val OFF_RANGE = 15f
