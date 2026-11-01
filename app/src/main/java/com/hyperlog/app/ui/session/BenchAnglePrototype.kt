package com.hyperlog.app.ui.session

// PROTOTYPE (#115), throwaway: three ways to show and set an adjustable-bench exercise's angle on
// the logging screen, switched by the magenta pill above the pad (debug builds only). Angles live
// in memory; "last time" is faked per exercise. Lives on prototype/bench-angle, never merged.

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hyperlog.app.BuildConfig
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon
import com.hyperlog.app.ui.theme.Sheet
import com.hyperlog.app.ui.theme.figureDisplay
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

internal object BenchProto {
    var variant by mutableStateOf("E")
    // A–D were judged (A chip + gauge, B card + level, C row + keypad, D row + gauge): E won; these refine its picture.
    val variants = listOf("E" to "Line bench", "F" to "Padded bench", "G" to "Angle arc")

    /** Angles set this session, by exercise id. */
    val set = mutableStateMapOf<String, Int>()

    private val adjustable = setOf(
        "Incline DB Press", "Incline Barbell Press", "Incline Smith Machine Press", "Decline DB Press",
        "Decline Barbell Bench Press", "Seated DB Shoulder Press", "Seated DB Arnold Press",
        "Seated Barbell Overhead Press", "Seated Smith Machine Press", "Chest-Supported DB Row",
        "Incline Chest-Supported DB Row", "Incline DB Y-Raise", "Incline DB Kelso Shrug", "Incline DB Curl",
    )

    fun adjustable(e: Exercise) = e.name in adjustable

    /** Faked: the angle the last session used. */
    fun lastTime(e: Exercise): Int = when {
        e.name.startsWith("Decline") -> 15
        e.name.startsWith("Seated") -> 80
        e.name.contains("Row") -> 35
        e.name.contains("Curl") -> 45
        else -> 30
    }

    fun cycle(by: Int) {
        val i = variants.indexOfFirst { it.first == variant }
        variant = variants[(i + by + variants.size) % variants.size].first
    }
}

private fun degrees(n: Int) = "$n°"

/** The phone's tilt from flat, smoothed as the old app did; null while waiting, and [Tilt.available] false without a sensor. */
private class Tilt(val angle: Float?, val available: Boolean)

@Composable
private fun rememberTilt(): Tilt {
    val context = LocalContext.current
    var angle by remember { mutableStateOf<Float?>(null) }
    var available by remember { mutableStateOf(true) }
    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var smooth: Float? = null
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val x = e.values[0]; val y = e.values[1]; val z = e.values[2]
                val m = sqrt(x * x + y * y + z * z)
                if (m < 0.01f) return
                val a = Math.toDegrees(acos((abs(z) / m).coerceIn(0f, 1f).toDouble())).toFloat()
                smooth = smooth?.let { it + 0.18f * (a - it) } ?: a
                angle = smooth!!.coerceIn(0f, 90f)
            }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
        }
        if (sensor == null) available = false
        else sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(listener) }
    }
    return Tilt(angle, available)
}

private fun offTarget(live: Int, target: Int?): String = when {
    target == null -> "No angle last time"
    abs(live - target) <= 3 -> "On target, ${degrees(target)}"
    live > target -> "${live - target}° over ${degrees(target)}"
    else -> "${target - live}° under ${degrees(target)}"
}

// ---------------------------------------------------------------- switcher

@Composable
internal fun BenchProtoSwitcher(modifier: Modifier = Modifier) {
    if (!BuildConfig.DEBUG) return
    val name = BenchProto.variants.first { it.first == BenchProto.variant }.second
    Surface(modifier.padding(bottom = 8.dp), shape = CircleShape, color = Color(0xFFD0108A), contentColor = Color.White, shadowElevation = 6.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { BenchProto.cycle(-1) }) { SymbolIcon(Symbol.ChevronLeft, "Previous variant") }
            // A tap on the label forgets the angles set, to try a variant from the start.
            Text(
                "${BenchProto.variant} · $name",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable { BenchProto.set.clear() }.padding(vertical = 8.dp),
            )
            IconButton(onClick = { BenchProto.cycle(1) }) { SymbolIcon(Symbol.ChevronRight, "Next variant") }
        }
    }
}

