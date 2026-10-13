package com.hyperlog.app.history

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.HyperlogDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/** The bodyweight log (SPEC, Bodyweight): one value a day, the latest feeding weighted bodyweight exercises. */
@RunWith(RobolectricTestRunner::class)
class BodyweightsTest {
    private lateinit var db: HyperlogDatabase

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HyperlogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun close() = db.close()

    @Test
    fun aDayHoldsOneValueAndTheLatestDayWins() = runTest {
        val log = Bodyweights(db)
        assertNull(log.latest())

        log.log(LocalDate.of(2026, 10, 12), 82.4)
        log.log(LocalDate.of(2026, 10, 1), 83.1)
        log.log(LocalDate.of(2026, 10, 12), 82.0)

        assertEquals(
            listOf(Bodyweight(LocalDate.of(2026, 10, 12), 82.0), Bodyweight(LocalDate.of(2026, 10, 1), 83.1)),
            log.entries.first(),
        )
        assertEquals(82.0, log.latest()!!, 0.001)
        // On a date: the value logged then or before it, else the first one logged.
        assertEquals(83.1, log.on(LocalDate.of(2026, 10, 5))!!, 0.001)
        assertEquals(83.1, log.on(LocalDate.of(2026, 9, 1))!!, 0.001)

        log.delete(LocalDate.of(2026, 10, 12))
        assertEquals(83.1, log.latest()!!, 0.001)
    }
}
