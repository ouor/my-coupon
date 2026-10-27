package com.ouor.mycoupon.ui.settings

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Security review S-05: the notices page lists the libraries the review found missing and carries license texts. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LicensesTest {
    @Test
    fun noticesCoverLibrariesAndTexts() {
        val rows = loadNotices(ApplicationProvider.getApplicationContext())
        val all = rows.joinToString("\n") { it.title + "\n" + it.body }
        listOf("okio", "Bouncy Castle", "PP-OCRv5", "RapidOCR", "Accompanist", "jsr305", "JSpecify", "javax.inject", "Jakarta")
            .forEach { assertTrue(it, all.contains(it, ignoreCase = true)) }
        listOf("Apache License", "Copyright (c) Microsoft Corporation", "Legion of the Bouncy Castle", "THIRD PARTY SOFTWARE NOTICES")
            .forEach { assertTrue(it, all.contains(it)) }
    }
}
