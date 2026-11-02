package com.hyperlog.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
import com.hyperlog.app.ui.theme.BottomActions
import com.hyperlog.app.ui.theme.CardMargin
import com.hyperlog.app.ui.theme.CardPadding
import com.hyperlog.app.ui.theme.ConnectedChoice
import com.hyperlog.app.ui.theme.GroupGap
import com.hyperlog.app.ui.theme.GroupHead
import com.hyperlog.app.ui.theme.Segment
import com.hyperlog.app.ui.theme.SegmentedList
import com.hyperlog.app.ui.theme.Sheet
import com.hyperlog.app.ui.theme.TextEdge
import com.hyperlog.app.ui.theme.Tones
import com.hyperlog.app.ui.theme.hyperlogTopBarColors
import com.hyperlog.app.ui.theme.selectedChipColors

/** The volume choices: Low, Moderate and High, each a range of weekly sets per muscle; any other range is Custom. */
val VolumeChoices = listOf(NumberRange(4, 8), NumberRange(8, 14), NumberRange(14, 20))

/** A connected choice's buttons, narrow, so `Hypertrophy` and `Moderate` keep their words in a row of three or four. */
private val ChoicePadding = PaddingValues(horizontal = 8.dp)

/**
 * Settings → Training style (SPEC, Coaching → Training style; `docs/design/training-style.md`):
 * the training targets, How I train with Save and Back to default, and About me, each field edited
 * in place and saved as it changes. Every setting is a tile of one group, its title on the text
 * edge and its control under it. Text fields stay on the page, not in dialogs, where Robolectric's
 * tests can't type.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    var picking by rememberSaveable { mutableStateOf<PickList?>(null) }
    // How I train's text as typed: kept only with Save, which waits in a strip over the keyboard
    // while the text differs from what's kept, wherever in the text the cursor is.
    // The default's wrapped markdown lines are joined to fit the screen; your own text shows as you wrote it.
    val shown = if (edited) style.howITrain.trim() else unwrapped(style.howITrain)
    var typed by rememberSaveable(shown) { mutableStateOf(shown) }
    val focus = LocalFocusManager.current
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
        bottomBar = {
            if (typed.trim() != shown) {
                BottomActions(Modifier.imePadding(), padding = PaddingValues(start = TextEdge, end = 16.dp, top = 16.dp, bottom = 16.dp)) {
                    Text(
                        stringResource(R.string.how_i_train_unsaved),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { typed = shown; focus.clearFocus() }) { Text(stringResource(R.string.how_i_train_discard)) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onHowITrain(typed.trim()); focus.clearFocus() }, enabled = typed.isNotBlank()) {
                        Text(stringResource(R.string.how_i_train_save))
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().verticalScroll(rememberScrollState())) {
            Targets(style.targets, onTargets)
            GroupHead(stringResource(R.string.how_i_train))
            HowITrain(typed, edited, onType = { typed = it }, onDefault = onDefaultHowITrain)
            GroupHead(stringResource(R.string.about_me))
            Text(
                stringResource(R.string.style_optional),
                Modifier.padding(start = TextEdge, end = TextEdge, bottom = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AboutMeFields(style.aboutMe, library, onAboutMe, onPick = { focus.clearFocus(); picking = it })
            Spacer(Modifier.navigationBarsPadding().height(GroupGap))
        }
    }
    picking?.let { list ->
        val me = style.aboutMe
        val taken = me.avoid + me.favour
        Sheet(onDismiss = { picking = null }) {
            ExercisePick(
                library.filter { !it.hidden && it.id !in taken },
                stringResource(if (list == PickList.Avoid) R.string.style_avoid_pick else R.string.style_favour_pick),
                onPick = { id ->
                    onAboutMe(if (list == PickList.Avoid) me.copy(avoid = me.avoid + id) else me.copy(favour = me.favour + id))
                    picking = null
                },
            )
        }
    }
}

/** The About me list an exercise is picked for. */
private enum class PickList { Avoid, Favour }

