package com.hyperlog.app.ui.session

import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.HorizontalDivider
import com.hyperlog.app.domain.LadderSpec
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperlog.app.R
import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.PadKey
import com.hyperlog.app.domain.SetEntry
import com.hyperlog.app.domain.WeightCheck
import com.hyperlog.app.domain.WeightLadder
import com.hyperlog.app.domain.formatKg
import com.hyperlog.app.session.FollowUp
import com.hyperlog.app.session.Correction
import com.hyperlog.app.domain.Extension
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon

/**
 * The compact pad, drawn like the system number keyboard: a strip with the set's decisions
 * (weight rungs, or RIR and full/partial), then four rows of four equal keys. Its surface runs
 * through the gesture area.
 */
internal const val PlateStripTag = "plate-strip"

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun Pad(
    entry: SetEntry,
    ladder: WeightLadder,
    check: WeightCheck?,
    actions: PadActions,
    modifier: Modifier = Modifier,
    /** The open set's drop, mini-set or hold being typed; its strip shows progress and Finish set. */
    followUp: FollowUp? = null,
    holdRunning: Boolean = false,
    canConfirm: Boolean = entry.canConfirm,
    /** The logged row being corrected: the hide key cancels it, and ✓ saves it. */
    correction: Correction? = null,
    /** The bar or machine's plates: drawn at the top while the weight is typed; null for none. */
    plates: LadderSpec.Plates? = null,
    /** The plates load a machine (its starting weight is the base), not a bar. */
    platesOnMachine: Boolean = false,
    /** A pin stack's loading for a weight, drawn at the top like the plates; null for any other equipment. */
    pinLoad: ((Double) -> com.hyperlog.app.domain.PinLoad?)? = null,
    /** The pad's top row: Next. */
    top: @Composable () -> Unit = {},
    /** A warm-up is on the pad, by index and count: no RIR, the hide key goes back to the set, ✓ ticks it. */
    warmup: Pair<Int, Int>? = null,
) {
    val onWeight = entry.field == Field.Weight
    val cs = MaterialTheme.colorScheme
    var wholeBar by remember { mutableStateOf(false) }
    if (wholeBar && plates != null) {
        com.hyperlog.app.ui.theme.Sheet(onDismiss = { wholeBar = false }) {
            WholeBar(plates, entry.weightKg, Modifier.padding(horizontal = Margin).padding(bottom = 24.dp).navigationBarsPadding(), machine = platesOnMachine)
        }
    }
    Surface(color = cs.surfaceContainer, modifier = modifier.testTag(PadTag)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp)) {
            top()
            // The plate calculator, at the top of the pad while a weight is typed, live.
            // Shown on the reps too, so the pad keeps its height from the weight to the reps.
            if (plates != null && check == null) {
                // A tap opens the whole bar, both sides with every plate.
                PlateLoad(plates, entry.weightKg, machine = platesOnMachine, modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClickLabel = stringResource(R.string.plates_show_whole)) { wholeBar = true }.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 4.dp).testTag(PlateStripTag))
                HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = cs.outlineVariant)
            }
            if (pinLoad != null && plates == null && check == null) {
                StackLoad(entry.weightKg?.let(pinLoad), entry.weightKg, Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 4.dp))
                HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = cs.outlineVariant)
            }
            Row(Modifier.fillMaxWidth().height(KeyHeight), verticalAlignment = Alignment.CenterVertically) {
                val typed = entry.weightKg
                if (check != null) {
                    WeightCheckStrip(check, actions)
                } else if (warmup != null && !onWeight) {
                    WarmupStrip(warmup.first, warmup.second)
                } else if (correction?.kind != null && !onWeight) {
                    CorrectionStrip(correction)
                } else if (followUp != null && !onWeight) {
                    FollowUpStrip(followUp, holdRunning, actions)
                } else if (onWeight && typed == null) {
                    // Nothing to place on the ladder yet: the strip stays empty until a weight is typed.
                    Spacer(Modifier.weight(1f))
                } else if (onWeight && typed != null) {
                    // The equipment's real weights around the one typed.
                    ladder.around(typed).forEach { kg ->
                        val label = formatKg(kg)
                        StripChip(label == entry.weight, Modifier.weight(1f), label, onClick = { actions.pickWeight(kg) }) {
                            Text(label, style = MaterialTheme.typography.titleSmall.merge(Tnum))
                        }
                    }
                } else {
                    val rirLabel = stringResource(R.string.pad_rir)
                    (0..4).forEach { v ->
                        StripChip(entry.rir == v, Modifier.weight(1f), "$rirLabel ${if (v == 4) "4+" else v}", onClick = { actions.selectRir(v) }) {
                            RirMark(v, filled = true)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    FullPartialToggle(entry.partialMode, onChange = actions::partialMode)
                }
            }
            Spacer(Modifier.height(KeyGap))
            Column(verticalArrangement = Arrangement.spacedBy(KeyGap)) {
                KeyRow { DigitKey(1, actions); DigitKey(2, actions); DigitKey(3, actions); SymbolKey(Symbol.Backspace, R.string.pad_backspace) { actions.press(PadKey.Backspace) } }
                KeyRow { DigitKey(4, actions); DigitKey(5, actions); DigitKey(6, actions); SymbolKey(Symbol.Remove, R.string.pad_minus) { actions.press(PadKey.Minus) } }
                KeyRow { DigitKey(7, actions); DigitKey(8, actions); DigitKey(9, actions); SymbolKey(Symbol.Add, R.string.pad_plus) { actions.press(PadKey.Plus) } }
                KeyRow {
                    // A correction can only be saved or cancelled, so the hide key cancels it.
                    if (correction != null) SymbolKey(Symbol.Close, R.string.pad_cancel_correction) { actions.cancelCorrection() }
                    else if (warmup != null) SymbolKey(Symbol.Close, R.string.pad_close_warmup) { actions.closeWarmup() }
                    else SymbolKey(Symbol.KeyboardHide, R.string.pad_hide) { actions.setPadVisible(false) }
                    DigitKey(0, actions)
                    if (onWeight) TextKey(".", stringResource(R.string.pad_dot)) { actions.press(PadKey.Dot) } else Spacer(Modifier.weight(1f))
                    DoneKey(entry, actions, keeps = check != null, enabled = canConfirm, saves = correction != null, warmup = warmup != null)
                }
            }
        }
    }
}

