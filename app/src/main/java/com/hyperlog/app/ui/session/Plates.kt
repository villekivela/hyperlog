package com.hyperlog.app.ui.session

import com.hyperlog.app.ui.theme.figureHeadline
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.domain.formatKg
import kotlin.math.abs
import kotlin.math.sqrt

// The plate calculator (docs/design/logging-screen.md, Plates): one side of the bar drawn with its
// plates as coloured blocks sized by weight, and a legend of `1 × 20` per plate with the bar's
// weight. The same component in the pad (while the weight is typed) and, compact, on warm-up rows.

/**
 * The fixed plate colours, the kg competition scale, like the RIR scale not following the theme:
 * 25 and 2.5 red, 20 and 2 blue, 15 and 1.5 yellow, 10 and 1 green, 5 and 0.5 white, 1.25 and
 * anything else silver. Mid tones, so each reads on light and dark surfaces.
 */
internal fun plateColor(kg: Double): Color {
    fun on(v: Double) = abs(kg - v) < 0.01
    return when {
        on(25.0) || on(2.5) -> Color(0xFFD9453B)
        on(20.0) || on(2.0) -> Color(0xFF2F6FDB)
        on(15.0) || on(1.5) -> Color(0xFFE5B12E)
        on(10.0) || on(1.0) -> Color(0xFF3B9C55)
        on(5.0) || on(0.5) -> Color(0xFFF2F2F2)
        else -> Color(0xFFA7ADB4)
    }
}

/** A plate's drawn height, as a share of the drawing's: heavier is taller, a 25 the full height. */
private fun plateHeight(kg: Double) = (0.3f + 0.7f * sqrt((kg / 25.0).toFloat())).coerceAtMost(1f)

/** A plate's drawn thickness. */
private fun plateWidth(kg: Double): Dp = when {
    kg >= 20 -> 6.dp
    kg >= 10 -> 5.dp
    kg >= 5 -> 4.dp
    else -> 3.dp
}

/** `Plates per side: 1 × 20, 1 × 15, on a 20 kg bar`, the component's one description. */
@Composable
private fun platesDescription(plates: LadderSpec.Plates, side: List<Double>?, totalKg: Double?, machine: Boolean): String {
    val base = formatKg(plates.baseKg)
    val alone = stringResource(if (machine) R.string.plates_machine_alone else R.string.plates_bar_alone, base)
    return when {
        side == null -> totalKg?.let { stringResource(R.string.plates_cannot, formatKg(it)) } ?: alone
        side.isEmpty() -> alone
        else -> {
            val list = legend(side).joinToString(", ") { (kg, n) -> "$n × ${formatKg(kg)}" }
            when {
                plates.baseKg <= 0 -> stringResource(R.string.plates_description_no_bar, list)
                machine -> stringResource(R.string.plates_description_machine, list, base)
                else -> stringResource(R.string.plates_description, list, base)
            }
        }
    }
}

/** Each plate once, heaviest first, with how many go on a side. */
private fun legend(side: List<Double>): List<Pair<Double, Int>> =
    side.groupingBy { it }.eachCount().toList().sortedByDescending { it.first }