@Composable
private fun Targets(t: TrainingTargets, onTargets: (TrainingTargets) -> Unit) {
    GroupHead(stringResource(R.string.style_targets))
    val goals = listOf(stringResource(R.string.style_goal_hypertrophy), stringResource(R.string.style_goal_strength), stringResource(R.string.style_goal_both))
    val volumes = listOf(stringResource(R.string.style_volume_low), stringResource(R.string.style_volume_moderate), stringResource(R.string.style_volume_high), stringResource(R.string.style_volume_custom))
    val failures = FailureRule.entries.map {
        stringResource(
            when (it) {
                FailureRule.IsolationAndMachines -> R.string.style_failure_safe
                FailureRule.Never -> R.string.style_failure_never
                FailureRule.AnyLift -> R.string.style_failure_any
            },
        )
    }
    val failureAbout = stringResource(
        when (t.failure) {
            FailureRule.IsolationAndMachines -> R.string.style_failure_safe_about
            FailureRule.Never -> R.string.style_failure_never_about
            FailureRule.AnyLift -> R.string.style_failure_any_about
        },
    )
    SegmentedList {
        item {
            Tile(stringResource(R.string.style_goal), stringResource(R.string.style_goal_about)) {
                ConnectedChoice(
                    goals, TrainingGoal.entries.indexOf(t.goal),
                    onSelect = { onTargets(t.copy(goal = TrainingGoal.entries[it])) },
                    contentPadding = ChoicePadding, fitLabels = true,
                )
            }
        }
        item {
            val choice = VolumeChoices.indexOf(t.volume).takeIf { it >= 0 } ?: VolumeChoices.size
            var custom by rememberSaveable { mutableStateOf(false) }
            val customShown = custom || choice == VolumeChoices.size
            Tile(
                stringResource(R.string.style_volume),
                if (customShown) stringResource(R.string.style_volume_about_custom) else stringResource(R.string.style_volume_about, t.volume.toString()),
            ) {
                ConnectedChoice(
                    volumes, if (custom) VolumeChoices.size else choice,
                    onSelect = {
                        custom = it == VolumeChoices.size
                        if (!custom) onTargets(t.copy(volume = VolumeChoices[it]))
                    },
                    contentPadding = ChoicePadding, fitLabels = true,
                )
                if (customShown) {
                    RangeFields(
                        stringResource(R.string.style_volume_range), t.volume, TrainingStyleJson.VOLUME,
                        stringResource(R.string.style_volume_low_end), stringResource(R.string.style_volume_high_end),
                        stringResource(R.string.style_volume_wrong),
                        onRange = { onTargets(t.copy(volume = it)) },
                    )
                }
            }
        }
        item {
            Tile(
                stringResource(R.string.style_across),
                stringResource(if (t.acrossBlock == AcrossBlock.Steady) R.string.style_across_steady_about else R.string.style_across_rising_about),
            ) {
                ConnectedChoice(
                    listOf(stringResource(R.string.style_across_steady), stringResource(R.string.style_across_rising)),
                    AcrossBlock.entries.indexOf(t.acrossBlock),
                    onSelect = { onTargets(t.copy(acrossBlock = AcrossBlock.entries[it])) },
                )
            }
        }
        item {
            Tile(stringResource(R.string.style_frequency), stringResource(R.string.style_frequency_about)) {
                ConnectedChoice(
                    Frequency.entries.map { it.key.replace("-", "–") },
                    Frequency.entries.indexOf(t.frequency),
                    onSelect = { onTargets(t.copy(frequency = Frequency.entries[it])) },
                )
            }
        }
        // How hard is one rule, shown and not chosen: a tile with no control.
        item { Tile(stringResource(R.string.style_how_hard), stringResource(R.string.style_how_hard_rule)) }
        item {
            // The line above the options says what the picked one means; three lines hold the longest, so a tap never shifts them.
            Tile(stringResource(R.string.style_failure), failureAbout, aboutLines = 3) {
                Options(failures, FailureRule.entries.indexOf(t.failure), onSelect = { onTargets(t.copy(failure = FailureRule.entries[it])) })
            }
        }
        item {
            SwitchTile(stringResource(R.string.style_calibration), stringResource(R.string.style_calibration_about), t.calibration) {
                onTargets(t.copy(calibration = it))
            }
        }
        item {
            Tile(stringResource(R.string.style_reps), stringResource(R.string.style_reps_about)) {
                RangeFields(
                    stringResource(R.string.style_reps_compound), t.compound, TrainingStyleJson.REPS,
                    stringResource(R.string.style_reps_compound_low), stringResource(R.string.style_reps_compound_high),
                    stringResource(R.string.style_reps_wrong),
                    onRange = { onTargets(t.copy(compound = it)) },
                )
                RangeFields(
                    stringResource(R.string.style_reps_isolation), t.isolation, TrainingStyleJson.REPS,
                    stringResource(R.string.style_reps_isolation_low), stringResource(R.string.style_reps_isolation_high),
                    stringResource(R.string.style_reps_wrong),
                    onRange = { onTargets(t.copy(isolation = it)) },
                )
            }
        }
    }
}

