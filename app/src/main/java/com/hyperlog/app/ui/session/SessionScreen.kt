package com.hyperlog.app.ui.session

import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import com.hyperlog.app.session.machineChoices
import com.hyperlog.app.domain.LadderSpec
import androidx.compose.foundation.lazy.itemsIndexed
import com.hyperlog.app.ui.theme.Sheet
import com.hyperlog.app.ui.theme.Tones
import com.hyperlog.app.ui.theme.cardBehind
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.coach.Change
import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.domain.SetEntry
import com.hyperlog.app.domain.WarmupSet
import com.hyperlog.app.domain.formatKg
import com.hyperlog.app.domain.summary
import com.hyperlog.app.domain.withPlates
import com.hyperlog.app.gym.EquipmentItem
import com.hyperlog.app.history.GoalProgress
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.TechniqueVideo
import com.hyperlog.app.library.search
import com.hyperlog.app.program.SetPrescription
import com.hyperlog.app.program.plannedWeight
import com.hyperlog.app.progression.Suggestion
import com.hyperlog.app.session.ActiveSession
import com.hyperlog.app.session.Correction
import com.hyperlog.app.session.FollowUp
import com.hyperlog.app.session.SessionExercise
import com.hyperlog.app.session.SessionOrder
import com.hyperlog.app.session.keptEdits
import com.hyperlog.app.ui.gym.ItemEditor
import com.hyperlog.app.ui.gym.LadderEditor
import com.hyperlog.app.ui.gym.LadderKind
import com.hyperlog.app.ui.history.GoalFigure
import com.hyperlog.app.ui.history.TrendChart
import com.hyperlog.app.ui.history.goalLabel
import com.hyperlog.app.ui.history.goalStatus
import com.hyperlog.app.ui.library.SearchField
import com.hyperlog.app.ui.library.openTechniqueVideo
import com.hyperlog.app.ui.program.SetSummary
import com.hyperlog.app.ui.program.symbol
import com.hyperlog.app.ui.program.techniqueText
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon
import kotlinx.coroutines.delay

