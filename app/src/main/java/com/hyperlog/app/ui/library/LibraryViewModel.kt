package com.hyperlog.app.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.ExerciseLibrary
import kotlinx.coroutines.launch

/** Edits to the exercise library. The list itself comes from [com.hyperlog.app.ui.session.SessionViewModel.exercises]. */
class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val library = ExerciseLibrary(HyperlogDatabase.get(application))

    fun save(exercise: Exercise, isNew: Boolean) {
        viewModelScope.launch { if (isNew) library.add(exercise) else library.update(exercise) }
    }

    fun setHidden(id: String, hidden: Boolean) {
        viewModelScope.launch { library.setHidden(id, hidden) }
    }
}
