package com.ouor.mycoupon.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import java.time.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** F14: [모든 데이터 삭제] leaves no settings behind, the last backup time included (lead decision 2026-10-28). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsTest {
    @Test
    fun wipeClearsLastBackupAndSettings() {
        val s = Settings(ApplicationProvider.getApplicationContext())
        s.app = AppSettings(listOf(30), 22 * 60)
        s.lastBackupAt = 1_793_000_000_000
        s.resetForWipe()
        assertEquals(0L, s.lastBackupAt)
        assertEquals(AppSettings(), s.app)
    }

    /** C2-17: asked after 15 s on S6 only, once per coupon, not after a day; C2-10 / C2-15 state goes on wipe. */
    @Test
    fun useAskOncePerCouponWithinADay() {
        val s = Settings(ApplicationProvider.getApplicationContext())
        s.recordLinger(5, shownMs = 5_000, now = 1_000)
        assertNull(s.pendingUseAsk(now = 2_000)) // AC3: 5 s is not enough
        s.recordLinger(5, shownMs = 20_000, now = 1_000)
        assertEquals(5L, s.pendingUseAsk(now = 2_000))
        assertNull(s.pendingUseAsk(now = 1_000 + 25 * 3600_000L)) // a day later: not asked
        s.answerUseAsk(5)
        s.recordLinger(5, shownMs = 20_000, now = 3_000)
        assertNull(s.pendingUseAsk(now = 4_000)) // AC4: once

        s.hideExpiringBanner(LocalDate.of(2026, 10, 28))
        s.amountInputSpent = true
        s.recordLinger(6, shownMs = 20_000, now = 3_000)
        s.resetForWipe()
        assertEquals(Long.MIN_VALUE, s.expiringBannerHiddenDay)
        assertEquals(false, s.amountInputSpent)
        assertNull(s.pendingUseAsk(now = 4_000))
    }
}
