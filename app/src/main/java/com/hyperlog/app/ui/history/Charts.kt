package com.hyperlog.app.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.domain.formatKg
import com.hyperlog.app.history.WeekTotal
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

// Charts (docs/design/history.md): thin marks on hairline grids, one accent (primary), ink for
// everything else. Each chart has its table in the rows under it, so nothing is read by touch alone.

/**
 * The colour of the context marks (top sets): a mid grey that stays apart from the accent line in
 * both themes (dataviz validator: normal-vision ΔE 18+ against the baseline and logo primaries,
 * 3:1 on the surface). Material's light `outline` sits too close to primary, so it is lifted a step
 * toward the surface there.
 */
@Composable
internal fun contextInk(): Color {
    val cs = MaterialTheme.colorScheme
    return if (cs.surface.luminance() > 0.5f) lerp(cs.outline, cs.surface, 0.2f) else cs.outline
}

/** Width of the tick labels at the chart's right edge. */
private val TickColumn = 40.dp

/** Height of the date axis under a chart. */
private val DateAxis = 24.dp

/**
 * A trend over [dates] (oldest first): [line] values joined by a 2 dp line in primary, [dots]
 * values as 8 dp ink dots, either null where a session has none. Tap or drag selects the nearest
 * date; [selected] gets a crosshair and the accent dot. A [goal] is a solid 1 dp reference line in
 * ink across the plot, kept on the scale, with [goalLabel] direct at its start.
 */
