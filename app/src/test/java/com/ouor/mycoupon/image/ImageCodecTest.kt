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

    /** O-17: each of the 8 EXIF orientations maps the x and y unit vectors as the EXIF spec says (null = identity). */
    @Test
    fun exifMatrixAllOrientations() {
        // orientation -> where (1,0) and (0,1) go, in y-down screen coordinates
        val expected = mapOf(
            2 to listOf(-1f, 0f, 0f, 1f), 3 to listOf(-1f, 0f, 0f, -1f), 4 to listOf(1f, 0f, 0f, -1f), 5 to listOf(0f, 1f, 1f, 0f),
            6 to listOf(0f, 1f, -1f, 0f), 7 to listOf(0f, -1f, -1f, 0f), 8 to listOf(0f, -1f, 1f, 0f),
        )
        for (o in 1..8) {
            val f = File.createTempFile("exif$o", ".jpg")
            ImageIO.write(BufferedImage(8, 4, BufferedImage.TYPE_INT_RGB), "jpg", f)
            ExifInterface(f.absolutePath).apply { setAttribute(ExifInterface.TAG_ORIENTATION, o.toString()); saveAttributes() }
            val m = ImageCodec.exifMatrix(f.readBytes())
            f.delete()
            if (o == 1) { assertNull(m); continue }
            val v = floatArrayOf(1f, 0f, 0f, 1f)
            m!!.mapVectors(v)
            assertEquals("orientation $o", expected[o], v.map { Math.round(it).toFloat() + 0f }) // + 0f: -0.0 equals 0.0
        }
    }
}
