package com.hyperlog.app

import androidx.room.RoomDatabase
import com.hyperlog.app.data.HyperlogDatabase
import kotlinx.coroutines.Dispatchers
import org.junit.rules.ExternalResource

/**
 * Gives each activity test an empty database that runs on the main thread, and no check-in kept
 * from before. Robolectric keeps both the shared instance and the database file between tests, and
 * the check-in's file too, so a session or check-in started in one test would open the next. Room's queries and writes run on the main looper, so the Compose test's
 * `waitForIdle` waits for them and for the flows they feed: a tap's write has landed before the
 * test's next step. Put it outside the compose rule.
 */
class FreshDatabaseRule : ExternalResource() {
    override fun before() {
        HyperlogDatabase.deleteForTests()
        checkIn().delete()
        HyperlogDatabase.forTests = { it.onMainThread() }
    }

    override fun after() {
        HyperlogDatabase.deleteForTests()
        checkIn().delete()
        HyperlogDatabase.forTests = { it }
    }
}

/** The file a check-in in progress is kept in (`CheckInViewModel`). */
private fun checkIn() = java.io.File(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().noBackupFilesDir, "check_in.json")

/** Runs Room's queries, writes and flows on the main thread, where Robolectric's tests run. */
fun <T : RoomDatabase> RoomDatabase.Builder<T>.onMainThread(): RoomDatabase.Builder<T> =
    setQueryCoroutineContext(Dispatchers.Main.immediate).allowMainThreadQueries()
