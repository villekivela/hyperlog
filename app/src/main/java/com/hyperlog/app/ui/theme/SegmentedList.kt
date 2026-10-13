package com.hyperlog.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon

// The grouped list of Android 16's Settings, shared by every list screen (Today's exercises,
// Settings, Gyms and equipment, the library, Program, past sessions): each row its own tile,
// SegmentGap apart, the group's outer corners large and its joins small (segmentShape), groups
// 16 dp apart. Rows are Segments, so every list draws the same.

/** What a [SegmentedList] holds: its rows, in order. */
class SegmentedListScope internal constructor() {
    internal val rows = mutableListOf<@Composable ColumnScope.() -> Unit>()
    internal val clicks = mutableListOf<(() -> Unit)?>()
    internal val labels = mutableListOf<String?>()

    /** A standard row: [symbol] in a tonal circle, [title], one line of [supporting], and [trailing]. */
    fun row(
        title: String,
        supporting: String? = null,
        symbol: Symbol? = null,
        onClick: (() -> Unit)? = null,
        supportingColor: Color? = null,
        titleColor: Color? = null,
        trailing: (@Composable () -> Unit)? = null,
    ) = item(onClick) { SegmentedRow(title, supporting, symbol, supportingColor, titleColor, trailing) }

    /** A row of its own layout, on the same tile. */
    fun item(onClick: (() -> Unit)? = null, onClickLabel: String? = null, content: @Composable ColumnScope.() -> Unit) {
        rows += content
        clicks += onClick
        labels += onClickLabel
    }
}

/** A group of rows as separate tiles, the group's first and last rounded large (Android 16 Settings). */
@Composable
fun SegmentedList(modifier: Modifier = Modifier, color: Color = Tones.card, content: SegmentedListScope.() -> Unit) {
    // The rows are gathered first, then laid out with their places in the group. Read resources
    // before the list: the builder isn't composable, so a row never misses a change.
    val scope = SegmentedListScope().apply(content)
    val count = scope.rows.size
    Column(modifier.fillMaxWidth()) {
        scope.rows.forEachIndexed { i, row ->
            Segment(i, count, color = color, onClick = scope.clicks[i], onClickLabel = scope.labels[i], content = row)
        }
    }
}

/** The gap between two groups. */
val GroupGap = 16.dp

/**
 * One row's content: a leading Material Symbol in a tonal circle (or its 40 dp column kept empty),
 * a title and one line of supporting text, and an optional trailing control.
 */
@Composable
fun SegmentedRow(
    title: String,
    supporting: String? = null,
    symbol: Symbol? = null,
    supportingColor: Color? = null,
    titleColor: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).then(if (symbol != null) Modifier.background(cs.secondaryContainer) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (symbol != null) SymbolIcon(symbol, null, Modifier.size(24.dp), tint = cs.onSecondaryContainer)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor ?: cs.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodyMedium.merge(com.hyperlog.app.ui.session.Tnum),
                    color = supportingColor ?: cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(16.dp))
            trailing()
        }
    }
}
