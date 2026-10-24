package com.hyperlog.app.coach

/**
 * A [CoachModel] that plays scripted turns in order: each a reply, or an error to throw. It keeps
 * every request it was sent, so a test can read what Claude would have been told.
 */
class FakeCoachModel(vararg turns: Turn) : CoachModel {
    sealed interface Turn {
        data class Reply(val reply: CoachReply) : Turn
        data class Fails(val error: CoachError) : Turn
    }

    private val script = ArrayDeque(turns.toList())
    val requests = mutableListOf<CoachRequest>()

    override suspend fun reply(request: CoachRequest): CoachReply {
        requests += request
        return when (val turn = script.removeFirstOrNull() ?: error("The script has no turn left for request ${requests.size}")) {
            is Turn.Reply -> turn.reply
            is Turn.Fails -> throw turn.error
        }
    }

    companion object {
        fun replies(vararg replies: CoachReply) = FakeCoachModel(*replies.map { Turn.Reply(it) }.toTypedArray())
    }
}
