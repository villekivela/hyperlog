package com.hyperlog.app.ui.session

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

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
// neighbour and 12 dp from the RIR chip.
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

/**
 * An exercise's name at the top of the session and past-exercise screens: headlineSmall at full
 * size. Space Grotesk runs wide, so a long name wraps to a second line before it ellipsizes.
 */
@Composable
internal fun ExerciseName(name: String, modifier: Modifier = Modifier) {
    Text(
        name,
        modifier,
        style = MaterialTheme.typography.headlineSmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The fixed RIR scale (3+ calm, 1–2 warm, 0 hot): a target chip's outline in dark mode. */
internal fun rirColor(rir: Int): Color = when {
    rir <= 0 -> Color(0xFFD9645B)
    rir <= 2 -> Color(0xFFE0A84E)
    else -> Color(0xFF6BAFA9)
}

/**
 * A target chip's outline colour. In light mode each is a darker shade of its hue, so the outline
 * keeps 3:1 against every light surface in daylight; dark mode keeps [rirColor].
 */
internal fun rirChipColor(rir: Int, dark: Boolean): Color = when {
    dark -> rirColor(rir)
    rir <= 0 -> Color(0xFFD5544A)
    rir <= 2 -> Color(0xFFAA741E)
    else -> Color(0xFF4A8983)
}

/** Whether the theme's surface is dark, for the colours that don't follow the theme. */
@Composable
internal fun isDarkSurface(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

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
