package com.hyperlog.app.ui.today

import com.hyperlog.app.ui.theme.interFigures
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.hyperlog.app.ui.theme.Group
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.itemsIndexed
import com.hyperlog.app.ui.theme.Banner
import com.hyperlog.app.ui.theme.CardPadding
import com.hyperlog.app.ui.theme.EmptyState
import com.hyperlog.app.ui.theme.Segment
import com.hyperlog.app.ui.theme.Sheet
import com.hyperlog.app.ui.theme.Tones
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.gym.MissingSwaps
import com.hyperlog.app.gym.SessionSwap
import com.hyperlog.app.coach.CoachLine
import com.hyperlog.app.program.DueReview
import com.hyperlog.app.gym.Gym
import com.hyperlog.app.gym.UsualGym
import com.hyperlog.app.coach.Workouts
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.testTag
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.PlannedDay
import com.hyperlog.app.program.Program
import com.hyperlog.app.ui.program.SetSummary
import com.hyperlog.app.ui.program.StarterOffer
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon
import com.hyperlog.app.ui.theme.HyperlogTheme
import com.hyperlog.app.update.UpdateBanner
import com.hyperlog.app.session.PastSessionItem
import com.hyperlog.app.today.DayKind
import com.hyperlog.app.today.TodayState
import com.hyperlog.app.session.date
import com.hyperlog.app.ui.program.WeekdayPlanSheetContent
import com.hyperlog.app.week.DayFit
import com.hyperlog.app.week.FitAsk
import com.hyperlog.app.week.Fit
import com.hyperlog.app.week.WeekdayPlan
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Today: a calendar of weeks, done and planned sessions marked, and the day picked on it below:
 * the next day to train with Start session, a day's finished sessions, a later planned day or a
 * rest day. With no program (or a finished one) today offers an ad-hoc session; while a session
 * runs, the way back in. Starting asks for the gym, with today's usual gym picked; with no gyms
 * set up it starts straight away (docs/design/program-and-today.md).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TodayScreen(
    sessionRunning: Boolean,
    onResume: () -> Unit,
    /**
     * Starts a session at the gym (null with no gyms): the program's next day, or ad hoc when
     * [adHoc]. The swaps (slot id -> exercise) are for equipment the gym doesn't have, this session only.
     */
    onStart: (gymId: Long?, adHoc: Boolean, swaps: Map<String, String>) -> Unit,
    modifier: Modifier = Modifier,
    /** Starts this week's missed day today at the gym (Train it today), with its swaps. */
    onTrainMissed: (gymId: Long?, swaps: Map<String, String>) -> Unit = { _, _ -> },
    gyms: List<Gym> = emptyList(),
    usualGym: UsualGym? = null,
    program: ActiveProgram? = null,
    exerciseNames: Map<String, String> = emptyMap(),
    onProgram: () -> Unit = {},
    /** The starter program, offered while no program runs; null if unreadable. */
    starter: Program? = null,
    onRunStarter: () -> Unit = {},
    /** The programs saved for later, offered with the starter while no program runs, each opening its page. */
    savedPrograms: List<com.hyperlog.app.program.ListedProgram> = emptyList(),
    onOpenSaved: (Long) -> Unit = {},
    /** Starts a check-in whose first answer is the words given: a day that runs over its training day's minutes. */
    onCheckInFit: (FitAsk) -> Unit = {},
    /** The exercise library, for the swaps the coach proposes. */
    library: List<Exercise> = emptyList(),
    /** The day of the last session finished; null with none yet. */
    lastTrained: LocalDate? = null,
    /** Opens Coach with the words for [days] days off filled in. */
    onEaseBack: (days: Int) -> Unit = {},
    /**
     * Your training days (the weekday plan): the calendar's planned dates fall on them, and time
     * off counts from the first one missed. The calendar's pencil sets them in their sheet.
     */
    weekdayPlan: WeekdayPlan = WeekdayPlan.None,
    onWeekdayPlan: (WeekdayPlan) -> Unit = {},
    /** Every finished session, which the calendar marks as done; null until they've been read. */
    sessions: List<PastSessionItem>? = emptyList(),
    /** Opens a finished session in History. */
    onOpenSession: (Long) -> Unit = {},
    /** A newer release to offer, above the day; null for none. */
    update: UpdateBanner? = null,
    onUpdate: () -> Unit = {},
    onDismissUpdate: () -> Unit = {},
    /** The program day of the session running, which Resume opens; it is the day Today shows. */
    runningDay: PlannedDay? = null,
    /** The session running trains a workout: Today shows it as the day, headed "Workout". */
    runningWorkout: Boolean = false,
    /** The workout from Claude waiting today; null with none. */
    workout: Workouts.Waiting? = null,
    /** Starts [workout]'s session at the gym (null with no gyms), with swaps for what the gym lacks. */
    onStartWorkout: (id: Long, gymId: Long?, swaps: Map<String, String>) -> Unit = { _, _, _ -> },
    /** Takes the waiting workout off Today. */
    onRemoveWorkout: (Long) -> Unit = {},
    /** Puts the workout just removed back, from the snackbar's Undo. */
    onUndoRemoveWorkout: () -> Unit = {},
    /** A Change was just accepted: bring the program day into view under the workout, then [onProgramDayShown]. */
    showProgramDay: Boolean = false,
    /**
     * The day picked on the calendar (epoch day; null: today), kept by the host so Back from a
     * session opened here returns to it. Without [onSelectDay] Today keeps its own.
     */
    selectedDay: Long? = null,
    onSelectDay: ((Long?) -> Unit)? = null,
    onProgramDayShown: () -> Unit = {},
    /** A workout was just added while a session runs: say it waits until the session ends. */
    workoutAddedNotice: Boolean = false,
    onWorkoutAddedNoticeShown: () -> Unit = {},
    /** The coach line: a pattern in the log, over today's day; null for none. */
    coachLine: CoachLine? = null,
    /** Starts a check-in whose first answer is the coach line's words. */
    onCheckInLine: (String, CoachLine) -> Unit = { _, _ -> },
    /** Hides the coach line until its pattern grows. */
    onHideCoachLine: (CoachLine) -> Unit = {},
    /** A finished block's review not yet opened (SPEC, Program → Block review); null for none. */
    dueReview: DueReview? = null,
    onOpenReview: (DueReview) -> Unit = {},
) {
    val snackbars = remember { SnackbarHostState() }
    val snackScope = rememberCoroutineScope()
    val addedText = stringResource(R.string.today_workout_added_mid_session)
    LaunchedEffect(workoutAddedNotice) {
        if (workoutAddedNotice) {
            // In Today's own scope, so marking it shown doesn't cancel it.
            snackScope.launch { snackbars.showSnackbar(addedText, duration = SnackbarDuration.Long) }
            onWorkoutAddedNoticeShown()
        }
    }
    val removedText = stringResource(R.string.today_workout_removed)
    val undoText = stringResource(R.string.today_workout_undo)
    /** The kind of session the gym sheet is open for: null when closed. */
    var picking by rememberSaveable { mutableStateOf<Kind?>(null) }
    val launch: (Kind, Long?, Map<String, String>) -> Unit = { kind, gym, swaps ->
        when (kind) {
            Kind.Workout -> workout?.let { onStartWorkout(it.id, gym, swaps) }
            Kind.AdHoc -> onStart(gym, true, swaps)
            Kind.Day -> onStart(gym, false, swaps)
            Kind.Missed -> onTrainMissed(gym, swaps)
        }
    }
    val start: (Kind) -> Unit = { kind ->
        when {
            sessionRunning -> onResume()
            gyms.isEmpty() -> launch(kind, null, emptyMap())
            else -> picking = kind
        }
    }
    val kind = picking
    val today = program?.today ?: LocalDate.now()
    var ownDay by rememberSaveable { mutableStateOf<Long?>(null) }
    val pickedDay = if (onSelectDay != null) selectedDay else ownDay
    val selected = pickedDay?.let(LocalDate::ofEpochDay) ?: today
    val setSelected: (LocalDate) -> Unit = { date ->
        val day = date.toEpochDay().takeIf { date != today }
        if (onSelectDay != null) onSelectDay(day) else ownDay = day
    }
    // Until the finished sessions are read Today holds its day: today drawn untrained (the next day's
    // hero and Start) would flash on every cold start, then jump.
    val loaded = sessions != null
    val finished = sessions.orEmpty()
    val state = remember(program, finished, weekdayPlan, lastTrained, workout != null, sessionRunning, runningDay, selected, today) {
        TodayState(
            program, finished, weekdayPlan, lastTrained,
            workout = workout != null, sessionRunning = sessionRunning, runningDay = runningDay, selected = selected, today = today,
        )
    }
    val workoutDay = remember(workout) { workout?.workout?.day() }
    if (kind != null && !sessionRunning) {
        Sheet(onDismiss = { picking = null }) {
            StartSheetContent(
                gyms, usualGym,
                onStart = { gym, swaps -> picking = null; launch(kind, gym, swaps) },
                day = when (kind) {
                    Kind.Day -> state.nextDay
                    Kind.Workout -> workoutDay
                    Kind.Missed -> program?.trainToday?.day
                    Kind.AdHoc -> null
                },
                library = library,
                // A workout starts at the gym picked on Coach, or Elsewhere, without a gym.
                picked = if (kind == Kind.Workout) workout?.gymId ?: ElsewhereChoice else null,
                elsewhere = kind == Kind.Workout,
                what = workout?.workout?.name?.takeIf { kind == Kind.Workout },
            )
        }
    }
    // A workout added for today is today's main Start; the program day becomes the second card.
    val workoutFirst = workout != null && workoutDay != null && !sessionRunning
    val workoutGym = if (gyms.isEmpty()) null else gyms.firstOrNull { it.id == workout?.gymId }?.name?.let { stringResource(R.string.today_workout_at, it) }
        ?: stringResource(R.string.coach_elsewhere)
    val removeWorkout: () -> Unit = {
        workout?.let { onRemoveWorkout(it.id) }
        // At once, with Undo for the long duration: a lifter looks up before tapping.
        snackScope.launch {
            snackbars.currentSnackbarData?.dismiss()
            val result = snackbars.showSnackbar(removedText, actionLabel = undoText, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) onUndoRemoveWorkout()
        }
    }
    val startLabel = stringResource(if (sessionRunning) R.string.today_resume_session else R.string.today_start_session)
    // Named for what it starts, since the program day's card sits beside it with its own Start.
    val fabLabel = if (workoutFirst) stringResource(R.string.today_start_workout) else startLabel

    // The sessions of this program's days, which a planned day's minutes come from.
    val programSessions = remember(finished, program?.id) { finished.filter { it.programId == program?.id } }
    val fitOf: (PlannedDay, LocalDate) -> DayFit = { day, date -> Fit.of(day, date, weekdayPlan, gyms, library, aboutMinutes(day, library, programSessions)) }
    val thisWeek = weekOf(today)
    // Back to the week of the first finished session and on to the last planned day's, at least four weeks each way.
    val firstWeek = minOf(finished.minOfOrNull { weekOf(it.date()) } ?: thisWeek, thisWeek.minusWeeks(4))
    val lastWeek = maxOf(state.planned.keys.maxOrNull()?.let(::weekOf) ?: thisWeek, thisWeek.plusWeeks(4))
    val todayPage = ChronoUnit.WEEKS.between(firstWeek, thisWeek).toInt()
    val weeks by rememberUpdatedState(ChronoUnit.WEEKS.between(firstWeek, lastWeek).toInt() + 1)
    // Sessions read in later reach further back: the pages start again on the selected day's week.
    val pager = remember(firstWeek) { PagerState(ChronoUnit.WEEKS.between(firstWeek, weekOf(selected)).toInt()) { weeks } }
    val scope = rememberCoroutineScope()
    val select: (LocalDate) -> Unit = { date ->
        setSelected(date)
        val page = ChronoUnit.WEEKS.between(firstWeek, weekOf(date)).toInt()
        if (page != pager.currentPage) scope.launch { pager.animateScrollToPage(page) }
    }
    // Paged to another week, the same weekday there is picked, so the card is always of the week
    // shown. The pages are whole weeks, so that day is always on the calendar.
    val shownSelected by rememberUpdatedState(selected)
    val shownSetSelected by rememberUpdatedState(setSelected)
    LaunchedEffect(pager, firstWeek) {
        snapshotFlow { pager.settledPage }.collect { page ->
            val monday = firstWeek.plusWeeks(page.toLong())
            if (weekOf(shownSelected) != monday) shownSetSelected(monday.plusDays(shownSelected.dayOfWeek.value - 1L))
        }
    }
    val isToday = state.isToday
    // Each day keeps its scroll for each kind of list it shows, saved while Today is away (the
    // host keeps Today's saved state): Back returns to the same card, and a day that turns from
    // planned to done, or another day, opens at its top.
    val dayKind = state.kind
    // A saved value comes back whatever the inputs, so the day and kind are in the key itself.
    val dayList = rememberSaveable(selected, dayKind, saver = LazyListState.Saver, key = "day-$selected-$dayKind") { LazyListState() }
    var editingWeekdayPlan by rememberSaveable { mutableStateOf(false) }
    if (editingWeekdayPlan) {
        Sheet(onDismiss = { editingWeekdayPlan = false }) {
            WeekdayPlanSheetContent(weekdayPlan, gyms, onWeekdayPlan, onDone = { editingWeekdayPlan = false })
        }
    }
    // A workout or a running session keeps its Start floating; the next day's Start is in its card.
    // Not before the sessions are read: nothing floats over the held day.
    val fab = loaded && isToday && (workoutFirst || (sessionRunning && state.nextDay != null))
    val bottom = if (fab) 112.dp else 24.dp
    val banners: @Composable ColumnScope.() -> Unit = {
        update?.let { UpdateBanner(it, onUpdate, onDismissUpdate) }
        state.easeBack?.let { days -> TimeOffBanner(days, onEaseBack = { onEaseBack(days) }) }
        if (dueReview != null && isToday && !sessionRunning) ReviewBanner(dueReview, program?.program?.title.orEmpty()) { onOpenReview(dueReview) }
        if (coachLine != null && !sessionRunning) CoachLineBanner(coachLine, exerciseNames, onCheckInLine, onHide = { onHideCoachLine(coachLine) })
        state.again?.let { AgainNotice(it) }
    }
    Scaffold(
        modifier = modifier,
        containerColor = Tones.screen,
        snackbarHost = { SnackbarHost(snackbars) },
        floatingActionButton = {
            if (fab) {
                // The expressive medium size: the day's one action, unmistakable. With a workout today, it starts that.
                MediumExtendedFloatingActionButton(onClick = { start(if (workoutFirst) Kind.Workout else Kind.Day) }) {
                    SymbolIcon(Symbol.PlayArrow, null, Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(fabLabel, style = MaterialTheme.typography.titleMedium)
                }
            }
        },
    ) { padding ->
      if (loaded) Column(Modifier.fillMaxSize().padding(padding)) {
        // No title bar: the calendar is the top of the screen.
        val shownWeek = firstWeek.plusWeeks(pager.currentPage.toLong())
        CalendarHead(
            shownWeek,
            week = state.programWeek(shownWeek),
            // An easy week sits outside the program, so the head names it instead of a week.
            easy = state.easyWeek(shownWeek),
            showToday = !isToday || pager.currentPage != todayPage,
            onToday = { select(today) },
            noWeekdayPlan = weekdayPlan.days.isEmpty(),
            onWeekdayPlan = { editingWeekdayPlan = true },
        )
        HorizontalPager(pager, Modifier.fillMaxWidth().testTag(CalendarTag)) { page ->
            WeekRow(firstWeek.plusWeeks(page.toLong()), today, selected, state.done, state.planned, onSelect = select, missed = state.missed)
        }
        Spacer(Modifier.height(8.dp))
        val day = state.day
        when {
            isToday && workoutFirst -> {
                // Today's state is kept while away, so each workout keeps its own scroll and a new
                // one starts at its top. A saved value comes back whatever the inputs, so the
                // workout is in the key itself.
                val list = rememberSaveable(workout, saver = LazyListState.Saver, key = "workout-${workout?.id}-${workout?.workout?.name}") { LazyListState() }
                // After a Change is accepted, what it changed is the program day, under the workout.
                LaunchedEffect(showProgramDay) {
                    if (showProgramDay) {
                        if (program?.next != null) {
                            list.animateScrollToItem(1)
                        }
                        onProgramDayShown()
                    }
                }
                LazyColumn(Modifier.weight(1f).fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = bottom)) {
                    item {
                        Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            update?.let { UpdateBanner(it, onUpdate, onDismissUpdate) }
                        }
                        WorkoutCard(workout!!, workoutDay!!, exerciseNames, gym = workoutGym, onRemove = removeWorkout)
                    }
                    // With training days, only a day planned on today is the program's card here.
                    program?.next?.takeIf { it.date == today }?.let { turn ->
                        item { ProgramDayCard(turn.day, exerciseNames, onStart = { start(Kind.Day) }) }
                    }
                    item { AdHocRow(onAdHoc = { start(Kind.AdHoc) }) }
                }
            }
            // A running session keeps its day on today, with Resume floating.
            dayKind == DayKind.Running && day != null -> NextDay(
                day, exerciseNames, Modifier.weight(1f),
                banners = banners,
                isWorkout = runningWorkout,
            )
            dayKind == DayKind.Done -> LazyColumn(Modifier.weight(1f).fillMaxSize().testTag(TodayDayListTag), state = dayList, contentPadding = PaddingValues(bottom = bottom)) {
                if (isToday) {
                    item { BannerColumn(banners) }
                    // What's next comes first after training, over what was done.
                    state.nextAfter(today)?.let { (date, day) -> item { NextLink(date, day, select, Modifier.padding(start = 12.dp, bottom = 8.dp)) } }
                }
                state.doneOn.forEach { session ->
                    item(key = "s${session.id}") {
                        DoneCard(session, onOpen = { onOpenSession(session.id) })
                        Spacer(Modifier.height(16.dp))
                    }
                    itemsIndexed(session.exercises, key = { i, _ -> "s${session.id}-$i" }) { i, exercise ->
                        Segment(i, session.exercises.size) { DoneRow(i + 1, exercise) }
                    }
                    item(key = "gap${session.id}") { Spacer(Modifier.height(16.dp)) }
                }
                if (isToday) item { AdHocButton { start(Kind.AdHoc) } }
            }
            dayKind == DayKind.Planned && day != null -> {
                val date = state.date ?: selected
                val groups = groupsOf(day)
                LazyColumn(Modifier.weight(1f).fillMaxSize().testTag(TodayDayListTag), state = dayList, contentPadding = PaddingValues(bottom = bottom)) {
                    item {
                        if (isToday) BannerColumn(banners)
                        if (state.isNext) {
                            HeroCard(
                                day, date = date.takeIf { it != today },
                                onStart = { start(Kind.Day) }.takeIf { state.offersStart },
                                library = library, finished = programSessions,
                                easyDays = state.backAfter,
                                fit = fitOf(day, date), onFitTime = onCheckInFit,
                            )
                            state.trainMissed?.let { missed ->
                                TrainMissedButton(missed.date, missed.day, onClick = { start(Kind.Missed) })
                            }
                        }
                        else PlannedCard(day, selected, last = state.isLastDay, library = library, finished = programSessions, fit = fitOf(day, selected), onFitTime = onCheckInFit)
                        Spacer(Modifier.height(16.dp))
                    }
                    itemsIndexed(groups, key = { _, g -> g.first().id }) { i, group ->
                        Segment(i, groups.size) { SlotRows(group, exerciseNames) }
                    }
                    if (state.isNext && isToday) item { AdHocButton { start(Kind.AdHoc) } }
                }
            }
            // A missed day keeps its plan, dimmed, with no Start.
            dayKind == DayKind.Missed && day != null -> {
                val groups = groupsOf(day)
                LazyColumn(Modifier.weight(1f).fillMaxSize().testTag(TodayDayListTag), state = dayList, contentPadding = PaddingValues(bottom = bottom)) {
                    item {
                        PlannedCard(day, selected, library = library, finished = programSessions, missed = true)
                        Spacer(Modifier.height(16.dp))
                    }
                    itemsIndexed(groups, key = { _, g -> g.first().id }) { i, group ->
                        // The words dim, not the tiles, so the group keeps its shape on the screen.
                        Segment(i, groups.size) { Column(Modifier.alpha(MissedAlpha)) { SlotRows(group, exerciseNames) } }
                    }
                }
            }
            isToday -> {
                update?.let { UpdateBanner(it, onUpdate, onDismissUpdate, Modifier.padding(bottom = 8.dp)) }
                // A finished program still points at its review.
                if (dueReview != null && !sessionRunning) {
                    ReviewBanner(dueReview, program?.program?.title.orEmpty(), Modifier.padding(bottom = 8.dp)) { onOpenReview(dueReview) }
                }
                val finished = program?.finished == true
                // With no program running, or the one running finished, the saved ones are on offer.
                val offerSaved = savedPrograms.isNotEmpty() && (program == null || finished) && !sessionRunning
                // Over the saved programs the offer keeps its own space small: the group is under it.
                val bottomSpace = if (offerSaved) 8.dp else 96.dp
                val main: @Composable (Modifier) -> Unit = { mod ->
                    if (program == null && starter != null && !sessionRunning) {
                        StarterOffer(starter, onRunStarter, mod, bottomSpace) {
                            TextButton(onClick = { start(Kind.AdHoc) }) { Text(stringResource(R.string.today_start_session)) }
                            TextButton(onClick = onProgram) { Text(stringResource(R.string.program_import_short)) }
                        }
                    } else {
                        EmptyState(
                            symbol = if (finished) Symbol.Flag else Symbol.FitnessCenter,
                            title = stringResource(if (finished) R.string.program_finished else R.string.today_empty_title),
                            body = stringResource(
                                when {
                                    finished && offerSaved -> R.string.program_finished_body_saved
                                    finished -> R.string.program_finished_body
                                    else -> R.string.today_no_program_body
                                },
                            ),
                            modifier = mod,
                            bottomSpace = bottomSpace,
                        ) {
                            Button(onClick = { start(Kind.AdHoc) }, contentPadding = ButtonDefaults.MediumContentPadding, modifier = Modifier.heightIn(min = ButtonDefaults.MediumContainerHeight)) {
                                SymbolIcon(Symbol.PlayArrow, null, Modifier.size(ButtonDefaults.MediumIconSize))
                                Spacer(Modifier.width(8.dp))
                                Text(startLabel, style = MaterialTheme.typography.titleMedium)
                            }
                            if (program == null && !sessionRunning) {
                                Spacer(Modifier.height(8.dp))
                                TextButton(onClick = onProgram) { Text(stringResource(R.string.program_import_short)) }
                            }
                        }
                    }
                }
                if (offerSaved) com.hyperlog.app.ui.program.WithSavedPrograms(savedPrograms, onOpenSaved, Modifier.weight(1f)) { main(Modifier) }
                else main(Modifier.weight(1f))
            }
            else -> LazyColumn(Modifier.weight(1f).fillMaxSize(), state = dayList, contentPadding = PaddingValues(bottom = bottom)) {
                item { RestCard(selected, state.restDay, select, onWeekdayPlan = { editingWeekdayPlan = true }) }
            }
        }
      }
    }
}

