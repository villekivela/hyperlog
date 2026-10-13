package com.hyperlog.app.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.domain.Extension
import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.SetEntry
import com.hyperlog.app.domain.formatKg
import com.hyperlog.app.program.HoldPosition
import com.hyperlog.app.program.RepRange
import com.hyperlog.app.program.SetPrescription
import com.hyperlog.app.program.Technique
import com.hyperlog.app.program.backoffWeight
import com.hyperlog.app.session.DEFAULT_DROP_REPS
import com.hyperlog.app.session.FollowUp
import com.hyperlog.app.ui.program.TechniqueLook
import com.hyperlog.app.ui.program.symbol
import com.hyperlog.app.ui.program.techniqueName
import com.hyperlog.app.ui.program.techniqueText
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon

// Techniques in the set table (docs/design/logging-screen.md): the label under a set, then its
// drops, mini-sets or hold as 48 dp sub-rows, planned before the set and filled in as they're done.

/** What a sub-row's Target column shows: a 0 ring for a drop (to failure), else the target. */
private sealed interface SubTarget {
    data object ToFailure : SubTarget
    data class Value(val text: String) : SubTarget
}

/**
 * The technique of set [index] of the exercise: its label, then its sub-rows. [plannedKg] is the
 * weight the set is planned or typed at, for drops and mini-sets still to come.
 */
internal fun LazyListScope.techniqueRows(table: TableState, index: Int, technique: Technique?, plannedKg: Double?) {
    technique ?: return
    val current = table.current
    val set = current.sets.getOrNull(index)
    val open = set != null && table.followUp != null && index == current.sets.size - 1
    item(key = "technique-$index") { Label(technique.symbol(), techniqueText(technique), done = set != null && !open) }
    val showTarget = current.plan != null
    val logged = set?.extensions.orEmpty()
    val lastTime = current.lastTime.getOrNull(index)?.extensions.orEmpty()
    when (technique) {
        is Technique.Dropset -> {
            val lastDrops = lastTime.filter { it.kind == Extension.Kind.Drop }
            // A set closed early keeps only the drops done; one still to do or open shows them all.
            val count = if (set == null || open) maxOf(technique.drops, logged.size) else logged.size
            val weights = arrayOfNulls<Double>(count)
            var before = if (set == null) plannedKg else logged.lastOrNull()?.weightKg ?: set.weightKg
            for (j in logged.size until count) {
                val kg = if (open && j == logged.size) table.entry?.weightKg else before?.let { backoffWeight(it, technique.percent, current.ladder) }
                weights[j] = kg
                before = kg
            }
            for (j in 0 until count) {
                val last = lastDrops.getOrNull(j)?.let { "${formatKg(it.weightKg)} × ${it.reps}" }
                item(key = "ext-$index-$j") {
                    when {
                        table.corrects(index, j) -> ActiveSubRow(showTarget, SubTarget.ToFailure, last, table, weightLocked = false, correcting = table.correction!!.entry, hold = false)
                        j < logged.size -> DoneSubRow(
                            showTarget, SubTarget.ToFailure, last, logged[j].weightKg, "${logged[j].reps}",
                            onCorrect = table.onCorrect?.let { { it(index, j) } },
                            description = stringResource(R.string.correct_drop_description, j + 1, index + 1),
                        )
                        open && j == logged.size -> ActiveSubRow(showTarget, SubTarget.ToFailure, last, table, weightLocked = false)
                        else -> PlannedSubRow(showTarget, SubTarget.ToFailure, last, weights[j], "${lastDrops.getOrNull(j)?.reps ?: DEFAULT_DROP_REPS}")
                    }
                }
            }
        }
        is Technique.MyoReps -> {
            val target = SubTarget.Value("${technique.miniSetReps}")
            logged.forEachIndexed { j, mini ->
                item(key = "ext-$index-$j") {
                    if (table.corrects(index, j)) ActiveSubRow(showTarget, target, null, table, weightLocked = true, correcting = table.correction!!.entry, hold = false)
                    else DoneSubRow(
                        showTarget, target, null, mini.weightKg, "${mini.reps}",
                        onCorrect = table.onCorrect?.let { { it(index, j) } },
                        description = stringResource(R.string.correct_mini_set_description, j + 1, index + 1),
                    )
                }
            }
            when {
                open -> item(key = "ext-$index-${logged.size}") { ActiveSubRow(showTarget, target, null, table, weightLocked = true) }
                // Before the set, the first mini-set shows what is coming: its target at the set's weight.
                set == null -> item(key = "ext-$index-0") { PlannedSubRow(showTarget, target, null, plannedKg, "${technique.miniSetReps}") }
            }
        }
        is Technique.IsoHold -> {
            val target = SubTarget.Value(seconds(technique.seconds))
            val last = lastTime.firstOrNull { it.kind == Extension.Kind.Hold }?.let { seconds(it.reps) }
            when {
                logged.isNotEmpty() -> item(key = "ext-$index-0") {
                    if (table.corrects(index, 0)) ActiveSubRow(showTarget, target, last, table, weightLocked = true, correcting = table.correction!!.entry, hold = true)
                    else DoneSubRow(
                        showTarget, target, last, logged[0].weightKg, seconds(logged[0].reps),
                        onCorrect = table.onCorrect?.let { { it(index, 0) } },
                        description = stringResource(R.string.correct_hold_description, index + 1),
                    )
                }
                open -> item(key = "ext-$index-0") { ActiveSubRow(showTarget, target, last, table, weightLocked = true) }
                set == null -> item(key = "ext-$index-0") { PlannedSubRow(showTarget, target, last, plannedKg, seconds(technique.seconds)) }
            }
        }
        else -> Unit
    }
}

