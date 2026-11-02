package com.hyperlog.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.hyperlog.app.coach.HowITrain
import com.hyperlog.app.coach.Principle
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
 * the training targets, How I train's principles and About me, each field edited in place and
 * saved as it changes. Every setting is a tile of one group, its title on the text
 * edge and its control under it. Text fields stay on the page, not in dialogs, where Robolectric's
 * tests can't type.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TrainingStyleScreen(
    style: TrainingStyle,
    library: List<Exercise>,
    onTargets: (TrainingTargets) -> Unit,
    onAboutMe: (AboutMe) -> Unit,
    onHowITrain: (HowITrain) -> Unit,
    onSetUpAgain: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    var picking by rememberSaveable { mutableStateOf<PickList?>(null) }
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
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().verticalScroll(rememberScrollState())) {
            val setUpAgain = stringResource(R.string.style_set_up_again)
            val setUpAgainAbout = stringResource(R.string.style_set_up_again_about)
            SegmentedList {
                row(
                    setUpAgain, setUpAgainAbout, Symbol.Tune,
                    onClick = onSetUpAgain,
                    trailing = { SymbolIcon(Symbol.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                )
            }
            Targets(style.targets, onTargets)
            GroupHead(stringResource(R.string.how_i_train))
            HowITrainFields(style.howITrain, onHowITrain)
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
    picking?.let { list -> PickSheet(list, style.aboutMe, library, onAboutMe, onDismiss = { picking = null }) }
}

/** The About me list an exercise is picked for. */
internal enum class PickList { Avoid, Favour }

@Composable
private fun Targets(t: TrainingTargets, onTargets: (TrainingTargets) -> Unit) {
    GroupHead(stringResource(R.string.style_targets))
    SegmentedList {
        item { GoalTile(t, onTargets) }
        item { VolumeTile(t, onTargets) }
        item { AcrossTile(t, onTargets) }
        item { FrequencyTile(t, onTargets) }
        item { HowHardTile() }
        item { FailureTile(t, onTargets) }
        item { CalibrationTile(t, onTargets) }
        item { RepsTile(t, onTargets) }
    }
}

// The tiles below are the page's controls, which the wizard (TrainingStyleWizard) shows one step at a time.

@Composable
internal fun GoalTile(t: TrainingTargets, onTargets: (TrainingTargets) -> Unit) {
    val goals = listOf(stringResource(R.string.style_goal_hypertrophy), stringResource(R.string.style_goal_strength), stringResource(R.string.style_goal_both))
    Tile(stringResource(R.string.style_goal), stringResource(R.string.style_goal_about)) {
        ConnectedChoice(
            goals, TrainingGoal.entries.indexOf(t.goal),
            onSelect = { onTargets(t.copy(goal = TrainingGoal.entries[it])) },
            contentPadding = ChoicePadding, fitLabels = true,
        )
    }
}

@Composable
internal fun VolumeTile(t: TrainingTargets, onTargets: (TrainingTargets) -> Unit) {
    val volumes = listOf(stringResource(R.string.style_volume_low), stringResource(R.string.style_volume_moderate), stringResource(R.string.style_volume_high), stringResource(R.string.style_volume_custom))
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

@Composable
internal fun AcrossTile(t: TrainingTargets, onTargets: (TrainingTargets) -> Unit) {
    Tile(
        stringResource(R.string.style_across),
        if (t.acrossBlock == AcrossBlock.Steady) stringResource(R.string.style_across_steady_about)
        else stringResource(R.string.style_across_rising_about, t.volume.high),
    ) {
        ConnectedChoice(
            listOf(stringResource(R.string.style_across_steady), stringResource(R.string.style_across_rising)),
            AcrossBlock.entries.indexOf(t.acrossBlock),
            onSelect = { onTargets(t.copy(acrossBlock = AcrossBlock.entries[it])) },
            // "Build up each week" keeps its last word beside the shorter "Same each week".
            contentPadding = PaddingValues(horizontal = 12.dp),
            fitLabels = true,
        )
    }
}

@Composable
private fun FrequencyTile(t: TrainingTargets, onTargets: (TrainingTargets) -> Unit) {
    Tile(stringResource(R.string.style_frequency), stringResource(R.string.style_frequency_about)) {
        ConnectedChoice(
            Frequency.entries.map { it.key.replace("-", "–") },
            Frequency.entries.indexOf(t.frequency),
            onSelect = { onTargets(t.copy(frequency = Frequency.entries[it])) },
        )
    }
}

/** How hard is one rule, shown and not chosen: a tile with no control. */
@Composable
internal fun HowHardTile() = Tile(stringResource(R.string.style_how_hard), stringResource(R.string.style_how_hard_rule))

@Composable
internal fun FailureTile(t: TrainingTargets, onTargets: (TrainingTargets) -> Unit) {
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
            // Calibration holds with Never, so the line says so while it's on.
            FailureRule.Never -> if (t.calibration) R.string.style_failure_never_calibration_about else R.string.style_failure_never_about
            FailureRule.AnyLift -> R.string.style_failure_any_about
        },
    )
    // The line above the options says what the picked one means; three lines hold the longest, so a tap never shifts them.
    Tile(stringResource(R.string.style_failure), failureAbout, aboutLines = 3) {
        Options(failures, FailureRule.entries.indexOf(t.failure), onSelect = { onTargets(t.copy(failure = FailureRule.entries[it])) })
    }
}

@Composable
internal fun CalibrationTile(t: TrainingTargets, onTargets: (TrainingTargets) -> Unit) {
    SwitchTile(stringResource(R.string.style_calibration), stringResource(R.string.style_calibration_about), t.calibration) {
        onTargets(t.copy(calibration = it))
    }
}

@Composable
internal fun RepsTile(t: TrainingTargets, onTargets: (TrainingTargets) -> Unit) {
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

/**
 * One setting on its own tile: [title] and [about] on the text edge, an optional
 * [trailing] control beside them, and the [content] (its control) under them, 12 dp down.
 */
@Composable
internal fun Tile(
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
internal fun SwitchTile(title: String, about: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
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
internal fun Options(options: List<String>, selected: Int?, onSelect: (Int) -> Unit) {
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
 * How I train's principles (SPEC, Training style → How I train): the fixed rules shown as rules,
 * the rest switches, and one line more. Their sentences are Claude's; you see only the labels.
 */
@Composable
internal fun HowITrainFields(how: HowITrain, onHowITrain: (HowITrain) -> Unit) {
    val always = stringResource(R.string.how_i_train_always)
    val labels = Principle.entries.associateWith { stringResource(it.label) }
    val description = stringResource(R.string.how_i_train_anything_else_description)
    val title = stringResource(R.string.style_anything_else)
    val hint = stringResource(R.string.how_i_train_anything_else_hint)
    SegmentedList {
        item {
            Tile(always) {
                Column {
                    Principle.entries.filter { it.fixed }.forEach { p ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(labels.getValue(p), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            // In the switches' column, centred on a Switch's 52 dp: on, and not yours to turn off.
                            Box(Modifier.width(52.dp), contentAlignment = Alignment.Center) {
                                SymbolIcon(Symbol.Check, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        Principle.entries.filterNot { it.fixed }.forEach { p ->
            item {
                SwitchTile(labels.getValue(p), null, how.on(p)) { on ->
                    onHowITrain(how.copy(off = if (on) how.off - p else how.off + p))
                }
            }
        }
        item {
            Tile(title) {
                var typed by rememberSaveable { mutableStateOf(how.anythingElse.orEmpty()) }
                OutlinedTextField(
                    value = typed,
                    onValueChange = {
                        typed = it
                        onHowITrain(how.copy(anythingElse = it.takeIf { t -> t.isNotBlank() }))
                    },
                    placeholder = { Text(hint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
                )
            }
        }
    }
}

/** A principle's short label on the page. */
private val Principle.label: Int
    get() = when (this) {
        Principle.FormBeforeLoad -> R.string.principle_form
        Principle.NoLoads -> R.string.principle_no_load
        Principle.SwapAroundPain -> R.string.principle_pain
        Principle.Alternatives -> R.string.principle_alternatives
        Principle.StretchBiased -> R.string.principle_stretch
        Principle.Stable -> R.string.principle_stable
        Principle.Deload -> R.string.principle_deload
    }

@Composable
private fun AboutMeFields(me: AboutMe, library: List<Exercise>, onAboutMe: (AboutMe) -> Unit, onPick: (PickList) -> Unit) {
    val names = library.associate { it.id to it.name }
    SegmentedList {
        item { AgeTile(me, onAboutMe) }
        item { WhereTile(me, onAboutMe) }
        item { ComingBackTile(me, onAboutMe) }
        item { AchesTile(me, onAboutMe) }
        item { AvoidTile(me, names, onAboutMe, onPick) }
        item { FavourTile(me, names, onAboutMe, onPick) }
        item { AnythingElseTile(me, onAboutMe) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AgeTile(me: AboutMe, onAboutMe: (AboutMe) -> Unit) {
    Tile(stringResource(R.string.style_age)) {
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

/** Tapped again, the picked one clears: every field of About me is optional. */
@Composable
internal fun WhereTile(me: AboutMe, onAboutMe: (AboutMe) -> Unit) {
    Tile(stringResource(R.string.style_where), stringResource(R.string.style_where_about)) {
        Options(Progress.entries.map { it.label }, me.progress?.let { Progress.entries.indexOf(it) }, onSelect = { i ->
            val p = Progress.entries[i]
            onAboutMe(me.copy(progress = p.takeIf { me.progress != p }))
        })
    }
}

@Composable
internal fun ComingBackTile(me: AboutMe, onAboutMe: (AboutMe) -> Unit) {
    SwitchTile(stringResource(R.string.style_coming_back), null, me.comingBack) { onAboutMe(me.copy(comingBack = it)) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AchesTile(me: AboutMe, onAboutMe: (AboutMe) -> Unit) {
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

@Composable
internal fun AvoidTile(me: AboutMe, names: Map<String, String>, onAboutMe: (AboutMe) -> Unit, onPick: (PickList) -> Unit) =
    Picked(stringResource(R.string.style_avoid), me.avoid, names, stringResource(R.string.style_avoid_add), onAdd = { onPick(PickList.Avoid) }) { id -> onAboutMe(me.copy(avoid = me.avoid - id)) }

@Composable
internal fun FavourTile(me: AboutMe, names: Map<String, String>, onAboutMe: (AboutMe) -> Unit, onPick: (PickList) -> Unit) =
    Picked(stringResource(R.string.style_favour), me.favour, names, stringResource(R.string.style_favour_add), onAdd = { onPick(PickList.Favour) }) { id -> onAboutMe(me.copy(favour = me.favour - id)) }

@Composable
internal fun AnythingElseTile(me: AboutMe, onAboutMe: (AboutMe) -> Unit) {
    val description = stringResource(R.string.style_anything_else_about_me)
    Tile(stringResource(R.string.style_anything_else)) {
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

/** Picks one exercise for [list] from the library, in a sheet: the page's and the wizard's. */
@Composable
internal fun PickSheet(list: PickList, me: AboutMe, library: List<Exercise>, onAboutMe: (AboutMe) -> Unit, onDismiss: () -> Unit) {
    val taken = me.avoid + me.favour
    Sheet(onDismiss = onDismiss) {
        ExercisePick(
            library.filter { !it.hidden && it.id !in taken },
            stringResource(if (list == PickList.Avoid) R.string.style_avoid_pick else R.string.style_favour_pick),
            onPick = { id ->
                onAboutMe(if (list == PickList.Avoid) me.copy(avoid = me.avoid + id) else me.copy(favour = me.favour + id))
                onDismiss()
            },
        )
    }
}

/**
 * An exercise list of About me on one tile: [title] with Add at its end ([add] names it for
 * TalkBack), then each picked exercise on a line with its ×, the × lined up under Add's end.
 */
@Composable
internal fun Picked(title: String, ids: List<String>, names: Map<String, String>, add: String, onAdd: () -> Unit, onRemove: (String) -> Unit) {
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
internal fun ExercisePick(exercises: List<Exercise>, title: String, onPick: (String) -> Unit) {
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
