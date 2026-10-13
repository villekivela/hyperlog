package com.hyperlog.app.ui.session

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
            stringResource(R.string.warmup_label),
            done = true,
        )
    }
    warmups.forEachIndexed { i, w ->
        item(key = "warmup-$i") {
            WarmupRow(
                n = i + 1,
                kg = w.weightKg,
                reps = w.reps,
                plates = plates,
                done = i < current.warmupsDone,
                onTick = { table.onTickWarmup(i) },
            )
        }
    }
}

@Composable
private fun WarmupRow(n: Int, kg: Double, reps: Int, plates: LadderSpec.Plates?, done: Boolean, onTick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val value = MaterialTheme.typography.titleLarge.merge(Tnum)
    // Planned looks planned, done looks done: muted like a prefill until ticked.
    val color = if (done) cs.onSurface else cs.outline
    val description = stringResource(R.string.warmup_description, n, formatKg(kg), reps)
    Row(
        Modifier.fillMaxWidth().heightIn(min = SubRowHeight)
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
        // The plates span Target and Last (a warm-up has neither), drawn as the pad draws them.
        Box(Modifier.weight(1f).padding(vertical = 4.dp)) { plates?.let { PlateLoad(it, kg, compact = true) } }
        Text(formatKg(kg), Modifier.width(ColKg), color = color, textAlign = TextAlign.Center, style = value)
        Text("$reps", Modifier.width(ColReps), color = color, textAlign = TextAlign.Center, style = value)
        Box(Modifier.width(ColRir), contentAlignment = Alignment.Center) {
            if (done) SymbolIcon(Symbol.Check, null, Modifier.size(20.dp), tint = cs.onSurfaceVariant)
        }
    }
}
