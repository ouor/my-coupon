package com.ouor.mycoupon.domain

import com.ouor.mycoupon.data.Coupon
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

/**
 * How a coupon is written on every surface (code review 2 A2-06): reminders, widget, tile, error messages and screens
 * call this, so a wording change is made once. Pure Kotlin.
 */
object CouponText {
    /** "12,400원", Korean grouping whatever the phone's locale */
    fun won(v: Long): String = NumberFormat.getNumberInstance(Locale.KOREA).format(v) + "원"

    /** "스타벅스 아메리카노 T"; "쿠폰" when nothing is known */
    fun title(c: Coupon): String = listOfNotNull(c.brand, shortName(c.brand, c.productName)).joinToString(" ").ifEmpty { "쿠폰" }

    private val leadingBracket = Regex("""^\s*[(\[（【]([^)\]）】]*)[)\]）】]\s*""")
    private fun norm(s: String) = s.filterNot(Char::isWhitespace).lowercase()

    /**
     * Display only (review U1): issuers repeat the brand in front of the name, "(메가MGC커피)팥빙 젤라또" with brand 메가MGC커피
     * → "팥빙 젤라또", "[CU] 30,000원권" → "30,000원권". Stored data and recognition stay as they are.
     */
    fun shortName(brand: String?, name: String?): String? {
        if (name == null || brand.isNullOrBlank()) return name
        val m = leadingBracket.find(name) ?: return name
        val inner = norm(m.groupValues[1])
        val b = norm(brand)
        val rest = name.substring(m.range.last + 1).trim()
        return if (rest.isNotEmpty() && inner.isNotEmpty() && (inner == b || inner.contains(b) || b.contains(inner))) rest else name
    }

    /** "D-3", "오늘", "기간 없음" (widget, tile) */
    fun dday(c: Coupon, today: LocalDate): String = when (val d = Stats.daysLeft(c, today)) {
        null -> "기간 없음"
        0L -> "오늘"
        else -> "D-$d"
    }
}