/**
 * One setting on its own tile: [title] and [about] on the text edge, an optional
 * [trailing] control beside them, and the [content] (its control) under them, 12 dp down.
 */
@Composable
private fun Tile(
    title: String,
    about: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    /** Lines kept for [about] when it changes with the tile's choice, so the choice under it never moves. */
    aboutLines: Int = 1,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(CardPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (about != null) {
                    Text(about, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, minLines = aboutLines)
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(16.dp))
                trailing()
            }
        }
        content?.invoke(this)
    }
}

/** A tile that is one switch: a tap anywhere on it turns it. */
@Composable
private fun SwitchTile(title: String, about: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Column(Modifier.toggleable(checked, role = Role.Switch, onValueChange = onChange)) {
        Tile(title, about, trailing = { Switch(checked = checked, onCheckedChange = null) })
    }
}

/**
 * A choice of a few long options, one a line: the picked one is a filled pill in
 * secondaryContainer with its radio on. Its words keep the text edge: the pill sits 4 dp inside
 * the tile and pads 12 dp. [onSelect] gets the tapped option, the picked one too.
 */
@Composable
private fun Options(options: List<String>, selected: Int?, onSelect: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.bleed(CardPadding - 4.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(CircleShape)
                    .then(if (on) Modifier.background(cs.secondaryContainer) else Modifier)
                    .selectable(on, role = Role.RadioButton, onClick = { onSelect(i) })
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = if (on) cs.onSecondaryContainer else cs.onSurface)
                RadioButton(selected = on, onClick = null, Modifier.size(40.dp))
            }
        }
    }
}

/** Widens the content by [by] on each side, out over its tile's padding. */
private fun Modifier.bleed(by: Dp) = layout { measurable, constraints ->
    val extra = (by * 2).roundToPx()
    val placeable = measurable.measure(constraints.copy(minWidth = constraints.maxWidth + extra, maxWidth = constraints.maxWidth + extra))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-by.roundToPx(), 0) }
}

/**
 * A range as two small number fields, its fewest and most, after an optional [label]. Each is kept
 * once the range is whole: both ends in [ends] and the fewest not above the most. Otherwise both
 * fields turn red, [wrong] shows under them and nothing is kept.
 */
