package com.hyperlog.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.coach.AboutMe
import com.hyperlog.app.coach.AcrossBlock
import com.hyperlog.app.coach.FailureRule
import com.hyperlog.app.coach.HowITrain
import com.hyperlog.app.coach.TrainingGoal
import com.hyperlog.app.coach.TrainingStyle
import com.hyperlog.app.coach.TrainingStyleJson
import com.hyperlog.app.coach.TrainingTargets
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon
import com.hyperlog.app.ui.theme.BottomActions
import com.hyperlog.app.ui.theme.GroupGap
import com.hyperlog.app.ui.theme.SegmentedList
import com.hyperlog.app.ui.theme.SegmentedListScope
import com.hyperlog.app.ui.theme.TextEdge
import com.hyperlog.app.ui.theme.Tones
import com.hyperlog.app.ui.theme.hyperlogTopBarColors

/** The wizard's steps (SPEC, Training style → The wizard), one choice a screen, then the summary. */
internal enum class WizardStep(val question: Int) {
    Goal(R.string.wizard_goal),
    Volume(R.string.wizard_volume),
    HowHard(R.string.wizard_how_hard),
    Reps(R.string.wizard_reps),
    Age(R.string.wizard_age),
    Where(R.string.wizard_where),
    Aches(R.string.wizard_aches),
    Exercises(R.string.wizard_exercises),
    AnythingElse(R.string.wizard_anything_else),
    Summary(R.string.wizard_summary),
}

/** The wizard's answers so far, kept across a rotation as the style's own JSON. */
private val DraftSaver = Saver<Pair<TrainingTargets, AboutMe>, String>(
    save = { (targets, aboutMe) -> TrainingStyleJson.encode(TrainingStyleJson.Kept(targets, aboutMe)) },
    restore = { json -> TrainingStyleJson.decode(json).let { it.targets to it.aboutMe } },
)

