package com.hyperlog.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.coach.AboutMe
import com.hyperlog.app.coach.AcrossBlock
import com.hyperlog.app.coach.AgeBand
import com.hyperlog.app.coach.BodyArea
import com.hyperlog.app.coach.FailureRule
import com.hyperlog.app.coach.Frequency
import com.hyperlog.app.coach.NumberRange
import com.hyperlog.app.coach.Progress
import com.hyperlog.app.coach.TrainingGoal
import com.hyperlog.app.coach.TrainingStyle
import com.hyperlog.app.coach.TrainingStyleJson
import com.hyperlog.app.coach.TrainingTargets
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.search
import com.hyperlog.app.ui.library.SearchField
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon
import com.hyperlog.app.ui.theme.CardMargin
import com.hyperlog.app.ui.theme.CardPadding
import com.hyperlog.app.ui.theme.ConnectedChoice
import com.hyperlog.app.ui.theme.GroupGap
import com.hyperlog.app.ui.theme.GroupHead
import com.hyperlog.app.ui.theme.Segment
import com.hyperlog.app.ui.theme.SegmentedList
import com.hyperlog.app.ui.theme.SegmentedRow
import com.hyperlog.app.ui.theme.Sheet
import com.hyperlog.app.ui.theme.TextEdge
import com.hyperlog.app.ui.theme.Tones
import com.hyperlog.app.ui.theme.hyperlogTopBarColors
import com.hyperlog.app.ui.theme.selectedChipColors

/** The volume choices: Low, Moderate and High, each a range of weekly sets per muscle; any other range is Custom. */
val VolumeChoices = listOf(NumberRange(4, 8), NumberRange(8, 14), NumberRange(14, 20))

