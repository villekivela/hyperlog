package com.hyperlog.app.coach

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.HyperlogDatabase
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Each reply's usage is added up by month at the model's prices (SPEC, Coaching → With a Claude
 * key, Cost): Opus 5.5 at $4 in, $5 for a cache write, $0.20 for a cache read and $20 out per
 * million tokens.
 */
@RunWith(RobolectricTestRunner::class)
class CoachCostTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: HyperlogDatabase
    private var month = YearMonth.of(2026, 10)
    private lateinit var cost: CoachCost

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(context, HyperlogDatabase::class.java).allowMainThreadQueries().build()
        cost = CoachCost(db) { month }
    }

    @After
    fun close() = db.close()

    private val request = CoachRequest(context = "The program", tools = emptyList(), messages = listOf(CoachMessage.User("Something hurts")))

    /** The first question writes the context to the cache. */
    private val first = CoachReply(
        text = "Which hand?",
        usage = listOf(CoachUsage("claude-opus-5-5", input = 2_000, cacheWrite = 30_000, output = 1_500)),
    )

    /** The next reads it from the cache. */
    private val second = CoachReply(
        text = "Use straps and train as planned.",
        usage = listOf(CoachUsage("claude-opus-5-5", input = 500, cacheRead = 30_000, output = 800)),
    )

    @Test
    fun repliesAddUpWithCacheWritesAndReads() = runTest {
        val coach = MeteredCoach(FakeCoachModel.replies(first, second), cost)
        coach.reply(request)
        coach.reply(request)
        // 2,000 × $4 + 30,000 × $5 + 1,500 × $20 = $0.188; 500 × $4 + 30,000 × $0.20 + 800 × $20 = $0.024.
        assertEquals(212_000L, cost.thisMonth.first())
        assertEquals("≈ $0.21", CoachCost.dollars(cost.thisMonth.first()))
    }

    @Test
    fun aRefusalIsPaidForAndAFallbackAtItsOwnModelsPrices() = runTest {
        val refused = CoachError.Refused("cyber", listOf(CoachUsage("claude-opus-5-5", input = 10_000)))
        // Refused on Opus 5.5 and answered on Opus 4.8, at $5 in and $25 out.
        val fellBack = CoachReply(
            text = "Train as planned.",
            usage = listOf(CoachUsage("claude-opus-5-5", input = 1_000), CoachUsage("claude-opus-4-8", input = 1_000, output = 1_000)),
        )
        val coach = MeteredCoach(FakeCoachModel(FakeCoachModel.Turn.Fails(refused), FakeCoachModel.Turn.Reply(fellBack)), cost)
        runCatching { coach.reply(request) }
        coach.reply(request)
        // 10,000 × $4; then 1,000 × $4 + 1,000 × $5 + 1,000 × $25.
        assertEquals(74_000L, cost.thisMonth.first())
    }

    @Test
    fun aNewMonthStartsAtNothing() = runTest {
        val coach = MeteredCoach(FakeCoachModel.replies(first, second), cost)
        coach.reply(request)
        month = YearMonth.of(2026, 11)
        val november = CoachCost(db) { month }
        assertEquals(0L, november.thisMonth.first())
        MeteredCoach(FakeCoachModel.replies(second), november).reply(request)
        assertEquals(24_000L, november.thisMonth.first())
        assertEquals("≈ $0.02", CoachCost.dollars(november.thisMonth.first()))
    }

    @Test
    fun noConnectionCostsNothing() = runTest {
        val coach = MeteredCoach(FakeCoachModel(FakeCoachModel.Turn.Fails(CoachError.NoConnection())), cost)
        runCatching { coach.reply(request) }
        assertEquals(0L, cost.thisMonth.first())
        assertEquals("≈ $0.00", CoachCost.dollars(cost.thisMonth.first()))
    }
}