internal const val TodayDayListTag = "today-day-list"

/** How far a missed day's plan is dimmed. */
internal const val MissedAlpha = 0.6f

/**
 * `Monday's Day A is missed · Train it today` under the hero, on a day with no training day: a
 * text button that starts that day today.
 */
@Composable
private fun TrainMissedButton(date: LocalDate, day: PlannedDay, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val weekday = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
    val words = stringResource(R.string.today_train_missed, weekday, day.letter)
    // What happened reads in the muted tone and the action in the accent, one button all the same.
    val action = words.lastIndexOf(" · ").takeIf { it >= 0 }
    val text = if (action == null) interFigures(words) else buildAnnotatedString {
        withStyle(SpanStyle(color = cs.onSurfaceVariant)) { append(interFigures(words.substring(0, action + 3))) }
        // The action never breaks: on a narrow screen it moves to the next line whole.
        append(interFigures(words.substring(action + 3).replace(' ', '\u00A0')))
    }
    // Read with plain spaces, as one button.
    val read = Modifier.clearAndSetSemantics {
        this.text = AnnotatedString(words)
        role = Role.Button
        onClick { onClick(); true }
    }
    TextButton(onClick = onClick, Modifier.padding(horizontal = 12.dp).padding(top = 8.dp).then(read)) {
        Text(text)
    }
}