/**
 * The plates on one side of the bar for [totalKg] (null: nothing typed yet, so the bar alone),
 * drawn and listed. [compact] is the warm-up rows' size of the same design. On a plate-loaded
 * [machine] the base is its starting weight (the empty machine or sled), marked with its own
 * symbol where a bar's weight has the slash; it counts toward the total either way.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlateLoad(plates: LadderSpec.Plates, totalKg: Double?, modifier: Modifier = Modifier, compact: Boolean = false, machine: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    val side = totalKg?.let { plates.perSide(it) }
    val description = platesDescription(plates, side, totalKg, machine)
    val text = (if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleSmall)
    Row(modifier.clearAndSetSemantics { contentDescription = description }, verticalAlignment = Alignment.CenterVertically) {
        BarSide(side.orEmpty(), height = if (compact) 24.dp else 40.dp, muted = side == null && totalKg != null, scale = if (compact) 1f else 1.5f)
        Spacer(Modifier.width(if (compact) 8.dp else 16.dp))
        // A warm-up row's legend is one line, so the row keeps its 48 dp.
        val legendRow: @Composable (@Composable () -> Unit) -> Unit = { items ->
            if (compact) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { items() }
            else FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) { items() }
        }
        legendRow {
            if (side == null && totalKg != null) {
                Text(stringResource(R.string.plates_not_on), style = text, color = cs.onSurfaceVariant)
                return@legendRow
            }
            legend(side.orEmpty()).forEach { (kg, n) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlateSwatch(kg, if (compact) 8.dp else 12.dp)
                    Spacer(Modifier.width(if (compact) 3.dp else 6.dp))
                    // Compact, a single plate is its weight alone: `20`, `2×20`.
                    Text(if (compact && n == 1) formatKg(kg) else if (compact) "$n×${formatKg(kg)}" else "$n × ${formatKg(kg)}", style = text, color = cs.onSurface, maxLines = 1, softWrap = false)
                }
            }
            // The bar's own weight is the pad's; a warm-up row has no room to repeat it.
            if (plates.baseKg > 0 && !compact) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (machine) MachineSwatch(12.dp) else BarSwatch(12.dp)
                    Spacer(Modifier.width(if (compact) 4.dp else 6.dp))
                    Text(formatKg(plates.baseKg), style = text, color = cs.onSurfaceVariant)
                }
            }
        }
    }
}

/** A plate's colour as a legend square; white plates get an outline so they show on light surfaces. */
@Composable
private fun PlateSwatch(kg: Double, size: Dp) {
    val color = plateColor(kg)
    val shape = RoundedCornerShape(2.dp)
    Box(
        Modifier.size(size).background(color, shape)
            .then(if (color.luminance() > 0.7f) Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape) else Modifier),
    )
}

/** The bar's legend mark: a short slash in the bar's colour. */
@Composable
private fun BarSwatch(size: Dp) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(size)) {
        drawLine(color, Offset(size.toPx() * 0.15f, size.toPx() * 0.85f), Offset(size.toPx() * 0.85f, size.toPx() * 0.15f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
    }
}

/** A plate-loaded machine's legend mark: the machine as a block on the bar, as the whole view draws it. */
@Composable
private fun MachineSwatch(size: Dp) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(size)) {
        val s = size.toPx()
        drawLine(color, Offset(0f, s / 2), Offset(s, s / 2), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        drawRoundRect(color, Offset(s * 0.25f, s * 0.2f), Size(s * 0.5f, s * 0.6f), CornerRadius(2.dp.toPx()))
    }
}

/**
 * One side of the bar, sleeve to the right: the end of the bar's grip, the collar, the plates
 * heaviest first from the collar out, and the rest of the sleeve.
 */
@Composable
private fun BarSide(side: List<Double>, height: Dp, muted: Boolean, scale: Float) {
    val cs = MaterialTheme.colorScheme
    val bar = if (muted) cs.outlineVariant else cs.onSurfaceVariant
    val outline = cs.outline
    val stub = height * 0.4f
    val sleeveEnd = height * 0.3f
    val collar = 3.dp
    val gap = 1.dp
    val platesWidth = side.fold(0.dp) { w, kg -> w + plateWidth(kg) * scale + gap }
    val width = stub + collar + gap + platesWidth + sleeveEnd
    Canvas(Modifier.width(width).height(height)) {
        val mid = size.height / 2
        val barStroke = (height.toPx() / 12).coerceAtLeast(2.dp.toPx())
        drawLine(bar, Offset(0f, mid), Offset(size.width, mid), strokeWidth = barStroke, cap = StrokeCap.Round)
        var x = stub.toPx()
        val collarH = size.height * 0.4f
        drawRoundRect(bar, Offset(x, mid - collarH / 2), Size(collar.toPx(), collarH), CornerRadius(1.dp.toPx()))
        x += collar.toPx() + gap.toPx()
        side.forEach { kg ->
            val w = (plateWidth(kg) * scale).toPx()
            val h = size.height * plateHeight(kg)
            val color = plateColor(kg)
            val topLeft = Offset(x, mid - h / 2)
            drawRoundRect(color, topLeft, Size(w, h), CornerRadius(1.5.dp.toPx()))
            if (color.luminance() > 0.7f) drawRoundRect(outline, topLeft, Size(w, h), CornerRadius(1.5.dp.toPx()), style = Stroke(1.dp.toPx()))
            x += w + gap.toPx()
        }
    }
}

