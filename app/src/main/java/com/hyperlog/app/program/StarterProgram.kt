package com.hyperlog.app.program

import android.content.Context

/**
 * The starter program that ships with the app, Hyperlog Full Body
 * (docs/programs/full-body-3-day.md), offered while no program runs. It is an ordinary program
 * file and imports like any other.
 */
object StarterProgram {
    /** Its file in the app's assets. */
    const val ASSET = "programs/full-body-3-day.json"

    fun source(context: Context): String = context.assets.open(ASSET).bufferedReader().use { it.readText() }
}
