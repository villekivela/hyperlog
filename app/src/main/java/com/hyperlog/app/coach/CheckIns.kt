package com.hyperlog.app.coach

import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * The unfinished check-in, kept in one file so it survives leaving the app until it ends. It sits
 * in `no_backup`: a check-in is for now, not for a backup.
 */
class CheckInStore(private val file: File) {
    fun load(): CheckIn? = if (!file.exists()) null else runCatching { json.decodeFromString(CheckIn.serializer(), file.readText()) }.getOrNull()

    fun save(checkIn: CheckIn?) {
        if (checkIn == null) {
            file.delete()
            return
        }
        val partial = File(file.parentFile, file.name + ".part")
        partial.writeText(json.encodeToString(CheckIn.serializer(), checkIn))
        if (!partial.renameTo(file)) partial.delete()
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

/** A check-in and what its screen shows. */
data class CheckInView(val checkIn: CheckIn, val step: CheckInStep)

/**
 * Runs the Coach tab's check-in (SPEC, Coaching → Check-in): each step is saved before Claude is
 * called, so a check-in left while thinking asks again on coming back. Back while thinking stops
 * the call; × drops the check-in.
 */
class CheckInRunner(
    private val scope: CoroutineScope,
    private val store: CheckInStore,
    /** Claude through the key, or the scripted coach; null with neither. */
    private val model: suspend () -> CoachModel?,
    private val world: suspend () -> CheckInWorld,
    /** The frame for a new check-in: built once, then sent with every call. */
    private val frame: suspend () -> CheckInFrame,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _state = MutableStateFlow<CheckInView?>(null)
    val state: StateFlow<CheckInView?> = _state.asStateFlow()

    private val _thinking = MutableStateFlow(false)

    /** A call is under way. */
    val thinking: StateFlow<Boolean> = _thinking.asStateFlow()

    private val _progress = MutableStateFlow<CoachProgress?>(null)

    /** What Claude is doing in the call under way, as its reply streams; null before it says. */
    val progress: StateFlow<CoachProgress?> = _progress.asStateFlow()

    private var current: CheckIn? = null
    private var call: Job? = null

    /** A check-in to write; a new one each time, so each is written, the latest last. */
    private class Write(val checkIn: CheckIn?)

    private val writes = MutableStateFlow<Write?>(null)

    init {
        // One writer, in order: the screen and the next call never wait for the disk.
        scope.launch(io) { writes.filterNotNull().collect { store.save(it.checkIn) } }
    }

    /**
     * Reads the check-in kept when the app last ran; one left while thinking asks again. A file
     * attached wasn't kept: the next call goes back to the step that took it instead.
     */
    fun restore() {
        scope.launch {
            val kept = withContext(io) { store.load() } ?: return@launch
            show(kept)
            if (kept.waiting) ask(kept)
        }
    }

    fun start(tile: Tile) = begin { CheckIn(tile.words, tile, frame()) }

    /**
     * Starts from your own first answer: Ease back in's, or How's it going's. [context], as a block
     * review's, goes after the frame's, for Claude to read with it.
     */
    fun start(opening: String, from: Opening, context: String? = null) = begin {
        CheckIn(opening, null, frame().let { f -> if (context == null) f else f.copy(context = "${f.context}\n\n$context") }, from)
    }

    fun answer(text: String) = next { coach, checkIn -> coach.answered(checkIn, text) }

    /** Attach a spreadsheet, PDF or photo: the file is the answer, kept in memory only. */
    fun attach(attachment: Attachment) = next { coach, checkIn -> coach.attached(checkIn, attachment) }

    fun tellMeMore() = next { coach, checkIn -> coach.toldMore(checkIn) }

    fun tryAgain() = next { coach, checkIn -> coach.retried(checkIn) }

    /** Back: the step before shows again, a call under way stopped; from the first step, the tiles. */
    fun back() {
        stop()
        scope.launch {
            val checkIn = current ?: return@launch
            save(coach()?.back(checkIn))
        }
    }

    /** ×, Done, or an accepted reply: the check-in ends. */
    fun close() {
        stop()
        scope.launch { save(null) }
    }

    private fun begin(make: suspend () -> CheckIn) {
        stop()
        scope.launch {
            val checkIn = make()
            save(checkIn)
            ask(checkIn)
        }
    }

    private fun next(change: (CheckInCoach, CheckIn) -> CheckIn) {
        // One answer at a time: none while Claude thinks.
        if (_thinking.value) return
        scope.launch {
            val checkIn = current ?: return@launch
            val coach = coach() ?: return@launch
            val next = change(coach, checkIn)
            if (next == checkIn) return@launch
            save(next)
            ask(next)
        }
    }

    /** Calls Claude for [checkIn], saving each reply as it comes. */
    private fun ask(checkIn: CheckIn) {
        _thinking.value = true
        _progress.value = null
        call = scope.launch {
            val job = coroutineContext[Job]
            try {
                val coach = coach() ?: return@launch
                save(coach.run(checkIn) { if (call === job) _progress.value = it })
            } finally {
                // A call stopped by Back or × doesn't end the one that followed it.
                if (call === job) {
                    _thinking.value = false
                    _progress.value = null
                }
            }
        }
    }

    private fun stop() {
        call?.cancel()
        call = null
        _thinking.value = false
        _progress.value = null
    }

    private suspend fun coach(): CheckInCoach? = model()?.let { CheckInCoach(it, world()) }

    private suspend fun save(checkIn: CheckIn?) {
        show(checkIn)
        writes.value = Write(checkIn)
    }

    private suspend fun show(checkIn: CheckIn?) {
        current = checkIn
        _state.value = checkIn?.let { c -> CheckInView(c, coach()?.step(c) ?: CheckInStep.Thinking) }
    }
}