/**
 * The weight check in place of the strip: the reason in the error colour, then three real
 * weights. ✓ turns error-coloured too, because the next ✓ keeps the weight as typed.
 */
@Composable
private fun androidx.compose.foundation.layout.RowScope.WeightCheckStrip(check: WeightCheck, actions: PadActions) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.weight(1.5f).padding(start = 8.dp).testTag(WeightCheckTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SymbolIcon(Symbol.Warning, contentDescription = null, tint = cs.error, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(check.reason, style = MaterialTheme.typography.labelLarge, color = cs.error, maxLines = 2)
    }
    check.options.forEach { kg ->
        val label = formatKg(kg)
        StripChip(false, Modifier.weight(1f), label, onClick = { actions.pickWeight(kg) }, outlined = true) {
            Text(label, style = MaterialTheme.typography.titleSmall.merge(Tnum))
        }
    }
}

/**
 * The strip while a drop, mini-set or hold is corrected: which one it is, after `edit`. It has no
 * RIR to pick, since drops and mini-sets are taken to failure.
 */
@Composable
private fun androidx.compose.foundation.layout.RowScope.CorrectionStrip(correction: Correction) {
    val n = (correction.extension ?: 0) + 1
    val text = when (correction.kind) {
        Extension.Kind.Drop -> stringResource(R.string.correct_strip_drop, n, correction.set + 1)
        Extension.Kind.MiniSet -> stringResource(R.string.correct_strip_mini_set, n, correction.set + 1)
        else -> stringResource(R.string.correct_strip_hold, correction.set + 1)
    }
    Spacer(Modifier.width(8.dp))
    SymbolIcon(Symbol.Edit, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.width(8.dp))
    Text(text, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, maxLines = 1)
}