/** `100 kg: a 20 kg bar with 2 × 20 on each side`, the whole bar's one description. */
@Composable
private fun wholeDescription(plates: LadderSpec.Plates, side: List<Double>?, totalKg: Double?, machine: Boolean): String {
    if (side == null || totalKg == null) return platesDescription(plates, side, totalKg, machine)
    val base = stringResource(if (machine) R.string.plates_whole_machine else R.string.plates_whole_bar, formatKg(plates.baseKg))
    if (side.isEmpty()) return stringResource(R.string.plates_whole_alone, formatKg(totalKg), base)
    val list = legend(side).joinToString(", ") { (kg, n) -> "$n × ${formatKg(kg)}" }
    return stringResource(R.string.plates_whole, formatKg(totalKg), base, list)
}

/**
 * The whole bar, from the pad's plate strip: the total, both sides drawn with every plate (the
 * heaviest inside), and a row per plate with how many go on each side, then the bar's or the
 * machine's starting weight.
 */
@Composable
internal fun WholeBar(plates: LadderSpec.Plates, totalKg: Double?, modifier: Modifier = Modifier, machine: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    val side = totalKg?.let { plates.perSide(it) }
    val description = wholeDescription(plates, side, totalKg, machine)
    androidx.compose.foundation.layout.Column(modifier.clearAndSetSemantics { contentDescription = description }) {
        Text(totalKg?.let { stringResource(R.string.weight_kg, formatKg(it)) }.orEmpty(), style = MaterialTheme.typography.figureHeadline)
        Text(
            stringResource(if (machine) R.string.plates_whole_machine_line else R.string.plates_whole_bar_line, formatKg(plates.baseKg)),
            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        BothSides(side.orEmpty(), machine, muted = side == null && totalKg != null)
        Spacer(Modifier.height(24.dp))
        if (side == null && totalKg != null) {
            Text(stringResource(R.string.plates_not_on), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
        }
        legend(side.orEmpty()).forEach { (kg, n) ->
            Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                PlateSwatch(kg, 16.dp)
                Spacer(Modifier.width(16.dp))
                Text(stringResource(R.string.weight_kg, formatKg(kg)), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.plates_each_side, n), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
            }
        }
        if (plates.baseKg > 0) {
            Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                if (machine) MachineSwatch(16.dp) else BarSwatch(16.dp)
                Spacer(Modifier.width(16.dp))
                Text(stringResource(if (machine) R.string.plates_machine_row else R.string.plates_bar_row), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.weight_kg, formatKg(plates.baseKg)), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
            }
        }
    }
}

/**
 * Both sides, mirrored about the middle: the bar's grip (or, on a machine, the machine as a
 * block), the collars, the plates heaviest inside, and the sleeves.
 */
@Composable
private fun BothSides(side: List<Double>, machine: Boolean, muted: Boolean) {
    val cs = MaterialTheme.colorScheme
    val bar = if (muted) cs.outlineVariant else cs.onSurfaceVariant
    val outline = cs.outline
    Canvas(Modifier.fillMaxWidth().height(96.dp)) {
        val mid = size.height / 2
        val cx = size.width / 2
        val stroke = 6.dp.toPx()
        drawLine(bar, Offset(0f, mid), Offset(size.width, mid), strokeWidth = stroke, cap = StrokeCap.Round)
        if (machine) {
            val w = size.width * 0.22f
            drawRoundRect(bar, Offset(cx - w / 2, mid - size.height * 0.3f), Size(w, size.height * 0.6f), CornerRadius(6.dp.toPx()))
        }
        val inner = if (machine) size.width * 0.11f + 6.dp.toPx() else size.width * 0.2f
        listOf(-1f, 1f).forEach { dir ->
            var x = cx + dir * inner
            val collarW = 5.dp.toPx(); val collarH = size.height * 0.36f
            drawRoundRect(bar, Offset(if (dir > 0) x else x - collarW, mid - collarH / 2), Size(collarW, collarH), CornerRadius(2.dp.toPx()))
            x += dir * (collarW + 2.dp.toPx())
            side.forEach { kg ->
                val w = (plateWidth(kg) * 2.2f).toPx()
                val h = size.height * plateHeight(kg)
                val color = plateColor(kg)
                val topLeft = Offset(if (dir > 0) x else x - w, mid - h / 2)
                drawRoundRect(color, topLeft, Size(w, h), CornerRadius(3.dp.toPx()))
                if (color.luminance() > 0.7f) drawRoundRect(outline, topLeft, Size(w, h), CornerRadius(3.dp.toPx()), style = Stroke(1.dp.toPx()))
                x += dir * (w + 2.dp.toPx())
            }
        }
    }
}
