package com.hyperlog.app.ui.program

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.FreshDatabaseRule
import com.hyperlog.app.MainActivity
import com.hyperlog.app.click
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.data.SettingEntity
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.Programs
import com.hyperlog.app.program.StarterProgram
import com.hyperlog.app.waitFor
import com.hyperlog.app.waitGone
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A finished block's review in the app, from Today's line to Check in (SPEC, Program → Block review). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class BlockReviewAppTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** The starter program past Ramp-in, and the scripted coach switched on, before the app starts. */
    private val setup = object : ExternalResource() {
        override fun before() = runBlocking {
            val db = HyperlogDatabase.get(context)
            db.settings().put(SettingEntity("scripted_coach", "on"))
            ExerciseLibrary(db).sync(LibrarySeed.fromAssets(context))
            val programs = Programs(db)
            programs.import(StarterProgram.source(context))
            db.programs().setPosition(programs.active.first()!!.id, 3, 0)
        }
    }

    val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabaseRule()).around(setup).around(compose)

    @Test
    fun todaysLineOpensTheReviewAndCheckInStartsACheckInWithIt() {
        compose.click("Ramp-in is done · See the review")
        compose.waitFor("Ramp-in review")
        compose.click("Check in about the next block")
        compose.waitGone("Ramp-in review")
        // A check-in with the scripted coach, the review in its context.
        compose.waitFor("Your lifts moved", substring = true)
        val kept = androidx.lifecycle.ViewModelProvider(compose.activity)[com.hyperlog.app.ui.coach.CheckInViewModel::class.java].checkIn.value!!.checkIn
        assertEquals("Ramp-in is done. Look at how it went and the next block.", kept.opening)
        assertTrue("## How Ramp-in went (weeks 1–2)" in kept.frame.context)
        // × goes back to the review; Back from it to Today, which no longer points at it.
        compose.onNodeWithContentDescription("Close the check-in").performClick()
        compose.waitFor("Ramp-in review")
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitFor("Week 3", substring = true)
        compose.onNodeWithText("Ramp-in is done · See the review").assertDoesNotExist()
    }
}
