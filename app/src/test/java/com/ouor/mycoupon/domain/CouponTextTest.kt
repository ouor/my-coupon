package com.ouor.mycoupon.domain

import com.ouor.mycoupon.data.Coupon
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/** A2-06: one wording for every surface. */
class CouponTextTest {
    private val today = LocalDate.of(2026, 10, 28)
    private fun c(expiry: LocalDate?, brand: String? = "메가MGC커피", name: String? = "(메가MGC커피)팥빙 젤라또") =
        Coupon(brand = brand, productName = name, expiryDate = expiry, imagePath = "", thumbPath = "", imageHash = "")

    @Test
    fun wonIgnoresPhoneLocale() {
        val old = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("10,000원", CouponText.won(10_000))
        } finally {
            Locale.setDefault(old)
        }
    }

    @Test
    fun titleAndDday() {
        assertEquals("메가MGC커피 팥빙 젤라또", CouponText.title(c(today)))
        assertEquals("쿠폰", CouponText.title(c(today, brand = null, name = null)))
        assertEquals("오늘", CouponText.dday(c(today), today))
        assertEquals("D-3", CouponText.dday(c(today.plusDays(3)), today))
        assertEquals("기간 없음", CouponText.dday(c(null), today))
    }
}
