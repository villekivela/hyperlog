package com.hyperlog.app.coach

import android.content.Context
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.data.SettingEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The training style as kept on the phone (SPEC, Coaching → Training style), in `setting`: the
 * training targets and About me as JSON ([STYLE]), and How I train only once edited ([HOW_I_TRAIN]);
 * without it the app's default text ([default]) is read, so a new default reaches everyone who
 * never edited theirs. Both go into backups with the other settings.
 */
class TrainingStyles(private val db: HyperlogDatabase, private val default: () -> String) {
    constructor(db: HyperlogDatabase, context: Context) : this(db, { defaultHowITrain(context) })

    val style: Flow<TrainingStyle> = combine(db.settings().observe(STYLE), db.settings().observe(HOW_I_TRAIN)) { kept, text ->
        val (targets, aboutMe) = TrainingStyleJson.decode(kept)
        TrainingStyle(targets, aboutMe, text ?: default())
    }

    /** Whether How I train is your own text, not the app's. */
    val edited: Flow<Boolean> = db.settings().observe(HOW_I_TRAIN).map { it != null }

    suspend fun current(): TrainingStyle = style.first()

    /** The app's own How I train. */
    fun defaultHowITrain(): String = default()

    suspend fun setTargets(targets: TrainingTargets) = edit { (_, aboutMe) -> targets to aboutMe }

    suspend fun setAboutMe(aboutMe: AboutMe) = edit { (targets, _) -> targets to aboutMe }

    /** One row holds both, so each edit reads it and writes it back, one at a time. */
    private suspend fun edit(change: (Pair<TrainingTargets, AboutMe>) -> Pair<TrainingTargets, AboutMe>) = writing.withLock {
        val (targets, aboutMe) = change(TrainingStyleJson.decode(db.settings().get(STYLE)))
        db.settings().put(SettingEntity(STYLE, TrainingStyleJson.encode(targets, aboutMe)))
    }

    /** Keeps [text] as How I train; the default's own text, or nothing, goes back to the default. */
    suspend fun setHowITrain(text: String) {
        if (text.isBlank() || text.trim() == default().trim()) db.settings().delete(HOW_I_TRAIN)
        else db.settings().put(SettingEntity(HOW_I_TRAIN, text.trim()))
    }

    companion object {
        private val writing = Mutex()

        /** The training targets and About me, as JSON. */
        const val STYLE = "training_style"

        /** How I train, once edited. */
        const val HOW_I_TRAIN = "how_i_train"

        /** The default How I train, shipped with the app. */
        const val ASSET = "coach/how-i-train.md"

        fun defaultHowITrain(context: Context): String = context.assets.open(ASSET).bufferedReader().use { it.readText() }.trim()
    }
}