/**
 * The logging screen (docs/design/logging-screen.md): one exercise at a time as set rows, the
 * Next line, and the pad. An ad-hoc session has no targets, so the Target column is left out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    session: ActiveSession,
    exercises: List<Exercise>,
    actions: SessionActions,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
    /** "Keep edits for the block": shows the session's edits as a Change for review. */
    onKeepEdits: (Change) -> Unit = {},
    /** The lift goals, each with its exercise's estimates, for the prescription sheet. */
    goals: List<GoalProgress> = emptyList(),
    /** The gym's shared plates, which a new plate-loaded machine starts with. */
) {
    var showLeave by rememberSaveable { mutableStateOf(false) }
    var showList by rememberSaveable { mutableStateOf(false) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var showRest by rememberSaveable { mutableStateOf(false) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var showFinish by rememberSaveable { mutableStateOf(false) }
    /** The set whose options sheet is open, by index; null when closed. */
    var setOptions by rememberSaveable { mutableStateOf<Int?>(null) }
    /** The session exercise being swapped, and the one asked about before it's removed. */
    var swapping by rememberSaveable { mutableStateOf<Long?>(null) }
    var removing by rememberSaveable { mutableStateOf<Long?>(null) }
    val offered = exercises.filter { !it.hidden }
    val now = rememberNow()
    val keepSummary = session.day?.let { day -> stringResource(R.string.session_keep_summary, day.day.name, day.block.name) }
    KeepScreenOn()

    // Back steps out of a correction, then out of a finished exercise, and only then asks to leave.
    BackHandler {
        when {
            session.correction != null -> actions.cancelCorrection()
            session.warmupEdit != null -> actions.closeWarmup()
            session.viewingId != null -> actions.closeView()
            else -> showLeave = true
        }
    }

    val current = session.current
    /** A finished exercise opened from the list, looked at in place of the current one. */
    val viewing = session.shown?.takeIf { session.viewingId != null }
    var addingMachine by rememberSaveable { mutableStateOf(false) }
    if (addingMachine && current != null && current.needsMachine) {
        // Machines get a name for what they are; a cable station serves many exercises.
        val equipment = current.exercise.equipment
        LadderEditor(
            title = stringResource(R.string.machine_new),
            name = if (equipment == Equipment.Machine) current.exercise.name else if (equipment == Equipment.SmithMachine) equipment.label else "",
            spec = null,
            kinds = LadderKind.Items,
            pinPlates = session.gym?.pinPlates.orEmpty(),
            readable = true,
            // A leg press or sled opens on plates (the gym's, from a zero start), a stack machine on a stack.
            defaultKind = if (current.exercise.plateLoaded) LadderKind.Plates else null,
            gymPlates = session.gym?.plates ?: LadderSpec.DefaultGymPlates,
            onSave = { name, spec -> actions.addMachine(name, spec); addingMachine = false },
            onDelete = null,
            onClose = { addingMachine = false },
        )
        return
    }
    // The machine's setup, opened from the exercise sheet: its name, ladder or starting weight.
    var editingMachine by rememberSaveable { mutableStateOf<Long?>(null) }
    val gym = session.gym
    val edited = editingMachine?.let { id -> session.exercises.firstNotNullOfOrNull { e -> e.machine?.takeIf { it.id == id } } }
    if (edited != null && gym != null) {
        ItemEditor(
            gym, edited,
            onSave = { actions.saveMachine(it); editingMachine = null },
            onDelete = null,
            onClose = { editingMachine = null },
        )
        return
    }
    // A machine set up as a guess asks for its stack's labels the first time it is used.
    val unsure = current?.machine?.takeIf { current.needsLadder }
    if (unsure != null) {
        LadderEditor(
            title = stringResource(R.string.ladder_set_up, unsure.name),
            name = null,
            spec = null,
            kinds = LadderKind.Items,
            pinPlates = session.gym?.pinPlates.orEmpty(),
            readable = true,
            machine = true,
            defaultKind = if (current.exercise.plateLoaded) LadderKind.Plates else null,
            gymPlates = session.gym?.plates ?: LadderSpec.DefaultGymPlates,
            onSave = { _, spec -> actions.setUpLadder(spec) },
            onDelete = null,
            onClose = actions::skipLadderSetup,
            intro = stringResource(R.string.ladder_set_up_intro, unsure.name),
        )
        return
    }
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize()) {
          Box(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
                if (current == null) {
                    ExercisePicker(
                        offered,
                        title = stringResource(R.string.session_add_exercise),
                        onPick = actions::addExercise,
                        modifier = Modifier.weight(1f),
                    )
                } else if (viewing != null) {
                    Header(
                        exercise = viewing,
                        session = session,
                        now = now,
                        onName = { showDetails = true },
                        onTimer = { showRest = true },
                        onAddExercise = { showPicker = true },
                        onFinish = { showFinish = true },
                        onSwap = null,
                        onRemove = null,
                        swappedFrom = viewing.inPlaceOf
                            ?.let { id -> exercises.firstOrNull { it.id == id }?.name },
                    )
                    SetTable(
                        TableState(
                            viewing, entry = null, followUp = null, weightFlagged = false, holdSeconds = null,
                            actions = actions, onSetOptions = {},
                            correction = session.correction,
                            onCorrect = { set, ext -> actions.correct(viewing.id, set, ext) },
                            undoable = { session.undoable(viewing, it) },
                            onUndo = { actions.undo(viewing.id) },
                        ),
                        Modifier.weight(1f),
                    )
                } else {
                    Header(
                        exercise = current,
                        session = session,
                        now = now,
                        onName = { showDetails = true },
                        onTimer = { showRest = true },
                        onAddExercise = { showPicker = true },
                        onFinish = { showFinish = true },
                        // Once a set is logged on it, the exercise is what was done.
                        onSwap = if (current.sets.isEmpty()) { { swapping = current.id } } else null,
                        onRemove = if (current.sets.isEmpty()) { { removing = current.id } } else null,
                        onKeepEdits = keepSummary?.let { summary -> session.keptEdits(summary)?.let { change -> { onKeepEdits(change) } } },
                        swappedFrom = current.inPlaceOf
                            ?.let { id -> exercises.firstOrNull { it.id == id }?.name },
                        why = current.suggestion?.takeIf { it.why != null && !current.done && !current.needsMachine },
                    )
                    if (current.needsMachine) {
                        MachinePicker(
                            session.gym?.name.orEmpty(),
                            machineChoices(current.exercise, session.gym?.items.orEmpty()),
                            onPick = actions::chooseMachine,
                            onAdd = { addingMachine = true },
                            onNotHere = actions::notAtGym,
                            modifier = Modifier.weight(1f),
                            pinPlates = session.gym?.pinPlates.orEmpty(),
                            gymPlates = session.gym?.plates ?: LadderSpec.DefaultGymPlates,
                        )
                    } else {
                        current.ladderOffer?.let { offer ->
                            LadderOffer(offer, current.machine?.name.orEmpty(), session.gym?.pinPlates.orEmpty(), onTake = actions::takeLadderOffer, onDecline = actions::declineLadderOffer)
                        }
                        val table = TableState(
                            current, session.entry, session.followUp, session.weightCheck != null,
                            holdSeconds = session.holdStartedAt?.let { ((now - it) / 1000).toInt().coerceAtLeast(0) },
                            actions = actions,
                            onSetOptions = { setOptions = it },
                            warmups = session.warmups,
                            onTickWarmup = actions::tickWarmup,
                            warmupEdit = session.warmupEdit,
                            onEditWarmup = actions::editWarmup,
                            onAddSet = { actions.addSet(current.id) },
                            correction = session.correction,
                            onCorrect = if (session.correctable) { { set, ext -> actions.correct(current.id, set, ext) } } else null,
                            onLogShown = actions::logShown,
                            undoable = { session.undoable(current, it) },
                            onUndo = { actions.undo(current.id) },
                            onAddDrop = { i ->
                                (current.techniqueAt(i) as? com.hyperlog.app.program.Technique.Dropset)?.let { d ->
                                    actions.setTechnique(i, d.copy(drops = d.drops + 1))
                                }
                            },
                        )
                        SetTable(table, Modifier.weight(1f))
                    }
                }
            }
          }
            // Next is the pad's top row, and stays at the bottom as the same row when the pad is hidden or gone.
            val nextRow: @Composable () -> Unit = when {
                current == null -> { {} }
                viewing != null -> { {
                    NextLine(stringResource(R.string.session_now), current.exercise.name, onClick = actions::closeView, symbol = Symbol.ChevronRight, description = stringResource(R.string.back))
                } }
                else -> { {
                    val next = session.next
                    NextLine(
                        label = if (next != null) stringResource(R.string.session_next) else null,
                        name = next?.exercise?.name ?: stringResource(R.string.session_last_exercise),
                        onClick = { showList = true },
                    )
                } }
            }
            val entry = session.entry
            val correction = session.correction
            val warmupEdit = session.warmupEdit
            val corrected = correction?.let { c -> session.exercises.firstOrNull { it.id == c.sessionExerciseId } }
            if (correction != null && corrected != null) {
                // A drop or mini-set has no RIR: ✓ needs only its weight and reps.
                val fixing = correction.entry
                val canConfirm = if (correction.kind == null || fixing.field == Field.Weight) fixing.canConfirm else fixing.toExtension(correction.kind) != null
                Pad(fixing, corrected.ladder, session.weightCheck, actions, canConfirm = canConfirm, correction = correction, plates = corrected.plates, platesOnMachine = corrected.platesOnMachine, pinLoad = corrected.pinLoader(), top = nextRow)
            } else if (viewing != null) {
                BottomRow(nextRow)
            } else if (current != null && warmupEdit != null) {
                // A warm-up on the pad: weight, then reps, then ticked; no RIR.
                val typed = warmupEdit.entry
                val canConfirm = if (typed.field == Field.Weight) typed.weightKg != null else typed.full.toIntOrNull()?.let { it > 0 } == true && typed.weightKg != null
                Pad(typed, current.ladder, null, actions, canConfirm = canConfirm, plates = current.plates, platesOnMachine = current.platesOnMachine, pinLoad = current.pinLoader(), top = nextRow,
                    warmup = warmupEdit.index to session.warmups.size)
            } else if (current != null && entry != null) {
                // A weight check always shows, even with the pad folded away.
                val followUp = session.followUp
                val holdRunning = session.holdStartedAt != null
                // A drop or mini-set has no RIR: ✓ needs only its weight and reps (a hold, its seconds or a running timer).
                val canConfirm = when {
                    followUp == null || entry.field == Field.Weight -> entry.canConfirm
                    holdRunning -> true
                    else -> entry.toExtension(com.hyperlog.app.domain.Extension.Kind.Drop) != null
                }
                if (session.padVisible || session.weightCheck != null) Pad(entry, current.ladder, session.weightCheck, actions, followUp = followUp, holdRunning = holdRunning, canConfirm = canConfirm, plates = current.plates, platesOnMachine = current.platesOnMachine, pinLoad = current.pinLoader(), top = nextRow)
                else HiddenPad(entry, actions, canConfirm = canConfirm, top = nextRow)
            } else if (current != null && current.done && session.exercises.all { it.prescription == null || it.done }) {
                DayDoneBar(onFinish = { showFinish = true })
            } else if (current != null) {
                BottomRow(nextRow)
            } else {
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }

    if (showLeave) {
        AlertDialog(
            onDismissRequest = { showLeave = false },
            title = { Text(stringResource(R.string.leave_title)) },
            text = { Text(stringResource(R.string.leave_body)) },
            // Every dialog puts its way out first and its action last, at the right.
            confirmButton = {
                TextButton(onClick = { showLeave = false; onLeave() }) { Text(stringResource(R.string.leave_leave)) }
            },
            dismissButton = { TextButton(onClick = { showLeave = false }) { Text(stringResource(R.string.leave_stay)) } },
        )
    }
    if (showFinish) {
        // Finishing is one menu row away from Add exercise, so it asks first, like leaving does.
        AlertDialog(
            onDismissRequest = { showFinish = false },
            title = { Text(stringResource(R.string.finish_title)) },
            text = { Text(stringResource(R.string.finish_body)) },
            confirmButton = {
                TextButton(onClick = { showFinish = false; actions.finish() }) { Text(stringResource(R.string.finish_finish)) }
            },
            dismissButton = { TextButton(onClick = { showFinish = false }) { Text(stringResource(R.string.finish_keep_going)) } },
        )
    }
    if (showList) {
        Sheet(onDismiss = { showList = false }) {
            SessionList(
                session,
                onStart = { actions.switchTo(it); showList = false },
                onOpen = { actions.open(it); showList = false },
                onAdd = { showList = false; showPicker = true },
                onFinish = { showList = false; showFinish = true },
                edits = { e, blocks ->
                    val block = blocks.indexOfFirst { b -> b.any { it.id == e.id } }
                    ExerciseEdits(
                        onSwap = if (e.sets.isEmpty()) { { showList = false; swapping = e.id } } else null,
                        onAddSet = if (e.prescription != null) { { actions.addSet(e.id) } } else null,
                        onMoveUp = if (block > 0) { { actions.move(e.id, -1) } } else null,
                        onMoveDown = if (block < blocks.size - 1) { { actions.move(e.id, 1) } } else null,
                        onRemove = if (e.sets.isEmpty()) { { removing = e.id } } else null,
                    )
                },
            )
        }
    }
    if (showPicker) {
        Sheet(onDismiss = { showPicker = false }) {
            ExercisePicker(
                offered,
                title = stringResource(R.string.session_add_exercise),
                onPick = { actions.addExercise(it); showPicker = false },
            )
        }
    }
    val detailed = viewing ?: current
    if (showDetails && detailed != null) {
        val current = detailed
        Sheet(onDismiss = { showDetails = false }) {
            ExerciseSheet(
                current.exercise,
                // With the session's changes to the sets' techniques.
                plan = current.prescription?.mapIndexed { i, set -> set.copy(technique = current.techniqueAt(i)) },
                slotNote = current.slot?.note,
                goal = goals.firstOrNull { it.goal.exerciseId == current.exercise.id },
                machine = current.machine?.let { m -> stringResource(R.string.machine_at, m.name, session.gym?.name.orEmpty()) },
                // The machine can change until a set is logged on it; after that the sets belong to it.
                onChangeMachine = if (current.machine != null && current.sets.isEmpty()) {
                    { showDetails = false; actions.changeMachine() }
                } else null,
                onEditMachine = current.machine?.takeIf { session.gym != null }?.let { m -> { showDetails = false; editingMachine = m.id } },
                // The full reason for the weight, and the old app's set for a machine, live here rather than on the screen.
                why = current.suggestion?.takeIf { !current.done }?.let { s -> s.why?.let { it to s.direction(current.sets.lastOrNull()?.weightKg) } },
                startingHint = current.startingHint,
            )
        }
    }
    val optionsFor = setOptions
    if (optionsFor != null && current != null) {
        val active = optionsFor == current.sets.size
        Sheet(onDismiss = { setOptions = null }) {
            SetSheet(
                number = optionsFor + 1,
                technique = current.techniqueAt(optionsFor),
                split = session.entry?.takeIf { active && current.exercise.unilateral && session.followUp == null }?.let { it.right != null },
                onTechnique = { actions.setTechnique(optionsFor, it); setOptions = null },
                onSplit = actions::split,
                target = current.prescription?.getOrNull(optionsFor)?.takeIf { current.editable(optionsFor) },
                onTarget = { reps, rir -> actions.setTarget(current.id, optionsFor, reps, rir) },
                onRemove = if (current.removable(optionsFor)) { { actions.removeSet(current.id, optionsFor); setOptions = null } } else null,
            )
        }
    }
    session.exercises.firstOrNull { it.id == swapping }?.let { swapped ->
        Sheet(onDismiss = { swapping = null }) {
            SwapPicker(swapped, exercises, onPick = { actions.swap(swapped.id, it); swapping = null })
        }
    }
    session.exercises.firstOrNull { it.id == removing }?.let { removed ->
        RemoveExerciseDialog(removed.exercise.name, onRemove = { actions.removeExercise(removed.id) }, onDismiss = { removing = null })
    }
    if (showRest && session.restEndsAt != null && !session.breathing) {
        Sheet(onDismiss = { showRest = false }) {
            RestSheet(session.restEndsAt - now, actions, onEnd = { showRest = false })
        }
    }
}

// ---------------------------------------------------------------- header

@Composable
private fun Header(
    exercise: SessionExercise,
    session: ActiveSession,
    now: Long,
    onName: () -> Unit,
    onTimer: () -> Unit,
    onAddExercise: () -> Unit,
    onFinish: () -> Unit,
    onSwap: (() -> Unit)?,
    onRemove: (() -> Unit)?,
    /** The planned exercise's name when the slot was swapped in this session. */
    swappedFrom: String?,
    /** Keeps the session's edits for the rest of the block; null when there are none to keep. */
    onKeepEdits: (() -> Unit)? = null,
    /** Why the set to do's weight is what it is; null for none (a set that follows the one before, a finished exercise). */
    why: Suggestion? = null,
) {
    var menu by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
    // Name, the one clock and ⋮ on one row.
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = Margin, end = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val details = stringResource(R.string.session_exercise_details)
        Text(
            exercise.exercise.name,
            Modifier.weight(1f).clickable(onClickLabel = details, onClick = onName),
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val hold = session.followUp as? com.hyperlog.app.session.FollowUp.Hold
        val holdStarted = session.holdStartedAt
        val restEnds = session.restEndsAt
        // One clock: the hold timer, the breathing pause before a mini-set, or rest.
        when {
            hold != null && holdStarted != null -> {
                Spacer(Modifier.width(8.dp))
                HoldPill(now - holdStarted, hold.seconds * 1000L)
            }
            restEnds != null && session.breathing -> if (restEnds > now) {
                Spacer(Modifier.width(8.dp))
                BreathPill(restEnds - now, session.restTotalMs)
            }
            restEnds != null -> {
                // A long name ellipsizes clear of the pill.
                Spacer(Modifier.width(8.dp))
                RestPill(restEnds - now, session.restTotalMs, onTimer)
            }
        }
        Box {
            IconButton(onClick = { menu = true }) {
                SymbolIcon(Symbol.MoreVert, stringResource(R.string.session_menu), tint = cs.onSurfaceVariant)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.session_add_exercise)) },
                    leadingIcon = { SymbolIcon(Symbol.Add, null) },
                    onClick = { menu = false; onAddExercise() },
                )
                onSwap?.let { swap ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.edit_swap)) },
                        leadingIcon = { SymbolIcon(Symbol.SwapHoriz, null) },
                        onClick = { menu = false; swap() },
                    )
                }
                onRemove?.let { remove ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.edit_remove_exercise)) },
                        leadingIcon = { SymbolIcon(Symbol.Delete, null) },
                        onClick = { menu = false; remove() },
                    )
                }
                onKeepEdits?.let { keep ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.session_keep_edits)) },
                        leadingIcon = { SymbolIcon(Symbol.Keep, null) },
                        onClick = { menu = false; keep() },
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.session_finish)) },
                    leadingIcon = { SymbolIcon(Symbol.CheckCircle, null) },
                    onClick = { menu = false; onFinish() },
                )
            }
        }
    }
    // What the slot is: a superset's partner, a swap's planned exercise, as outlined chips.
    val partner = SessionOrder.partner(session.exercises, exercise)
    val superset = exercise.slot?.takeIf { partner != null }?.let { stringResource(R.string.chip_superset, it.label, partner!!.exercise.name) }
    if (superset != null || swappedFrom != null) {
        Row(
            Modifier.fillMaxWidth().padding(start = Margin, end = Margin, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            superset?.let { HeaderChip(null, it, Modifier.weight(1f, fill = false)) }
            swappedFrom?.let { HeaderChip(Symbol.SwapHoriz, stringResource(R.string.chip_swapped, it), Modifier.weight(1f, fill = false)) }
        }
    }
    why?.let { SuggestionLine(it, from = exercise.sets.lastOrNull()?.weightKg) }
    }
}

