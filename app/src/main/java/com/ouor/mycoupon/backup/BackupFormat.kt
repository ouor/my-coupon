package com.ouor.mycoupon.backup

import com.ouor.mycoupon.data.AppSettings
import com.ouor.mycoupon.data.CodeSource
import com.ouor.mycoupon.data.Coupon
import com.ouor.mycoupon.data.CouponStatus
import com.ouor.mycoupon.data.CouponType
import com.ouor.mycoupon.data.UsageKind
import com.ouor.mycoupon.data.UsageLog
import com.ouor.mycoupon.data.UsedReason
import kotlinx.serialization.Serializable
import java.time.LocalDate

/*
 * Backup file format v1, frozen (code review A-02). These types ARE the file: never rename, retype or remove a field,
 * and give every new field a default. A breaking change is a new V2 type set + FORMAT_VERSION bump, with V1 still read.
 * Enums are stored as their names (strings) so a renamed app enum cannot silently change the file.
 * Field names equal the 1.0 development files (the Room entity used to be serialised directly), so those still open.
 */

/** Plaintext inside the encrypted file: a ZIP with manifest.json + images/<n>. */
@Serializable
data class BackupManifest(
    val formatVersion: Int = FORMAT_VERSION,
    val createdAt: Long,
    val settings: SettingsV1 = SettingsV1(),
    val coupons: List<BackupCoupon>,
) {
    companion object {
        const val FORMAT_VERSION = 1
    }
}

@Serializable
data class SettingsV1(val reminderOffsets: List<Int> = listOf(7, 1, 0), val reminderMinute: Int = 9 * 60)

/** [coupon].imagePath holds the ZIP entry name; ids are re-assigned on import. */
@Serializable
data class BackupCoupon(val coupon: CouponV1, val usage: List<UsageV1> = emptyList())

