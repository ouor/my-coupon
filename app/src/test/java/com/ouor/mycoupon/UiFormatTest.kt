package com.ouor.mycoupon

import com.ouor.mycoupon.data.Coupon
import com.ouor.mycoupon.data.CouponStatus
import com.ouor.mycoupon.data.UsedReason
import com.ouor.mycoupon.ui.component.CouponState
import com.ouor.mycoupon.ui.component.codeGroups
import com.ouor.mycoupon.ui.component.codeSpoken
import com.ouor.mycoupon.ui.component.dotted
import com.ouor.mycoupon.ui.component.firstReason
import com.ouor.mycoupon.ui.component.reasonText
import com.ouor.mycoupon.ui.component.state
import com.ouor.mycoupon.ui.component.subject
import com.ouor.mycoupon.ui.settings.timeLabel
import com.ouor.mycoupon.ui.coupon.usageMessage
import com.ouor.mycoupon.domain.UsageInput
import com.ouor.mycoupon.domain.UsageInputException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class UiFormatTest {
    private val today = LocalDate.of(2026, 10, 27)
    private fun c(exp: LocalDate?, status: CouponStatus = CouponStatus.ACTIVE, reason: UsedReason? = null) =
        Coupon(expiryDate = exp, status = status, usedReason = reason, imagePath = "", thumbPath = "", imageHash = "")

    @Test fun states() {
        assertEquals(CouponState.AVAILABLE, c(today.plusDays(8)).state(today))
        assertEquals(CouponState.SOON, c(today.plusDays(7)).state(today))
        assertEquals(CouponState.TODAY, c(today).state(today))
        assertEquals(CouponState.EXPIRED, c(today.minusDays(1)).state(today))
        assertEquals(CouponState.NO_EXPIRY, c(null).state(today))
        assertEquals(CouponState.GIVEN, c(today, CouponStatus.USED, UsedReason.GIVEN).state(today))
        assertEquals(CouponState.USED, c(today.minusDays(3), CouponStatus.USED, UsedReason.USED).state(today))
    }

    @Test fun formats() {
        assertEquals("2026.11.03(화)", LocalDate.of(2026, 11, 3).dotted())
        assertEquals(listOf("4550", "0000", "0000", "4993"), codeGroups("4550 0000 0000 4993"))
        assertEquals("1234, 5678, 90", codeSpoken("1234567890"))
        assertEquals("오전 9:00", timeLabel(9 * 60))
        assertEquals("오후 12:30", timeLabel(12 * 60 + 30))
        assertEquals("오전 12:05", timeLabel(5))
    }

    @Test fun reasons() {
        // expiry: MULTIPLE_CANDIDATES/NO_KEYWORD before YEAR_INFERRED before LOW_CONFIDENCE (screens.md S5)
        assertEquals("MULTIPLE_CANDIDATES", firstReason(listOf("LOW_CONFIDENCE", "YEAR_INFERRED", "MULTIPLE_CANDIDATES")))
        assertEquals("AMBIGUOUS", firstReason(listOf("LOW_CONFIDENCE", "AMBIGUOUS")))
        assertEquals("사진과 같은지 확인해 주세요", reasonText("code", "SOMETHING_NEW"))
    }

    @Test fun usageCopy() {
        val over = runCatching { UsageInput.spent(30000, 40000) }.exceptionOrNull() as UsageInputException
        assertEquals("남은 30,000원보다 많이 쓸 수 없어요", usageMessage(over, 30000, true))
        val same = runCatching { UsageInput.remaining(30000, 30000) }.exceptionOrNull() as UsageInputException
        assertEquals("잔액이 그대로예요. 쓴 금액이 있을 때 기록해 주세요", usageMessage(same, 30000, false))
    }

    @Test fun subjectParticle() {
        assertEquals("번호가", "번호".subject())
        assertEquals("상품명이", "상품명".subject())
        assertEquals("금액이", "금액".subject())
        assertEquals("메모가", "메모".subject())
    }

    @Test fun shortNameDropsRepeatedBrand() {
        val n = { b: String?, p: String? -> com.ouor.mycoupon.reminder.ReminderPlanner.shortName(b, p) }
        assertEquals("팥빙 젤라또 파르페", n("메가MGC커피", "(메가MGC커피)팥빙 젤라또 파르페"))
        assertEquals("(ICE)아메리카노", n("메가MGC커피", "(메가MGC커피)(ICE)아메리카노"))
        assertEquals("30,000원권", n("CU", "[CU] 30,000원권"))
        assertEquals("아메리카노", n("메가커피", "(메가MGC커피)아메리카노".replace("메가MGC커피", "메가 커피")))
        assertEquals("[EVENT]메가MGC커피 카페라떼", n("메가MGC커피", "[EVENT]메가MGC커피 카페라떼"))
        assertEquals("(CU)", n("CU", "(CU)"))
        assertEquals("(CU)김밥", n(null, "(CU)김밥"))
        assertEquals(null, n("CU", null))
    }

    @Test fun codeTailShowsLastFour() {
        assertEquals("번호 끝 2085", com.ouor.mycoupon.ui.component.codeTail("4550 3004 1841 2085"))
    }

    @Test fun thousandsCommasAndCursor() {
        val t = com.ouor.mycoupon.ui.component.ThousandsTransformation.filter(androidx.compose.ui.text.AnnotatedString("1234567"))
        assertEquals("1,234,567", t.text.text)
        assertEquals(9, t.offsetMapping.originalToTransformed(7))
        assertEquals(0, t.offsetMapping.originalToTransformed(0))
        assertEquals(1, t.offsetMapping.originalToTransformed(1))
        assertEquals(3, t.offsetMapping.originalToTransformed(2))
        assertEquals(7, t.offsetMapping.transformedToOriginal(9))
        assertEquals(1, t.offsetMapping.transformedToOriginal(2))
        assertEquals("100", com.ouor.mycoupon.ui.component.ThousandsTransformation.filter(androidx.compose.ui.text.AnnotatedString("100")).text.text)
    }
}
