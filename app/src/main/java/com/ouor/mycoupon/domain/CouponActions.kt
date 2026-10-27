package com.ouor.mycoupon.domain

import com.ouor.mycoupon.data.Coupon
import com.ouor.mycoupon.data.CouponRepository
import com.ouor.mycoupon.data.CouponStatus
import com.ouor.mycoupon.data.UsageKind
import com.ouor.mycoupon.data.UsageLog
import com.ouor.mycoupon.data.UsedReason
import com.ouor.mycoupon.data.CouponType
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** S4 [N개 등록]. */
@Singleton
class CouponRegistrar @Inject constructor(private val repo: CouponRepository) {
    suspend fun register(list: List<Coupon>): List<Long> {
        val now = System.currentTimeMillis()
        return repo.insertAll(list.map {
            it.copy(
                id = 0,
                codeKey = Coupon.codeKey(it.code),
                balance = if (it.type == CouponType.AMOUNT) it.balance ?: it.faceValue else null,
                createdAt = now, updatedAt = now,
            )
        })
    }
}

/** Why S8 input was refused (spec F7 AC2). */
enum class UsageError { NOT_AMOUNT, NEGATIVE, OVER_BALANCE, NO_CHANGE, SPENT_ZERO }

/** Invalid input from S8. [message] is user-facing Korean; [error] lets the UI pick its own copy. */
class UsageInputException(val error: UsageError, message: String) : IllegalArgumentException(message)

/** Pure checks for S8 input; [balance] is the current balance. Returns the balance left after the input. */
object UsageInput {
    private fun won(v: Long) = "%,d원".format(v)

    fun remaining(balance: Long, remaining: Long): Long {
        if (remaining < 0) throw UsageInputException(UsageError.NEGATIVE, "0원보다 적게 입력할 수 없어요")
        if (remaining > balance) throw UsageInputException(UsageError.OVER_BALANCE, "남은 금액은 지금 잔액 ${won(balance)}보다 클 수 없어요")
        if (remaining == balance) throw UsageInputException(UsageError.NO_CHANGE, "잔액이 그대로예요. 쓴 금액이 있을 때 기록해 주세요")
        return remaining
    }

    fun spent(balance: Long, spent: Long): Long {
        if (spent <= 0) throw UsageInputException(UsageError.SPENT_ZERO, "쓴 금액을 입력해 주세요")
        if (spent > balance) throw UsageInputException(UsageError.OVER_BALANCE, "쓴 금액은 지금 잔액 ${won(balance)}보다 클 수 없어요")
        return balance - spent
    }
}

/** F7 use / balance / undo, F16 give-away, F3 review confirmation, F18 bookkeeping. */
@Singleton
class CouponActions @Inject constructor(private val repo: CouponRepository) {

    /** Exchange coupon [사용 완료], or F16 [넘겼어요] with [UsedReason.GIVEN] and the optional "누구에게" ([givenTo], C2-25). */
    suspend fun markUsed(id: Long, reason: UsedReason = UsedReason.USED, givenTo: String? = null) = repo.transaction {
        val c = repo.get(id) ?: return@transaction
        if (c.status == CouponStatus.USED) return@transaction
        val now = System.currentTimeMillis()
        if (c.type == CouponType.AMOUNT) {
            repo.addUsage(UsageLog(couponId = id, at = now, spent = c.balance ?: 0, balanceAfter = 0))
        } else {
            repo.addUsage(UsageLog(couponId = id, at = now, spent = null, balanceAfter = null))
        }
        val to = givenTo?.trim()?.take(MAX_GIVEN_TO)?.ifEmpty { null }.takeIf { reason == UsedReason.GIVEN }
        repo.update(used(c, reason, now).copy(balance = if (c.type == CouponType.AMOUNT) 0 else null, givenTo = to))
    }

