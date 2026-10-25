package com.hyperlog.app.ui.program

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.FreshDatabaseRule
import com.hyperlog.app.MainActivity
import com.hyperlog.app.click
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.openTab
import com.hyperlog.app.program.Programs
import com.hyperlog.app.program.StarterProgram
import com.hyperlog.app.program.TestPrograms
import com.hyperlog.app.waitFor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/** Program → ⋮ → Programs in the app: Run again, and Export through the system save dialog. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class ProgramsAppTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** The starter program ran, then another replaced it. */
    private val setup = object : ExternalResource() {
        override fun before() = runBlocking {
            val db = HyperlogDatabase.get(context)
            ExerciseLibrary(db).sync(LibrarySeed.fromAssets(context))
            Programs(db).import(StarterProgram.source(context))
            Programs(db).import(TestPrograms.twoDays("Second"))
            Unit
        }
    }

    val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabaseRule()).around(setup).around(compose)

    private fun openPrograms() {
        compose.openTab("Program")
        compose.waitFor("Second")
        compose.onNodeWithContentDescription("More").performClick()
        compose.click("Programs")
        compose.waitFor("Past")
    }

    @Test
    fun runAgainStartsTheStarterProgramAfreshFromWeekOne() {
        openPrograms()
        compose.click("Hyperlog Full Body")
        compose.click("Run again")
        compose.click("Replace")
        compose.waitFor("Hyperlog Full Body")
        val active = runBlocking { Programs(HyperlogDatabase.get(context)).active.first()!! }
        assertEquals(listOf("Hyperlog Full Body", com.hyperlog.app.program.Position(1, 0)), listOf(active.program.title, active.position))
    }

    @Test
    fun twoQuickTapsOnSaveForLaterSaveTheProgramOnce() {
        compose.waitFor("Today")
        val db = HyperlogDatabase.get(context)
        val library = runBlocking { db.exercises().all() }
        val reply = """{"program": ${TestPrograms.twoDays("For later")}}"""
        val proposal = (com.hyperlog.app.coach.ProgramProposal.read(
            kotlinx.serialization.json.Json.parseToJsonElement(reply) as kotlinx.serialization.json.JsonObject,
            library.map { it.id }.toSet(), library.associate { it.id to it.name },
        ) as com.hyperlog.app.coach.ProgramProposal.Result.Valid).proposal
        val model = androidx.lifecycle.ViewModelProvider(compose.activity)[ProgramViewModel::class.java]
        var saved = 0
        compose.runOnUiThread {
            model.reviewProgram(proposal)
            repeat(2) { model.saveProgram(saved = { saved++ }, failed = {}) }
        }
        compose.waitForIdle()
        assertEquals(1, runBlocking { Programs(db).listed.first().count { it.title == "For later" } })
        assertEquals(1, saved)
    }

    @Test
    fun exportWritesTheProgramsFileWhereTheSaveDialogSays() {
        openPrograms()
        compose.click("Hyperlog Full Body")
        compose.click("Export")
        val asked = shadowOf(compose.activity).nextStartedActivityForResult
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, asked.intent.action)
        assertEquals("hyperlog-full-body.json", asked.intent.getStringExtra(Intent.EXTRA_TITLE))
        // The system save dialog hands back this file.
        val file = File(compose.activity.cacheDir, "exported.json").also { it.writeText("old") }
        shadowOf(compose.activity).receiveResult(asked.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
        // The write empties the file first, so wait for the whole program, not for any change. It
        // reads the program from the database, which runs on the main looper here: idle it meanwhile.
        val source = StarterProgram.source(context)
        val until = System.currentTimeMillis() + 5_000
        while (file.readText() != source && System.currentTimeMillis() < until) {
            compose.waitForIdle()
            Thread.sleep(50)
        }
        assertEquals(source, file.readText())
    }
}
