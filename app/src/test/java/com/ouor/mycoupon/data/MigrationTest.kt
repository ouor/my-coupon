package com.ouor.mycoupon.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Room schema frame (code review A-01). app/schemas/.../1.json is the shipped v1 schema; it must keep matching the
 * entities. A schema change adds N.json + a Migration and a case here: create the old version, insert rows, migrate
 * with runMigrationsAndValidate(N, listOf(MIGRATION_x_N)), check the rows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {
    private val file = File(ApplicationProvider.getApplicationContext<android.content.Context>().filesDir, "migration-test.db")

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), file, AndroidSQLiteDriver(), AppDatabase::class)

    @Test
    fun v1MatchesExportedSchema() {
        helper.createDatabase(1).close()
        // validates the DB built from 1.json (tables, columns, indices, foreign keys)
        helper.runMigrationsAndValidate(1, emptyList()).close()
        // and the app's Room code accepts it: its identity hash equals the current entities'
        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java, file.path)
            .setDriver(AndroidSQLiteDriver()).build()
        runBlocking { db.coupons().all() }
        db.close()
    }
}