/** `No sessions last week · Week 7 again`, one line over the hero after an empty week. */
@Composable
private fun AgainNotice(week: Int) {
    val cs = MaterialTheme.colorScheme
    // Replay, not the time-off line's History: the two can stack, and this one says "again".
    Banner(Symbol.Replay, stringResource(R.string.today_week_again, week), container = cs.surfaceContainerHigh, content = cs.onSurface)
}

/** The update and time-off banners, over today's day. */
@Composable
private fun BannerColumn(banners: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { banners() }
}

/**
 * `Hyperlog 0.2.0 is available` over `Tap to update`, a primary-container banner at the top of
 * Today: tapping anywhere updates, the cross dismisses that version.
 */
@Composable
private fun UpdateBanner(banner: UpdateBanner, onUpdate: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Banner(
        Symbol.SystemUpdate,
        stringResource(R.string.update_available, banner.release.version.name),
        modifier,
        body = stringResource(
            when (banner) {
                is UpdateBanner.Available -> R.string.update_tap
                is UpdateBanner.Downloading -> R.string.update_downloading
                is UpdateBanner.Failed -> R.string.update_failed
            },
        ),
        bodyColor = if (banner is UpdateBanner.Failed) cs.error else null,
        onClick = if (banner is UpdateBanner.Downloading) null else onUpdate,
    ) {
        IconButton(onClick = onDismiss) { SymbolIcon(Symbol.Close, stringResource(R.string.update_dismiss)) }
    }
}

