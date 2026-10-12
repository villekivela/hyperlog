package com.hyperlog.app

import com.hyperlog.app.data.HyperlogDatabase
import org.junit.rules.ExternalResource

/**
 * Gives each activity test an empty database. Robolectric keeps both the shared instance and the
 * database file between tests, so a session started in one test would open the next. Put it
 * outside the compose rule.
 */
class FreshDatabaseRule : ExternalResource() {
    override fun before() {
        HyperlogDatabase.deleteForTests()
    }

    override fun after() {
        HyperlogDatabase.deleteForTests()
    }
}