/** The pad folded down to its bottom corners: show the pad, and ✓ to log the set as shown. */
@Composable
internal fun HiddenPad(entry: SetEntry, actions: PadActions, modifier: Modifier = Modifier, canConfirm: Boolean = entry.canConfirm, top: @Composable () -> Unit = {}) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.testTag(PadTag)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp)) {
        top()
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KeyGap),
        ) {
            SymbolKey(Symbol.Keyboard, R.string.pad_show) { actions.setPadVisible(true) }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.weight(1f))
            DoneKey(entry, actions, keeps = false, enabled = canConfirm)
        }
        }
    }
}

/** The strip while a warm-up's reps are typed: which warm-up it is, after `stairs`. A warm-up has no RIR. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.WarmupStrip(index: Int, count: Int) {
    Spacer(Modifier.width(8.dp))
    SymbolIcon(Symbol.Stairs, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.width(8.dp))
    Text(stringResource(R.string.warmup_strip, index + 1, count), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, maxLines = 1)
}

internal const val PadTag = "pad"
internal const val DoneKeyTag = "pad-done"
internal const val WeightCheckTag = "weight-check"


@Composable
private fun KeyRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(KeyGap), content = content)
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.DigitKey(value: Int, actions: PadActions) {
    TextKey("$value", "$value") { actions.press(PadKey.Digit(value)) }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TextKey(label: String, description: String, onClick: () -> Unit) {
    Key(description, onClick = onClick) { Text(label, fontSize = 24.sp) }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SymbolKey(symbol: Symbol, description: Int, onClick: () -> Unit) {
    Key(stringResource(description), onClick = onClick) { SymbolIcon(symbol, contentDescription = null) }
}

/** ✓: one key, the same size as the others, filled primary. On the weight it reads →. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.DoneKey(entry: SetEntry, actions: PadActions, keeps: Boolean, enabled: Boolean, saves: Boolean = false, warmup: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    val onWeight = entry.field == Field.Weight
    Key(
        stringResource(if (onWeight) R.string.pad_next else if (saves) R.string.pad_save_correction else if (warmup) R.string.pad_tick_warmup else R.string.pad_log),
        modifier = Modifier.testTag(DoneKeyTag),
        container = when { keeps -> cs.error; enabled -> cs.primary; else -> cs.surfaceContainerHigh },
        content = when { keeps -> cs.onError; enabled -> cs.onPrimary; else -> cs.outline },
        onClick = actions::done,
    ) {
        SymbolIcon(if (onWeight) Symbol.ArrowForward else Symbol.Check, contentDescription = null)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Key(
    description: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    content: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
) {
    val view = LocalView.current
    Surface(
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onClick()
        },
        shape = KeyShape,
        color = container,
        contentColor = content,
        modifier = modifier.weight(1f).height(KeyHeight).semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) { label() }
    }
}

@Composable
private fun StripChip(
    selected: Boolean,
    modifier: Modifier,
    description: String,
    onClick: () -> Unit,
    outlined: Boolean = false,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        border = if (outlined) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
        modifier = modifier.height(40.dp).padding(horizontal = 4.dp).semantics {
            contentDescription = description
            this.selected = selected
        },
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/** F types full reps, P types partials into the same cell. */
@Composable
private fun FullPartialToggle(partial: Boolean, onChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.height(40.dp).background(cs.surfaceContainerHighest, CircleShape).padding(4.dp)) {
        listOf(false to R.string.pad_full, true to R.string.pad_partial).forEach { (p, label) ->
            val description = stringResource(if (p) R.string.pad_partial_description else R.string.pad_full_description)
            Surface(
                onClick = { onChange(p) },
                shape = CircleShape,
                color = if (partial == p) cs.primary else Color.Transparent,
                contentColor = if (partial == p) cs.onPrimary else cs.onSurface,
                modifier = Modifier.width(48.dp).fillMaxHeight().semantics {
                    contentDescription = description
                    role = Role.Tab
                    selected = partial == p
                },
            ) {
                Box(contentAlignment = Alignment.Center) { Text(stringResource(label), style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}
