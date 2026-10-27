package com.ouor.mycoupon.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/*
 * Enum names are stored in the DB (Room stores name) and the backup file: never rename or remove a constant (code review A-02).
 * The OCR module's enums are mapped in ImportSession, so the storage format is owned here, not by the Python port.
 */

/** spec §6.1 type: exchange (one use) or amount voucher (balance). */
enum class CouponType { EXCHANGE, AMOUNT }

enum class CouponStatus { ACTIVE, USED }

/** Why a coupon left the active list: used it, or gave it to someone (F16). Not a sale state. */
enum class UsedReason { USED, GIVEN }

/** spec §6.1 codeSource: barcode decode, OCR digits, or typed by the user. */
enum class CodeSource { DECODED, OCR, USER }

/** spec §6.1. Expired is computed (`ACTIVE && expiryDate < today`), never stored. Backup format: backup/BackupFormat.kt. */
@Entity(tableName = "coupon", indices = [Index("codeKey"), Index("imageHash"), Index("status")])
data class Coupon(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val brand: String? = null,
    val productName: String? = null,
    val type: CouponType = CouponType.EXCHANGE,
    /** won, AMOUNT only */
    val faceValue: Long? = null,
    /** won, AMOUNT only; starts at faceValue */
    val balance: Long? = null,
    val code: String? = null,
    /** [code] reduced to [0-9A-Za-z], upper-cased: the duplicate key (F4). */
    val codeKey: String? = null,
    /** ZXing format name (CODE_128, EAN_13, QR_CODE, ...), PIN (typed, no barcode) or NONE (OCR digits). */
    val codeFormat: String? = null,
    val codeSource: CodeSource? = null,
    val expiryDate: LocalDate? = null,
    val issuer: String? = null,
    /** notes section text (F12), verbatim */
    val notes: String? = null,
    /** free memo; the detected sender lands here (F2) */
    val memo: String? = null,
    /** field key (porting-spec: brand, productName, faceValue, code, expiryDate, sender) -> reason codes. Empty = nothing to check. */
    val needsReview: Map<String, List<String>> = emptyMap(),
    /** field key -> [x0, y0, x1, y1] in original-image pixels (S5 highlight) */
    val fieldBoxes: Map<String, List<Double>> = emptyMap(),
    /** expiry candidates for the S5 chips */
    val expiryCandidates: List<LocalDate> = emptyList(),
    /** app-internal copy of the original bytes */
    val imagePath: String,
    val thumbPath: String,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    /** SHA-256 of the original bytes (F4 without a number) */
    val imageHash: String,
    /** MediaStore uri the image came from (F10, F9); null for shared images and after restore */
    val sourceUri: String? = null,
    val status: CouponStatus = CouponStatus.ACTIVE,
    val usedReason: UsedReason? = null,
    val usedAt: Long? = null,
    /** F18: used within 7 days after opening it from a reminder */
    val usedViaReminder: Boolean = false,
    /** F18: last time the user opened this coupon from a reminder notification */
    val reminderOpenedAt: Long? = null,
    /** days before expiry to remind; null = settings default (S12) */
    val reminderOffsets: List<Int>? = null,
    /** F10: user already answered the trash suggestion */
    val trashAsked: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val needsCheck get() = needsReview.isNotEmpty()
    fun isExpired(today: LocalDate) = status == CouponStatus.ACTIVE && expiryDate != null && expiryDate < today
    fun isAvailable(today: LocalDate) = status == CouponStatus.ACTIVE && !isExpired(today)

    companion object {
        fun codeKey(code: String?): String? =
            code?.filter { it in '0'..'9' || it in 'A'..'Z' || it in 'a'..'z' }?.uppercase()?.ifEmpty { null }
    }
}

/** USE: S8 / [사용 완료] / [넘겼어요]. ADJUST: the balance typed in S5 edit (code review A-04), undone like a use. */
enum class UsageKind { USE, ADJUST }

/** spec §6.2: amount-voucher history, also the undo record. */
@Entity(
    tableName = "usage_log",
    foreignKeys = [ForeignKey(entity = Coupon::class, parentColumns = ["id"], childColumns = ["couponId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("couponId")],
)
data class UsageLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val couponId: Long,
    val at: Long,
    /** won spent in this record (AMOUNT; an ADJUST that raises the balance is negative); null for an exchange coupon */
    val spent: Long?,
    /** balance after this record (AMOUNT) */
    val balanceAfter: Long?,
    val kind: UsageKind = UsageKind.USE,
)

enum class ScanResult { COUPON, NOT_COUPON, REGISTERED, DISMISSED }

/** spec §6.2: F9 resume + no re-suggestion. Keyed by MediaStore image id. */
@Entity(tableName = "scan_log")
data class ScanLog(
    @PrimaryKey val mediaId: Long,
    val result: ScanResult,
    val at: Long,
)