/**
 * The training style's wizard (SPEC, Coaching → Training style → The wizard): one choice a screen
 * in the check-in steps' look, each opening on the [style]'s current value, and a summary with
 * Done. It is local: no key, no call. The page's own tiles are the controls. Back steps back, × leaves
 * without saving, and [onDone] gets every answer at once. How I train's principles open from the
 * summary and are kept as they change, as on the page ([onHowITrain]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingStyleWizard(
    style: TrainingStyle,
    library: List<Exercise>,
    onDone: (TrainingTargets, AboutMe) -> Unit,
    onHowITrain: (HowITrain) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var draft by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf(style.targets to style.aboutMe) }
    // Set when a summary row sent you back to a step: Next returns to the summary.
    var fromSummary by rememberSaveable { mutableStateOf(false) }
    val wrongRanges = remember { mutableStateMapOf<String, Boolean>() }
    var principles by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf<PickList?>(null) }
    val focus = LocalFocusManager.current
    val (targets, me) = draft
    val onTargets: (TrainingTargets) -> Unit = { draft = it to draft.second }
    val onAboutMe: (AboutMe) -> Unit = { draft = draft.first to it }
    val current = WizardStep.entries[step]
    // A typed range is kept as its field loses focus, so leave the field before the step goes.
    val leave = { focus.clearFocus(); }
    val back = {
        leave()
        fromSummary = false
        if (principles) principles = false else if (step > 0) step -= 1 else onClose()
    }
    BackHandler(onBack = back)
    if (principles) {
        HowITrainPage(style.howITrain, onHowITrain, onBack = back, modifier)
        return
    }
    CompositionLocalProvider(LocalWrongRanges provides wrongRanges) { Scaffold(
        modifier = modifier.imePadding(),
        containerColor = Tones.screen,
        topBar = {
            TopAppBar(
                colors = hyperlogTopBarColors(),
                title = { Text(stringResource(R.string.wizard_title)) },
                navigationIcon = { IconButton(onClick = back) { SymbolIcon(Symbol.ArrowBack, stringResource(R.string.checkin_back)) } },
                actions = { IconButton(onClick = onClose) { SymbolIcon(Symbol.Close, stringResource(R.string.wizard_close)) } },
            )
        },
        bottomBar = {
            BottomActions {
                Text(
                    stringResource(R.string.wizard_progress, step + 1, WizardStep.entries.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                if (current == WizardStep.Summary) {
                    Button(onClick = { leave(); onDone(targets, me) }) { Text(stringResource(R.string.checkin_done)) }
                } else {
                    Button(
                        onClick = {
                            leave()
                            if (fromSummary) { fromSummary = false; step = WizardStep.Summary.ordinal } else step += 1
                        },
                        // A wrong range keeps nothing, so Next waits for it to be put right (it shows its rule).
                        enabled = wrongRanges.values.none { it },
                    ) { Text(stringResource(R.string.wizard_next)) }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = GroupGap)) {
            Text(stringResource(current.question), Modifier.padding(horizontal = TextEdge).padding(bottom = 24.dp), style = MaterialTheme.typography.headlineMedium)
            val names = library.associate { it.id to it.name }
            when (current) {
                WizardStep.Goal -> SegmentedList { item { GoalTile(targets, onTargets, titled = false) } }
                WizardStep.Volume -> SegmentedList {
                    item { VolumeTile(targets, onTargets) }
                    item { AcrossTile(targets, onTargets) }
                }
                WizardStep.HowHard -> SegmentedList {
                    item { HowHardTile() }
                    item { FailureTile(targets, onTargets) }
                    item { CalibrationTile(targets, onTargets) }
                }
                WizardStep.Reps -> SegmentedList { item { RepsTile(targets, onTargets, titled = false) } }
                WizardStep.Age -> SegmentedList { item { AgeTile(me, onAboutMe, titled = false) } }
                WizardStep.Where -> SegmentedList {
                    item { WhereTile(me, onAboutMe, titled = false) }
                    item { ComingBackTile(me, onAboutMe) }
                }
                WizardStep.Aches -> SegmentedList { item { AchesTile(me, onAboutMe, titled = false) } }
                WizardStep.Exercises -> SegmentedList {
                    item { AvoidTile(me, names, onAboutMe, onPick = { focus.clearFocus(); picking = it }) }
                    item { FavourTile(me, names, onAboutMe, onPick = { focus.clearFocus(); picking = it }) }
                }
                WizardStep.AnythingElse -> SegmentedList { item { AnythingElseTile(me, onAboutMe, titled = false) } }
                WizardStep.Summary -> Summary(targets, me, names, onEdit = { step = it.ordinal; fromSummary = true }, onHowITrain = { principles = true })
            }
        }
    } }
    picking?.let { list -> PickSheet(list, me, library, onAboutMe, onDismiss = { picking = null }) }
}

/** Every answer on a line of its own, tapped to go back to its step, then the link to How I train. */
@Composable
private fun Summary(targets: TrainingTargets, me: AboutMe, names: Map<String, String>, onEdit: (WizardStep) -> Unit, onHowITrain: () -> Unit) {
    val none = stringResource(R.string.wizard_none)
    val goal = stringResource(
        when (targets.goal) {
            TrainingGoal.Hypertrophy -> R.string.style_goal_hypertrophy
            TrainingGoal.Strength -> R.string.style_goal_strength
            TrainingGoal.Both -> R.string.style_goal_both
        },
    )
    val across = stringResource(if (targets.acrossBlock == AcrossBlock.Steady) R.string.style_across_steady else R.string.style_across_rising)
    val failure = stringResource(
        when (targets.failure) {
            FailureRule.IsolationAndMachines -> R.string.style_failure_safe
            FailureRule.Never -> R.string.style_failure_never
            FailureRule.AnyLift -> R.string.style_failure_any
        },
    )
    val calibration = stringResource(if (targets.calibration) R.string.wizard_calibration_on else R.string.wizard_calibration_off)
    val where = listOfNotNull(me.progress?.label, if (me.comingBack) stringResource(R.string.style_coming_back) else null).joinToString(" · ").ifEmpty { none }
    val volume = stringResource(R.string.wizard_volume_line, targets.volume.toString(), across)
    val howHard = stringResource(R.string.wizard_how_hard_line, failure, calibration)
    val reps = stringResource(R.string.wizard_reps_line, targets.compound.toString(), targets.isolation.toString())
    val aches = me.aches.keys.joinToString(", ") { it.label }.ifEmpty { none }
    val avoid = me.avoid.joinToString(", ") { names[it] ?: it }.ifEmpty { none }
    val favour = me.favour.joinToString(", ") { names[it] ?: it }.ifEmpty { none }
    val titles = listOf(
        R.string.style_goal, R.string.style_volume, R.string.style_how_hard, R.string.style_reps, R.string.style_age, R.string.style_where,
        R.string.style_aches, R.string.style_avoid, R.string.style_favour, R.string.style_anything_else,
    ).map { stringResource(it) }
    val chevron = @Composable { SymbolIcon(Symbol.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    // Each answer is a tile on the page's text edge, its words wrapping rather than cut short.
    fun SegmentedListScope.answer(title: String, answer: String, onClick: () -> Unit) =
        item(onClick = onClick) { Tile(title, answer, trailing = chevron) }
    fun SegmentedListScope.answer(title: String, answer: String, step: WizardStep) = answer(title, answer) { onEdit(step) }
    SegmentedList {
        answer(titles[0], goal, WizardStep.Goal)
        answer(titles[1], volume, WizardStep.Volume)
        answer(titles[2], howHard, WizardStep.HowHard)
        answer(titles[3], reps, WizardStep.Reps)
        answer(titles[4], me.age?.label ?: none, WizardStep.Age)
        answer(titles[5], where, WizardStep.Where)
        answer(titles[6], aches, WizardStep.Aches)
        answer(titles[7], avoid, WizardStep.Exercises)
        answer(titles[8], favour, WizardStep.Exercises)
        answer(titles[9], me.anythingElse ?: none, WizardStep.AnythingElse)
    }
    Spacer(Modifier.height(GroupGap))
    val howITrain = stringResource(R.string.how_i_train)
    val howITrainAbout = stringResource(R.string.wizard_how_i_train_about)
    SegmentedList { answer(howITrain, howITrainAbout, onHowITrain) }
    Spacer(Modifier.navigationBarsPadding())
}

/** How I train's principles, opened from the summary: kept as they change, as on the page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HowITrainPage(how: HowITrain, onHowITrain: (HowITrain) -> Unit, onBack: () -> Unit, modifier: Modifier) {
    Scaffold(
        modifier = modifier.imePadding(),
        containerColor = Tones.screen,
        topBar = {
            TopAppBar(
                colors = hyperlogTopBarColors(),
                title = { Text(stringResource(R.string.how_i_train)) },
                navigationIcon = { IconButton(onClick = onBack) { SymbolIcon(Symbol.ArrowBack, stringResource(R.string.checkin_back)) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = GroupGap)) {
            HowITrainFields(how, onHowITrain)
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}