/**
 * The day of the session running: the update and time-off banners, week and block over the day's
 * name, then its slots as one group of rows (label, exercise, sets).
 */
@Composable
private fun NextDay(
    day: PlannedDay,
    names: Map<String, String>,
    modifier: Modifier = Modifier,
    banners: @Composable ColumnScope.() -> Unit = {},
    /** [day] is a workout's, being trained: headed "Workout", not by week and block. */
    isWorkout: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    // The bottom padding keeps the last row clear of the floating Start session.
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 112.dp)) {
        item {
            Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { banners() }
            Column(Modifier.padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 16.dp)) {
                Text(
                    if (isWorkout) stringResource(R.string.today_workout) else stringResource(R.string.today_week_block, day.position.week, day.block.name),
                    style = MaterialTheme.typography.labelLarge,
                    color = cs.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(interFigures(day.day.name), style = MaterialTheme.typography.headlineSmall)
            }
        }
        // One row per slot, one segment per group: a superset's partners share theirs.
        val groups = day.slots.groupBy { it.group }.values.toList()
        itemsIndexed(groups, key = { _, g -> g.first().id }) { i, group ->
            Segment(i, groups.size) { SlotRows(group, names) }
        }
    }
}

/** An ad-hoc session, a row of its own after today's workout and program day. */
@Composable
private fun AdHocRow(onAdHoc: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Segment(0, 1, onClick = onAdHoc) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = CardPadding, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SymbolIcon(Symbol.FitnessCenter, null, Modifier.size(24.dp), tint = cs.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            Text(stringResource(R.string.today_ad_hoc), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            SymbolIcon(Symbol.ChevronRight, null, tint = cs.onSurfaceVariant)
        }
    }
}

