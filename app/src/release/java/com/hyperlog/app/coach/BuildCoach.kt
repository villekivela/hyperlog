package com.hyperlog.app.coach

/** A release build has no scripted coach: only Claude, through a key, or Ask Claude. */
internal object BuildCoach {
    const val SCRIPTED = false

    @Suppress("UNUSED_PARAMETER")
    fun scripted(world: suspend () -> ScriptedWorld): CoachModel? = null
}
