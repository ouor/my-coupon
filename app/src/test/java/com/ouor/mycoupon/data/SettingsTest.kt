package com.ouor.mycoupon.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
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
}
