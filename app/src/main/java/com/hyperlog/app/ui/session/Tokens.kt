package com.hyperlog.app.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Tokens from docs/design/logging-screen.md: an 8 dp grid with 4 dp half steps.

/** Left edge of all text: exercise name, set numbers, Next line, sheets. */
internal val Margin = 24.dp
/** Cards on the session screen (the ladder offer) sit [TableMargin] from the screen edge. */
internal val TableMargin = 12.dp
/** Edge of a tappable row's ripple on the flat table; its content keeps the [Margin]. */
internal val TapInset = 16.dp
internal val RowPad = Margin - TapInset
internal val RowHeight = 64.dp
internal val LabelHeight = 32.dp
/** Drops, myo mini-sets and holds under their set. */
internal val SubRowHeight = 48.dp
/** A tappable value in a sub-row, inside its 48 dp. */
internal val SubCellHeight = 40.dp
/** The index column: `W` or the set number on a [IndexSize] circle. */
internal val ColIndex = 40.dp
internal val IndexSize = 32.dp
/** Kept for the prescription sheet's set numbers. */
internal val ColSet = 32.dp
// kg and Reps cells sit [CellInset] inside their columns, so a focused cell keeps 8 dp from its
// neighbour and 12 dp from the RIR dot.
internal val ColKg = 72.dp
internal val ColReps = 80.dp
internal val ColRir = 36.dp
/** The check column at the end of every row. */
internal val ColCheck = 40.dp
internal val CellInset = 4.dp
/** A cell's value keeps this clear of its outline on each side. */
internal val CellPad = 8.dp
internal val CellHeight = 48.dp
internal val KeyHeight = 56.dp
internal val KeyGap = 4.dp
internal val KeyShape = RoundedCornerShape(12.dp)
/** A tappable row's ripple. No row is filled, the active one included. */
internal val RowRipple = RoundedCornerShape(24.dp)
internal val CellShape = RoundedCornerShape(12.dp)
/** A chip under the exercise name. */
internal val ChipShape = RoundedCornerShape(8.dp)

/** Tabular figures, so columns of numbers line up. */
internal val Tnum = TextStyle(fontFeatureSettings = "tnum")

/** The fixed RIR scale (3+ calm, 1–2 warm, 0 hot). It does not follow the dynamic theme. */
internal fun rirColor(rir: Int): Color = when {
    rir <= 0 -> Color(0xFFD9645B)
    rir <= 2 -> Color(0xFFE0A84E)
    else -> Color(0xFF6BAFA9)
}

/** An RIR mark: a ring is a target, a filled dot is a result or the selection. */
@Composable
internal fun RirMark(rir: Int, filled: Boolean, modifier: Modifier = Modifier) {
    val color = rirColor(rir)
    Box(
        modifier.size(20.dp).then(
            if (filled) Modifier.background(color, CircleShape) else Modifier.border(2.dp, color, CircleShape),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (rir >= 4) "4+" else "$rir",
            style = MaterialTheme.typography.labelSmall.merge(Tnum),
            fontSize = if (rir >= 4) 9.sp else 11.sp,
            color = if (filled) Color.Black.copy(alpha = 0.87f) else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** Which way a suggestion moves the weight, for the why-line's symbol. */
internal enum class Direction { Up, Flat, Down }

/** The fixed trend scale, like RIR's: calm green up, neutral for the same weight, amber down (a deload is no failure, so never red). */
internal val Direction.color: Color
    get() = when (this) {
        Direction.Up -> Color(0xFF5FAE7A)
        Direction.Flat -> Color(0xFF9AA1A9)
        Direction.Down -> Color(0xFFE0A84E)
    }

internal val Direction.symbol: com.hyperlog.app.ui.symbols.Symbol
    get() = when (this) {
        Direction.Up -> com.hyperlog.app.ui.symbols.Symbol.TrendingUp
        Direction.Flat -> com.hyperlog.app.ui.symbols.Symbol.TrendingFlat
        Direction.Down -> com.hyperlog.app.ui.symbols.Symbol.TrendingDown
    }

/** The direction of [this] suggestion against [from], the set before's weight (none for a first set). */
internal fun com.hyperlog.app.progression.Suggestion.direction(from: Double?): Direction = when (kind) {
    com.hyperlog.app.progression.Suggestion.Kind.Up -> Direction.Up
    com.hyperlog.app.progression.Suggestion.Kind.Down, com.hyperlog.app.progression.Suggestion.Kind.Deload -> Direction.Down
    com.hyperlog.app.progression.Suggestion.Kind.Adjust -> if (from == null || kg > from) Direction.Up else Direction.Down
    else -> Direction.Flat
}
