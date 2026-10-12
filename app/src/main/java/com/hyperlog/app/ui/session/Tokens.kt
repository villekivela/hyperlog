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
/** Edge of the active row's highlight; its content keeps the [Margin]. */
internal val RowInset = 16.dp
internal val RowPad = Margin - RowInset
internal val RowHeight = 64.dp
internal val LabelHeight = 32.dp
internal val ColSet = 32.dp
internal val ColKg = 64.dp
internal val ColReps = 80.dp
internal val ColRir = 24.dp
internal val CellHeight = 48.dp
internal val KeyHeight = 56.dp
internal val KeyGap = 4.dp
internal val KeyShape = RoundedCornerShape(12.dp)
internal val RowShape = RoundedCornerShape(16.dp)
internal val CellShape = RoundedCornerShape(12.dp)

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
