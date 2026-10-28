package com.ouor.mycoupon.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.execSQL
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    }

    /** v1 -> v2 (C2-19, C2-25): every v1 value survives, the new columns are null (spec C2-19 AC2). */
    @Test
    fun v1To2KeepsRows() {
        helper.createDatabase(1).use { c ->
            c.execSQL(
                "INSERT INTO coupon (id, brand, productName, type, faceValue, balance, code, codeKey, codeFormat, codeSource, " +
                    "expiryDate, issuer, notes, memo, needsReview, fieldBoxes, expiryCandidates, imagePath, thumbPath, imageWidth, " +
                    "imageHeight, imageHash, sourceUri, status, usedReason, usedAt, usedViaReminder, reminderOpenedAt, " +
                    "reminderOffsets, trashAsked, createdAt, updatedAt) VALUES (7, 'B', 'P', 'AMOUNT', 10000, 4000, '1234', '1234', " +
                    "'CODE_128', 'DECODED', 20000, 'I', 'N', 'M', '{}', '{}', '', 'i.jpg', 't.jpg', 10, 20, 'h', NULL, 'ACTIVE', " +
                    "NULL, NULL, 0, NULL, '7,1', 0, 1, 2)",
            )
            c.execSQL("INSERT INTO usage_log (id, couponId, at, spent, balanceAfter, kind) VALUES (1, 7, 5, 6000, 4000, 'USE')")
        }
        helper.runMigrationsAndValidate(2, emptyList()).close()
        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java, file.path)
            .setDriver(AndroidSQLiteDriver()).addMigrations(AppDatabase.MIGRATION_2_3).build()
        runBlocking {
            val c = db.coupons().get(7)!!
            assertEquals(listOf("B", "P", "1234", "M"), listOf(c.brand, c.productName, c.code, c.memo))
            assertEquals(4000L, c.balance)
            assertEquals(listOf(7, 1), c.reminderOffsets)
            assertNull(c.authCode)
            assertNull(c.givenTo)
            assertEquals(1, db.usage().list(7).size)
            db.coupons().update(c.copy(authCode = "A1", givenTo = "G"))
            assertEquals("A1", db.coupons().get(7)!!.authCode)
        }
        db.close()
    }

    /** v2 -> v3 (A2-01): image paths become file names, scan_log rows get dateAdded 0 (unknown, trusted). */
    @Test
    fun v2To3KeepsRowsAndNamesImages() {
        helper.createDatabase(2).use { c ->
            c.execSQL(
                "INSERT INTO coupon (id, brand, productName, type, faceValue, balance, code, codeKey, codeFormat, codeSource, " +
                    "expiryDate, issuer, notes, memo, needsReview, fieldBoxes, expiryCandidates, imagePath, thumbPath, imageWidth, " +
                    "imageHeight, imageHash, sourceUri, status, usedReason, usedAt, usedViaReminder, reminderOpenedAt, " +
                    "reminderOffsets, trashAsked, createdAt, updatedAt, authCode, givenTo) VALUES (7, 'B', 'P', 'AMOUNT', 10000, 4000, " +
                    "'1234', '1234', 'CODE_128', 'DECODED', 20000, 'I', 'N', 'M', '{}', '{}', '', " +
                    "'/data/user/0/com.ouor.mycoupon/files/images/a.b.jpg', '/data/user/0/com.ouor.mycoupon/files/thumbs/t.jpg', " +
                    "10, 20, 'h', NULL, 'ACTIVE', NULL, NULL, 0, NULL, '7,1', 0, 1, 2, 'A1', 'G')",
            )
            c.execSQL("INSERT INTO scan_log (mediaId, result, at) VALUES (5, 'NOT_COUPON', 3)")
        }
        helper.runMigrationsAndValidate(3, listOf(AppDatabase.MIGRATION_2_3)).use { c ->
            c.prepare("SELECT imagePath, thumbPath FROM coupon WHERE id = 7").use { st ->
                st.step()
                assertEquals(listOf("a.b.jpg", "t.jpg"), listOf(st.getText(0), st.getText(1)))
            }
            c.prepare("SELECT dateAdded FROM scan_log WHERE mediaId = 5").use { st ->
                st.step()
                assertEquals(0L, st.getLong(0))
            }
        }
        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java, file.path)
            .setDriver(AndroidSQLiteDriver()).addMigrations(AppDatabase.MIGRATION_2_3).build()
        runBlocking {
            val c = db.coupons().get(7)!!
            assertEquals(listOf("B", "1234", "A1", "G"), listOf(c.brand, c.code, c.authCode, c.givenTo))
            assertEquals(4000L, c.balance)
        }
        db.close()
    }
}
