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
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.openTab
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.Programs
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
import org.robolectric.annotation.Config

/**
 * Saved programs in the app (SPEC, Program → Programs): Today offers them while no program runs,
 * a saved one's page starts it, and Resume picks a program replaced mid-way up again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class SavedProgramsAppTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val db get() = HyperlogDatabase.get(context)

    private val setup = object : ExternalResource() {
        override fun before() = runBlocking { ExerciseLibrary(db).sync(LibrarySeed.fromAssets(context)) }
    }

    val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabaseRule()).around(setup).around(compose)

    private fun active() = runBlocking { Programs(db).active.first() }

    /** The phone holds these programs; the app reads them as they change. */
    private fun holds(programs: suspend (Programs) -> Unit) = runBlocking { programs(Programs(db)) }

    private val forLater: suspend (Programs) -> Unit = { it.save(TestPrograms.twoDays("For later")) }

    @Test
    fun withNothingRunningTodayOffersTheSavedProgramAndItsPageStartsIt() {
        holds(forLater)
        compose.waitFor("For later")
        compose.onNodeWithText("Starter program").assertExists()
        compose.click("For later")
        compose.waitFor("Saved program")
        compose.click("Start program")
        compose.waitFor("Week 1 · Main", substring = true)
        assertEquals(listOf("For later", Position(1, 0)), active()!!.let { listOf(it.program.title, it.position) })
    }

    @Test
    fun aFinishedProgramLeavesTodayOfferingTheSavedOnes() {
        holds { p ->
            p.import(TestPrograms.twoDays("Finished"))
            val finished = p.active.first()!!
            generateSequence(Position(1, 0)) { finished.program.next(it) }.forEach { p.advance(finished.id, it) }
            forLater(p)
        }
        compose.waitFor("Program finished")
        compose.click("For later")
        compose.click("Start program")
        compose.click("Replace")
        compose.waitFor("Week 1 · Main", substring = true)
        assertEquals("For later", active()!!.program.title)
    }

    @Test
    fun aSavedProgramsPageGoesBackToWhereItWasOpened() {
        holds(forLater)
        compose.click("For later")
        compose.waitFor("Saved program")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitFor("Starter program")
        assertEquals(null, active())
    }

    @Test
    fun programsOpensASavedProgramsPageAndBackReturnsToTheList() {
        holds { p ->
            p.import(TestPrograms.twoDays("Current"))
            p.save(TestPrograms.twoDays("For later"))
        }
        compose.openTab("Program")
        compose.waitFor("Current")
        compose.onNodeWithContentDescription("More").performClick()
        compose.click("Programs")
        compose.click("For later")
        compose.waitFor("Saved program")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitFor("Saved")
        compose.onNodeWithText("Current").assertExists()
    }

    @Test
    fun resumePicksAProgramReplacedMidWayUpWhereItWas() {
        holds { p ->
            p.import(TestPrograms.twoDays("First"))
            p.advance(p.active.first()!!.id, Position(1, 0))
            p.import(TestPrograms.twoDays("Second"))
        }
        compose.openTab("Program")
        compose.waitFor("Second")
        compose.onNodeWithContentDescription("More").performClick()
        compose.click("Programs")
        compose.click("First")
        compose.click("Resume")
        compose.click("Replace")
        compose.waitFor("First")
        assertEquals(listOf("First", Position(1, 1)), active()!!.let { listOf(it.program.title, it.position) })
    }
}
