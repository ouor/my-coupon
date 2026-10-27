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
import com.ouor.mycoupon.ui.screen.timeLabel
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
}
