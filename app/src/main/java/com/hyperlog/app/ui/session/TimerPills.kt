package com.hyperlog.app.ui.session

import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon

// The header's timer pills (docs/design/logging-screen.md): rest, a myo breathing pause and an iso hold.

/**
 * The one clock. During rest it drains left to right; after rest it counts up in a muted
 * container until the next set is logged.
 */
@Composable
internal fun RestPill(leftMs: Long, totalMs: Long, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val over = leftMs <= 0
    TimerPill(
        Symbol.Timer, stringResource(R.string.rest_title), clock(leftMs),
        fraction = if (over || totalMs <= 0) 0f else leftMs.toFloat() / totalMs,
        container = if (over) cs.surfaceContainerHigh else cs.secondaryContainer,
        content = if (over) cs.onSurfaceVariant else cs.onSecondaryContainer,
        onClick = onClick,
    )
}

/** The 15 s breathing pause before a myo mini-set, drained like rest, with `air`. */
@Composable
internal fun BreathPill(leftMs: Long, totalMs: Long) {
    TimerPill(
        Symbol.Air, stringResource(R.string.breathing), clock(leftMs),
        fraction = if (totalMs <= 0) 0f else leftMs.toFloat() / totalMs,
    )
}

/** The iso hold's timer: it counts up and fills toward the target; past it, it reads in primary. */
@Composable
internal fun HoldPill(heldMs: Long, targetMs: Long) {
    val cs = MaterialTheme.colorScheme
    val reached = heldMs >= targetMs
    HoldReachedBuzz(reached)
    TimerPill(
        Symbol.PanTool, stringResource(R.string.technique_iso_name), clock(-heldMs).removePrefix("+"),
        fraction = if (reached) 0f else if (targetMs <= 0) 1f else heldMs.toFloat() / targetMs,
        container = if (reached) cs.primary else cs.secondaryContainer,
        content = if (reached) cs.onPrimary else cs.onSecondaryContainer,
    )
}

/**
 * The header's timer pill, for rest, a breathing pause and a hold: [symbol] and [time] on a
 * 40 dp pill, filled from the left to [fraction] of its width. [onClick] null: it can't be tapped.
 */
@Composable
private fun TimerPill(
    symbol: Symbol,
    description: String,
    time: String,
    fraction: Float,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    onClick: (() -> Unit)? = null,
) {
    val fill = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
    val filled = fraction.coerceIn(0f, 1f)
    val inside = @Composable {
        Row(
            Modifier.drawBehind { drawRect(fill, size = Size(size.width * filled, size.height)) }.height(40.dp).padding(start = 12.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SymbolIcon(symbol, description, Modifier.width(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(time, style = MaterialTheme.typography.titleMedium.merge(Tnum))
        }
    }
    if (onClick != null) Surface(onClick = onClick, shape = CircleShape, color = container, contentColor = content) { inside() }
    else Surface(shape = CircleShape, color = container, contentColor = content) { inside() }
}

/** Buzzes once when the hold reaches its target. */
@Composable
private fun HoldReachedBuzz(reached: Boolean) {
    val context = LocalContext.current
    LaunchedEffect(reached) {
        if (reached) context.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(350, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}

/** `1:42` while counting down, `+0:12` after. */
internal fun clock(leftMs: Long): String {
    val over = leftMs <= 0
    val ms = if (over) -leftMs else leftMs
    val secs = if (over) ms / 1000 else (ms + 999) / 1000
    return (if (over) "+" else "") + "%d:%02d".format(secs / 60, secs % 60)
}
