package com.hyperlog.app.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.history.Bodyweight
import com.hyperlog.app.history.Bodyweights
import com.hyperlog.app.history.ExerciseTrend
import com.hyperlog.app.history.History
import com.hyperlog.app.history.LiftGoals
import com.hyperlog.app.history.WeekSets
import com.hyperlog.app.session.SessionRuntime
import com.hyperlog.app.ui.program.ProgramStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * History's Progress tab, an exercise's trend and the bodyweight log. The session list lives in
 * [com.hyperlog.app.ui.session.PastSessionsViewModel], which also opens and corrects sessions.
 */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val db = HyperlogDatabase.get(application)
    private val history = History(db, ProgramStore.get(application))
    private val log = Bodyweights(db)
    private val goals = LiftGoals(db)
    private val runtime = SessionRuntime.get(application)
    private val _weeks = MutableStateFlow<List<WeekSets>?>(null)
    private val _exercises = MutableStateFlow<List<ExerciseTrend>?>(null)
    private val _trend = MutableStateFlow<ExerciseTrend?>(null)

    /** Sets per muscle, this week first; null until read. */
    val weeks: StateFlow<List<WeekSets>?> = _weeks.asStateFlow()

    /** Every exercise trained, the one done last first; null until read. */
    val exercises: StateFlow<List<ExerciseTrend>?> = _exercises.asStateFlow()

    /** The exercise trend open; null when none is. */
    val trend: StateFlow<ExerciseTrend?> = _trend.asStateFlow()

    /** The bodyweight log, newest day first. */
    val bodyweights: StateFlow<List<Bodyweight>> = log.entries.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Reads Progress again, with any session finished or corrected since. */
    fun refresh() = run {
        _weeks.value = history.weeklySets()
        _exercises.value = history.exercises()
        _trend.value?.let { _trend.value = history.trend(it.exercise.id) }
    }

    fun openTrend(exerciseId: String) = run { _trend.value = history.trend(exerciseId) }

    fun closeTrend() {
        _trend.value = null
    }

    /** Logs [kg] for today. Weighted bodyweight estimates and a running session's suggestion follow it. */
    fun logBodyweight(kg: Double) = run {
        log.log(LocalDate.now(), kg)
        changed()
    }

    /** Sets the open exercise's lift goal. Only History and the prescription sheet read it, never progression. */
    fun setGoal(kg: Double, by: LocalDate?) = run {
        val trend = _trend.value ?: return@run
        goals.set(trend.exercise.id, kg, by)
        _trend.value = history.trend(trend.exercise.id)
    }

    fun clearGoal() = run {
        val trend = _trend.value ?: return@run
        goals.clear(trend.exercise.id)
        _trend.value = history.trend(trend.exercise.id)
    }

    fun deleteBodyweight(date: LocalDate) = run {
        log.delete(date)
        changed()
    }

    private suspend fun changed() {
        runtime.logger.readAgain()
        _exercises.value = history.exercises()
        _trend.value?.let { _trend.value = history.trend(it.exercise.id) }
    }

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
