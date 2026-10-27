package com.ouor.mycoupon.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** A4: start-up cleaning removes only unreferenced copies made before this process started. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImageStoreTest {
    @Test
    fun removesOnlyOldOrphans() {
        val store = ImageStore(ApplicationProvider.getApplicationContext())
        val kept = store.save(byteArrayOf(1)).path
        val orphan = store.save(byteArrayOf(2)).path
        val fresh = store.save(byteArrayOf(3)).path
        File(kept).setLastModified(1_000)
        File(orphan).setLastModified(1_000)
        File(fresh).setLastModified(10_000)
        assertEquals(1, store.removeOrphans(setOf(kept), before = 5_000))
        assertTrue(File(kept).exists())
        assertFalse(File(orphan).exists())
        assertTrue(File(fresh).exists())
    }
}
