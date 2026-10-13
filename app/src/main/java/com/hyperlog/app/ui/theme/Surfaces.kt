package com.hyperlog.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon

// The tonal surface hierarchy (docs/design/designer-brief.md): screens sit on surfaceContainer,
// as the system's own apps do, and their sections are cards a tone or two lighter (dark) or
// whiter (light), so sections divide by tone rather than by lines.

object Tones {
    /** Behind every screen but the session's, which keeps `surface` so the pad stands off it. */
    val screen: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceContainer

    /** A section or a group of rows on a screen. */
    val card: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.run {
            if (surface.luminance() < 0.5f) surfaceBright else surfaceContainerLowest
        }

    /** A group of rows inside a bottom sheet (the sheet is surfaceContainerLow). */
    val sheetCard: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceContainerHighest

    /** The navigation bar and the bottom action strips: a tone off the screen, so they stand off it. */
    val bar: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceContainerHighest
}

/** A card's or a group's distance from the screen edge; its content adds the same, for the 24 dp text edge. */
val CardMargin = 12.dp
val CardPadding = 12.dp
private val Outer = 24.dp
private val Inner = 4.dp

/** A lone card: the expressive large radius. */
val CardShape = RoundedCornerShape(Outer)

/** Gap between the rows of a group. */
val SegmentGap = 2.dp

/** Row [index] of [count] in a group: the group's outer corners round, the joins between rows nearly square. */
fun segmentShape(index: Int, count: Int) = RoundedCornerShape(
    topStart = if (index == 0) Outer else Inner,
    topEnd = if (index == 0) Outer else Inner,
    bottomStart = if (index == count - 1) Outer else Inner,
    bottomEnd = if (index == count - 1) Outer else Inner,
)

/**
 * Row [index] of a group of [count] rows: a card segment [CardMargin] in from the edges, rows
 * [SegmentGap] apart. Its content lays out from the segment's edge; add [CardPadding] for the
 * 24 dp text edge.
 */
@Composable
fun Segment(
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    color: Color = Tones.card,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth()
            .padding(horizontal = CardMargin)
            .padding(bottom = if (index < count - 1) SegmentGap else 0.dp)
            .clip(segmentShape(index, count))
            .background(color)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick) else Modifier),
        content = content,
    )
}

/** A lone card, [CardMargin] in from the edges, with [CardPadding] inside. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    color: Color = Tones.card,
    padding: Dp = CardPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = CardMargin).clip(CardShape).background(color).padding(padding),
        content = content,
    )
}

/** A group's head: labelLarge in primary at the 24 dp edge, over the group it names. */
@Composable
fun GroupHead(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Text(
        text,
        modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = color,
    )
}

/**
 * A banner: a filled container card that stands off the screen, with its symbol, a title, an
 * optional line under it, and an action or dismiss at the end. [container] decides its weight:
 * primaryContainer for something to do (an update), tertiaryContainer for advice (time off).
 */
@Composable
fun Banner(
    symbol: Symbol,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    bodyColor: Color? = null,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    content: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    onClick: (() -> Unit)? = null,
    end: (@Composable RowScope.() -> Unit)? = null,
) {
    CompositionLocalProvider(LocalContentColor provides content) {
        Row(
            modifier.fillMaxWidth()
                .padding(horizontal = CardMargin)
                .clip(CardShape)
                .background(container)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .heightIn(min = 72.dp)
                .padding(start = CardPadding + 4.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SymbolIcon(symbol, null, Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                body?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = bodyColor ?: content.copy(alpha = 0.8f)) }
            }
            end?.invoke(this)
        }
    }
}

/** Top app bars sit on the screen's tone and lift a tone when content scrolls under them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun hyperlogTopBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = Tones.screen,
    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
)