/**
 * Settings → Training style (SPEC, Coaching → Training style): the training targets, How I train
 * with Save and Back to default, and About me, each field edited in place and saved as it changes.
 * Text fields stay on the page, not in dialogs, where Robolectric's tests can't type.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingStyleScreen(
    style: TrainingStyle,
    edited: Boolean,
    library: List<Exercise>,
    onTargets: (TrainingTargets) -> Unit,
    onAboutMe: (AboutMe) -> Unit,
    onHowITrain: (String) -> Unit,
    onDefaultHowITrain: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        containerColor = Tones.screen,
        modifier = modifier,
        topBar = {
            TopAppBar(
                colors = hyperlogTopBarColors(),
                navigationIcon = { IconButton(onClick = onBack) { SymbolIcon(Symbol.ArrowBack, stringResource(R.string.back)) } },
                title = { Text(stringResource(R.string.training_style)) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
            Targets(style.targets, onTargets)
            GroupHead(stringResource(R.string.how_i_train))
            HowITrain(style.howITrain, edited, onHowITrain, onDefaultHowITrain)
            GroupHead(stringResource(R.string.about_me))
            AboutMeFields(style.aboutMe, library, onAboutMe, onPick = { picking = it })
            Spacer(Modifier.navigationBarsPadding().height(GroupGap))
        }
    }
    picking?.let { list ->
        val me = style.aboutMe
        val taken = me.avoid + me.favour
        Sheet(onDismiss = { picking = null }) {
            ExercisePick(
                library.filter { !it.hidden && it.id !in taken },
                stringResource(if (list == AVOID) R.string.style_avoid_pick else R.string.style_favour_pick),
                onPick = { id ->
                    onAboutMe(if (list == AVOID) me.copy(avoid = me.avoid + id) else me.copy(favour = me.favour + id))
                    picking = null
                },
            )
        }
    }
}

private const val AVOID = "avoid"
private const val FAVOUR = "favour"

@Composable
private fun Targets(t: TrainingTargets, onTargets: (TrainingTargets) -> Unit) {
    GroupHead(stringResource(R.string.style_targets))
    val howHard = stringResource(R.string.style_how_hard)
    val howHardRule = stringResource(R.string.style_how_hard_rule)
    SegmentedList {
        item {
            Labelled(stringResource(R.string.style_goal), stringResource(R.string.style_goal_about)) {
                ConnectedChoice(
                    listOf(stringResource(R.string.style_goal_hypertrophy), stringResource(R.string.style_goal_strength), stringResource(R.string.style_goal_both)),
                    TrainingGoal.entries.indexOf(t.goal),
                    onSelect = { onTargets(t.copy(goal = TrainingGoal.entries[it])) },
                )
            }
        }
        item {
            val choice = VolumeChoices.indexOf(t.volume).takeIf { it >= 0 } ?: VolumeChoices.size
            var custom by rememberSaveable { mutableStateOf(false) }
            Labelled(stringResource(R.string.style_volume), stringResource(R.string.style_volume_about, t.volume.toString())) {
                ConnectedChoice(
                    listOf(stringResource(R.string.style_volume_low), stringResource(R.string.style_volume_moderate), stringResource(R.string.style_volume_high), stringResource(R.string.style_volume_custom)),
                    if (custom) VolumeChoices.size else choice,
                    onSelect = {
                        custom = it == VolumeChoices.size
                        if (!custom) onTargets(t.copy(volume = VolumeChoices[it]))
                    },
                    fitLabels = true,
                )
                if (custom || choice == VolumeChoices.size) {
                    RangeFields(
                        t.volume, TrainingStyleJson.VOLUME,
                        stringResource(R.string.style_volume_low_end), stringResource(R.string.style_volume_high_end),
                        stringResource(R.string.style_volume_wrong),
                        onRange = { onTargets(t.copy(volume = it)) },
                    )
                }
            }
        }
        item {
            Labelled(stringResource(R.string.style_across), stringResource(if (t.acrossBlock == AcrossBlock.Steady) R.string.style_across_steady_about else R.string.style_across_rising_about)) {
                ConnectedChoice(
                    listOf(stringResource(R.string.style_across_steady), stringResource(R.string.style_across_rising)),
                    AcrossBlock.entries.indexOf(t.acrossBlock),
                    onSelect = { onTargets(t.copy(acrossBlock = AcrossBlock.entries[it])) },
                )
            }
        }
        item {
            Labelled(stringResource(R.string.style_frequency), stringResource(R.string.style_frequency_about)) {
                ConnectedChoice(
                    Frequency.entries.map { it.key.replace("-", "–") },
                    Frequency.entries.indexOf(t.frequency),
                    onSelect = { onTargets(t.copy(frequency = Frequency.entries[it])) },
                )
            }
        }
        row(howHard, howHardRule, Symbol.LocalFireDepartment, supportingLines = 2)
        FailureRule.entries.forEach { rule ->
            val (title, about) = when (rule) {
                FailureRule.IsolationAndMachines -> R.string.style_failure_safe to R.string.style_failure_safe_about
                FailureRule.Never -> R.string.style_failure_never to R.string.style_failure_never_about
                FailureRule.AnyLift -> R.string.style_failure_any to R.string.style_failure_any_about
            }
            item {
                Box(Modifier.selectable(t.failure == rule, role = Role.RadioButton, onClick = { onTargets(t.copy(failure = rule)) })) {
                    SegmentedRow(
                        stringResource(title), stringResource(about), if (rule == FailureRule.entries.first()) Symbol.Flag else null,
                        supportingLines = 2, selected = false,
                        trailing = { RadioButton(selected = t.failure == rule, onClick = null) },
                    )
                }
            }
        }
        item {
            Box(Modifier.toggleable(t.calibration, role = Role.Switch, onValueChange = { onTargets(t.copy(calibration = it)) })) {
                SegmentedRow(
                    stringResource(R.string.style_calibration), stringResource(R.string.style_calibration_about), Symbol.CheckCircle,
                    supportingLines = 3,
                    trailing = { Switch(checked = t.calibration, onCheckedChange = null) },
                )
            }
        }
        item {
            Labelled(stringResource(R.string.style_reps), stringResource(R.string.style_reps_about)) {
                Text(stringResource(R.string.style_reps_compound), style = MaterialTheme.typography.labelLarge)
                RangeFields(
                    t.compound, TrainingStyleJson.REPS,
                    stringResource(R.string.style_reps_compound_low), stringResource(R.string.style_reps_compound_high),
                    stringResource(R.string.style_reps_wrong),
                    onRange = { onTargets(t.copy(compound = it)) },
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.style_reps_isolation), style = MaterialTheme.typography.labelLarge)
                RangeFields(
                    t.isolation, TrainingStyleJson.REPS,
                    stringResource(R.string.style_reps_isolation_low), stringResource(R.string.style_reps_isolation_high),
                    stringResource(R.string.style_reps_wrong),
                    onRange = { onTargets(t.copy(isolation = it)) },
                )
            }
        }
    }
}

/** A choice on its own tile: [title] and [about] over the control, on the card's text edge. */
@Composable
private fun Labelled(title: String, about: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(CardPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(about, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content()
    }
}

/**
 * Two number fields for a range, its fewest and most, each kept once the range is whole: both
 * ends in [ends] and the fewest not above the most. Otherwise [wrong] shows and nothing is kept.
 */
@Composable
private fun RangeFields(range: NumberRange, ends: IntRange, lowLabel: String, highLabel: String, wrong: String, onRange: (NumberRange) -> Unit) {
    var low by rememberSaveable(range.low) { mutableStateOf(range.low.toString()) }
    var high by rememberSaveable(range.high) { mutableStateOf(range.high.toString()) }
    val typed = NumberRange(low.toIntOrNull() ?: -1, high.toIntOrNull() ?: -1)
    val valid = typed.low in ends && typed.high in ends && typed.low <= typed.high
    fun changed(l: String, h: String) {
        val r = NumberRange(l.toIntOrNull() ?: -1, h.toIntOrNull() ?: -1)
        if (r.low in ends && r.high in ends && r.low <= r.high && r != range) onRange(r)
    }
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NumberField(low, lowLabel, !valid, Modifier.weight(1f)) { low = it; changed(it, high) }
            Text("–", style = MaterialTheme.typography.bodyLarge)
            NumberField(high, highLabel, !valid, Modifier.weight(1f)) { high = it; changed(low, it) }
        }
        if (!valid) Text(wrong, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun NumberField(value: String, description: String, error: Boolean, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(2)) },
        singleLine = true,
        isError = error,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.semantics { contentDescription = description },
    )
}

