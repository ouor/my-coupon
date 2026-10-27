package com.ouor.mycoupon.image

import android.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** S-15: the sent copy loses location and device tags, keeps its orientation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImageCodecTest {
    @Test
    fun stripsPrivateExif() {
        val f = File.createTempFile("exif", ".jpg")
        ImageIO.write(BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "jpg", f)
        ExifInterface(f.absolutePath).apply {
            setAttribute(ExifInterface.TAG_GPS_LATITUDE, "37/1,30/1,0/1")
            setAttribute(ExifInterface.TAG_MODEL, "PhoneX")
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        assertEquals("PhoneX", ExifInterface(f.absolutePath).getAttribute(ExifInterface.TAG_MODEL))
        ImageCodec.stripPrivateExif(f)
        val e = ExifInterface(f.absolutePath)
        assertNull(e.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertNull(e.getAttribute(ExifInterface.TAG_MODEL))
        assertEquals(ExifInterface.ORIENTATION_ROTATE_90, e.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0))
        f.delete()
    }
}