// ---------------------------------------------------------------- A: a chip and a gauge sheet

/** A: an outlined chip beside the superset and swap chips, `30° last time`, filled once set. */
@Composable
internal fun BenchChipA(e: Exercise, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val now = BenchProto.set[e.id]
    var open by remember { mutableStateOf(false) }
    Row(
        modifier.height(32.dp).clip(ChipShape)
            .then(if (now != null) Modifier.background(cs.secondaryContainer) else Modifier.border(1.dp, cs.outlineVariant, ChipShape))
            .clickable { open = true }
            .padding(start = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (now != null) cs.onSecondaryContainer else cs.onSurfaceVariant
        SymbolIcon(Symbol.AirlineSeatReclineNormal, null, Modifier.size(18.dp), tint = tint)
        Spacer(Modifier.width(8.dp))
        Text(now?.let(::degrees) ?: "${degrees(BenchProto.lastTime(e))} last time", style = MaterialTheme.typography.labelLarge, color = tint)
    }
    if (open) Sheet(onDismiss = { open = false }) {
        GaugeSheet(e.name, now ?: BenchProto.lastTime(e)) { BenchProto.set[e.id] = it; open = false }
    }
}

@Composable
private fun GaugeSheet(name: String, target: Int?, onUse: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val tilt = rememberTilt()
    var typing by remember { mutableStateOf(!tilt.available) }
    var typed by remember { mutableStateOf("") }
    val live = tilt.angle?.roundToInt()
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
        Text("Bench angle", style = MaterialTheme.typography.titleLarge)
        Text(
            if (tilt.available) "$name · lay the phone on the backrest" else "$name · this phone has no tilt sensor",
            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(220.dp)) {
            val track = cs.outlineVariant
            val fill = cs.primary
            val band = cs.tertiary.copy(alpha = 0.45f)
            val needle = cs.onSurface
            Canvas(Modifier.fillMaxSize()) {
                val pad = 12.dp.toPx()
                val r = minOf(size.width * 0.62f, size.height - 2 * pad)
                val o = Offset(pad, size.height - pad)
                val arc = Size(2 * r, 2 * r)
                val tl = Offset(o.x - r, o.y - r)
                val w = 14.dp.toPx()
                drawArc(track, -90f, 90f, false, tl, arc, style = Stroke(w))
                target?.let { t -> drawArc(band, -(t + 5f).coerceAtMost(90f), 10f, false, tl, arc, style = Stroke(w)) }
                tilt.angle?.let { a ->
                    drawArc(fill, -a, a, false, tl, arc, style = Stroke(w, cap = StrokeCap.Round))
                    val rad = Math.toRadians(a.toDouble())
                    drawLine(needle, o, Offset(o.x + (r - w) * cos(rad).toFloat(), o.y - (r - w) * sin(rad).toFloat()), 6.dp.toPx(), StrokeCap.Round)
                }
                drawLine(track, o, Offset(o.x + r, o.y), 3.dp.toPx(), StrokeCap.Round)
            }
            Column(Modifier.align(Alignment.TopEnd), horizontalAlignment = Alignment.End) {
                Text(live?.let(::degrees) ?: "–", style = MaterialTheme.typography.figureDisplay)
                if (live != null) Text(offTarget(live, target), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
        }
        if (typing) {
            OutlinedTextField(
                typed, { v -> typed = v.filter(Char::isDigit).take(2) },
                label = { Text("Angle") }, suffix = { Text("°") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            val value = if (typing) typed.toIntOrNull()?.coerceIn(0, 90) else live
            if (!typing) TextButton(onClick = { typing = true }) { Text("Type it") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { value?.let(onUse) }, enabled = value != null) { Text(value?.let { "Use ${degrees(it)}" } ?: "Use") }
        }
    }
}

// ---------------------------------------------------------------- B: a setup card, a quiet line and a level

/** B: before the first set, a card that asks for the bench, like the ladder offer. */
@Composable
internal fun BenchCardB(e: Exercise) {
    val cs = MaterialTheme.colorScheme
    var measuring by remember { mutableStateOf(false) }
    val last = BenchProto.lastTime(e)
    Surface(
        color = cs.tertiaryContainer,
        shape = com.hyperlog.app.ui.theme.CardShape,
        modifier = Modifier.fillMaxWidth().padding(horizontal = com.hyperlog.app.ui.theme.CardMargin).padding(bottom = 8.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SymbolIcon(Symbol.AirlineSeatReclineNormal, null, Modifier.size(20.dp), tint = cs.onTertiaryContainer)
                Spacer(Modifier.width(8.dp))
                Text("Set the bench to ${degrees(last)}", style = MaterialTheme.typography.titleSmall, color = cs.onTertiaryContainer)
            }
            Text(
                "As last time. Lay the phone on the backrest to check it.",
                style = MaterialTheme.typography.bodyMedium, color = cs.onTertiaryContainer,
                modifier = Modifier.padding(top = 4.dp, end = 8.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { BenchProto.set[e.id] = last }) { Text("It's at ${degrees(last)}", color = cs.onTertiaryContainer) }
                TextButton(onClick = { measuring = true }) { Text("Measure", color = cs.onTertiaryContainer) }
            }
        }
    }
    if (measuring) Level(e.name, last, onUse = { BenchProto.set[e.id] = it; measuring = false }, onClose = { measuring = false })
}

/** B: once the card is answered (or a set is logged), one quiet line under the name. */
@Composable
internal fun BenchLineB(e: Exercise) {
    val cs = MaterialTheme.colorScheme
    var measuring by remember { mutableStateOf(false) }
    val now = BenchProto.set[e.id]
    Row(
        Modifier.fillMaxWidth().clickable { measuring = true }.padding(horizontal = Margin, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SymbolIcon(Symbol.AirlineSeatReclineNormal, null, Modifier.size(18.dp), tint = cs.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(
            if (now != null) "Bench ${degrees(now)}" else "Bench ${degrees(BenchProto.lastTime(e))} last time",
            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
        )
    }
    if (measuring) Level(e.name, now ?: BenchProto.lastTime(e), onUse = { BenchProto.set[e.id] = it; measuring = false }, onClose = { measuring = false })
}

/** B's measuring: the whole screen as a level, read from a step away with the phone on the bench. Tap anywhere to use it. */
@Composable
private fun Level(name: String, target: Int?, onUse: (Int) -> Unit, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val tilt = rememberTilt()
        var typing by remember { mutableStateOf(!tilt.available) }
        var typed by remember { mutableStateOf("") }
        val live = tilt.angle?.roundToInt()
        val near = live != null && target != null && abs(live - target) <= 3
        val ink = Color(0xFFF1F2F4)
        val muted = Color(0xFF9AA1A9)
        val good = Direction.Up.color
        Surface(
            Modifier.fillMaxSize().then(if (!typing && live != null) Modifier.clickable { onUse(live) } else Modifier),
            color = Color(0xFF0E0F11), contentColor = ink,
        ) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    IconButton(onClick = onClose) { SymbolIcon(Symbol.Close, "Close", tint = ink) }
                }
                Spacer(Modifier.weight(1f))
                if (typing) {
                    OutlinedTextField(
                        typed, { v -> typed = v.filter(Char::isDigit).take(2) },
                        label = { Text("Angle") }, suffix = { Text("°") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Button(onClick = { typed.toIntOrNull()?.coerceIn(0, 90)?.let(onUse) }, enabled = typed.isNotEmpty(), modifier = Modifier.padding(top = 16.dp)) {
                        Text("Use ${typed.ifEmpty { "–" }}°")
                    }
                } else {
                    // The backrest, side on, tilting with the phone.
                    Canvas(Modifier.size(160.dp, 110.dp)) {
                        val o = Offset(size.width * 0.3f, size.height - 8.dp.toPx())
                        val r = size.height - 16.dp.toPx()
                        drawLine(muted, Offset(o.x - 8.dp.toPx(), o.y), Offset(size.width, o.y), 6.dp.toPx(), StrokeCap.Round)
                        target?.let { t ->
                            val rad = Math.toRadians(t.toDouble())
                            drawLine(muted.copy(alpha = 0.35f), o, Offset(o.x - r * cos(rad).toFloat(), o.y - r * sin(rad).toFloat()), 6.dp.toPx(), StrokeCap.Round)
                        }
                        tilt.angle?.let { a ->
                            val rad = Math.toRadians(a.toDouble())
                            drawLine(if (near) good else ink, o, Offset(o.x - r * cos(rad).toFloat(), o.y - r * sin(rad).toFloat()), 8.dp.toPx(), StrokeCap.Round)
                        }
                    }
                    Text(
                        live?.let(::degrees) ?: "–",
                        style = MaterialTheme.typography.figureDisplay.copy(fontSize = 128.sp, lineHeight = 136.sp),
                        color = if (near) good else ink,
                    )
                    if (target != null) {
                        Text("Target ${degrees(target)}", style = MaterialTheme.typography.titleMedium, color = muted)
                        // How far off: a dot on ±15°, the centre tick is the target.
                        Canvas(Modifier.padding(top = 24.dp).size(280.dp, 32.dp)) {
                            val cy = size.height / 2
                            drawLine(muted.copy(alpha = 0.4f), Offset(0f, cy), Offset(size.width, cy), 3.dp.toPx(), StrokeCap.Round)
                            drawLine(muted, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 3.dp.toPx(), StrokeCap.Round)
                            tilt.angle?.let { a ->
                                val d = ((a - target) / 15f).coerceIn(-1f, 1f)
                                drawCircle(if (near) good else ink, 10.dp.toPx(), Offset(size.width / 2 + d * size.width / 2, cy))
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                if (!typing) {
                    Text(live?.let { "Tap anywhere to use ${degrees(it)}" } ?: "Waiting for the sensor", style = MaterialTheme.typography.bodyLarge, color = muted)
                    TextButton(onClick = { typing = true }) { Text("Type it instead", color = ink) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- C: a row of the table, typed on a keypad

/** C: the angle as the table's first row, a cell like a set's weight. */
@Composable
internal fun BenchRowC(e: Exercise) {
    val cs = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    val now = BenchProto.set[e.id]
    Row(
        Modifier.fillMaxWidth().height(RowHeight).padding(horizontal = TapInset).clip(RowRipple).clickable { open = true }.padding(horizontal = RowPad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(ColIndex), contentAlignment = Alignment.CenterStart) {
            SymbolIcon(Symbol.AirlineSeatReclineNormal, null, Modifier.size(22.dp), tint = cs.onSurfaceVariant)
        }
        Text("Bench", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Box(
            Modifier.width(ColKg).height(CellHeight).border(1.dp, if (now != null) cs.outline else cs.outlineVariant, CellShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                degrees(now ?: BenchProto.lastTime(e)),
                style = MaterialTheme.typography.titleMedium,
                color = if (now != null) cs.onSurface else cs.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
        Spacer(Modifier.width(ColReps + ColRir + ColCheck))
    }
    if (open) Sheet(onDismiss = { open = false }) {
        Keypad(e.name, now ?: BenchProto.lastTime(e), art = BenchProto.variant) { BenchProto.set[e.id] = it; open = false }
    }
}

/** C's entry: the live reading fills the number until a key is typed, and ✓ takes what shows. */
@Composable
private fun Keypad(name: String, target: Int?, art: String = "C", onUse: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val tilt = rememberTilt()
    var typed by remember { mutableStateOf("") }
    val live = tilt.angle?.roundToInt()
    val value = typed.toIntOrNull()?.coerceIn(0, 90) ?: live
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
        Text("Bench angle · $name", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
        val status = when {
            typed.isNotEmpty() -> "Typed" + (target?.let { t -> value?.let { " · " + offTarget(it, t).replaceFirstChar(Char::lowercase) } } ?: "")
            live != null -> "From the phone · ${offTarget(live, target).replaceFirstChar(Char::lowercase)}"
            tilt.available -> "Waiting for the sensor"
            else -> "Type the angle"
        }
        // The picture shows what ✓ takes: the typed angle, else the live one.
        val shown = typed.toIntOrNull()?.coerceIn(0, 90)?.toFloat() ?: tilt.angle
        val near = value != null && target != null && abs(value - target) <= 3
        when (art) {
            "D" -> Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                GaugeArt(shown, target, near, Modifier.size(150.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(value?.let(::degrees) ?: "–", style = MaterialTheme.typography.figureDisplay, color = if (near) Direction.Up.color else cs.onSurface)
                    Text(status, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.End)
                }
            }
            "E" -> Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                BenchArt(shown, target, near, Modifier.size(150.dp, 96.dp))
                Text(value?.let(::degrees) ?: "–", style = MaterialTheme.typography.figureDisplay, color = if (near) Direction.Up.color else cs.onSurface)
                Text(status, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                if (target != null) OffDot(shown, target, near, Modifier.padding(top = 12.dp, bottom = 8.dp).size(240.dp, 24.dp))
            }
            "F" -> Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                PaddedBenchArt(shown, target, near, Modifier.size(200.dp, 120.dp))
                Text(value?.let(::degrees) ?: "–", style = MaterialTheme.typography.figureDisplay, color = if (near) Direction.Up.color else cs.onSurface)
                Text(status, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                if (target != null) OffDot(shown, target, near, Modifier.padding(top = 12.dp, bottom = 8.dp).size(240.dp, 24.dp))
            }
            "G" -> Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ArcBenchArt(shown, target, near, Modifier.size(260.dp, 150.dp))
                Text(value?.let(::degrees) ?: "–", style = MaterialTheme.typography.figureDisplay, color = if (near) Direction.Up.color else cs.onSurface)
                Text(status, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
            }
            else -> {
                Text(
                    value?.let(::degrees) ?: "–",
                    style = MaterialTheme.typography.figureDisplay,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                )
                Text(status, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp))
            }
        }
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "✓"))
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = KeyGap / 2), horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
                row.forEach { key ->
                    val ok = key == "✓"
                    Surface(
                        onClick = {
                            when (key) {
                                "⌫" -> typed = typed.dropLast(1)
                                "✓" -> value?.let(onUse)
                                else -> if (typed.length < 2) typed += key
                            }
                        },
                        enabled = !ok || value != null,
                        shape = KeyShape,
                        color = if (ok) cs.primary else cs.surfaceContainerHighest,
                        contentColor = if (ok) cs.onPrimary else cs.onSurface,
                        modifier = Modifier.weight(1f).height(KeyHeight),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            when (key) {
                                "⌫" -> SymbolIcon(Symbol.Backspace, "Delete")
                                "✓" -> SymbolIcon(Symbol.Check, "Use")
                                else -> Text(key, style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- D and E: A's and B's pictures, for C's keypad

/** A's quarter gauge: the track, a band around the target, the angle filled and a needle as the backrest. */
@Composable
private fun GaugeArt(angle: Float?, target: Int?, near: Boolean, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val track = cs.outlineVariant
    val fill = if (near) Direction.Up.color else cs.primary
    val band = cs.onSurfaceVariant.copy(alpha = 0.5f)
    val needle = if (near) Direction.Up.color else cs.onSurface
    Canvas(modifier) {
        val w = 12.dp.toPx()
        val pad = w
        val r = minOf(size.width, size.height) - 2 * pad
        val o = Offset(pad, size.height - pad)
        val arc = Size(2 * r, 2 * r)
        val tl = Offset(o.x - r, o.y - r)
        drawArc(track, -90f, 90f, false, tl, arc, style = Stroke(w))
        target?.let { t -> drawArc(band, -(t + 4f).coerceAtMost(90f), 8f, false, tl, arc, style = Stroke(w)) }
        angle?.let { a ->
            drawArc(fill, -a, a, false, tl, arc, style = Stroke(w, cap = StrokeCap.Round))
            val rad = Math.toRadians(a.toDouble())
            drawLine(needle, o, Offset(o.x + (r - w) * cos(rad).toFloat(), o.y - (r - w) * sin(rad).toFloat()), 5.dp.toPx(), StrokeCap.Round)
        }
        drawLine(track, o, Offset(o.x + r, o.y), 3.dp.toPx(), StrokeCap.Round)
    }
}

/** B's bench, side on: the seat, the target backrest faint and the angle's backrest over it. */
@Composable
private fun BenchArt(angle: Float?, target: Int?, near: Boolean, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val seat = cs.onSurfaceVariant
    val ink = if (near) Direction.Up.color else cs.onSurface
    Canvas(modifier) {
        val o = Offset(size.width * 0.42f, size.height - 8.dp.toPx())
        val r = minOf(size.height - 16.dp.toPx(), o.x - 8.dp.toPx())
        drawLine(seat, Offset(o.x - 6.dp.toPx(), o.y), Offset(size.width - 8.dp.toPx(), o.y), 6.dp.toPx(), StrokeCap.Round)
        target?.let { t ->
            val rad = Math.toRadians(t.toDouble())
            drawLine(seat.copy(alpha = 0.35f), o, Offset(o.x - r * cos(rad).toFloat(), o.y - r * sin(rad).toFloat()), 6.dp.toPx(), StrokeCap.Round)
        }
        angle?.let { a ->
            val rad = Math.toRadians(a.toDouble())
            drawLine(ink, o, Offset(o.x - r * cos(rad).toFloat(), o.y - r * sin(rad).toFloat()), 8.dp.toPx(), StrokeCap.Round)
        }
    }
}

/** B's off-target line: a dot on ±15°, the centre tick is the target. */
@Composable
private fun OffDot(angle: Float?, target: Int, near: Boolean, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val line = cs.outlineVariant
    val tick = cs.onSurfaceVariant
    val dot = if (near) Direction.Up.color else cs.onSurface
    Canvas(modifier) {
        val cy = size.height / 2
        drawLine(line, Offset(0f, cy), Offset(size.width, cy), 3.dp.toPx(), StrokeCap.Round)
        drawLine(tick, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 3.dp.toPx(), StrokeCap.Round)
        angle?.let { a ->
            val d = ((a - target) / 15f).coerceIn(-1f, 1f)
            drawCircle(dot, 9.dp.toPx(), Offset(size.width / 2 + d * size.width / 2, cy))
        }
    }
}

// ---------------------------------------------------------------- F and G: E's picture, refined

/** F: a real bench, side on: a seat pad on legs, the backrest pad with the phone on it, the target's pad faint behind. */
@Composable
private fun PaddedBenchArt(angle: Float?, target: Int?, near: Boolean, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val frame = cs.onSurfaceVariant
    val pad = cs.surfaceContainerHighest
    val ink = if (near) Direction.Up.color else cs.onSurface
    Canvas(modifier) {
        val t = 14.dp.toPx()
        val hinge = Offset(size.width * 0.46f, size.height - 30.dp.toPx())
        val back = minOf(hinge.x - 4.dp.toPx(), hinge.y - 4.dp.toPx())
        val corner = androidx.compose.ui.geometry.CornerRadius(t / 2, t / 2)
        // Legs and the frame under the seat.
        drawLine(frame, Offset(hinge.x - 12.dp.toPx(), hinge.y + t / 2), Offset(hinge.x - 20.dp.toPx(), size.height - 2.dp.toPx()), 4.dp.toPx(), StrokeCap.Round)
        drawLine(frame, Offset(size.width - 24.dp.toPx(), hinge.y + t / 2), Offset(size.width - 16.dp.toPx(), size.height - 2.dp.toPx()), 4.dp.toPx(), StrokeCap.Round)
        // The seat.
        drawRoundRect(pad, Offset(hinge.x, hinge.y - t / 2), Size(size.width - hinge.x - 8.dp.toPx(), t), corner)
        drawRoundRect(frame, Offset(hinge.x, hinge.y - t / 2), Size(size.width - hinge.x - 8.dp.toPx(), t), corner, style = Stroke(2.dp.toPx()))
        // A backrest pad pointing left from the hinge, turned up by its angle.
        fun backrest(deg: Float, fillColor: Color, edge: Color, phone: Boolean) {
            rotate(deg, hinge) {
                val tl = Offset(hinge.x - back, hinge.y - t / 2)
                drawRoundRect(fillColor, tl, Size(back, t), corner)
                drawRoundRect(edge, tl, Size(back, t), corner, style = Stroke(2.dp.toPx()))
                if (phone) {
                    // The phone lying on the pad, screen up.
                    val pw = back * 0.42f
                    drawRoundRect(edge, Offset(hinge.x - back * 0.78f, hinge.y - t / 2 - 6.dp.toPx()), Size(pw, 5.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                }
            }
        }
        target?.let { backrest(it.toFloat(), Color.Transparent, frame.copy(alpha = 0.35f), phone = false) }
        angle?.let { backrest(it, pad, ink, phone = true) }
    }
}

/** G: the line bench with its angle drawn: a wedge from seat to backrest, the target a tick on its arc. */
@Composable
private fun ArcBenchArt(angle: Float?, target: Int?, near: Boolean, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val seat = cs.onSurfaceVariant
    val ink = if (near) Direction.Up.color else cs.onSurface
    val wedge = (if (near) Direction.Up.color else cs.onSurface).copy(alpha = 0.14f)
    val scale = cs.outlineVariant
    Canvas(modifier) {
        val o = Offset(size.width * 0.5f, size.height - 8.dp.toPx())
        val r = minOf(size.height - 16.dp.toPx(), o.x - 8.dp.toPx())
        val ar = r * 0.8f
        val box = Size(2 * ar, 2 * ar)
        val tl = Offset(o.x - ar, o.y - ar)
        // A 0–90° scale around the hinge, ticked every 15°; the backrest swings over the left quarter.
        drawArc(scale, 180f, 90f, false, tl, box, style = Stroke(2.dp.toPx()))
        for (d in 0..90 step 15) {
            val rad = Math.toRadians(d.toDouble())
            val dir = Offset(-cos(rad).toFloat(), -sin(rad).toFloat())
            drawLine(scale, o + dir * (ar - 5.dp.toPx()), o + dir * (ar + 5.dp.toPx()), 2.dp.toPx())
        }
        angle?.let { a -> drawArc(wedge, 180f, a, true, tl, box) }
        drawLine(seat, Offset(o.x - 6.dp.toPx(), o.y), Offset(size.width - 8.dp.toPx(), o.y), 6.dp.toPx(), StrokeCap.Round)
        target?.let { t ->
            val rad = Math.toRadians(t.toDouble())
            val dir = Offset(-cos(rad).toFloat(), -sin(rad).toFloat())
            drawLine(seat, o + dir * (ar - 12.dp.toPx()), o + dir * (ar + 12.dp.toPx()), 4.dp.toPx(), StrokeCap.Round)
        }
        angle?.let { a ->
            val rad = Math.toRadians(a.toDouble())
            drawLine(ink, o, Offset(o.x - r * cos(rad).toFloat(), o.y - r * sin(rad).toFloat()), 8.dp.toPx(), StrokeCap.Round)
        }
    }
}
