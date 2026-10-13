package com.hyperlog.app.update

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.HyperlogDatabase
import java.io.IOException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** When the app opens it offers a newer release, reading GitHub at most once a day (SPEC, Distribution). */
@RunWith(RobolectricTestRunner::class)
class UpdateCheckTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: HyperlogDatabase
    private lateinit var preferences: UpdatePreferences
    private var now = 1_760_000_000_000L
    private var published: Release? = Release(AppVersion(0, 2, 0), "https://example.com/hyperlog-0.2.0.apk")
    private var reads = 0
    private val source = ReleaseSource { reads++; published }

    /** Installed: 0.1.0. */
    private fun check(source: ReleaseSource = this.source, timeoutMs: Long = 10_000) =
        UpdateCheck(source, preferences, installed = 100, clock = { now }, timeoutMs = timeoutMs)

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(context, HyperlogDatabase::class.java).allowMainThreadQueries().build()
        preferences = UpdatePreferences(db)
    }

    @After
    fun close() = db.close()

    @Test
    fun aNewerReleaseIsOffered() = runTest {
        assertEquals(published, check().run())
    }

    @Test
    fun theInstalledOrAnOlderReleaseIsNot() = runTest {
        published = Release(AppVersion(0, 1, 0), "https://example.com/a.apk")
        assertNull(check().run())
        preferences.forgetLastCheck()
        published = Release(AppVersion(0, 0, 9), "https://example.com/b.apk")
        assertNull(check().run())
        preferences.forgetLastCheck()
        published = null
        assertNull(check().run())
    }

    @Test
    fun githubIsReadAtMostOnceADay() = runTest {
        check().run()
        now += 23 * 3_600_000L
        // Later the same day the release found is offered again without asking GitHub.
        assertEquals(published, check().run())
        assertEquals(1, reads)
        now += 2 * 3_600_000L
        published = Release(AppVersion(0, 3, 0), "https://example.com/hyperlog-0.3.0.apk")
        assertEquals(published, check().run())
        assertEquals(2, reads)
    }

    @Test
    fun dismissingHidesThatVersionOnly() = runTest {
        val offered = check().run()!!
        preferences.dismiss(offered.version)
        assertNull(check().run())
        now += 25 * 3_600_000L
        published = Release(AppVersion(0, 3, 0), "https://example.com/hyperlog-0.3.0.apk")
        assertEquals(published, check().run())
    }

    @Test
    fun turnedOffItNeverAsks() = runTest {
        assertTrue(preferences.checkForUpdates.first())
        preferences.setCheckForUpdates(false)
        assertFalse(preferences.checkForUpdates.first())
        assertNull(check().run())
        assertEquals(0, reads)
    }

    @Test
    fun offlineIsNoBannerAndAsksAgainNextTime() = runTest {
        assertNull(check(source = { throw IOException("offline") }).run())
        // A failed read isn't a check: the next start tries again.
        assertEquals(published, check().run())
    }

    @Test
    fun aSlowNetworkGivesUp() = runTest {
        assertNull(check(source = { awaitCancellation() }, timeoutMs = 1_000).run())
    }
}
