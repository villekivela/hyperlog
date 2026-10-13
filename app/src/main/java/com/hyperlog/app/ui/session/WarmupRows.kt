package com.hyperlog.app.ui.session

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.domain.formatKg
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon

// Warm-ups and the plate calculator in the set table (docs/design/logging-screen.md, Warm-ups).
// Warm-ups are 48 dp rows above the first work set, muted until ticked off; they are never logged.

/** The warm-ups before the first work set: a label, then one row each. Nothing once a work set is logged. */
internal fun LazyListScope.warmupRows(table: TableState) {
    val warmups = table.warmups
    if (warmups.isEmpty()) return
    val current = table.current
    val plates = current.plates
    item(key = "warmup-label") {
        Label(
            Symbol.Stairs,
            stringResource(if (plates != null) R.string.warmup_label_plates else R.string.warmup_label),
            done = true,
        )
    }
    warmups.forEachIndexed { i, w ->
        item(key = "warmup-$i") {
            WarmupRow(
                n = i + 1,
                kg = w.weightKg,
                reps = w.reps,
                plates = plates?.let { platesText(it, w.weightKg, current.exercise.equipment) },
                done = i < current.warmupsDone,
                onTick = { table.onTickWarmup(i) },
            )
        }
    }
}

@Composable
private fun WarmupRow(n: Int, kg: Double, reps: Int, plates: String?, done: Boolean, onTick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val value = MaterialTheme.typography.titleLarge.merge(Tnum)
    // Planned looks planned, done looks done: muted like a prefill until ticked.
    val color = if (done) cs.onSurface else cs.outline
    val description = stringResource(R.string.warmup_description, n, formatKg(kg), reps)
    Row(
        Modifier.fillMaxWidth().height(SubRowHeight)
            .toggleable(value = done, role = Role.Checkbox, onValueChange = { onTick() })
            .semantics { contentDescription = description }
            .padding(horizontal = RowPad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.warmup_lead),
            Modifier.width(ColSet).clearAndSetSemantics { },
            style = MaterialTheme.typography.labelLarge,
            color = cs.onSurfaceVariant,
        )
        // The plates span Target and Last: a warm-up has neither.
        Text(
            plates.orEmpty(),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium.merge(Tnum),
            color = cs.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(formatKg(kg), Modifier.width(ColKg), color = color, textAlign = TextAlign.Center, style = value)
        Text("$reps", Modifier.width(ColReps), color = color, textAlign = TextAlign.Center, style = value)
        Box(Modifier.width(ColRir), contentAlignment = Alignment.Center) {
            if (done) SymbolIcon(Symbol.Check, null, Modifier.size(20.dp), tint = cs.onSurfaceVariant)
        }
    }
}

/**
 * The plate calculator under the active work set: the plates on each side for the weight in the
 * pad. Nothing when the plates can't make it (the weight check says so on ✓).
 */
internal fun LazyListScope.plateLine(table: TableState) {
    val plates = table.current.plates ?: return
    val kg = table.activeKg ?: return
    val side = plates.perSide(kg) ?: return
    val equipment = table.current.exercise.equipment
    item(key = "plates") { PlateLine(platesText(plates, kg, equipment)!!, perSide = side.isNotEmpty()) }
}

@Composable
private fun PlateLine(text: String, perSide: Boolean) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().height(LabelHeight).padding(start = RowPad + ColSet, end = RowPad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SymbolIcon(Symbol.Album, null, Modifier.size(20.dp), tint = color)
        Spacer(Modifier.width(8.dp))
        Text(
            if (perSide) stringResource(R.string.plates_per_side, text) else text,
            style = MaterialTheme.typography.labelLarge.merge(Tnum),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** `20 · 15`, `Empty bar`, `No plates` (a sled or machine alone); null when the plates can't make [kg]. */
@Composable
private fun platesText(plates: LadderSpec.Plates, kg: Double, equipment: Equipment): String? {
    val side = plates.perSide(kg) ?: return null
    return when {
        side.isNotEmpty() -> side.joinToString(" · ") { formatKg(it) }
        equipment == Equipment.Barbell -> stringResource(R.string.plates_empty_bar)
        else -> stringResource(R.string.plates_none)
    }
}
