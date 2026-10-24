package com.hyperlog.app.coach

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper

/**
 * A check-in's call keeps going when you leave the app (SPEC, Coaching → Check-in): it runs in a
 * foreground service with an ongoing notification, and a reply that lands while you're away says so.
 */
@RunWith(RobolectricTestRunner::class)
class CheckInKeeperTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val notifications get() = shadowOf(context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
    private val frame = CheckInFrame("Context", emptyList())
    private val thinking = MutableStateFlow(false)
    private val view = MutableStateFlow<CheckInView?>(null)
    private var visible = true

    @After
    fun clear() {
        CheckInWork.text.value = null
    }

    private fun newProgram(step: CheckInStep) = CheckInView(CheckIn(Tile.NewProgram.words, Tile.NewProgram, frame), step)

    @Test
    fun aCallRunsInAForegroundServiceThatStopsWhenTheReplyComes() = runTest(UnconfinedTestDispatcher()) {
        CheckInKeeper(context, backgroundScope, thinking, view, visible = { visible })
        view.value = newProgram(CheckInStep.Thinking)
        thinking.value = true
        // The service is started for the call, and says what Claude is doing.
        val started = shadowOf(context).nextStartedService
        assertEquals(CheckInService::class.java.name, started.component?.className)
        assertEquals("Claude is building your program…", CheckInWork.text.value)
        val controller = Robolectric.buildService(CheckInService::class.java, started).create().startCommand(0, 1)
        val service = controller.get()
        assertEquals("Claude is building your program…", shadowOf(service).lastForegroundNotification.extras.getString("android.title"))
        // The reply comes: the service stops itself, and in the app nothing more is said.
        view.value = newProgram(CheckInStep.Words("Here it is."))
        thinking.value = false
        ShadowLooper.idleMainLooper()
        assertNull(CheckInWork.text.value)
        assertTrue(shadowOf(service).isStoppedBySelf)
        // As Android would once it stopped.
        controller.destroy()
        assertTrue(notifications.allNotifications.none { it.extras.getString("android.title") == "Claude answered" })
    }

    @Test
    fun aReplyThatComesWhileYoureAwaySaysWhatCameAndOpensTheCheckIn() = runTest(UnconfinedTestDispatcher()) {
        CheckInKeeper(context, backgroundScope, thinking, view, visible = { visible })
        thinking.value = true
        visible = false
        view.value = newProgram(CheckInStep.Question("How many days a week?", listOf("3", "4"), own = true))
        thinking.value = false
        val posted = notifications.allNotifications.single()
        assertEquals("Claude asked a question", posted.extras.getString("android.title"))
        val open = shadowOf(posted.contentIntent).savedIntent
        assertEquals(MainActivity::class.java.name, open.component?.className)
        assertTrue(open.getBooleanExtra(CheckInNotices.EXTRA_OPEN_CHECK_IN, false))
        // The next call takes it down.
        thinking.value = true
        assertTrue(notifications.allNotifications.isEmpty())
    }

    @Test
    fun eachKindOfReplyHasItsWords() {
        val notices = CheckInNotices(context)
        assertEquals("Your program is ready", notices.readyTitle(CheckInStep.ProgramReview(sample())))
        assertEquals("Claude answered", notices.readyTitle(CheckInStep.Words("Fine.")))
        assertEquals("Claude asked a question", notices.readyTitle(CheckInStep.Question("Which?", listOf("A", "B"), own = false)))
        assertEquals("Claude couldn’t finish", notices.readyTitle(CheckInStep.Failed(CheckInFailure.Dropped)))
    }

    /** A program proposal, as small as reads. */
    private fun sample(): ProgramProposal {
        val library = com.hyperlog.app.library.LibrarySeed.parse(java.io.File("src/main/assets/exercises.json").readText()).exercises
        val reply = kotlinx.serialization.json.JsonObject(mapOf("program" to ProgramFormat.example(library)))
        return (ProgramProposal.read(reply, library.map { it.id }.toSet(), emptyMap()) as ProgramProposal.Result.Valid).proposal
    }
}