/** What kind of session the start sheet is open for. */
private enum class Kind { Day, AdHoc, Workout, Missed }

internal const val WorkoutCardTag = "today-workout"

/** The start sheet's choice for a workout without a gym. */
private const val ElsewhereChoice = -1L

/**
 * The workout from Claude added for today (SPEC, Coaching → Workout), first on Today: "Workout"
 * over its name, summary and gym ([gym]: `At Itis`, `Elsewhere`) on the accent head, with ⋮ →
 * Remove; then its exercises with their rest. Start session, floating, starts it.
 */
@Composable
private fun WorkoutCard(
    waiting: Workouts.Waiting,
    day: PlannedDay,
    names: Map<String, String>,
    gym: String?,
    onRemove: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val groups = remember(day) { day.slots.groupBy { it.group }.values.toList() }
    val count = groups.size + 1
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.padding(top = 8.dp).testTag(WorkoutCardTag)) {
        Segment(0, count, color = cs.secondaryContainer) {
            Row(Modifier.fillMaxWidth().padding(start = CardPadding, top = 16.dp, bottom = 16.dp, end = 4.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.today_workout), style = MaterialTheme.typography.labelLarge, color = cs.primary)
                    Spacer(Modifier.height(4.dp))
                    Text(interFigures(waiting.workout.name), style = MaterialTheme.typography.headlineSmall, color = cs.onSecondaryContainer)
                    Spacer(Modifier.height(4.dp))
                    Text(waiting.workout.summary, style = MaterialTheme.typography.bodyMedium, color = cs.onSecondaryContainer)
                    gym?.let {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SymbolIcon(Symbol.LocationOn, null, Modifier.size(18.dp), tint = cs.onSecondaryContainer)
                            Spacer(Modifier.width(8.dp))
                            Text(it, style = MaterialTheme.typography.labelLarge, color = cs.onSecondaryContainer)
                        }
                    }
                }
                androidx.compose.foundation.layout.Box {
                    IconButton(onClick = { menu = true }) {
                        SymbolIcon(Symbol.MoreVert, stringResource(R.string.today_workout_more), tint = cs.onSecondaryContainer)
                    }
                    androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(stringResource(R.string.today_workout_remove)) },
                            leadingIcon = { SymbolIcon(Symbol.Delete, null) },
                            onClick = { menu = false; onRemove() },
                        )
                    }
                }
            }
        }
        groups.forEachIndexed { i, group -> Segment(i + 1, count) { SlotRows(group, names, rest = true) } }
    }
}