@Composable
fun TrendChart(
    dates: List<LocalDate>,
    line: List<Double?>,
    dots: List<Double?>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    axis: Boolean = true,
    /** False for a small trend line: no grid, ticks or crosshair. */
    ticks: Boolean = true,
    goal: Double? = null,
    goalLabel: String? = null,
    description: String,
) {
    val cs = MaterialTheme.colorScheme
    val context = contextInk()
    val measurer = rememberTextMeasurer()
    val tick = MaterialTheme.typography.labelSmall.copy(color = cs.onSurfaceVariant)
    // Muted ink: apart from the hairline grid, quieter than the estimate it is read against.
    val goalInk = cs.onSurfaceVariant
    val goalStyle = MaterialTheme.typography.labelSmall.copy(color = cs.onSurfaceVariant)
    val values = (line + dots).filterNotNull()
    if (dates.isEmpty() || values.isEmpty()) return
    val scaled = values + listOfNotNull(goal)
    val scale = remember(scaled) { Scale.of(scaled.min(), scaled.max()) }
    Canvas(
        modifier.fillMaxWidth().height(if (axis) height + DateAxis else height)
            .semantics { contentDescription = description }
            .pointerInput(dates) {
                detectTapGestures { onSelect(nearest(dates, it.x, size.width.toFloat() - TickColumn.toPx())) }
            }
            .pointerInput(dates) {
                detectHorizontalDragGestures { change, _ -> onSelect(nearest(dates, change.position.x, size.width.toFloat() - TickColumn.toPx())) }
            },
    ) {
        val plotW = size.width - (if (ticks) TickColumn.toPx() else 0f)
        val plotH = height.toPx()
        val pad = 8.dp.toPx()
        fun x(i: Int) = xOf(dates, i, plotW)
        fun y(v: Double) = (pad + (plotH - 2 * pad) * (1 - (v - scale.min) / (scale.max - scale.min))).toFloat()
        // Hairline grid with its ticks at the right edge.
        if (ticks) scale.ticks.forEach { t ->
            val ty = y(t)
            drawLine(cs.outlineVariant, Offset(0f, ty), Offset(plotW, ty), strokeWidth = 1.dp.toPx())
            label(measurer, scale.format(t), tick, Offset(plotW + 8.dp.toPx(), ty), centerY = true)
        }
        // The goal: a reference, so thinner than the data and in ink, labelled where the line ends.
        goal?.let { g ->
            val gy = y(g)
            drawLine(goalInk, Offset(0f, gy), Offset(plotW, gy), strokeWidth = 1.dp.toPx())
            goalLabel?.let { text ->
                val layout = measurer.measure(text, goalStyle)
                // At the start, above the line (or under it along the top): the early sessions sit
                // below the goal, and the latest point, at the right, keeps its accent dot clear.
                val ly = if (gy - layout.size.height - 4.dp.toPx() < 0f) gy + 4.dp.toPx() else gy - layout.size.height - 4.dp.toPx()
                drawText(layout, topLeft = Offset(0f, ly))
            }
        }
        // The crosshair finds the date.
        if (ticks) drawLine(cs.outline, Offset(x(selected), 0f), Offset(x(selected), plotH), strokeWidth = 1.dp.toPx())
        val ring = 2.dp.toPx()
        dots.forEachIndexed { i, v ->
            if (v == null) return@forEachIndexed
            drawCircle(cs.surface, radius = 4.dp.toPx() + ring, center = Offset(x(i), y(v)))
            drawCircle(context, radius = 4.dp.toPx(), center = Offset(x(i), y(v)))
        }
        val joined = line.withIndex().filter { it.value != null }
        if (joined.size >= 2) {
            val path = Path()
            joined.forEachIndexed { n, (i, v) -> if (n == 0) path.moveTo(x(i), y(v!!)) else path.lineTo(x(i), y(v!!)) }
            drawPath(path, cs.primary, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        // The selected session: the accent dot on the line (or on the dot when it has no line value).
        (line.getOrNull(selected) ?: dots.getOrNull(selected))?.let { v ->
            drawCircle(cs.surface, radius = 6.dp.toPx() + ring, center = Offset(x(selected), y(v)))
            drawCircle(cs.primary, radius = 6.dp.toPx(), center = Offset(x(selected), y(v)))
        }
        if (axis) {
            val ay = plotH + 4.dp.toPx()
            label(measurer, short(dates.first()), tick, Offset(0f, ay))
            if (dates.size > 1) label(measurer, short(dates.last()), tick, Offset(plotW, ay), alignEnd = true)
        }
    }
}

/** A 72 × 24 dp line of [values] in ink, its latest value as the accent dot. */
@Composable
fun Sparkline(values: List<Double>, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    if (values.isEmpty()) return
    Canvas(modifier.size(72.dp, 24.dp)) {
        val lo = values.min()
        val hi = values.max()
        val pad = 4.dp.toPx()
        fun x(i: Int) = if (values.size == 1) size.width - pad else pad + (size.width - 2 * pad) * i / (values.size - 1)
        fun y(v: Double) = if (hi - lo < 1e-9) size.height / 2 else (pad + (size.height - 2 * pad) * (1 - (v - lo) / (hi - lo))).toFloat()
        if (values.size > 1) {
            val path = Path()
            values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
            drawPath(path, cs.onSurfaceVariant, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        drawCircle(cs.surface, radius = 4.dp.toPx() + 2.dp.toPx(), center = Offset(x(values.size - 1), y(values.last())))
        drawCircle(cs.primary, radius = 4.dp.toPx(), center = Offset(x(values.size - 1), y(values.last())))
    }
}

/** Sets as counted: whole sets plain ("7"), a half with its decimal ("7.5"). */
fun formatSets(sets: Double): String {
    val halves = Math.round(sets * 2)
    return if (halves % 2 == 0L) "${halves / 2}" else "${halves / 2}.5"
}

/**
 * The work sets of [weeks] (oldest first) as bars from zero, with the sets on a tick column and the
 * first and last week under them. Tap or drag selects the nearest week; [selected] is in full ink.
 */
@Composable
fun WorkSetsChart(
    weeks: List<WeekTotal>,
    selected: Int,
    onSelect: (Int) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
) {
    val cs = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val tick = MaterialTheme.typography.labelSmall.copy(color = cs.onSurfaceVariant)
    val unit = stringResource(R.string.history_sets_axis)
    val scale = remember(weeks) {
        // Zero and the top: a chart this short has no room for the ticks between.
        Scale.of(0.0, weeks.maxOf { it.workSets }.toDouble().coerceAtLeast(1.0)).let { s -> s.copy(min = 0.0, ticks = listOf(0.0, s.max)) }
    }
    fun nearestWeek(px: Float, width: Float) = (px / (width / weeks.size)).toInt().coerceIn(0, weeks.lastIndex)
    // Room for the labels, which grow with the system's text size.
    val labels = measurer.measure(unit, tick)
    val density = LocalDensity.current
    val column = maxOf(TickColumn, with(density) { labels.size.width.toDp() + 8.dp })
    val axis = maxOf(DateAxis, with(density) { labels.size.height.toDp() + 12.dp })
    Canvas(
        modifier.fillMaxWidth().height(height + axis)
            .semantics { contentDescription = description }
            .pointerInput(weeks, column) { detectTapGestures { onSelect(nearestWeek(it.x, size.width - column.toPx())) } }
            .pointerInput(weeks, column) { detectHorizontalDragGestures { change, _ -> onSelect(nearestWeek(change.position.x, size.width - column.toPx())) } },
    ) {
        val plotW = size.width - column.toPx()
        val plotH = height.toPx()
        fun y(v: Double) = (plotH * (1 - (v - scale.min) / (scale.max - scale.min))).toFloat()
        scale.ticks.forEach { t ->
            drawLine(cs.outlineVariant, Offset(0f, y(t)), Offset(plotW, y(t)), strokeWidth = 1.dp.toPx())
            label(measurer, scale.format(t), tick, Offset(plotW + 8.dp.toPx(), y(t)), centerY = true)
        }
        val slot = plotW / weeks.size
        val bar = slot * 0.6f
        weeks.forEachIndexed { i, week ->
            val top = y(week.workSets.toDouble())
            val left = slot * i + (slot - bar) / 2
            drawRoundRect(
                if (i == selected) cs.primary else cs.primary.copy(alpha = 0.38f),
                topLeft = Offset(left, top), size = Size(bar, plotH - top), cornerRadius = CornerRadius(4.dp.toPx()),
            )
        }
        // The week read, on the axis, so an empty one is marked too.
        drawCircle(cs.primary, radius = 4.dp.toPx(), center = Offset(slot * selected + slot / 2, plotH + 4.dp.toPx()))
        val ay = plotH + 8.dp.toPx()
        label(measurer, short(weeks.first().start), tick, Offset(0f, ay))
        if (weeks.size > 1) label(measurer, short(weeks.last().start), tick, Offset(plotW, ay), alignEnd = true)
        label(measurer, unit, tick, Offset(size.width, ay), alignEnd = true)
    }
}

/**
 * [sets] along one track from zero, with the range [low]–[high] a band on it, on a scale up to
 * [most]: the band says where the bar should reach, in tone, so being short never reads as a fault.
 */
@Composable
fun TargetBar(sets: Double, low: Int, high: Int, most: Double, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Canvas(modifier.height(12.dp)) {
        fun x(v: Double) = (size.width * (v / most.coerceAtLeast(1.0))).toFloat()
        val bar = 8.dp.toPx()
        val line = (size.height - bar) / 2
        // One track from zero, the band a raised stretch of it, and the sets along the same line.
        drawRoundRect(cs.surfaceContainerHighest, topLeft = Offset(0f, line), size = Size(size.width, bar), cornerRadius = CornerRadius(bar / 2))
        drawRoundRect(cs.outline, topLeft = Offset(x(low.toDouble()), 0f), size = Size(x(high.toDouble()) - x(low.toDouble()), size.height), cornerRadius = CornerRadius(4.dp.toPx()))
        if (sets > 0) drawRoundRect(cs.primary, topLeft = Offset(0f, line), size = Size(x(sets), bar), cornerRadius = CornerRadius(bar / 2))
    }
}

/** A value axis with 3 or 4 clean ticks covering [min]–[max]. */
private data class Scale(val min: Double, val max: Double, val ticks: List<Double>, val step: Double) {
    fun format(v: Double) = if (step >= 1) formatKg(Math.round(v).toDouble()) else formatKg(Math.round(v * 10) / 10.0)

    companion object {
        fun of(lo: Double, hi: Double): Scale {
            val span = (hi - lo).takeIf { it > 1e-9 } ?: (abs(hi) * 0.1).coerceAtLeast(1.0)
            val step = nice(span / 3)
            val min = floor((lo - span * 0.05) / step) * step
            val max = ceil((hi + span * 0.05) / step) * step
            val ticks = generateSequence(min) { it + step }.takeWhile { it <= max + step / 2 }.toList()
            return Scale(min, max, ticks, step)
        }

        /** 1, 2, 2.5 or 5 times a power of ten, at least [raw]. */
        private fun nice(raw: Double): Double {
            val p = 10.0.pow(floor(log10(raw)))
            return listOf(1.0, 2.0, 2.5, 5.0, 10.0).first { it * p >= raw - 1e-9 } * p
        }
    }
}

private fun xOf(dates: List<LocalDate>, i: Int, width: Float): Float {
    val pad = 8f
    if (dates.size == 1) return width / 2
    val first = dates.first().toEpochDay()
    val span = (dates.last().toEpochDay() - first).coerceAtLeast(1)
    return pad + (width - 2 * pad) * (dates[i].toEpochDay() - first) / span
}

private fun nearest(dates: List<LocalDate>, px: Float, width: Float): Int =
    dates.indices.minBy { abs(xOf(dates, it, width) - px) }

private fun DrawScope.label(measurer: TextMeasurer, text: String, style: TextStyle, at: Offset, centerY: Boolean = false, alignEnd: Boolean = false) {
    val layout = measurer.measure(text, style)
    val x = if (alignEnd) at.x - layout.size.width else at.x
    val y = if (centerY) at.y - layout.size.height / 2f else at.y
    drawText(layout, topLeft = Offset(x.coerceAtLeast(0f), y.coerceAtLeast(0f)))
}