@Composable
private fun RangeFields(label: String?, range: NumberRange, ends: IntRange, lowLabel: String, highLabel: String, wrong: String, onRange: (NumberRange) -> Unit) {
    var low by rememberSaveable(range.low) { mutableStateOf(range.low.toString()) }
    var high by rememberSaveable(range.high) { mutableStateOf(range.high.toString()) }
    val typed = NumberRange(low.toIntOrNull() ?: -1, high.toIntOrNull() ?: -1)
    val valid = typed.low in ends && typed.high in ends && typed.low <= typed.high
    // Kept when you leave a field (Next, Done, or a tap elsewhere), never halfway through typing an end.
    fun commit() {
        if (valid && typed != range) onRange(typed)
    }
    val focus = LocalFocusManager.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (label != null) Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            NumberField(low, lowLabel, !valid, ImeAction.Next, onDone = { focus.moveFocus(FocusDirection.Next) }, onLeave = ::commit) { low = it }
            Text("–", Modifier.width(24.dp), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            NumberField(high, highLabel, !valid, ImeAction.Done, onDone = { focus.clearFocus() }, onLeave = ::commit) { high = it }
        }
        if (!valid) {
            Text(
                wrong,
                Modifier.fillMaxWidth().padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = if (label != null) TextAlign.End else TextAlign.Start,
            )
        }
    }
}

/**
 * A two-digit number field, 80 dp wide, its figure centred; [description] names it for TalkBack
 * and the tests. Focused, its whole number is selected, so typing replaces it wherever the thumb
 * landed. [onLeave] runs as focus leaves it.
 */
@Composable
private fun NumberField(value: String, description: String, error: Boolean, action: ImeAction, onDone: () -> Unit, onLeave: () -> Unit, onChange: (String) -> Unit) {
    var field by remember { mutableStateOf(TextFieldValue(value)) }
    if (field.text != value) field = TextFieldValue(value, TextRange(value.length))
    var focused by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = field,
        onValueChange = {
            val digits = it.text.filter(Char::isDigit).take(2)
            field = it.copy(text = digits, selection = if (digits == it.text) it.selection else TextRange(digits.length))
            if (digits != value) onChange(digits)
        },
        singleLine = true,
        isError = error,
        textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = action),
        keyboardActions = KeyboardActions(onAny = { onDone() }),
        modifier = Modifier.width(80.dp).semantics { contentDescription = description }.onFocusChanged {
            if (it.isFocused && !focused) field = field.copy(selection = TextRange(0, field.text.length))
            if (!it.isFocused && focused) onLeave()
            focused = it.isFocused
        },
    )
}

/**
 * How I train as the asset writes it, its markdown's wrapped lines joined: a continuation line
 * (indented under its bullet) belongs to the line before, so the field wraps it to the screen.
 */
private fun unwrapped(text: String) = text.trim().replace(Regex("\n[ \t]+"), " ")

/**
 * How I train on its tile: whose text it is, the text in a field that grows with it, and Back to
 * default once it's your own. Save is the screen's strip.
 */
