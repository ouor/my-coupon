package com.ouor.mycoupon.domain

import com.ouor.mycoupon.data.Coupon
import com.ouor.mycoupon.data.CouponRepository
import com.ouor.mycoupon.data.CouponStatus
import com.ouor.mycoupon.data.UsageLog
import com.ouor.mycoupon.data.UsedReason
import com.ouor.mycoupon.ocr.core.CouponType
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

    /** Exchange coupon [사용 완료], or F16 [넘겼어요] with [UsedReason.GIVEN]. */
    suspend fun markUsed(id: Long, reason: UsedReason = UsedReason.USED) = repo.transaction {
        val c = repo.get(id) ?: return@transaction
        if (c.status == CouponStatus.USED) return@transaction
        val now = System.currentTimeMillis()
        if (c.type == CouponType.AMOUNT) {
            repo.addUsage(UsageLog(couponId = id, at = now, spent = c.balance ?: 0, balanceAfter = 0))
        } else {
            repo.addUsage(UsageLog(couponId = id, at = now, spent = null, balanceAfter = null))
        }
        repo.update(used(c, reason, now).copy(balance = if (c.type == CouponType.AMOUNT) 0 else null))
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
    suspend fun recordSpent(id: Long, spent: Long) {
        val c = repo.get(id) ?: return
        val bal = c.balance ?: c.faceValue ?: 0
        recordRemaining(id, UsageInput.spent(bal, spent))
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
        repo.update(c.copy(status = CouponStatus.ACTIVE, usedReason = null, usedAt = null, usedViaReminder = false, balance = balance))
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
