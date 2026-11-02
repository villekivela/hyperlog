package com.hyperlog.app.coach

import android.content.Context
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.data.SettingEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The training style as kept on the phone (SPEC, Coaching → Training style), in `setting`: the
 * training targets, About me and How I train as JSON ([STYLE]), with the principles' sentences
 * read from the app's asset ([sentences]), so a reworded principle reaches everyone. It goes into
 * backups with the other settings.
 */
class TrainingStyles(private val db: HyperlogDatabase, private val sentences: () -> Map<Principle, String>) {
    constructor(db: HyperlogDatabase, context: Context) : this(db, { principles(context) })

    val style: Flow<TrainingStyle> = db.settings().observe(STYLE).map { kept ->
        val (targets, aboutMe, howITrain) = TrainingStyleJson.decode(kept)
        TrainingStyle(targets, aboutMe, howITrain, sentences())
    }

    suspend fun current(): TrainingStyle = style.first()

    suspend fun setTargets(targets: TrainingTargets) = edit { it.copy(targets = targets) }

    suspend fun setAboutMe(aboutMe: AboutMe) = edit { it.copy(aboutMe = aboutMe) }

    suspend fun setHowITrain(howITrain: HowITrain) = edit { it.copy(howITrain = howITrain) }

    /** One row holds all three, so each edit reads it and writes it back, one at a time. */
    private suspend fun edit(change: (TrainingStyleJson.Kept) -> TrainingStyleJson.Kept) = writing.withLock {
        db.settings().put(SettingEntity(STYLE, TrainingStyleJson.encode(change(TrainingStyleJson.decode(db.settings().get(STYLE))))))
    }

    companion object {
        private val writing = Mutex()

        /** The training targets, About me and How I train, as JSON. */
        const val STYLE = "training_style"

        fun principles(context: Context): Map<Principle, String> =
            Principles.parse(context.assets.open(Principles.ASSET).bufferedReader().use { it.readText() })
    }
}
