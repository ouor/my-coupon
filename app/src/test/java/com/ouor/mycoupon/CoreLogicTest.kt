package com.ouor.mycoupon

import com.ouor.mycoupon.crash.CrashLog
import com.ouor.mycoupon.data.Coupon
import com.ouor.mycoupon.data.CouponRepository
import com.ouor.mycoupon.data.CouponStatus
import com.ouor.mycoupon.data.ListFilter
import com.ouor.mycoupon.data.ListSort
import com.ouor.mycoupon.data.UsedReason
import com.ouor.mycoupon.domain.Stats
import com.ouor.mycoupon.ocr.CouponRecognizer
import com.ouor.mycoupon.ocr.core.CodeSource
import com.ouor.mycoupon.ocr.core.CouponType
import com.ouor.mycoupon.ocr.core.Extraction
import com.ouor.mycoupon.ocr.core.Field
import com.ouor.mycoupon.ocr.core.ReviewReason
import com.ouor.mycoupon.reminder.ReminderPlanner
import com.ouor.mycoupon.widget.WidgetData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class CoreLogicTest {
    private val today = LocalDate.of(2026, 10, 27)
    private var n = 0L
    private fun c(
        expiry: LocalDate?, brand: String = "B", product: String = "P", type: CouponType = CouponType.EXCHANGE, balance: Long? = null,
        status: CouponStatus = CouponStatus.ACTIVE, offsets: List<Int>? = null, code: String? = null,
    ) = Coupon(id = ++n, brand = brand, productName = product, type = type, balance = balance, faceValue = balance, expiryDate = expiry,
        status = status, reminderOffsets = offsets, code = code, codeKey = Coupon.codeKey(code), imagePath = "", thumbPath = "", imageHash = "h$n", createdAt = n)

    @Test
    fun reminderDueAndOrder() {
        val a = c(today.plusDays(7), product = "seven")
        val b = c(today.plusDays(1), type = CouponType.AMOUNT, balance = 30000, product = "money")
        val d = c(today, product = "today")
        val skip = listOf(
            c(today.plusDays(2)), c(null), c(today.minusDays(1)), c(today.plusDays(7), status = CouponStatus.USED),
            c(today.plusDays(7), offsets = listOf(30)), // per-coupon offsets override the default
        )
        val custom = c(today.plusDays(30), offsets = listOf(30), product = "custom")
        val due = ReminderPlanner.due(listOf(a, d) + skip + listOf(b, custom), listOf(7, 1, 0), today)
        assertEquals(listOf("money", "today", "seven", "custom"), due.map { it.productName })
    }

    @Test
    fun reminderTexts() {
        val one = c(today.plusDays(3), brand = "스타벅스", product = "아메리카노 T")
        assertEquals("스타벅스 아메리카노 T", ReminderPlanner.title(one))
        assertEquals("3일 남았어요 · ${today.plusDays(3).monthValue}월 ${today.plusDays(3).dayOfMonth}일까지", ReminderPlanner.body(one, today))
        assertEquals("오늘까지예요", ReminderPlanner.body(c(today), today))
        assertEquals("잔액 12,400원 · 3일 남았어요", ReminderPlanner.body(c(today.plusDays(3), type = CouponType.AMOUNT, balance = 12400), today))
    }

    @Test
    fun nextRun() {
        val t = LocalTime.of(9, 0)
        assertEquals(LocalDateTime.of(2026, 10, 27, 9, 0), ReminderPlanner.nextRun(LocalDateTime.of(2026, 10, 27, 8, 0), t))
        assertEquals(LocalDateTime.of(2026, 10, 28, 9, 0), ReminderPlanner.nextRun(LocalDateTime.of(2026, 10, 27, 9, 0), t))
    }

    @Test
    fun listFilterSortSearch() {
        val soon = c(today.plusDays(1), brand = "GS25", product = "삼각김밥")
        val later = c(today.plusDays(10), brand = "스타벅스")
        val none = c(null, brand = "CU")
        val expired = c(today.minusDays(1))
        val used = c(today.plusDays(5), status = CouponStatus.USED)
        val all = listOf(none, later, soon, expired, used)
        assertEquals(listOf(soon, later, none), CouponRepository.list(all, ListFilter.AVAILABLE, ListSort.EXPIRY, "", today))
        assertEquals(setOf(expired, used), CouponRepository.list(all, ListFilter.PAST, ListSort.EXPIRY, "", today).toSet())
        assertEquals(listOf(soon), CouponRepository.list(all, ListFilter.AVAILABLE, ListSort.EXPIRY, "gs", today))
        assertEquals(listOf(soon), CouponRepository.list(all, ListFilter.AVAILABLE, ListSort.EXPIRY, "삼각 김밥", today))
        assertEquals(listOf(none, later, soon), CouponRepository.list(all, ListFilter.AVAILABLE, ListSort.RECENT, "", today))
    }

    @Test
    fun codeKey() {
        assertEquals("1234ABCD", Coupon.codeKey("1234-ab cd"))
        assertNull(Coupon.codeKey(" - "))
    }

    @Test
    fun stats() {
        val zone = ZoneId.systemDefault()
        fun usedAt(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli() + 3600_000
        val all = listOf(
            c(today).copy(status = CouponStatus.USED, usedReason = UsedReason.USED, usedAt = usedAt(today), usedViaReminder = true),
            c(today.minusDays(1)).copy(status = CouponStatus.USED, usedReason = UsedReason.USED, usedAt = usedAt(today)), // after expiry
            c(null).copy(status = CouponStatus.USED, usedReason = UsedReason.USED, usedAt = usedAt(today)),
            c(today).copy(status = CouponStatus.USED, usedReason = UsedReason.GIVEN, usedAt = usedAt(today)), // given away: not "used"
        )
        val s = Stats.of(all)
        assertEquals(2, s.usedBeforeExpiry)
        assertEquals(1, s.usedViaReminder)
    }

    @Test
    fun widgetTop3() {
        val list = listOf(c(null), c(today.plusDays(5)), c(today.plusDays(1)), c(today.plusDays(9)), c(today.minusDays(1)))
        assertEquals(listOf(today.plusDays(1), today.plusDays(5), today.plusDays(9)), WidgetData.top(list, today).map { it.expiryDate })
        assertEquals("오늘", WidgetData.dday(c(today), today))
    }

    @Test
    fun decodedQrAlwaysNeedsCheck() {
        val base = Extraction("2", true, null, null, CouponType.EXCHANGE, null, "ABC123", "QR_CODE", CodeSource.DECODED, null,
            emptyList(), null, null, null, emptyMap(), emptyMap(), emptyList())
        assertEquals(listOf(ReviewReason.ALWAYS_CHECK), CouponRecognizer.withAppRules(base).needsReview[Field.CODE])
        val code128 = base.copy(codeFormat = "CODE_128")
        assertFalse(CouponRecognizer.withAppRules(code128).needsReview.containsKey(Field.CODE))
    }

    @Test
    fun crashScrub() {
        val s = CrashLog.scrub("For input string: \"1234567890123456\" 스타벅스 아메리카노 at 12")
        assertFalse(s.contains("1234"))
        assertFalse(s.contains("스타벅스"))
        assertTrue(s.contains("at 12"))
    }

    @Test
    fun usageInputErrors() {
        fun err(block: () -> Unit) = try { block(); null } catch (e: com.ouor.mycoupon.domain.UsageInputException) { assertTrue(e.message!!.any { it in '가'..'힣' }); e.error }
        val u = com.ouor.mycoupon.domain.UsageInput

        assertEquals(12400L, u.remaining(30000, 12400))
        assertEquals(0L, u.remaining(30000, 0))
        assertEquals(com.ouor.mycoupon.domain.UsageError.NO_CHANGE, err { u.remaining(30000, 30000) })
        assertEquals(com.ouor.mycoupon.domain.UsageError.OVER_BALANCE, err { u.remaining(30000, 30001) })
        assertEquals(com.ouor.mycoupon.domain.UsageError.NEGATIVE, err { u.remaining(30000, -1) })
        assertEquals(12400L, u.spent(30000, 17600))
        assertEquals(com.ouor.mycoupon.domain.UsageError.SPENT_ZERO, err { u.spent(30000, 0) })
        assertEquals(com.ouor.mycoupon.domain.UsageError.OVER_BALANCE, err { u.spent(30000, 30001) })
    }

    @Test
    fun scrubbedThrowableForSystemHandler() {
        val e = IllegalStateException("code 1234567890 스타벅스", RuntimeException("pin 98765"))
        val s = CrashLog.scrubbed(e)
        assertEquals("java.lang.IllegalStateException: code # *", s.toString())
        assertEquals("java.lang.RuntimeException: pin #", s.cause.toString())
        assertEquals(e.stackTrace.toList(), s.stackTrace.toList())
    }
}