internal const val ProgramDayCardTag = "today-program-day"

/**
 * The program day under today's workout: a card whose head holds week and block over the day's
 * name and a tonal Start, so its heading and its Start show together; then its exercises.
 */
@Composable
private fun ProgramDayCard(day: PlannedDay, names: Map<String, String>, onStart: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val groups = remember(day) { day.slots.groupBy { it.group }.values.toList() }
    val count = groups.size + 1
    Column(Modifier.padding(top = 24.dp, bottom = 16.dp).testTag(ProgramDayCardTag)) {
        Segment(0, count) {
            Row(
                Modifier.fillMaxWidth().padding(start = CardPadding, end = CardPadding, top = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.today_week_block, day.position.week, day.block.name), style = MaterialTheme.typography.labelLarge, color = cs.primary)
                    Spacer(Modifier.height(4.dp))
                    Text(interFigures(day.day.name), style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.width(12.dp))
                androidx.compose.material3.FilledTonalButton(onClick = onStart) {
                    SymbolIcon(Symbol.PlayArrow, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.today_workout_start))
                }
            }
        }
        groups.forEachIndexed { i, group -> Segment(i + 1, count) { SlotRows(group, names) } }
    }
}

/**
 * `11 days since your last session` with **Ease back in**, a tertiary-container banner: it opens
 * Coach with "I've had 11 days off. Ease me back in." in the words.
 */