/** An outlined chip under the name: `⇄ for Bench Press`, `A1 + Leg Curl`. It only says what the slot is. */
@Composable
private fun HeaderChip(symbol: Symbol?, text: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier.height(32.dp).border(1.dp, cs.outlineVariant, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .padding(start = if (symbol != null) 8.dp else 12.dp, end = 12.dp).testTag(HeaderChipTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        symbol?.let {
            SymbolIcon(it, null, Modifier.size(18.dp), tint = cs.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

internal const val HeaderChipTag = "header-chip"

@Composable
private fun RestSheet(leftMs: Long, actions: SessionActions, onEnd: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Margin).padding(bottom = 24.dp).navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.rest_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = { actions.adjustRest(-15_000) }, Modifier.width(96.dp)) { Text("−15") }
            Text(
                clock(leftMs),
                Modifier.width(160.dp),
                style = MaterialTheme.typography.displayMedium.merge(Tnum),
                textAlign = TextAlign.Center,
            )
            FilledTonalButton(onClick = { actions.adjustRest(15_000) }, Modifier.width(96.dp)) { Text("+15") }
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = { actions.endRest(); onEnd() }) { Text(stringResource(R.string.rest_end)) }
    }
}

// ---------------------------------------------------------------- suggestion

internal const val SuggestionTag = "suggestion"

/**
 * Why the set to do's weight is what it is (SPEC, Every suggestion says why): one short muted
 * line under the name, led by the direction in the fixed trend scale. The full sentence is in the
 * exercise sheet.
 */
@Composable
private fun SuggestionLine(suggestion: Suggestion, from: Double?) {
    val direction = suggestion.direction(from)
    val text = suggestion.short ?: suggestion.why ?: return
    Row(
        Modifier.fillMaxWidth().testTag(SuggestionTag).padding(start = Margin, end = Margin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SymbolIcon(direction.symbol, null, Modifier.size(20.dp), tint = direction.color)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium.merge(Tnum), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

internal const val StartingHintTag = "starting-hint"

/**
 * The stack the weights typed on a guessed machine show, offered once as advice: a
 * `tertiaryContainer` card with the steps, the ladder in one line, and Use or Keep as typed.
 */
@Composable
internal fun LadderOffer(offer: com.hyperlog.app.domain.LadderSpec.PinStack, machine: String, pinPlates: List<Double>, onTake: () -> Unit, onDecline: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    androidx.compose.material3.Surface(
        color = cs.tertiaryContainer,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = com.hyperlog.app.ui.theme.CardMargin).padding(bottom = 8.dp).testTag(LadderOfferTag),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 8.dp)) {
            Text(
                stringResource(R.string.ladder_offer, com.hyperlog.app.domain.StackReading.steps(offer)),
                style = MaterialTheme.typography.titleSmall, color = cs.onTertiaryContainer,
            )
            Text(
                stringResource(R.string.ladder_offer_body, machine, offer.summary(pinPlates)),
                style = MaterialTheme.typography.bodyMedium.merge(Tnum), color = cs.onTertiaryContainer,
                modifier = Modifier.padding(top = 4.dp, end = 8.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDecline) { Text(stringResource(R.string.ladder_offer_keep), color = cs.onTertiaryContainer) }
                TextButton(onClick = onTake) { Text(stringResource(R.string.ladder_offer_use), color = cs.onTertiaryContainer) }
            }
        }
    }
}

internal const val LadderOfferTag = "ladder-offer"

/** A machine's weight from the old app, in the exercise sheet: muted, since the old log doesn't say which machine it was. */
@Composable
private fun StartingHintRow(set: String) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().testTag(StartingHintTag), verticalAlignment = Alignment.Top) {
        SymbolIcon(Symbol.History, null, Modifier.size(20.dp), tint = muted)
        Spacer(Modifier.width(16.dp))
        // The set on a line of its own, so it never breaks inside "@ 2 RIR".
        Column {
            Text(stringResource(R.string.starting_hint), style = MaterialTheme.typography.bodyMedium, color = muted)
            Text(set, style = MaterialTheme.typography.bodyMedium.merge(Tnum), color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Every planned set is logged: the pad gives way to Finish session. */
@Composable
private fun DayDoneBar(onFinish: () -> Unit) {
    Row(
        // Where the pad was: a primary-container strip, rounded at the top like a sheet.
        Modifier.fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(MaterialTheme.colorScheme.primaryContainer).navigationBarsPadding()
            .padding(start = Margin, end = 16.dp, top = 20.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SymbolIcon(Symbol.CheckCircle, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        Spacer(Modifier.width(16.dp))
        Text(stringResource(R.string.session_day_done), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Button(onClick = onFinish) { Text(stringResource(R.string.session_finish)) }
    }
}

// ---------------------------------------------------------------- next line and sheets

/**
 * Next: the pad's top row, on the pad's surface: "Next" and the next exercise outside the one on
 * screen, or "Now" and the way back from a finished exercise.
 */
@Composable
internal fun NextLine(label: String?, name: String, onClick: () -> Unit, symbol: Symbol = Symbol.ExpandLess, description: String? = null) {
    Row(
        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).height(48.dp).padding(start = 16.dp, end = 12.dp).testTag(NextRowTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        label?.let {
            Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
        }
        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        SymbolIcon(symbol, description ?: stringResource(R.string.session_list), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal const val NextRowTag = "next-row"

/** The Next row alone at the bottom, on the pad's surface, when there is no pad. */
@Composable
internal fun BottomRow(content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp)) { content() }
    }
}

/** The session's exercises with what has been logged, plus adding one and finishing. */
@Composable
internal fun SessionList(
    session: ActiveSession,
    onStart: (Long) -> Unit,
    /** Opens a finished exercise to look at or correct. */
    onOpen: (Long) -> Unit = {},
    onAdd: () -> Unit,
    onFinish: () -> Unit,
    /** What each row's ⋮ offers, given the session's blocks ([SessionOrder.blocks]); null for no ⋮. */
    edits: ((SessionExercise, List<List<SessionExercise>>) -> ExerciseEdits)? = null,
) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.padding(bottom = 16.dp).navigationBarsPadding().verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.session_title), Modifier.padding(horizontal = Margin), style = MaterialTheme.typography.titleLarge)
        session.gym?.let {
            Text(it.name, Modifier.padding(horizontal = Margin), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        val numbered = session.exercises.any { it.slot != null }
        // A superset on screen is current as a pair: its partner comes up by itself.
        val onScreen = session.current?.let { c -> SessionOrder.group(session.exercises, c).map { it.id } }.orEmpty()
        val blocks = SessionOrder.blocks(session.exercises)
        session.exercises.forEachIndexed { index, e ->
            val now = e.id in onScreen
            // One group of rows on the sheet; the exercise on screen is lifted into secondary container.
            com.hyperlog.app.ui.theme.Segment(index, session.exercises.size, color = if (now) cs.secondaryContainer else com.hyperlog.app.ui.theme.Tones.sheetCard) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(start = Margin - com.hyperlog.app.ui.theme.CardMargin, end = if (edits != null) 4.dp else 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(40.dp)) {
                    when {
                        now -> SymbolIcon(Symbol.RadioButtonChecked, null, tint = cs.primary)
                        // Done means every planned set; an ad-hoc exercise is done once it has a set.
                        e.done || (e.prescription == null && e.sets.isNotEmpty()) -> SymbolIcon(Symbol.CheckCircle, null, tint = cs.onSurfaceVariant)
                        else -> SymbolIcon(Symbol.RadioButtonUnchecked, null, tint = cs.outline)
                    }
                }
                // A program's slots keep the day's numbering (1, 2a, 2b) in a column of their own,
                // so names align; superset partners' labels are tertiary, so the pair reads as one group.
                if (numbered) {
                    Box(Modifier.width(40.dp)) {
                        e.slot?.label?.let {
                            val paired = SessionOrder.partner(session.exercises, e) != null
                            Text(it, style = MaterialTheme.typography.labelLarge.merge(Tnum), color = if (paired) cs.tertiary else cs.onSurfaceVariant)
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(e.exercise.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (e.sets.isNotEmpty()) {
                        Text(
                            e.sets.joinToString("   ") { it.summary().replace(' ', ' ') },
                            style = MaterialTheme.typography.bodyMedium.merge(Tnum),
                            color = cs.onSurfaceVariant,
                        )
                    } else {
                        e.prescription?.let { SetSummary(it) }
                    }
                }
                when {
                    now -> Unit
                    e.done -> FilledTonalButton(onClick = { onOpen(e.id) }) { Text(stringResource(R.string.session_open_exercise)) }
                    else -> FilledTonalButton(onClick = { onStart(e.id) }) { Text(stringResource(R.string.session_start_exercise)) }
                }
                edits?.let { ExerciseEditMenu(e.exercise.name, it(e, blocks)) }
            }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = Margin - 12.dp, end = 16.dp, top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onAdd) {
                SymbolIcon(Symbol.Add, null, Modifier.width(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.session_add_exercise))
            }
            TextButton(onClick = onFinish) { Text(stringResource(R.string.session_finish)) }
        }
    }
}

/**
 * "Which machine?": the gym's equipment with the weights each gives, and Add machine. It stands
 * in for the set table until answered, so no set can start on an unknown machine.
 */
@Composable
private fun MachinePicker(
    gym: String,
    /** The machines of the exercise's kind first, then the gym's others ([machineChoices]). */
    choices: Pair<List<EquipmentItem>, List<EquipmentItem>>,
    onPick: (Long) -> Unit,
    onAdd: () -> Unit,
    /** The gym has no equipment for it: remember that, and swap the exercise. */
    onNotHere: () -> Unit,
    modifier: Modifier = Modifier,
    /** The gym's plates for the pin, for the weights a pin stack gives. */
    pinPlates: List<Double> = emptyList(),
    /** The gym's plates, which a plate-loaded machine with no pairs of its own loads. */
    gymPlates: List<Double> = LadderSpec.DefaultGymPlates,
) {
    val cs = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Text(
            stringResource(R.string.machine_title),
            Modifier.padding(start = Margin, end = Margin, top = 8.dp),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            stringResource(R.string.machine_body, gym),
            Modifier.padding(start = Margin, end = Margin, top = 4.dp, bottom = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
        )
        // The machines that fit as one group of rows, the gym's others under their own head, then the ways out.
        Spacer(Modifier.height(8.dp))
        val (fit, others) = choices
        fit.forEachIndexed { i, item -> MachineRow(item, i, fit.size, muted = false, onPick, pinPlates, gymPlates) }
        if (others.isNotEmpty()) {
            com.hyperlog.app.ui.theme.GroupHead(stringResource(R.string.machine_others, gym), color = cs.onSurfaceVariant)
            others.forEachIndexed { i, item -> MachineRow(item, i, others.size, muted = true, onPick, pinPlates, gymPlates) }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onAdd, Modifier.padding(start = Margin - 12.dp, top = 8.dp)) {
            SymbolIcon(Symbol.Add, null, Modifier.width(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.machine_add))
        }
        TextButton(onClick = onNotHere, Modifier.padding(start = Margin - 12.dp)) {
            SymbolIcon(Symbol.Block, null, Modifier.width(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.machine_not_here, gym))
        }
    }
}

/** The pad's pin-stack loading for this exercise's machine; null unless it is a pin stack. */
private fun com.hyperlog.app.session.SessionExercise.pinLoader(): ((Double) -> com.hyperlog.app.domain.PinLoad?)? =
    if (machine?.ladder is com.hyperlog.app.domain.LadderSpec.PinStack) { kg -> pinLoad(kg) } else null

/** One of the gym's machines with the weights it gives; another kind's in a muted name. */
@Composable
private fun MachineRow(item: EquipmentItem, i: Int, count: Int, muted: Boolean, onPick: (Long) -> Unit, pinPlates: List<Double> = emptyList(), gymPlates: List<Double> = LadderSpec.DefaultGymPlates) {
    val cs = MaterialTheme.colorScheme
            com.hyperlog.app.ui.theme.Segment(i, count, onClick = { onPick(item.id) }) {
            Column(
                Modifier.fillMaxWidth().heightIn(min = 72.dp)
                    .padding(horizontal = Margin - com.hyperlog.app.ui.theme.CardMargin, vertical = 12.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(item.name, style = MaterialTheme.typography.bodyLarge, color = if (muted) cs.onSurfaceVariant else cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    item.ladder.withPlates(gymPlates).summary(pinPlates),
                    style = MaterialTheme.typography.bodyMedium.merge(Tnum),
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            }
}

/** The library's exercises, hidden ones left out, with a search field on top. */
@Composable
private fun ExercisePicker(exercises: List<Exercise>, title: String, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(modifier.fillMaxWidth()) {
        Text(
            title,
            Modifier.padding(start = Margin, end = Margin, top = 16.dp, bottom = 16.dp),
            style = MaterialTheme.typography.headlineSmall,
        )
        SearchField(
            query,
            onChange = { query = it },
            placeholder = stringResource(R.string.session_search),
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
        )
        val listState = rememberLazyListState()
        LaunchedEffect(query) { listState.scrollToItem(0) }
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).imePadding(), state = listState) {
            val found = exercises.search(query)
            itemsIndexed(found, key = { _, it -> it.id }) { i, e ->
                com.hyperlog.app.ui.theme.Segment(i, found.size, color = MaterialTheme.colorScheme.surfaceContainerHigh, onClick = { onPick(e.id) }) {
                    Text(
                        e.name,
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = com.hyperlog.app.ui.theme.CardPadding, vertical = 16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            item { Spacer(Modifier.navigationBarsPadding().height(16.dp)) }
        }
    }
}

/**
 * The exercise's prescription sheet. An ad-hoc session has no prescription, so it shows what
 * the library knows: equipment and muscles, the technique note and the technique video, or a
 * YouTube search for the technique when the exercise has none.
 */
@Composable
internal fun ExerciseSheet(
    exercise: Exercise,
    machine: String?,
    onChangeMachine: (() -> Unit)?,
    onEditMachine: (() -> Unit)? = null,
    plan: List<SetPrescription>? = null,
    slotNote: String? = null,
    goal: GoalProgress? = null,
    /** The full reason for the suggested weight and its direction; null with none. */
    why: Pair<String, Direction>? = null,
    /** A machine's set from the old app (`45 kg × 12 @ 1 RIR, 12 Sep`); null with none. */
    startingHint: String? = null,
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(horizontal = Margin).padding(bottom = 24.dp).navigationBarsPadding()) {
        Text(exercise.name, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(exercise.summary, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        machine?.let {
            Spacer(Modifier.height(16.dp))
            Row(Modifier.cardBehind(Tones.sheetCard, inset = -12.dp).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                SymbolIcon(Symbol.LocationOn, null, Modifier.size(20.dp), tint = cs.onSurfaceVariant)
                Spacer(Modifier.width(16.dp))
                Text(it, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                onEditMachine?.let { edit ->
                    val label = stringResource(R.string.machine_edit_description)
                    TextButton(onClick = edit, Modifier.semantics { contentDescription = label }) {
                        Text(stringResource(R.string.machine_edit))
                    }
                }
                onChangeMachine?.let { change ->
                    val label = stringResource(R.string.machine_change_description)
                    TextButton(onClick = change, Modifier.semantics { contentDescription = label }) {
                        Text(stringResource(R.string.machine_change))
                    }
                }
            }
        }
        why?.let { (text, direction) ->
            Spacer(Modifier.height(16.dp))
            Row(Modifier.testTag(SuggestionTag)) {
                SymbolIcon(direction.symbol, null, Modifier.size(20.dp), tint = direction.color)
                Spacer(Modifier.width(16.dp))
                Text(text, style = MaterialTheme.typography.bodyLarge)
            }
        }
        startingHint?.let {
            Spacer(Modifier.height(16.dp))
            StartingHintRow(it)
        }
        plan?.let { Prescription(it) }
        goal?.let { GoalBlock(exercise.name, it) }
        slotNote?.let { note ->
            Spacer(Modifier.height(24.dp))
            val label = stringResource(R.string.sheet_program_note)
            Row(Modifier.semantics { contentDescription = label }) {
                SymbolIcon(Symbol.ViewTimeline, null, Modifier.size(20.dp), tint = cs.onSurfaceVariant)
                Spacer(Modifier.width(16.dp))
                Text(note, style = MaterialTheme.typography.bodyLarge)
            }
        }
        exercise.note?.let { note ->
            Spacer(Modifier.height(24.dp))
            Row {
                SymbolIcon(Symbol.StickyNote2, null, Modifier.size(20.dp), tint = cs.onSurfaceVariant)
                Spacer(Modifier.width(16.dp))
                Text(note, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Spacer(Modifier.height(24.dp))
        val link = exercise.video
        if (link != null) {
            val at = TechniqueVideo.parse(link)?.startLabel
            FilledTonalButton(onClick = { context.openTechniqueVideo(link) }) {
                SymbolIcon(Symbol.SmartDisplay, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (at != null) stringResource(R.string.sheet_video_at, at) else stringResource(R.string.sheet_video))
            }
        } else {
            // No video in the library: a YouTube search for the technique, in the same Custom Tab.
            FilledTonalButton(onClick = { context.openTechniqueVideo(TechniqueVideo.searchUrl(exercise.name)) }) {
                SymbolIcon(Symbol.Search, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.sheet_video_search))
            }
        }
    }
}

/**
 * The lift goal under the prescription: the flag, the estimate against the goal and how far is
 * left, then the estimate's trend with the goal line, small and without a grid.
 */
@Composable
private fun GoalBlock(name: String, progress: GoalProgress) {
    val cs = MaterialTheme.colorScheme
    val estimate = progress.estimateKg
    Spacer(Modifier.height(24.dp))
    Row {
        SymbolIcon(Symbol.Flag, null, Modifier.padding(top = 4.dp).size(20.dp), tint = cs.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            GoalFigure(estimate, progress.goal, MaterialTheme.typography.titleLarge)
            Text(goalStatus(estimate, progress.goal), style = MaterialTheme.typography.bodyMedium.merge(Tnum), color = cs.onSurfaceVariant)
            val points = progress.points.filter { it.estimateKg != null }
            if (points.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                TrendChart(
                    dates = points.map { it.date },
                    line = points.map { it.estimateKg },
                    dots = points.map { null },
                    selected = points.size - 1,
                    onSelect = {},
                    height = 64.dp,
                    axis = false,
                    ticks = false,
                    goal = progress.goal.kg,
                    goalLabel = goalLabel(progress.goal),
                    description = stringResource(R.string.goal_chart, name),
                )
            }
        }
    }
}

/**
 * Every planned set: number, target reps and RIR ring, and its technique or back-off rule. Rest
 * is one line when every set shares it, else at the end of each row.
 */
@Composable
private fun Prescription(plan: List<SetPrescription>) {
    val cs = MaterialTheme.colorScheme
    val sameRest = plan.map { it.rest }.distinct().size == 1
    Spacer(Modifier.height(24.dp))
    // The plan is one card on the sheet, its edge 12 dp out from the text.
    Column(Modifier.cardBehind(Tones.sheetCard, inset = -12.dp).padding(vertical = 4.dp)) {
    plan.forEachIndexed { i, set ->
        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${i + 1}",
                Modifier.width(ColSet),
                style = MaterialTheme.typography.titleMedium.merge(Tnum),
                color = cs.onSurfaceVariant,
            )
            Text(set.reps.toString(), Modifier.width(64.dp), style = MaterialTheme.typography.bodyLarge.merge(Tnum), maxLines = 1)
            Box(Modifier.width(32.dp)) { set.rir?.let { RirMark(it, filled = false) } }
            val technique = set.technique
            val backoff = set.backoff
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                when {
                    technique != null -> {
                        SymbolIcon(technique.symbol(), null, Modifier.size(20.dp), tint = cs.tertiary)
                        Spacer(Modifier.width(8.dp))
                        Text(techniqueText(technique), style = MaterialTheme.typography.bodyMedium, color = cs.tertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    backoff != null -> {
                        SymbolIcon(Symbol.SouthEast, null, Modifier.size(20.dp), tint = cs.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.technique_backoff, backoff.percent),
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (!sameRest) Text(clock(set.rest * 1000L), style = MaterialTheme.typography.bodyMedium.merge(Tnum), color = cs.onSurfaceVariant)
        }
    }
    if (sameRest) {
        Spacer(Modifier.height(8.dp))
        Row(Modifier.heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            SymbolIcon(Symbol.Timer, null, Modifier.size(20.dp), tint = cs.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            Text(stringResource(R.string.sheet_rest, clock(plan.first().rest * 1000L)), style = MaterialTheme.typography.bodyLarge.merge(Tnum))
        }
    }
    }
    Spacer(Modifier.height(8.dp))
}

// ---------------------------------------------------------------- effects

@Composable
private fun rememberNow(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(250)
        }
    }
    return now
}

/** The phone stays awake on the session screen, so rest can be read at a glance. */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

