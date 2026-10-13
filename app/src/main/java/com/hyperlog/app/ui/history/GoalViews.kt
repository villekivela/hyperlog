package com.hyperlog.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.domain.formatKg
import com.hyperlog.app.history.LiftGoal
import com.hyperlog.app.ui.session.Margin
import com.hyperlog.app.ui.session.Tnum
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// Lift goals (docs/design/history.md, Lift goals): the estimate leads, the goal follows it in ink.

/** `90 kg  of 100 kg`: the estimate in [figure] type, the goal after it, smaller and muted. */
@Composable
internal fun GoalFigure(estimateKg: Double?, goal: LiftGoal, figure: TextStyle, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(modifier) {
        Text(estimateKg?.let { "${estimateText(it)} kg" } ?: "–", Modifier.alignByBaseline(), style = figure)
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.goal_of, formatKg(goal.kg)),
            Modifier.alignByBaseline(),
            style = MaterialTheme.typography.titleMedium.merge(Tnum),
            color = cs.onSurfaceVariant,
        )
    }
}

/** `10 kg to go · by Thu 31 Dec`, `Goal reached`, or `No estimate yet` before one. */
@Composable
internal fun goalStatus(estimateKg: Double?, goal: LiftGoal): String {
    val by = goal.by?.let { stringResource(R.string.goal_by, long(it)) }
    val status = when {
        estimateKg == null -> stringResource(R.string.goal_no_estimate)
        estimateKg >= goal.kg -> stringResource(R.string.goal_reached)
        // Worked out from the figure as shown, so the two add up.
        else -> (goal.kg - estimateText(estimateKg).toDouble()).let { left ->
            if (left <= 0) stringResource(R.string.goal_nearly) else stringResource(R.string.goal_to_go, formatKg(left))
        }
    }
    return listOfNotNull(status, by).joinToString(" · ")
}

/** The flag and [goalStatus], one 32 dp row in muted ink. */
@Composable
internal fun GoalStatusRow(estimateKg: Double?, goal: LiftGoal, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val reached = estimateKg != null && estimateKg >= goal.kg
    Row(modifier.heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        SymbolIcon(if (reached) Symbol.CheckCircle else Symbol.Flag, null, Modifier.size(16.dp), tint = if (reached) cs.primary else cs.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(goalStatus(estimateKg, goal), style = MaterialTheme.typography.bodyMedium.merge(Tnum), color = cs.onSurfaceVariant)
    }
}

/** The goal line's direct label on a chart: `Goal 100`. */
@Composable
internal fun goalLabel(goal: LiftGoal) = stringResource(R.string.goal_line, formatKg(goal.kg))

/**
 * Sets or changes a lift goal in place (a text field in a dialog never idles under Robolectric):
 * the target in kg, an optional date from the date picker, Save, and Remove for a goal there is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GoalEditor(
    goal: LiftGoal?,
    estimateKg: Double?,
    onSave: (Double, LocalDate?) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(goal?.kg?.let(::formatKg).orEmpty()) }
    var by by rememberSaveable { mutableStateOf(goal?.by?.toEpochDay()) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val kg = text.replace(',', '.').toDoubleOrNull()?.takeIf { it in 1.0..1000.0 }
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(start = Margin, end = 8.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.goal_field)) },
                placeholder = { Text(estimateKg?.let { formatKg(Math.ceil(it / 5) * 5) }.orEmpty()) },
                suffix = { Text("kg") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { kg?.let { onSave(it, by?.let(LocalDate::ofEpochDay)) } }, enabled = kg != null) { Text(stringResource(R.string.history_save)) }
            IconButton(onClick = onDismiss) { SymbolIcon(Symbol.Close, stringResource(R.string.history_cancel)) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            val day = by
            if (day == null) {
                TextButton(onClick = { picking = true }) {
                    SymbolIcon(Symbol.CalendarToday, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.goal_add_date))
                }
            } else {
                InputChip(
                    selected = false,
                    onClick = { picking = true },
                    label = { Text(stringResource(R.string.goal_by_chip, long(LocalDate.ofEpochDay(day)))) },
                    trailingIcon = {
                        IconButton(onClick = { by = null }, Modifier.size(24.dp)) {
                            SymbolIcon(Symbol.Close, stringResource(R.string.goal_remove_date), Modifier.size(18.dp))
                        }
                    },
                )
            }
            if (goal != null) {
                TextButton(onClick = onRemove) { Text(stringResource(R.string.goal_remove), color = cs.error) }
            }
        }
    }
    if (picking) {
        // The picker works in UTC midnights.
        val state = rememberDatePickerState(
            initialSelectedDateMillis = by?.let { LocalDate.ofEpochDay(it).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { by = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }
                    picking = false
                }) { Text(stringResource(R.string.goal_pick_date)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.history_cancel)) } },
        ) { DatePicker(state) }
    }
}