private fun seconds(s: Int) = "$s s"

@Composable
private fun SubRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().height(SubRowHeight).padding(horizontal = RowPad),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** The set column of a row being corrected: `edit` in primary. */
@Composable
internal fun RowScope.EditLead(description: String? = null) {
    Box(Modifier.width(ColSet).then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)) {
        SymbolIcon(Symbol.Edit, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

/** The set column of a sub-row: `subdirectory_arrow_right`, so it reads as part of the set above. */
@Composable
private fun RowScope.SubLead() {
    Box(Modifier.width(ColSet)) {
        SymbolIcon(Symbol.SubdirectoryArrowRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RowScope.SubTargetCell(show: Boolean, target: SubTarget) {
    if (!show) return
    Box(Modifier.width(ColTarget), contentAlignment = Alignment.CenterStart) {
        when (target) {
            SubTarget.ToFailure -> RirMark(0, filled = false)
            is SubTarget.Value -> Text(
                target.text,
                style = MaterialTheme.typography.bodyMedium.merge(Tnum),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RowScope.SubLast(last: String?) {
    Text(
        last.orEmpty(),
        Modifier.weight(1f),
        style = MaterialTheme.typography.bodyMedium.merge(Tnum),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** A drop or mini-set still to come: muted like every prefill. */
@Composable
private fun PlannedSubRow(showTarget: Boolean, target: SubTarget, last: String?, kg: Double?, reps: String) {
    val style = MaterialTheme.typography.titleLarge.merge(Tnum)
    val color = MaterialTheme.colorScheme.outline
    SubRow {
        SubLead()
        SubTargetCell(showTarget, target)
        SubLast(last)
        Text(kg?.let(::formatKg).orEmpty(), Modifier.width(ColKg), color = color, textAlign = TextAlign.Center, style = style)
        Text(reps, Modifier.width(ColReps), color = color, textAlign = TextAlign.Center, style = style, maxLines = 1)
        Spacer(Modifier.width(ColRir))
    }
}

/**
 * A drop or mini-set done: full contrast, ending in a check, since drops and mini-sets have no
 * RIR. A tap loads it into the pad to correct it.
 */
@Composable
private fun DoneSubRow(showTarget: Boolean, target: SubTarget, last: String?, kg: Double, reps: String, onCorrect: (() -> Unit)? = null, description: String? = null) {
    val style = MaterialTheme.typography.titleLarge.merge(Tnum)
    val color = MaterialTheme.colorScheme.onSurface
    SubRow(if (onCorrect != null) Modifier.clip(RowShape).clickable(onClickLabel = description, onClick = onCorrect).semantics { contentDescription = description.orEmpty() } else Modifier) {
        SubLead()
        SubTargetCell(showTarget, target)
        SubLast(last)
        Text(formatKg(kg), Modifier.width(ColKg), color = color, textAlign = TextAlign.Center, style = style)
        Text(reps, Modifier.width(ColReps), color = color, textAlign = TextAlign.Center, style = style, maxLines = 1)
        Box(Modifier.width(ColRir), contentAlignment = Alignment.Center) {
            SymbolIcon(Symbol.Check, stringResource(R.string.sub_row_logged), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The drop, mini-set or hold being done, highlighted like the active row. A mini-set and a hold
 * are at the set's weight, so their weight is muted and can't be tapped.
 */
@Composable
private fun ActiveSubRow(
    showTarget: Boolean,
    target: SubTarget,
    last: String?,
    table: TableState,
    weightLocked: Boolean,
    /** A logged drop, mini-set or hold being corrected, with the hold's seconds in [hold]; null for the one being done. */
    correcting: SetEntry? = null,
    hold: Boolean = table.followUp is FollowUp.Hold,
) {
    val entry = correcting ?: table.entry ?: return
    val actions = table.actions
    fun tap(field: Field) {
        actions.focus(field)
        actions.setPadVisible(true)
    }
    SubRow(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, RowShape).testTag(ActiveSubRowTag)) {
        // Being corrected: `edit` in place of the arrow, as a corrected set's number is.
        if (correcting != null) EditLead() else SubLead()
        SubTargetCell(showTarget, target)
        SubLast(last)
        if (weightLocked) {
            Text(
                entry.weight,
                Modifier.width(ColKg),
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge.merge(Tnum),
            )
        } else {
            Cell(AnnotatedString(entry.weight), focused = entry.field == Field.Weight, description = stringResource(R.string.col_kg), modifier = Modifier.width(ColKg)) { tap(Field.Weight) }
        }
        val reps = when {
            correcting == null && table.holdSeconds != null -> AnnotatedString(seconds(table.holdSeconds))
            hold -> AnnotatedString(seconds(entry.full.toIntOrNull() ?: 0))
            else -> repsAnnotated(entry)
        }
        Cell(reps, focused = entry.field == Field.Reps, description = stringResource(R.string.col_reps), modifier = Modifier.width(ColReps), height = SubCellHeight) { tap(Field.Reps) }
        Spacer(Modifier.width(ColRir))
    }
}

internal const val ActiveSubRowTag = "active-sub-row"

/**
 * The pad's strip during a drop, mini-set or hold: where you are in the set, the hold's timer,
 * and **Finish set** to close the set early.
 */
@Composable
internal fun RowScope.FollowUpStrip(followUp: FollowUp, holdRunning: Boolean, actions: PadActions) {
    val cs = MaterialTheme.colorScheme
    val (symbol, text) = when (followUp) {
        is FollowUp.Drop -> TechniqueLook.Dropset.symbol to stringResource(R.string.follow_drop, followUp.index + 1, followUp.count)
        is FollowUp.MiniSet -> TechniqueLook.MyoReps.symbol to stringResource(R.string.follow_mini_set, followUp.index + 1, followUp.max)
        is FollowUp.Hold -> TechniqueLook.IsoHold.symbol to stringResource(R.string.follow_hold, followUp.seconds)
    }
    Spacer(Modifier.width(8.dp))
    SymbolIcon(symbol, null, Modifier.size(20.dp), tint = cs.tertiary)
    Spacer(Modifier.width(8.dp))
    Text(text, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    if (followUp is FollowUp.Hold) {
        FilledTonalButton(onClick = { if (holdRunning) actions.stopHold() else actions.startHold() }) {
            SymbolIcon(if (holdRunning) Symbol.Stop else Symbol.PlayArrow, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (holdRunning) R.string.hold_stop else R.string.hold_start))
        }
    }
    TextButton(onClick = actions::finishSet) { Text(stringResource(R.string.follow_finish_set)) }
}

/** The techniques a set can be given in the session, each with the program format's defaults. */
private val Offered: List<Technique> = listOf(
    Technique.Dropset(),
    Technique.MyoReps(),
    Technique.LengthenedPartials(),
    Technique.IsoHold(seconds = 20, position = HoldPosition.Stretch),
    Technique.Failure,
)

/** A technique's numbers, for the set sheet under its name: `2 drops of 25%`, `20 s in the stretch`. */
@Composable
private fun techniqueDetail(technique: Technique): String = when (technique) {
    is Technique.Dropset -> if (technique.drops == 1) stringResource(R.string.technique_detail_drop_one, technique.percent)
    else stringResource(R.string.technique_detail_drops, technique.drops, technique.percent)
    is Technique.MyoReps -> stringResource(R.string.technique_detail_myo, technique.miniSetReps, technique.maxMiniSets)
    is Technique.LengthenedPartials -> technique.partials?.let { stringResource(R.string.technique_detail_partials, it) }
        ?: stringResource(R.string.technique_detail_partials_failure)
    is Technique.IsoHold -> when (technique.position) {
        HoldPosition.Stretch -> stringResource(R.string.technique_detail_iso_stretch, technique.seconds)
        HoldPosition.Contraction -> stringResource(R.string.technique_detail_iso_contraction, technique.seconds)
    }
    Technique.Failure -> stringResource(R.string.technique_detail_failure)
}

/**
 * A set's options, from its number: the technique (none, or one of five), and for a unilateral
 * exercise's active set, left and right apart. Picking a technique applies it and closes.
 */
@Composable
internal fun SetSheet(
    number: Int,
    technique: Technique?,
    /** Whether the set is split into left and right; null when it can't be (not unilateral, or not the active set). */
    split: Boolean?,
    onTechnique: (Technique?) -> Unit,
    onSplit: (Boolean) -> Unit,
    /** A planned set still to do: its target, to change for this session; null otherwise. */
    target: SetPrescription? = null,
    onTarget: (RepRange, Int?) -> Unit = { _, _ -> },
    /** Takes the set out of this session; null when it can't go. */
    onRemove: (() -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = Margin, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.set_sheet_title, number), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            onRemove?.let { remove ->
                TextButton(onClick = remove) {
                    SymbolIcon(Symbol.Delete, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.edit_remove_set))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        target?.let {
            TargetEditor(it, onTarget)
            Spacer(Modifier.height(16.dp))
        }
        Text(
            stringResource(R.string.set_sheet_technique),
            Modifier.padding(horizontal = Margin),
            style = MaterialTheme.typography.labelLarge,
            color = cs.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Column(Modifier.selectableGroup()) {
            TechniqueOption(null, technique == null, stringResource(R.string.set_sheet_none), stringResource(R.string.set_sheet_none_body)) { onTechnique(null) }
            Offered.forEach { offered ->
                // The set's own technique keeps its numbers; the others come with the defaults.
                val shown = technique?.takeIf { it::class == offered::class } ?: offered
                TechniqueOption(shown, technique != null && technique::class == offered::class, techniqueName(offered), techniqueDetail(shown)) { onTechnique(shown) }
            }
        }
        if (split != null) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth()
                    .toggleable(split, role = Role.Switch, onValueChange = onSplit)
                    .heightIn(min = 72.dp)
                    .padding(horizontal = Margin, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.set_sheet_split), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.set_sheet_split_body), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
                Switch(checked = split, onCheckedChange = null)
            }
        }
    }
}

@Composable
private fun TechniqueOption(technique: Technique?, selected: Boolean, name: String, detail: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth()
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(start = 12.dp, end = Margin, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, Modifier.size(48.dp))
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        technique?.let { SymbolIcon(it.symbol(), null, Modifier.size(20.dp), tint = cs.tertiary) }
    }
}
