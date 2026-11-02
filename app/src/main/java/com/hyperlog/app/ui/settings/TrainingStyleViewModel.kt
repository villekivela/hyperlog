package com.hyperlog.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hyperlog.app.coach.AboutMe
import com.hyperlog.app.coach.TrainingStyle
import com.hyperlog.app.coach.TrainingStyles
import com.hyperlog.app.coach.TrainingTargets
import com.hyperlog.app.data.HyperlogDatabase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Settings → Training style: the training style as kept, and each edit saved as it is made. */
class TrainingStyleViewModel(application: Application) : AndroidViewModel(application) {
    private val styles = TrainingStyles(HyperlogDatabase.get(application), application)

    /** The kept style; null until it is read. */
    val style: StateFlow<TrainingStyle?> = styles.style.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** How I train is your own text. */
    val edited: StateFlow<Boolean> = styles.edited.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setTargets(targets: TrainingTargets) {
        viewModelScope.launch { styles.setTargets(targets) }
    }

    fun setAboutMe(aboutMe: AboutMe) {
        viewModelScope.launch { styles.setAboutMe(aboutMe) }
    }

    fun setHowITrain(text: String) {
        viewModelScope.launch { styles.setHowITrain(text) }
    }

    /** Back to default: the edited text goes, and the app's is read again. */
    fun defaultHowITrain() = setHowITrain("")
}
