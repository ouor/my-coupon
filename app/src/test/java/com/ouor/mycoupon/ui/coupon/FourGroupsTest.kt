package com.ouor.mycoupon.ui.coupon

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Test

class FourGroupsTest {
    @Test fun groupsAndMapsCursorBothWays() {
        val raw = "ABCD12345"
        val t = FourGroups.filter(AnnotatedString(raw))
        assertEquals("ABCD 1234 5", t.text.text)
        for (o in 0..raw.length) {
            val tr = t.offsetMapping.originalToTransformed(o)
            assertEquals(o, t.offsetMapping.transformedToOriginal(tr))
        }
        assertEquals(9, t.offsetMapping.transformedToOriginal(t.text.length))
    }
}
