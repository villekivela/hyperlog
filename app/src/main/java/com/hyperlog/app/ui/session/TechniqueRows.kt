package com.hyperlog.app.ui.session

import com.hyperlog.app.ui.theme.interFigures
import com.hyperlog.app.ui.theme.Group
import com.hyperlog.app.ui.theme.Tones
import com.hyperlog.app.ui.theme.CardMargin
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

// The pad's strip during a technique, and the set sheet.

/**
 * The pad's strip during a drop, mini-set or hold: where you are in the set, the hold's timer,
 * and **Finish set** to close the set early.
 */
@Composable
internal fun RowScope.FollowUpStrip(followUp: FollowUp, holdRunning: Boolean, actions: PadActions) {
    val cs = MaterialTheme.colorScheme
    val (symbol, text) = when (followUp) {
        is FollowUp.Drop -> TechniqueLook.Dropset.symbol to stringResource(R.string.follow_drop, followUp.index + 1, followUp.count)
        is FollowUp.MiniSet -> TechniqueLook.MyoReps.symbol to stringResource(R.string.follow_mini_set, followUp.index + 1)
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
    is Technique.MyoReps -> stringResource(R.string.technique_detail_myo, technique.miniSetReps)
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
            Text(interFigures(stringResource(R.string.set_sheet_title, number)), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
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
        Group(Modifier.selectableGroup(), color = Tones.sheetCard) {
            TechniqueOption(null, technique == null, stringResource(R.string.set_sheet_none), stringResource(R.string.set_sheet_none_body)) { onTechnique(null) }
            Offered.forEach { offered ->
                // The set's own technique keeps its numbers; the others come with the defaults.
                val shown = technique?.takeIf { it::class == offered::class } ?: offered
                TechniqueOption(shown, technique != null && technique::class == offered::class, techniqueName(offered), techniqueDetail(shown)) { onTechnique(shown) }
            }
        }
        if (split != null) {
            Spacer(Modifier.height(16.dp))
            Group(color = Tones.sheetCard) {
            Row(
                Modifier.fillMaxWidth()
                    .toggleable(split, role = Role.Switch, onValueChange = onSplit)
                    .heightIn(min = 72.dp)
                    .padding(horizontal = Margin - CardMargin, vertical = 8.dp),
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
}

@Composable
private fun TechniqueOption(technique: Technique?, selected: Boolean, name: String, detail: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    // The picked technique is lifted into secondary container, as a picked situation is.
    Row(
        Modifier.fillMaxWidth()
            .then(if (selected) Modifier.background(cs.secondaryContainer) else Modifier)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(start = 0.dp, end = Margin - CardMargin, top = 4.dp, bottom = 4.dp),
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
