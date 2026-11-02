package com.hyperlog.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hyperlog.app.coach.AboutMe
import com.hyperlog.app.coach.HowITrain
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

    /** The wizard's Done was pressed; null until it is read, so Today shows no card and New program no gate before then. */
    val done: StateFlow<Boolean?> = styles.done.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Today's card was dismissed; null until it is read. */
    val cardDismissed: StateFlow<Boolean?> = styles.cardDismissed.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun finish(targets: TrainingTargets, aboutMe: AboutMe) {
        viewModelScope.launch { styles.finish(targets, aboutMe) }
    }

    fun dismissCard() {
        viewModelScope.launch { styles.dismissCard() }
    }

    fun setTargets(targets: TrainingTargets) {
        viewModelScope.launch { styles.setTargets(targets) }
    }

    fun setAboutMe(aboutMe: AboutMe) {
        viewModelScope.launch { styles.setAboutMe(aboutMe) }
    }

    fun setHowITrain(howITrain: HowITrain) {
        viewModelScope.launch { styles.setHowITrain(howITrain) }
    }
}
