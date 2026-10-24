package com.hyperlog.app.ui.settings

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.coach.ClaudeKeys
import com.hyperlog.app.coach.CoachCost
import com.hyperlog.app.coach.CoachError
import com.hyperlog.app.coach.KeySeal
import com.hyperlog.app.data.HyperlogDatabase
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A key typed in Settings is saved only once the API accepts it (SPEC, Coaching → With a Claude key). */
@RunWith(RobolectricTestRunner::class)
class ClaudeKeySetupTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, HyperlogDatabase::class.java).allowMainThreadQueries().build()
    private val plain = object : KeySeal {
        override fun seal(plain: ByteArray) = plain
        override fun open(sealed: ByteArray) = sealed
    }
    private val keys = ClaudeKeys(ClaudeKeys.file(context), plain)
    private val checked = mutableListOf<String>()
    private var answer: CoachError? = null

    private val setup = ClaudeKeySetup(keys, CoachCost(db) { YearMonth.of(2026, 10) }) { key ->
        checked += key
        answer?.let { throw it }
    }

    @After
    fun close() {
        db.close()
        ClaudeKeys.file(context).delete()
    }

    @Test
    fun anAcceptedKeyIsSavedAndShownByItsEnding() = runTest {
        setup.save("sk-ant-api03-test-a1b2")
        assertEquals(listOf("sk-ant-api03-test-a1b2"), checked)
        assertEquals("sk-ant-api03-test-a1b2", keys.key())
        assertEquals(ClaudeKeyStatus(ending = "a1b2", monthCost = 0, check = KeyCheck.Idle), setup.status.first())
        setup.remove()
        assertNull(keys.key())
        assertEquals(ClaudeKeyStatus(), setup.status.first())
    }

    @Test
    fun aRefusedKeyIsntSavedAndSaysSo() = runTest {
        answer = CoachError.KeyRefused()
        setup.save("sk-ant-wrong")
        assertNull(keys.key())
        assertEquals(KeyCheck.Refused, setup.status.first().check)
        setup.dismiss()
        assertEquals(KeyCheck.Idle, setup.status.first().check)
    }

    @Test
    fun aKeyThatCantBeSealedSaysSoAndIsntKept() = runTest {
        val broken = object : KeySeal {
            override fun seal(plain: ByteArray): ByteArray = throw java.security.KeyStoreException("Keystore is locked")
            override fun open(sealed: ByteArray) = sealed
        }
        val setup = ClaudeKeySetup(ClaudeKeys(ClaudeKeys.file(context), broken), CoachCost(db) { YearMonth.of(2026, 10) }) {}
        setup.save("sk-ant-api03-test-a1b2")
        assertEquals(ClaudeKeyStatus(check = KeyCheck.NotSaved), setup.status.first())
    }

    @Test
    fun noConnectionIsntSavedAndSaysToTryAgain() = runTest {
        answer = CoachError.NoConnection()
        setup.save("sk-ant-api03-test-a1b2")
        assertNull(keys.key())
        assertEquals(KeyCheck.Unreachable, setup.status.first().check)
    }
}