@Serializable
data class CouponV1(
    val id: Long = 0,
    val brand: String? = null,
    val productName: String? = null,
    val type: String = "EXCHANGE",
    val faceValue: Long? = null,
    val balance: Long? = null,
    val code: String? = null,
    val codeKey: String? = null,
    val codeFormat: String? = null,
    val codeSource: String? = null,
    /** ISO yyyy-MM-dd */
    val expiryDate: String? = null,
    val issuer: String? = null,
    val notes: String? = null,
    val memo: String? = null,
    /** schema v2 (C2-19, C2-25): absent in cycle-1 files, so default null */
    val authCode: String? = null,
    val givenTo: String? = null,
    val needsReview: Map<String, List<String>> = emptyMap(),
    val fieldBoxes: Map<String, List<Double>> = emptyMap(),
    val expiryCandidates: List<String> = emptyList(),
    val imagePath: String,
    val thumbPath: String = "",
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val imageHash: String = "",
    val sourceUri: String? = null,
    val status: String = "ACTIVE",
    val usedReason: String? = null,
    val usedAt: Long? = null,
    val usedViaReminder: Boolean = false,
    val reminderOpenedAt: Long? = null,
    val reminderOffsets: List<Int>? = null,
    val trashAsked: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Serializable
data class UsageV1(
    val id: Long = 0,
    val couponId: Long = 0,
    val at: Long,
    val spent: Long? = null,
    val balanceAfter: Long? = null,
    val kind: String = "USE",
)

/**
 * Entity <-> file. Reading is a trust boundary (any file the user picks): enums must be known, text is capped and
 * settings are clamped to what the UI can produce (security review S-04). Throws [BackupFormatException].
 */
object BackupFormat {
    /** Longest text field kept from a file (S-10): notes are the longest real field, a few hundred chars. */
    const val MAX_TEXT = 4000

    fun toFile(c: Coupon, entry: String) = CouponV1(
        id = c.id, brand = c.brand, productName = c.productName, type = c.type.name, faceValue = c.faceValue, balance = c.balance,
        code = c.code, codeKey = c.codeKey, codeFormat = c.codeFormat, codeSource = c.codeSource?.name,
        expiryDate = c.expiryDate?.toString(), issuer = c.issuer, notes = c.notes, memo = c.memo,
        authCode = c.authCode, givenTo = c.givenTo,
        needsReview = c.needsReview, fieldBoxes = c.fieldBoxes, expiryCandidates = c.expiryCandidates.map { it.toString() },
        imagePath = entry, thumbPath = "", imageWidth = c.imageWidth, imageHeight = c.imageHeight, imageHash = c.imageHash,
        sourceUri = null, status = c.status.name, usedReason = c.usedReason?.name, usedAt = c.usedAt,
        usedViaReminder = c.usedViaReminder, reminderOpenedAt = c.reminderOpenedAt, reminderOffsets = c.reminderOffsets,
        trashAsked = c.trashAsked, createdAt = c.createdAt, updatedAt = c.updatedAt,
    )

    fun toFile(l: UsageLog) = UsageV1(id = l.id, couponId = l.couponId, at = l.at, spent = l.spent, balanceAfter = l.balanceAfter, kind = l.kind.name)

    fun toFile(s: AppSettings) = SettingsV1(s.reminderOffsets, s.reminderMinute)

    /** File -> entity without id, image paths or source uri (the caller fills them). */
    fun toEntity(f: CouponV1, imagePath: String, thumbPath: String, imageHash: String): Coupon {
        val type = enumOf<CouponType>(f.type)
        return Coupon(
            brand = f.brand.cap(), productName = f.productName.cap(), type = type,
            faceValue = f.faceValue?.takeIf { type == CouponType.AMOUNT && it >= 0 },
            balance = f.balance?.takeIf { type == CouponType.AMOUNT && it >= 0 },
            code = f.code.cap(), codeKey = Coupon.codeKey(f.code.cap()), codeFormat = f.codeFormat.cap(),
            codeSource = f.codeSource?.let { enumOf<CodeSource>(it) },
            expiryDate = f.expiryDate?.let(::date), issuer = f.issuer.cap(), notes = f.notes.cap(), memo = f.memo.cap(),
            authCode = f.authCode.cap(), givenTo = f.givenTo.cap(),
            needsReview = f.needsReview.entries.take(16).associate { (k, v) -> k.take(32) to v.take(16).map { it.take(32) } },
            fieldBoxes = f.fieldBoxes.entries.take(16).filter { it.value.size == 4 }.associate { (k, v) -> k.take(32) to v },
            expiryCandidates = f.expiryCandidates.take(16).map(::date),
            imagePath = imagePath, thumbPath = thumbPath,
            imageWidth = f.imageWidth.coerceAtLeast(0), imageHeight = f.imageHeight.coerceAtLeast(0),
            imageHash = imageHash, sourceUri = null,
            status = enumOf(f.status), usedReason = f.usedReason?.let { enumOf<UsedReason>(it) }, usedAt = f.usedAt,
            usedViaReminder = f.usedViaReminder, reminderOpenedAt = f.reminderOpenedAt,
            reminderOffsets = f.reminderOffsets?.let(::offsets), trashAsked = f.trashAsked,
            createdAt = f.createdAt, updatedAt = f.updatedAt,
        )
    }

    fun toEntity(l: UsageV1, couponId: Long) = UsageLog(couponId = couponId, at = l.at, spent = l.spent, balanceAfter = l.balanceAfter, kind = enumOf(l.kind))

    fun toSettings(s: SettingsV1) = AppSettings(s.reminderOffsets, s.reminderMinute).valid()

    /** Only the S11 choices; an empty list (reminders off) stays empty. */
    private fun offsets(l: List<Int>) = l.filter { it in AppSettings.OFFSET_CHOICES }.distinct().sortedDescending()

    private fun String?.cap() = this?.take(MAX_TEXT)

    private fun date(s: String): LocalDate = try {
        LocalDate.parse(s)
    } catch (_: Exception) {
        throw BackupFormatException("bad date")
    }

    private inline fun <reified E : Enum<E>> enumOf(name: String): E =
        enumValues<E>().firstOrNull { it.name == name } ?: throw BackupFormatException("unknown ${E::class.simpleName}")
}