@Composable
private fun TimeOffBanner(days: Int, onEaseBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Banner(
        Symbol.History,
        stringResource(R.string.coach_days_off, days),
        container = cs.tertiaryContainer,
        content = cs.onTertiaryContainer,
    ) {
        Button(
            onClick = onEaseBack,
            colors = ButtonDefaults.buttonColors(containerColor = cs.tertiary, contentColor = cs.onTertiary),
        ) { Text(stringResource(R.string.coach_ease_back)) }
        Spacer(Modifier.width(8.dp))
    }
}

/**
 * The coach line: `Bench press hasn't moved in 3 sessions` with **Check in** and ×, a neutral
 * banner, quieter than time off. Check in starts a check-in whose first answer is the line.
 */
@Composable
private fun CoachLineBanner(line: CoachLine, names: Map<String, String>, onCheckIn: (String, CoachLine) -> Unit, onHide: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val words = coachLineText(line, names)
    val answer = stringResource(R.string.coach_line_answer, words)
    // Balanced breaks: a long line wraps into even lines instead of leaving `turns` alone.
    Banner(
        Symbol.Insights, words,
        container = cs.surfaceContainerHighest, content = cs.onSurface,
        titleStyle = MaterialTheme.typography.titleSmall.copy(lineBreak = LineBreak.Heading),
    ) {
        TextButton(onClick = { onCheckIn(answer, line) }) { Text(stringResource(R.string.coach_line_check_in)) }
        IconButton(onClick = onHide) { SymbolIcon(Symbol.Close, stringResource(R.string.coach_line_hide)) }
    }
}

/**
 * `Build is done · See the review`, or once the program is finished `<title> is done`, in the
 * coach line's style; a tap opens the review.
 */
@Composable
private fun ReviewBanner(due: DueReview, title: String, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Banner(
        Symbol.Flag, stringResource(R.string.review_due_block, if (due.programDone) title else due.blockName), modifier,
        container = cs.surfaceContainerHighest, content = cs.onSurface,
        titleStyle = MaterialTheme.typography.titleSmall.copy(lineBreak = LineBreak.Heading),
        onClick = onOpen,
    )
}

/** [line]'s words, its exercises named from [names]. */
@Composable
private fun coachLineText(line: CoachLine, names: Map<String, String>): String {
    fun name(id: String) = names[id] ?: id
    return when (line) {
        is CoachLine.DayMissed ->
            if (line.missed == line.of) stringResource(R.string.coach_line_missed_all, line.name, line.of)
            else stringResource(R.string.coach_line_missed, line.name, line.missed, line.of)
        is CoachLine.Stall -> stringResource(R.string.coach_line_stall, name(line.exercise), line.sessions)
        is CoachLine.Grinding -> stringResource(R.string.coach_line_grinding, name(line.exercise), line.sessions)
        is CoachLine.SameSwap -> stringResource(R.string.coach_line_swap, name(line.exercise), line.sessions)
    }
}

/**
 * Where you train today: the gyms as a single choice, today's usual one picked and saying why,
 * and Start. Changing the gym is one tap on another row.
 */
