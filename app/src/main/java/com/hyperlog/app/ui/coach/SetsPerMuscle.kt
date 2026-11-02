package com.hyperlog.app.ui.coach

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.coach.MuscleVolume
import com.hyperlog.app.coach.VolumeStatus
import com.hyperlog.app.program.BlockReviews
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon
import com.hyperlog.app.ui.theme.CardPadding
import com.hyperlog.app.ui.theme.GroupHead
import com.hyperlog.app.ui.theme.SegmentedList
import com.hyperlog.app.ui.theme.SegmentedRow

/** The list folds past [Folded] muscles to the first [Shown], as the block review's volume does. */
private const val Folded = 8
private const val Shown = 6

/**
 * Hard sets a week per muscle against the range (SPEC, Coaching → New program → Sets per muscle):
 * `Quads  16 sets a week · 14–21`, a focused muscle marked Focus and one outside its range `under`
 * or `over`, in words as well as colour. With [before] (a Change's review) each count reads
 * `12 → 16`, the plan before the Change, and a muscle the Change leaves alone is left out. Nothing
 * here blocks anything.
 */
@Composable
internal fun SetsPerMuscle(rows: List<MuscleVolume>, before: Map<String, Double>? = null) {
    val shownRows = if (before == null) rows else rows.filter { before[it.muscle] != it.sets }
    if (shownRows.isEmpty()) return
    GroupHead(stringResource(R.string.sets_per_muscle))
    var open by rememberSaveable { mutableStateOf(false) }
    val folds = shownRows.size > Folded
    val shown = if (folds && !open) shownRows.take(Shown) else shownRows
    val cs = MaterialTheme.colorScheme
    // Resources are read before the list: its builder isn't composable.
    val context = LocalContext.current
    val focus = stringResource(R.string.sets_per_muscle_focus)
    val under = stringResource(R.string.sets_per_muscle_under)
    val over = stringResource(R.string.sets_per_muscle_over)
    val lines = shown.map { row ->
        val sets = BlockReviews.formatSets(row.sets)
        val was = before?.let { BlockReviews.formatSets(it[row.muscle] ?: 0.0) }
        val one = sets == "1"
        val supporting = when (was) {
            null -> context.getString(if (one) R.string.sets_per_muscle_line_one else R.string.sets_per_muscle_line, sets, row.range.toString())
            else -> context.getString(if (one) R.string.sets_per_muscle_change_one else R.string.sets_per_muscle_change, was, sets, row.range.toString())
        }
        val flag = when (row.status) { VolumeStatus.Under -> under; VolumeStatus.Over -> over; VolumeStatus.Within -> null }
        // Focus is a fact about the muscle, in the accent; under and over are the warning, in the error colour.
        val mark = buildAnnotatedString {
            if (row.focused) withStyle(SpanStyle(color = cs.primary)) { append(focus) }
            if (row.focused && flag != null) append(" · ")
            if (flag != null) withStyle(SpanStyle(color = cs.error)) { append(flag) }
        }
        Triple(row, supporting, mark)
    }
    val toggle = if (open) stringResource(R.string.review_volume_fewer)
    else pluralStringResource(R.plurals.review_volume_more, shownRows.size - Shown, shownRows.size - Shown)
    SegmentedList {
        lines.forEach { (row, supporting, mark) ->
            item {
                SegmentedRow(
                    row.muscle, supporting,
                    symbol = when (row.status) { VolumeStatus.Under -> Symbol.ArrowDownward; VolumeStatus.Over -> Symbol.ArrowUpward; VolumeStatus.Within -> Symbol.Check },
                    supportingColor = if (row.status == VolumeStatus.Within) null else cs.error,
                    supportingLines = 2,
                    trailing = if (mark.isEmpty()) null else ({ Text(mark, style = MaterialTheme.typography.labelLarge) }),
                )
            }
        }
        if (folds) {
            item(onClick = { open = !open }) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = CardPadding), verticalAlignment = Alignment.CenterVertically) {
                    Text(toggle, style = MaterialTheme.typography.labelLarge, color = cs.primary)
                    Spacer(Modifier.width(4.dp))
                    SymbolIcon(if (open) Symbol.ExpandLess else Symbol.ExpandMore, null, Modifier.size(20.dp), tint = cs.primary)
                }
            }
        }
    }
}
