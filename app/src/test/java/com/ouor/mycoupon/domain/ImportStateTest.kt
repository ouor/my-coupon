package com.ouor.mycoupon.domain

import com.ouor.mycoupon.data.Coupon
import com.ouor.mycoupon.data.CouponType
import com.ouor.mycoupon.data.Duplicate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** C2-09 S4 counts and [모두 맞아요] groups, C2-24 camera review marks. */
class ImportStateTest {
    private val today = LocalDate.of(2026, 10, 28)
    private fun coupon(product: String = "P", expiry: LocalDate? = today.plusDays(30), review: Map<String, List<String>> = emptyMap()) =
        Coupon(brand = "CU", productName = product, expiryDate = expiry, needsReview = review, imagePath = "", thumbPath = "", imageHash = "h")

    private fun draft(key: Int, c: Coupon, isCoupon: Boolean = true, dup: Duplicate? = null) =
        Draft(key, DraftStage.DONE, coupon = c, isCoupon = isCoupon, duplicate = dup)

    @Test
    fun countsEachCardOnce() {
        val st = ImportState(drafts = listOf(
            draft(0, coupon()), draft(1, coupon(), dup = Duplicate.REGISTERED), draft(2, coupon(expiry = today.minusDays(1))),
            draft(3, coupon(), isCoupon = false), Draft(4, DraftStage.FAILED), Draft(5),
        ))
        assertEquals(ImportCounts(fresh = 1, registered = 1, expired = 1, notCoupon = 1, failed = 1), st.counts(today))
    }

    @Test
    fun groupsNeedEqualValuesAndTheSameField() {
        val r = mapOf("expiryDate" to listOf("NO_KEYWORD"))
        val st = ImportState(drafts = (0 until 11).map { draft(it, coupon(review = r)) } +
            draft(11, coupon(product = "Q", review = r)) + draft(12, coupon()))
        val g = st.reviewGroups().single() // AC3: the "Q" card and the card without a mark are not in it
        assertEquals("expiryDate", g.field)
        assertEquals((0 until 11).toList(), g.keys)
    }

    @Test
    fun capturedMarksEveryField() {
        val c = ImportSession.captured(coupon(review = mapOf("expiryDate" to listOf("NO_KEYWORD"))).copy(type = CouponType.AMOUNT))
        assertEquals(setOf("brand", "productName", "code", "expiryDate", "faceValue"), c.needsReview.keys)
        assertEquals(listOf("CAPTURED", "NO_KEYWORD"), c.needsReview["expiryDate"])
    }
}