    /** Amount voucher: the balance left after this purchase. 0 marks it used; the same balance is refused (no 0원 record). */
    suspend fun recordRemaining(id: Long, remaining: Long) = repo.transaction {
        val c = repo.get(id) ?: return@transaction
        val bal = c.balance ?: c.faceValue ?: 0
        if (c.type != CouponType.AMOUNT) throw UsageInputException(UsageError.NOT_AMOUNT, "금액권만 잔액을 기록할 수 있어요")
        UsageInput.remaining(bal, remaining)
        val now = System.currentTimeMillis()
        repo.addUsage(UsageLog(couponId = id, at = now, spent = bal - remaining, balanceAfter = remaining))
        repo.update(if (remaining == 0L) used(c, UsedReason.USED, now).copy(balance = 0) else c.copy(balance = remaining))
    }

    /** Amount voucher: the amount spent (S8 "쓴 금액으로 입력"). Must not exceed the balance (F7 AC2). */
    suspend fun recordSpent(id: Long, spent: Long) = repo.transaction {
        // read and write in one transaction: a second record in between must not be checked against a stale balance (A-05)
        val c = repo.get(id) ?: return@transaction
        val bal = c.balance ?: c.faceValue ?: 0
        recordRemaining(id, UsageInput.spent(bal, spent))
    }

    /**
     * S5 save of a stored coupon (code review A-04). Only the fields the user changed ([before] = what the editor opened
     * with, [after] = what it saves) are written onto the current row, so a use, an undo or a [맞아요] that landed while
     * the editor was open is kept. A changed balance leaves an ADJUST record, so "마지막 기록 취소" restores the previous one.
     * Throws [IllegalArgumentException] when the result breaks an invariant ([validate]).
     */
    suspend fun edit(id: Long, before: Coupon, after: Coupon) = repo.transaction {
        val cur = repo.get(id) ?: return@transaction
        fun <T> pick(f: (Coupon) -> T): T = if (f(after) != f(before)) f(after) else f(cur)
        val type = pick { it.type }
        val face = if (type == CouponType.AMOUNT) pick { it.faceValue } else null
        val typedBalance = pick { it.balance }
        // the editor's rule: a balance still equal to the face value follows a corrected face value
        val balance = when {
            type != CouponType.AMOUNT -> null
            after.balance != before.balance -> typedBalance
            cur.type != CouponType.AMOUNT -> face
            cur.balance == null || cur.balance == cur.faceValue -> face
            else -> cur.balance
        }
        val merged = cur.copy(
            brand = pick { it.brand }, productName = pick { it.productName }, type = type, faceValue = face, balance = balance,
            code = pick { it.code }, codeFormat = pick { it.codeFormat }, codeSource = pick { it.codeSource },
            expiryDate = pick { it.expiryDate }, issuer = pick { it.issuer }, notes = pick { it.notes }, memo = pick { it.memo },
            authCode = pick { it.authCode }, givenTo = pick { it.givenTo },
            reminderOffsets = pick { it.reminderOffsets },
            // review marks: the user's confirmations apply, ones confirmed elsewhere meanwhile stay confirmed
            needsReview = cur.needsReview.filterKeys { k -> k in after.needsReview || k !in before.needsReview },
        )
        validate(merged)
        // only a balance the user typed is a record; one that follows a corrected face value is a correction
        if (after.balance != before.balance && type == CouponType.AMOUNT && cur.type == CouponType.AMOUNT && balance != cur.balance && balance != null) {
            val old = cur.balance ?: cur.faceValue ?: 0
            repo.addUsage(UsageLog(couponId = id, at = System.currentTimeMillis(), spent = old - balance, balanceAfter = balance, kind = UsageKind.ADJUST))
        }
        repo.update(merged)
    }

    /** S5 "쿠폰 아님 · 빼기" on a stored coupon: the row, its history (cascade) and the image copies. */
    suspend fun delete(id: Long) {
        repo.get(id)?.let { repo.delete(it) }
    }

