package com.ouor.mycoupon.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ouor.mycoupon.backup.BackupFormat
import com.ouor.mycoupon.domain.CouponActions
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Modifier
import java.time.LocalDate

/**
 * A2-04: a new Coupon field must reach the backup (CouponV1, toFile, toEntity) and the S5 save (CouponActions.edit).
 * The field list comes from the class, so a field added without updating [full] and the mappings fails here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CouponFieldsTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .allowMainThreadQueries().build()
    private val repo = CouponRepository(db, ImageStore(ApplicationProvider.getApplicationContext()), emptySet())

    @After fun close() = db.close()

    private val fields = Coupon::class.java.declaredFields.filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic }
        .onEach { it.isAccessible = true }

    private fun differing(a: Coupon, b: Coupon) = fields.filter { it.get(a) != it.get(b) }.map { it.name }.toSet()

    /** Every field set to a value no default has. */
    private val full = Coupon(
        id = 0, brand = "B", productName = "P", type = CouponType.AMOUNT, faceValue = 9_000, balance = 4_000, code = "C1",
        codeKey = "C1", codeFormat = "QR_CODE", codeSource = CodeSource.USER, expiryDate = LocalDate.of(2031, 2, 3), issuer = "I",
        notes = "N", memo = "M", authCode = "A", givenTo = "G", needsReview = mapOf("brand" to listOf("x")),
        fieldBoxes = mapOf("code" to listOf(1.0, 2.0, 3.0, 4.0)), expiryCandidates = listOf(LocalDate.of(2031, 2, 3)),
        imagePath = "/i/a.jpg", thumbPath = "/t/a.jpg", imageWidth = 7, imageHeight = 8, imageHash = "h", sourceUri = "content://x/1",
        status = CouponStatus.USED, usedReason = UsedReason.GIVEN, usedAt = 5, usedViaReminder = true, reminderOpenedAt = 6,
        reminderOffsets = listOf(3), trashAsked = true, createdAt = 1, updatedAt = 2,
    )

    @Test
    fun fullSetsEveryField() {
        val plain = Coupon(id = 99, imagePath = "", thumbPath = "", imageHash = "", createdAt = 0, updatedAt = 0)
        assertEquals("fields full() leaves at their default", emptySet<String>(), fields.map { it.name }.toSet() - differing(full, plain))
    }

    /** toEntity(toFile(c)) keeps every field but the ones the restore assigns itself. */
    @Test
    fun backupRoundTrip() {
        val back = BackupFormat.toEntity(BackupFormat.toFile(full, "images/0"), full.imagePath, full.thumbPath, full.imageHash)
        assertEquals(setOf("sourceUri"), differing(full, back) - "codeKey" - "id")
    }

    /** Every field the user edits on S5 is saved; the rest are named here on purpose. */
    @Test
    fun editSavesEveryEditableField() = runBlocking {
        val id = repo.insertAll(listOf(Coupon(imagePath = "/i/b.jpg", thumbPath = "/t/b.jpg", imageHash = "h2"))).single()
        val before = repo.get(id)!!
        val after = full.copy(id = id, status = CouponStatus.ACTIVE, usedReason = null, usedAt = null)
        CouponActions(repo).edit(id, before, after)
        val notEditable = setOf(
            "id", "codeKey", "needsReview", "fieldBoxes", "expiryCandidates", "imagePath", "thumbPath", "imageWidth", "imageHeight",
            "imageHash", "sourceUri", "status", "usedReason", "usedAt", "usedViaReminder", "reminderOpenedAt", "trashAsked",
            "createdAt", "updatedAt",
        )
        val missed = differing(after, repo.get(id)!!) - notEditable
        assertTrue("edit() drops $missed", missed.isEmpty())
    }
}
