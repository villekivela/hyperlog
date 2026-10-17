package com.hyperlog.app.ui.theme

import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ButtonColors
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
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
private val Outer = 32.dp
private val Inner = 8.dp

/** A lone card: the expressive large radius. */
val CardShape = RoundedCornerShape(Outer)

/** Gap between the rows of a group. */
val SegmentGap = 4.dp

/** Row [index] of [count] in a group: the group's outer corners round, the joins between rows small. */
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

/**
 * Draws a card behind full-width content, [inset] in from each side: for sections whose rows
 * already keep the 24 dp text edge themselves (History's Progress, a trend's head).
 */
fun Modifier.cardBehind(color: Color, shape: Shape = CardShape, inset: Dp = CardMargin): Modifier = drawBehind {
    val i = inset.toPx()
    translate(left = i) {
        drawOutline(shape.createOutline(Size(size.width - 2 * i, size.height), layoutDirection, this), color)
    }
}

/**
 * Rows of a settings-like list on one card, [CardMargin] in, no padding: list items keep their
 * own insets (and set a transparent container).
 */
@Composable
fun Group(modifier: Modifier = Modifier, color: Color = Tones.card, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(horizontal = CardMargin).clip(CardShape).background(color), content = content)
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

/**
 * A symbol in an expressive shape (the 9-sided cookie) filled with primary container: the one
 * memorable element of an empty or first-run screen.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShapedSymbol(symbol: Symbol, modifier: Modifier = Modifier, size: Dp = 72.dp) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier.size(size).clip(MaterialShapes.Cookie9Sided.toShape()).background(cs.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        SymbolIcon(symbol, null, Modifier.size(size / 2.25f), tint = cs.onPrimaryContainer)
    }
}

/** An empty screen: the shaped symbol, a title and a line, and its ways forward, a little above the middle. */
@Composable
fun EmptyState(
    symbol: Symbol,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ShapedSymbol(symbol)
        Spacer(Modifier.height(24.dp))
        Text(interFigures(title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        actions()
        // Optical centre: sit a little above the middle.
        Spacer(Modifier.height(96.dp))
    }
}

/**
 * A screen's bottom actions (Accept, Review, Ask Claude…): a strip in [Tones.bar], rounded at the
 * top like a sheet, so it stands off the screen rather than sitting on it. It runs through the
 * gesture area.
 */
@Composable
fun BottomActions(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(start = 24.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(Tones.bar)
            .navigationBarsPadding()
            .padding(padding),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * A choice of two to four options as a connected button group (Material 3 Expressive): the picked
 * one fills and rounds fully, the rest sit on [Tones.card]. [labels] are the options' texts.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConnectedChoice(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Tones.card,
    /** Narrower for four options in a row, so `45 min` keeps its unit. */
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    /** Widths follow the labels, so one long name (`Deload + test`) keeps its words beside short ones. */
    fitLabels: Boolean = false,
    /** It sits on the accent card (primaryContainer): the picked one in white (choiceColors). */
    onAccent: Boolean = false,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        labels.forEachIndexed { i, label ->
            ToggleButton(
                checked = i == selected,
                onCheckedChange = { onSelect(i) },
                // Each button's padding counts as a few characters, so short names still get room.
                modifier = Modifier.weight(if (fitLabels) label.length + 4f else 1f).semantics { role = Role.RadioButton },
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    labels.size - 1 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = choiceColors(MaterialTheme.colorScheme, color, onAccent).let {
                    ToggleButtonDefaults.colors(containerColor = it.unchecked, contentColor = it.onUnchecked, checkedContainerColor = it.checked, checkedContentColor = it.onChecked)
                },
                contentPadding = contentPadding,
            ) { Text(label, maxLines = 1) }
        }
    }
}

/** A connected choice's colours: the checked button's fill and words, the others' fill and words. */
data class ChoiceColors(val checked: Color, val onChecked: Color, val unchecked: Color, val onUnchecked: Color)

/**
 * The colours of a connected choice on [card], or on the accent card when [onAccent]. On a neutral card the picked one is filled
 * primary (Material's toggle button). On the accent card (primaryContainer) primary would vanish in
 * light, where the two are one violet, so the picked one is filled onPrimaryContainer with the
 * card's violet for its words, as the hero's Start, and the others sit in a shaded well.
 */
fun choiceColors(scheme: ColorScheme, card: Color, onAccent: Boolean): ChoiceColors = with(scheme) {
    if (onAccent) ChoiceColors(onPrimaryContainer, primaryContainer, scrim.copy(alpha = 0.16f), onPrimaryContainer)
    else ChoiceColors(primary, onPrimary, card, onSurfaceVariant)
}

/**
 * A filled button's fill and words on the accent card (primaryContainer): onPrimaryContainer with
 * the card's violet for words, as the hero's Start. A primary button vanishes there in light,
 * where primary and primaryContainer are one violet.
 */
fun accentButton(scheme: ColorScheme): AccentButton = AccentButton(scheme.onPrimaryContainer, scheme.primaryContainer)

/** A filled button's [fill] and [words] on the accent card. */
data class AccentButton(val fill: Color, val words: Color)

/** [accentButton] as a filled button's colours. */
@Composable
fun accentButtonColors(): ButtonColors = accentButton(MaterialTheme.colorScheme).let {
    ButtonDefaults.buttonColors(containerColor = it.fill, contentColor = it.words)
}

/** Top app bars sit on the screen's tone and lift a tone when content scrolls under them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun hyperlogTopBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = Tones.screen,
    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
)

/**
 * Filter chips pick in primary container: secondary container is too close to a card's tone in a
 * grey (monochrome) palette for a choice to read.
 */
@Composable
fun selectedChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
)