    /** S13 [되돌리기]: back to ACTIVE. A use that zeroed an amount voucher is undone with it. */
    suspend fun undoUse(id: Long) = repo.transaction {
        val c = repo.get(id) ?: return@transaction
        if (c.status != CouponStatus.USED) return@transaction
        val last = repo.usageOf(id).firstOrNull()
        var balance = c.balance
        if (last != null && (c.type != CouponType.AMOUNT || last.balanceAfter == 0L)) {
            repo.removeUsage(last)
            if (c.type == CouponType.AMOUNT) balance = (last.balanceAfter ?: 0) + (last.spent ?: 0)
        }
        repo.update(c.copy(status = CouponStatus.ACTIVE, usedReason = null, usedAt = null, usedViaReminder = false, balance = balance, givenTo = null))
    }

    /** S6 "마지막 기록 취소" on an amount voucher. */
    suspend fun undoLastBalance(id: Long) = repo.transaction {
        val c = repo.get(id) ?: return@transaction
        val logs = repo.usageOf(id)
        val last = logs.firstOrNull() ?: return@transaction
        repo.removeUsage(last)
        val prev = logs.getOrNull(1)?.balanceAfter ?: c.faceValue
        val reopened = if (c.status == CouponStatus.USED) c.copy(status = CouponStatus.ACTIVE, usedReason = null, usedAt = null, usedViaReminder = false) else c
        repo.update(reopened.copy(balance = prev))
    }

    /** S5 [맞아요]: the field (porting-spec key) no longer needs a check. */
    suspend fun confirm(id: Long, field: String) {
        val c = repo.get(id) ?: return
        repo.update(c.copy(needsReview = c.needsReview - field))
    }

    /** N1 tap → S6: remembered for F18 "알림 받고 쓴 쿠폰". */
    suspend fun openedFromReminder(id: Long) {
        val c = repo.get(id) ?: return
        repo.update(c.copy(reminderOpenedAt = System.currentTimeMillis()))
    }

    /** F10 [그대로 두기] / after the trash request: do not ask again. */
    suspend fun trashAnswered(id: Long) {
        val c = repo.get(id) ?: return
        repo.update(c.copy(trashAsked = true))
    }

    /** S12: null = follow settings. */
    suspend fun setReminderOffsets(id: Long, offsets: List<Int>?) {
        val c = repo.get(id) ?: return
        repo.update(c.copy(reminderOffsets = offsets?.distinct()?.sortedDescending()))
    }

    /** Invariants every write keeps (code review A-11). */
    fun validate(c: Coupon) {
        if (c.type == CouponType.EXCHANGE) require(c.faceValue == null && c.balance == null) { "exchange coupon with an amount" }
        c.faceValue?.let { require(it >= 0) { "negative face value" } }
        c.balance?.let { b ->
            require(b >= 0) { "negative balance" }
            c.faceValue?.let { require(b <= it) { "balance above face value" } }
        }
    }

    private companion object {
        /** S-10 style cap on a typed name */
        const val MAX_GIVEN_TO = 40
    }

    private fun used(c: Coupon, reason: UsedReason, now: Long) = c.copy(
        status = CouponStatus.USED, usedReason = reason, usedAt = now,
        usedViaReminder = c.reminderOpenedAt != null && now - c.reminderOpenedAt <= TimeUnit.DAYS.toMillis(7),
    )
}

/** F18: counts only, never amounts. */
data class UsageStats(val usedBeforeExpiry: Int, val usedViaReminder: Int)

object Stats {
    fun of(all: List<Coupon>): UsageStats {
        val used = all.filter { it.status == CouponStatus.USED && it.usedReason == UsedReason.USED }
        val beforeExpiry = used.count { c ->
            val at = c.usedAt ?: return@count false
            val day = java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            c.expiryDate == null || day <= c.expiryDate
        }
        return UsageStats(beforeExpiry, used.count { it.usedViaReminder })
    }

    /** D-day number: 0 = today, negative = expired, null = no expiry. */
    fun daysLeft(c: Coupon, today: LocalDate = LocalDate.now()): Long? =
        c.expiryDate?.let { java.time.temporal.ChronoUnit.DAYS.between(today, it) }
}
