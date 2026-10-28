package com.ouor.mycoupon.backup

import com.ouor.mycoupon.data.CodeSource
import com.ouor.mycoupon.data.Coupon
import com.ouor.mycoupon.data.CouponType
import com.ouor.mycoupon.data.UsageKind
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * Backup format v1 is frozen (code review A-02): the fixture (synthetic values, written in the 1.0 development shape,
 * defaults omitted) must keep decoding into today's entities. Also the S-04 checks on untrusted values.
 */
class BackupFormatTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val manifest = json.decodeFromString(BackupManifest.serializer(), javaClass.getResource("/backup-v1.json")!!.readText())

    @Test
    fun v1FixtureDecodes() {
        val (a, b) = manifest.coupons
        val c = BackupFormat.toEntity(a.coupon, "/img/a", "/thumb/a", "real-hash")
        assertEquals(CouponType.EXCHANGE, c.type)
        assertEquals(CodeSource.DECODED, c.codeSource)
        assertEquals(LocalDate.of(2027, 3, 31), c.expiryDate)
        assertEquals(listOf(LocalDate.of(2027, 3, 31), LocalDate.of(2027, 4, 30)), c.expiryCandidates)
        assertEquals("real-hash", c.imageHash) // S-04: the file's claim is ignored
        assertNull(c.sourceUri)
        assertNull(c.authCode) // cycle-1 file: the v2 fields default to null (C2-19 AC3)
        assertNull(c.givenTo)
        assertEquals(0L, c.id)
        assertEquals(mapOf("expiryDate" to listOf("NO_KEYWORD")), c.needsReview)

        val d = BackupFormat.toEntity(b.coupon, "/img/b", "/thumb/b", "h")
        assertEquals(CouponType.AMOUNT, d.type)
        assertEquals(4000L, d.balance)
        assertEquals(listOf(14), d.reminderOffsets) // 2 is not an S11 choice
        val log = BackupFormat.toEntity(b.usage.single(), 99)
        assertEquals(99L, log.couponId)
        assertEquals(UsageKind.USE, log.kind)
    }

    /** S-04: an out-of-range minute used to crash reminderTime at every app start. */
    @Test
    fun settingsAreClamped() {
        val s = BackupFormat.toSettings(manifest.settings)
        assertEquals(listOf(30, 7, 0), s.reminderOffsets)
        assertEquals(9 * 60, s.reminderMinute)
        s.reminderTime
    }

    @Test
    fun roundTrip() {
        val c = Coupon(brand = "B", type = CouponType.AMOUNT, faceValue = 5000, balance = 5000, code = "1", codeSource = CodeSource.OCR,
            authCode = "A12", givenTo = "G",
            expiryDate = LocalDate.of(2027, 1, 1), imagePath = "/x", thumbPath = "/t", imageHash = "h", createdAt = 1, updatedAt = 2)
        val text = json.encodeToString(CouponV1.serializer(), BackupFormat.toFile(c, "images/0"))
        val back = BackupFormat.toEntity(json.decodeFromString(CouponV1.serializer(), text), "/x", "/t", "h")
        assertEquals(c.copy(codeKey = "1"), back)
    }

    @Test(expected = BackupFormatException::class)
    fun unknownEnumRefused() {
        BackupFormat.toEntity(CouponV1(type = "GIFT", imagePath = "images/0"), "", "", "")
    }

    /** D-12: a file that breaks the app's rules comes back inside them (balance <= face, a used coupon has usedAt). */
    @Test
    fun invariantsRestored() {
        val c = BackupFormat.toEntity(CouponV1(type = "AMOUNT", faceValue = 5_000, balance = 9_000, imagePath = "images/0"), "", "", "h")
        assertEquals(5_000L, c.balance)
        val n = BackupFormat.toEntity(CouponV1(type = "AMOUNT", faceValue = 5_000, balance = null, imagePath = "images/0"), "", "", "h")
        assertEquals(5_000L, n.balance)
        val u = BackupFormat.toEntity(CouponV1(type = "PRODUCT", status = "USED", updatedAt = 77, imagePath = "images/0"), "", "", "h")
        assertEquals(77L, u.usedAt)
    }
}
