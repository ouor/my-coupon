package com.ouor.mycoupon.data

import androidx.room.withTransaction
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** F4: why a draft starts unchecked. */
enum class Duplicate {
    /** same number (or same image) as a coupon already in the app */
    REGISTERED,
    /** same number as a coupon already used: stronger warning */
    USED,
    /** same number as an earlier image of this batch */
    IN_BATCH,
}

/** S1 filter chips (S13 = PAST). */
enum class ListFilter { AVAILABLE, PAST }

/** S1 sort: expiry first (no-expiry last), or newest first. */
enum class ListSort { EXPIRY, RECENT }

/** Anything that must follow a coupon change: widget refresh, reminder (re)scheduling. */
interface CouponChangeListener {
    fun onCouponsChanged()
}

/** Room access + the side effects every write needs. Logs never include coupon contents (spec §7.1-5). */
@Singleton
class CouponRepository @Inject constructor(
    private val db: AppDatabase,
    private val images: ImageStore,
    private val listeners: Set<@JvmSuppressWildcards CouponChangeListener>,
) {
    private val coupons = db.coupons()
    private val usage = db.usage()

    // schema v3 (A2-01): the DB keeps image file names, everything above the repository sees absolute paths
    private fun Coupon.loaded() = copy(imagePath = images.image(imagePath), thumbPath = images.thumb(thumbPath))
    private fun Coupon.stored() = copy(imagePath = File(imagePath).name, thumbPath = File(thumbPath).name)
    private fun List<Coupon>.loaded() = map { it.loaded() }

    fun observeAll(): Flow<List<Coupon>> = coupons.observeAll().map { it.loaded() }
    fun observe(id: Long): Flow<Coupon?> = coupons.observe(id).map { it?.loaded() }
    fun observeUsage(id: Long): Flow<List<UsageLog>> = usage.observe(id)
    fun observeBrands(): Flow<List<String>> = coupons.observeBrands()
    suspend fun get(id: Long) = coupons.get(id)?.loaded()
    suspend fun all() = coupons.all().loaded()
    suspend fun active() = coupons.active().loaded()
    suspend fun usageOf(id: Long) = usage.list(id)
    suspend fun allUsage() = usage.all()

    /** S1 / S13 list; re-emits when the date changes (F5 AC1: expired coupons move to PAST at midnight). */
    fun observeList(filter: Flow<ListFilter>, sort: Flow<ListSort>, query: Flow<String>): Flow<List<Coupon>> =
        combine(coupons.observeAll(), filter, sort, query, today()) { all, f, s, q, today -> list(all, f, s, q, today) }

    /** Current date, re-emitted after each midnight. */
    fun today(): Flow<LocalDate> = flow {
        while (true) {
            emit(LocalDate.now())
            val now = LocalDateTime.now()
            delay(Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toMillis() + 1000)
        }
    }.distinctUntilChanged()

    suspend fun insertAll(list: List<Coupon>): List<Long> = db.withTransaction { list.map { coupons.insert(it.stored()) } }.also { changed() }

    suspend fun update(c: Coupon) {
        coupons.update(c.stored().copy(codeKey = Coupon.codeKey(c.code), updatedAt = System.currentTimeMillis()))
        changed()
    }

    /** Deletes the coupon, its history and its image copies (gallery original untouched). */
    suspend fun delete(c: Coupon) {
        coupons.delete(c)
        images.delete(c.imagePath, c.thumbPath)
        changed()
    }

    suspend fun addUsage(l: UsageLog) = usage.insert(l)
    suspend fun removeUsage(l: UsageLog) = usage.delete(l)
    /** Listeners run again after the commit: [update]/[delete] inside the block notify before it, when readers still see old rows (QA R1-02). */
    suspend fun <T> transaction(block: suspend () -> T): T = db.withTransaction { block() }.also { changed() }

    /** F4: same number as a stored coupon (USED wins), or the same image bytes when there is no number. */
    suspend fun findDuplicate(c: Coupon): Duplicate? {
        val key = c.codeKey
        if (key != null) {
            val same = coupons.byCodeKey(key)
            if (same.any { it.status == CouponStatus.USED }) return Duplicate.USED
            if (same.isNotEmpty()) return Duplicate.REGISTERED
            return null
        }
        return if (coupons.byImageHash(c.imageHash).isNotEmpty()) Duplicate.REGISTERED else null
    }

    /** [dateAdded] null keeps the one already stored (S4 marks know only the id). */
    suspend fun markScan(mediaId: Long, r: ScanResult, dateAdded: Long? = null) = db.withTransaction {
        val d = dateAdded ?: db.scans().get(mediaId)?.dateAdded ?: 0
        db.scans().put(ScanLog(mediaId, r, System.currentTimeMillis(), d))
    }

    /** Settled images: MediaStore id -> DATE_ADDED it had when checked (0 = unknown, from schema v2). */
    suspend fun scanned(): Map<Long, Long> = db.scans().settled().associate { it.mediaId to it.dateAdded }

    /** F14: everything app-side (gallery originals untouched). */
    suspend fun wipe() {
        db.withTransaction {
            coupons.deleteAll()
            db.scans().deleteAll()
        }
        images.wipe()
        // deleted rows must not linger in the WAL file (secure_delete zeroes them in the DB file, AppModule)
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        changed()
    }

    /**
     * Tells the widget, tile and reminders. Inside [transaction] it waits: the transaction tells them once, after the commit,
     * so they never read rows that are not committed yet (A2-11).
     */
    fun changed() {
        if (!db.inTransaction()) listeners.forEach { it.onCouponsChanged() }
    }

    companion object {
        /** spec F5 search: brand / product / memo / given-to (C2-25), case- and space-insensitive substring. */
        fun matches(c: Coupon, q: String): Boolean {
            val k = q.filterNot { it.isWhitespace() }.lowercase()
            if (k.isEmpty()) return true
            return listOfNotNull(c.brand, c.productName, c.memo, c.givenTo).any { it.filterNot(Char::isWhitespace).lowercase().contains(k) }
        }

        fun list(all: List<Coupon>, f: ListFilter, s: ListSort, q: String, today: LocalDate): List<Coupon> {
            val shown = all.filter { (if (f == ListFilter.AVAILABLE) it.isAvailable(today) else !it.isAvailable(today)) && matches(it, q) }
            return when (s) {
                ListSort.RECENT -> shown.sortedByDescending { it.createdAt }
                ListSort.EXPIRY -> if (f == ListFilter.PAST) shown.sortedByDescending { it.usedAt ?: it.expiryDate?.toEpochDay()?.times(86_400_000) ?: 0 }
                else shown.sortedWith(compareBy<Coupon> { it.expiryDate == null }.thenBy { it.expiryDate }.thenByDescending { it.createdAt })
            }
        }

        /** C2-10: available coupons that end today or tomorrow, soonest first (the banner count and its tap filter). */
        fun endingSoon(all: List<Coupon>, today: LocalDate): List<Coupon> =
            all.filter { it.isAvailable(today) && it.expiryDate != null && it.expiryDate <= today.plusDays(1) }.sortedBy { it.expiryDate }

        /**
         * C2-15 brand chips: brands of available coupons; the brand of the latest [사용 완료] (not a give-away) first,
         * then more available coupons first, then name.
         */
        fun brandOrder(all: List<Coupon>, today: LocalDate): List<String> {
            val available = all.filter { it.isAvailable(today) && !it.brand.isNullOrBlank() }.groupingBy { it.brand!! }.eachCount()
            val last = all.filter { it.usedReason == UsedReason.USED && it.brand != null }.maxByOrNull { it.usedAt ?: 0 }?.brand
            return available.keys.sortedWith(compareByDescending<String> { it == last }.thenByDescending { available[it] }.thenBy { it })
        }
    }
}