@Composable
internal fun StartSheetContent(
    gyms: List<Gym>,
    usualGym: UsualGym?,
    /** The gym picked (null: Elsewhere, without a gym) and the swaps for what it lacks. */
    onStart: (Long?, Map<String, String>) -> Unit,
    /** The program day to start, checked against the chosen gym's equipment; null ad hoc. */
    day: PlannedDay? = null,
    library: List<Exercise> = emptyList(),
    /** The gym to pick in place of the usual one: a workout's, picked on Coach, or [ElsewhereChoice]. */
    picked: Long? = null,
    /** Offers Elsewhere after the gyms: a session without a gym. */
    elsewhere: Boolean = false,
    /** What starts, under the title: a workout's name. */
    what: String? = null,
) {
    var chosen by rememberSaveable {
        mutableStateOf(picked?.takeIf { p -> gyms.any { it.id == p } || (elsewhere && p == ElsewhereChoice) } ?: usualGym?.id ?: gyms.first().id)
    }
    val away = elsewhere && chosen == ElsewhereChoice
    val gym = if (away) null else gyms.firstOrNull { it.id == chosen } ?: gyms.first()
    val missing = remember(day, gym?.missing, library) { if (gym == null) emptyList() else day?.let { MissingSwaps.forDay(it, library, gym.missing) }.orEmpty() }
    /** Proposed swaps turned down, by slot id; another gym starts over. */
    var declined by remember(chosen) { mutableStateOf(emptySet<String>()) }
    val names = remember(library) { library.associate { it.id to it.name } }
    val today = remember { LocalDate.now().dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + "s" }
    run {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp).selectableGroup()) {
            Text(
                stringResource(R.string.start_title),
                Modifier.padding(horizontal = 24.dp).padding(bottom = if (what == null) 8.dp else 0.dp),
                style = MaterialTheme.typography.headlineSmall,
            )
            what?.let {
                Text(
                    it,
                    Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Group(color = Tones.sheetCard) { gyms.forEach { gym ->
                val selected = gym.id == chosen
                Row(
                    Modifier.fillMaxWidth()
                        .then(if (selected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
                        .selectable(selected, role = Role.RadioButton, onClick = { chosen = gym.id })
                        .heightIn(min = 64.dp)
                        .padding(start = 0.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected, onClick = null, Modifier.size(48.dp))
                    Spacer(Modifier.width(4.dp))
                    Column {
                        Text(gym.name, style = MaterialTheme.typography.bodyLarge)
                        // Only a weekday plan or habit makes it usual; the gym trained at last says so.
                        val why = when (usualGym?.takeIf { it.id == gym.id }?.why) {
                            UsualGym.Why.Weekday -> stringResource(R.string.start_usual, today)
                            UsualGym.Why.Last -> stringResource(R.string.start_last)
                            UsualGym.Why.First, null -> null
                        }
                        if (why != null) {
                            Text(
                                why,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
                // Somewhere not set up: the session runs without a gym, on the usual dumbbells and plates.
                if (elsewhere) {
                    val selected = away
                    Row(
                        Modifier.fillMaxWidth()
                            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
                            .selectable(selected, role = Role.RadioButton, onClick = { chosen = ElsewhereChoice })
                            .heightIn(min = 64.dp)
                            .padding(end = 12.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null, Modifier.size(48.dp))
                        Spacer(Modifier.width(4.dp))
                        Column {
                            Text(stringResource(R.string.coach_elsewhere), style = MaterialTheme.typography.bodyLarge)
                            Text(stringResource(R.string.start_elsewhere), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (missing.isNotEmpty() && gym != null) {
                MissingSwaps(gym.name, missing, names, declined, onToggle = { slot -> declined = if (slot in declined) declined - slot else declined + slot })
            }
            Spacer(Modifier.height(16.dp))
            val swaps = missing.filter { it.replacement != null && it.slot !in declined }.associate { it.slot to it.replacement!! }
            Button(onClick = { onStart(if (away) null else gym?.id, swaps) }, Modifier.align(Alignment.End).padding(end = 16.dp).heightIn(min = ButtonDefaults.MediumContainerHeight), contentPadding = ButtonDefaults.MediumContentPadding) {
                Text(stringResource(R.string.start_start))
            }
        }
    }
}

/**
 * What the gym hasn't got, under the gyms: each proposed swap a ticked checkbox row reading as
 * the session will (the exercise, "In place of ..." over it); one with nothing that fits says so.
 */
@Composable
private fun MissingSwaps(gym: String, swaps: List<SessionSwap>, names: Map<String, String>, declined: Set<String>, onToggle: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Text(
        stringResource(R.string.start_missing, gym),
        Modifier.padding(horizontal = 24.dp).padding(top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = cs.onSurfaceVariant,
    )
    swaps.forEach { swap ->
        val from = names[swap.exercise] ?: swap.exercise
        val to = swap.replacement
        if (to == null) {
            Text(
                stringResource(R.string.start_missing_none, from),
                Modifier.padding(start = 64.dp, end = 24.dp).heightIn(min = 48.dp).padding(vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
            )
        } else {
            val on = swap.slot !in declined
            Row(
                Modifier.fillMaxWidth()
                    .toggleable(on, role = Role.Checkbox, onValueChange = { onToggle(swap.slot) })
                    .heightIn(min = 56.dp)
                    .padding(start = 12.dp, end = 24.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = on, onCheckedChange = null, Modifier.size(48.dp))
                Spacer(Modifier.width(4.dp))
                Column {
                    Text(
                        stringResource(R.string.edit_swapped_from, from),
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onSurfaceVariant,
                    )
                    Text(names[to] ?: to, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Preview
@Composable
private fun TodayScreenPreview() {
    HyperlogTheme { TodayScreen(sessionRunning = false, onResume = {}, onStart = { _, _, _ -> }) }
}

/**
 * One group of a day's slots, as Today lists them: the label centred on the row, beside the name
 * and the sets under it, and a Change's swap as "In place of …" over the name. A superset's partners share the group.
 */
@Composable
internal fun SlotRows(
    group: List<com.hyperlog.app.program.PlannedSlot>,
    names: Map<String, String>,
    /** Adds each exercise's rest after its sets (`· 1:30 rest`): a workout's, whose rest decides whether it fits the time. */
    rest: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    group.forEach { slot ->
        // The label is centred on the row, beside the name and the sets.
        Row(
            Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = CardPadding, vertical = 12.dp).testTag(SlotRowTag),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                slot.label,
                Modifier.width(40.dp).testTag(SlotLabelTag),
                style = MaterialTheme.typography.titleSmall,
                color = cs.primary,
            )
            Column(Modifier.weight(1f)) {
                // A Change's swap reads as the session will: "In place of …" over the name.
                slot.inPlaceOf?.let { planned ->
                    Text(
                        stringResource(R.string.edit_swapped_from, names[planned] ?: planned),
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.tertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    names[slot.exercise] ?: slot.exercise,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (rest && slot.sets.isNotEmpty()) {
                    Row {
                        SetSummary(slot.sets)
                        Text(
                            "  ·  " + stringResource(R.string.today_rest, restText(slot.sets.map { it.rest })),
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                        )
                    }
                } else {
                    SetSummary(slot.sets)
                }
            }
        }
    }
}

/** Rest as the timer shows it: `1:30`, or `1:00–2:00` when an exercise's sets rest differently. */
internal fun restText(seconds: List<Int>): String {
    fun clock(s: Int) = "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
    val lo = seconds.min()
    val hi = seconds.max()
    return if (lo == hi) clock(lo) else "${clock(lo)}–${clock(hi)}"
}

internal const val CalendarTag = "today-calendar"
internal const val TodayButtonTag = "today-calendar-today"
internal const val SlotRowTag = "today-slot-row"
internal const val SlotLabelTag = "today-slot-label"
