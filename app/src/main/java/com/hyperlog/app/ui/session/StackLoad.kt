package com.hyperlog.app.ui.session

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.domain.PinLoad
import com.hyperlog.app.domain.formatKg

// The pin-stack loading (SPEC, Stacks with add-ons), in the plate strip's language: a drawing on
// the left, here the stack from the front with the pin in its plate, the machine's add-ons on top
// and the gym's plates hung on the pin in their competition colours; then a legend of swatches.

internal const val StackLoadTag = "stack-load"

/**
 * How to load [load] on a pin stack, drawn and listed; [load] null (a weight off the stack, or
 * none typed) mutes the drawing and says so when [typedKg] is set.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StackLoad(load: PinLoad?, typedKg: Double?, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val text = MaterialTheme.typography.titleSmall.merge(Tnum)
    val description = when {
        load != null -> stringResource(R.string.stack_load_description, load.hint(), formatKg(load.kg))
        typedKg != null -> stringResource(R.string.stack_load_off, formatKg(typedKg))
        else -> stringResource(R.string.stack_load_none)
    }
    Row(modifier.testTag(StackLoadTag).clearAndSetSemantics { contentDescription = description }, verticalAlignment = Alignment.CenterVertically) {
        StackDrawing(load, muted = load == null && typedKg != null, height = 40.dp)
        Spacer(Modifier.width(16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (load == null) {
                if (typedKg != null) Text(stringResource(R.string.stack_not_on), style = text, color = cs.onSurfaceVariant)
                return@FlowRow
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PinSwatch(12.dp)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.stack_pin, formatKg(load.pin)), style = text, color = cs.onSurface, maxLines = 1, softWrap = false)
            }
            if (load.builtIn.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AddOnSwatch(12.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        pluralStringResource(R.plurals.stack_add_ons, load.builtIn.size, load.builtIn.size),
                        style = text, color = cs.onSurface, maxLines = 1, softWrap = false,
                    )
                }
            }
            load.onPin.groupingBy { it }.eachCount().toList().sortedByDescending { it.first }.forEach { (kg, n) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlateSwatchSmall(kg, 12.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("$n × ${formatKg(kg)}", style = text, color = cs.onSurface, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

/**
 * The stack from the front: five plates, the pin through the middle one, the machine's add-on
 * block on top when used and the loose plates hanging off the pin's head.
 */
@Composable
private fun StackDrawing(load: PinLoad?, muted: Boolean, height: Dp) {
    val cs = MaterialTheme.colorScheme
    val plate = if (muted) cs.outlineVariant else cs.onSurfaceVariant
    val pin = if (muted) cs.outlineVariant else cs.primary
    val outline = cs.outline
    val hung = load?.onPin.orEmpty().sortedDescending()
    val addOn = load?.builtIn?.isNotEmpty() == true
    val width = 40.dp + (hung.size * 6).dp
    Canvas(Modifier.width(width).height(height)) {
        val slabW = 28.dp.toPx()
        val gap = 1.5.dp.toPx()
        val slabs = 5
        val top = height.toPx() * 0.2f
        val slabH = (size.height - top - gap * (slabs - 1)) / slabs
        // The add-on block rides on the top plate.
        drawRoundRect(if (addOn) pin else cs.outlineVariant, Offset(slabW * 0.3f, 0f), Size(slabW * 0.4f, top - gap * 2), CornerRadius(1.5.dp.toPx()), style = if (addOn) androidx.compose.ui.graphics.drawscope.Fill else Stroke(1.dp.toPx()))
        repeat(slabs) { i ->
            val y = top + i * (slabH + gap)
            drawRoundRect(plate, Offset(0f, y), Size(slabW, slabH), CornerRadius(1.5.dp.toPx()), alpha = if (i <= 2) 1f else 0.45f)
        }
        // The pin: through the third plate, its head out to the right.
        val pinY = top + 2 * (slabH + gap) + slabH / 2
        drawLine(pin, Offset(slabW * 0.5f, pinY), Offset(slabW + 6.dp.toPx() + hung.size * 6.dp.toPx(), pinY), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(pin, radius = 3.dp.toPx(), center = Offset(slabW * 0.5f, pinY))
        var x = slabW + 4.dp.toPx()
        hung.forEach { kg ->
            val color = plateColor(kg)
            val h = size.height * (if (kg >= 2.5) 0.5f else 0.38f)
            val w = 4.dp.toPx()
            val tl = Offset(x, pinY - h / 2)
            drawRoundRect(color, tl, Size(w, h), CornerRadius(1.dp.toPx()))
            if (color.luminance() > 0.7f) drawRoundRect(outline, tl, Size(w, h), CornerRadius(1.dp.toPx()), style = Stroke(1.dp.toPx()))
            x += 6.dp.toPx()
        }
    }
}

/** The pin's legend mark: a dot with the pin's shaft. */
@Composable
private fun PinSwatch(size: Dp) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(size)) {
        val s = this.size.width
        drawLine(color, Offset(s * 0.2f, s / 2), Offset(s, s / 2), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(color, radius = s * 0.22f, center = Offset(s * 0.25f, s / 2))
    }
}

/** A built-in add-on's legend mark: the block that rides on the stack. */
@Composable
private fun AddOnSwatch(size: Dp) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(size)) {
        val s = this.size.width
        drawRoundRect(color, Offset(s * 0.2f, s * 0.1f), Size(s * 0.6f, s * 0.35f), CornerRadius(1.5.dp.toPx()))
        drawRoundRect(color, Offset(0f, s * 0.55f), Size(s, s * 0.35f), CornerRadius(1.5.dp.toPx()), alpha = 0.45f)
    }
}

/** A loose plate's colour square, as in the plate strip's legend. */
@Composable
private fun PlateSwatchSmall(kg: Double, size: Dp) {
    val color = plateColor(kg)
    val outline = MaterialTheme.colorScheme.outline
    Canvas(Modifier.size(size)) {
        drawRoundRect(color, Offset.Zero, this.size, CornerRadius(2.dp.toPx()))
        if (color.luminance() > 0.7f) drawRoundRect(outline, Offset.Zero, this.size, CornerRadius(2.dp.toPx()), style = Stroke(1.dp.toPx()))
    }
}