@Composable
private fun HowITrain(typed: String, edited: Boolean, onType: (String) -> Unit, onDefault: () -> Unit) {
    var asking by remember { mutableStateOf(false) }
    val description = stringResource(R.string.how_i_train)
    SegmentedList {
        item {
            Column(Modifier.fillMaxWidth().padding(CardPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(if (edited) R.string.how_i_train_own else R.string.how_i_train_about),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = typed,
                    onValueChange = onType,
                    minLines = 4,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
                )
                if (edited) {
                    // The label on the text edge's far side: a text button's padding sits outside it.
                    TextButton(onClick = { asking = true }, Modifier.align(Alignment.End).offset(x = 12.dp)) {
                        Text(stringResource(R.string.how_i_train_default))
                    }
                }
            }
        }
    }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            icon = { SymbolIcon(Symbol.Undo, null) },
            title = { Text(stringResource(R.string.how_i_train_default_title)) },
            text = { Text(stringResource(R.string.how_i_train_default_text)) },
            confirmButton = { TextButton(onClick = { asking = false; onDefault() }) { Text(stringResource(R.string.how_i_train_default)) } },
            dismissButton = { TextButton(onClick = { asking = false }) { Text(stringResource(R.string.how_i_train_cancel)) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AboutMeFields(me: AboutMe, library: List<Exercise>, onAboutMe: (AboutMe) -> Unit, onPick: (PickList) -> Unit) {
    val names = library.associate { it.id to it.name }
    val ages = AgeBand.entries
    val where = Progress.entries.map { it.label }
    SegmentedList {
        item {
            Tile(stringResource(R.string.style_age)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ages.forEach { band ->
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
            // Tapped again, the picked one clears: every field here is optional.
            Tile(stringResource(R.string.style_where), stringResource(R.string.style_where_about)) {
                Options(where, me.progress?.let { Progress.entries.indexOf(it) }, onSelect = { i ->
                    val p = Progress.entries[i]
                    onAboutMe(me.copy(progress = p.takeIf { me.progress != p }))
                })
            }
        }
        item {
            SwitchTile(stringResource(R.string.style_coming_back), null, me.comingBack) { onAboutMe(me.copy(comingBack = it)) }
        }
        item {
            Tile(stringResource(R.string.style_aches), stringResource(R.string.style_aches_about)) {
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
        item { Picked(stringResource(R.string.style_avoid), me.avoid, names, stringResource(R.string.style_avoid_add), onAdd = { onPick(PickList.Avoid) }) { id -> onAboutMe(me.copy(avoid = me.avoid - id)) } }
        item { Picked(stringResource(R.string.style_favour), me.favour, names, stringResource(R.string.style_favour_add), onAdd = { onPick(PickList.Favour) }) { id -> onAboutMe(me.copy(favour = me.favour - id)) } }
        item {
            val description = stringResource(R.string.style_anything_else)
            Tile(description) {
                var typed by rememberSaveable { mutableStateOf(me.anythingElse.orEmpty()) }
                OutlinedTextField(
                    value = typed,
                    onValueChange = {
                        typed = it
                        onAboutMe(me.copy(anythingElse = it.takeIf { t -> t.isNotBlank() }))
                    },
                    placeholder = { Text(stringResource(R.string.style_anything_else_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
                )
            }
        }
    }
}

/**
 * An exercise list of About me on one tile: [title] with Add at its end ([add] names it for
 * TalkBack), then each picked exercise on a line with its ×, the × lined up under Add's end.
 */
@Composable
private fun Picked(title: String, ids: List<String>, names: Map<String, String>, add: String, onAdd: () -> Unit, onRemove: (String) -> Unit) {
    val removes = ids.associateWith { stringResource(R.string.style_remove, names[it] ?: it) }
    Tile(
        title,
        trailing = {
            FilledTonalButton(
                onClick = onAdd,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                modifier = Modifier.semantics { contentDescription = add },
            ) {
                SymbolIcon(Symbol.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.style_add))
            }
        },
    ) {
        if (ids.isNotEmpty()) {
            Column {
                ids.forEach { id ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(names[id] ?: id, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        IconButton(onClick = { onRemove(id) }, Modifier.offset(x = 12.dp)) { SymbolIcon(Symbol.Close, removes.getValue(id)) }
                    }
                }
            }
        }
    }
}

/** The library's exercises with the swap picker's search, for an About me list. */
@Composable
private fun ExercisePick(exercises: List<Exercise>, title: String, onPick: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxWidth()) {
        Text(title, Modifier.padding(start = TextEdge, end = TextEdge, top = 8.dp, bottom = 16.dp), style = MaterialTheme.typography.headlineSmall)
        // You came to find one exercise: the search takes the keyboard at once.
        val search = remember { FocusRequester() }
        SearchField(query, onChange = { query = it }, placeholder = stringResource(R.string.session_search), modifier = Modifier.padding(horizontal = CardMargin).padding(bottom = 8.dp).focusRequester(search))
        LaunchedEffect(Unit) { runCatching { search.requestFocus() } }
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