@Composable
private fun HowITrain(text: String, edited: Boolean, onSave: (String) -> Unit, onDefault: () -> Unit) {
    var typed by rememberSaveable(text) { mutableStateOf(text) }
    var asking by remember { mutableStateOf(false) }
    SegmentedList {
        item {
            Column(Modifier.fillMaxWidth().padding(CardPadding)) {
                Text(stringResource(R.string.how_i_train_about), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text(stringResource(R.string.how_i_train)) },
                    minLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (edited) TextButton(onClick = { asking = true }) { Text(stringResource(R.string.how_i_train_default)) }
                    TextButton(onClick = { onSave(typed) }, enabled = typed.trim() != text.trim() && typed.isNotBlank()) {
                        Text(stringResource(R.string.how_i_train_save))
                    }
                }
            }
        }
    }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(stringResource(R.string.how_i_train_default_title)) },
            text = { Text(stringResource(R.string.how_i_train_default_text)) },
            confirmButton = { TextButton(onClick = { asking = false; onDefault() }) { Text(stringResource(R.string.how_i_train_default)) } },
            dismissButton = { TextButton(onClick = { asking = false }) { Text(stringResource(R.string.backup_cancel)) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AboutMeFields(me: AboutMe, library: List<Exercise>, onAboutMe: (AboutMe) -> Unit, onPick: (String) -> Unit) {
    val names = library.associate { it.id to it.name }
    SegmentedList {
        item {
            Labelled(stringResource(R.string.style_age), stringResource(R.string.style_optional)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AgeBand.entries.forEach { band ->
                        FilterChip(
                            selected = me.age == band,
                            onClick = { onAboutMe(me.copy(age = band.takeIf { me.age != band })) },
                            label = { Text(band.label) },
                            colors = selectedChipColors(),
                        )
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(top = CardPadding)) {
                Column(Modifier.padding(horizontal = CardPadding)) {
                    Text(stringResource(R.string.style_where), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.style_where_about), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Progress.entries.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(me.progress == p, role = Role.RadioButton, onClick = { onAboutMe(me.copy(progress = p.takeIf { me.progress != p })) })
                            .padding(horizontal = CardPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(p.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        RadioButton(selected = me.progress == p, onClick = null)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .toggleable(me.comingBack, role = Role.Switch, onValueChange = { onAboutMe(me.copy(comingBack = it)) })
                        .padding(horizontal = CardPadding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.style_coming_back), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = me.comingBack, onCheckedChange = null)
                }
            }
        }
        item {
            Labelled(stringResource(R.string.style_aches), stringResource(R.string.style_aches_about)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BodyArea.entries.forEach { area ->
                        val on = area in me.aches
                        FilterChip(
                            selected = on,
                            onClick = { onAboutMe(me.copy(aches = if (on) me.aches - area else (me.aches + (area to "")).toSortedMap(compareBy { it.ordinal }))) },
                            label = { Text(area.label) },
                            colors = selectedChipColors(),
                        )
                    }
                }
                me.aches.forEach { (area, note) ->
                    var typed by rememberSaveable(area) { mutableStateOf(note) }
                    OutlinedTextField(
                        value = typed,
                        onValueChange = {
                            typed = it
                            onAboutMe(me.copy(aches = me.aches + (area to it)))
                        },
                        label = { Text(area.label) },
                        placeholder = { Text(stringResource(R.string.style_ache_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(GroupGap))
    PickedList(stringResource(R.string.style_avoid), me.avoid, names, stringResource(R.string.style_avoid_add), onAdd = { onPick(AVOID) }) { id ->
        onAboutMe(me.copy(avoid = me.avoid - id))
    }
    Spacer(Modifier.height(GroupGap))
    PickedList(stringResource(R.string.style_favour), me.favour, names, stringResource(R.string.style_favour_add), onAdd = { onPick(FAVOUR) }) { id ->
        onAboutMe(me.copy(favour = me.favour - id))
    }
    Spacer(Modifier.height(GroupGap))
    SegmentedList {
        item {
            Column(Modifier.fillMaxWidth().padding(CardPadding)) {
                var typed by rememberSaveable { mutableStateOf(me.anythingElse.orEmpty()) }
                OutlinedTextField(
                    value = typed,
                    onValueChange = {
                        typed = it
                        onAboutMe(me.copy(anythingElse = it.takeIf { t -> t.isNotBlank() }))
                    },
                    label = { Text(stringResource(R.string.style_anything_else)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Exercises picked for one list, each with its ×, and a row to add one. */
@Composable
private fun PickedList(title: String, ids: List<String>, names: Map<String, String>, add: String, onAdd: () -> Unit, onRemove: (String) -> Unit) {
    val removes = ids.associateWith { stringResource(R.string.style_remove, names[it] ?: it) }
    SegmentedList {
        ids.forEach { id ->
            row(names[id] ?: id, title, Symbol.Exercise, trailing = { IconButton(onClick = { onRemove(id) }) { SymbolIcon(Symbol.Close, removes.getValue(id)) } })
        }
        row(add, null, Symbol.Add, onClick = onAdd)
    }
}

/** The library's exercises with the swap picker's search, for an About me list. */
@Composable
private fun ExercisePick(exercises: List<Exercise>, title: String, onPick: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxWidth()) {
        Text(title, Modifier.padding(start = TextEdge, end = TextEdge, top = 16.dp, bottom = 16.dp), style = MaterialTheme.typography.headlineSmall)
        SearchField(query, onChange = { query = it }, placeholder = stringResource(R.string.session_search), modifier = Modifier.padding(horizontal = CardMargin).padding(bottom = 8.dp))
        val listState = rememberLazyListState()
        LaunchedEffect(query) { listState.scrollToItem(0) }
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).imePadding(), state = listState) {
            val found = if (query.isBlank()) exercises.sortedBy { it.name } else exercises.search(query)
            itemsIndexed(found, key = { _, it -> it.id }) { i, e ->
                Segment(i, found.size, color = Tones.sheetCard, onClick = { onPick(e.id) }) {
                    Text(e.name, Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = CardPadding, vertical = 16.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
            item { Spacer(Modifier.navigationBarsPadding().height(16.dp)) }
        }
    }
}

/** The Training style row's line: the goal and volume, as Settings shows them. */
@Composable
fun trainingStyleLine(targets: TrainingTargets): String = stringResource(
    R.string.style_row_line,
    stringResource(
        when (targets.goal) {
            TrainingGoal.Hypertrophy -> R.string.style_goal_hypertrophy
            TrainingGoal.Strength -> R.string.style_goal_strength
            TrainingGoal.Both -> R.string.style_goal_both
        },
    ),
    targets.volume.toString(),
)
