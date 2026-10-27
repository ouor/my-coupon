package com.ouor.mycoupon.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest

/** Stateless image and hash helpers shared by data, ocr, gallery and ui (code review A-03: ocr no longer depends on data). */
object ImageCodec {
    /** Largest image accepted (S-09/S-10): a 200 MP JPEG is ~60 MB at most, coupon screenshots are < 5 MB. */
    const val MAX_BYTES = 50 shl 20

    /** Whole stream, or null once it passes [max] bytes. */
    fun readCapped(i: java.io.InputStream, max: Int): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = i.read(buf)
            if (n < 0) return out.toByteArray()
            if (out.size() + n > max) return null
            out.write(buf, 0, n)
        }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** Streams [f] (models are too big to read into the heap at once). */
    fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(1 shl 16)
        f.inputStream().use { i -> while (true) { val n = i.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** EXIF orientation as a rotation/flip matrix (cv2.imread applies it; BitmapFactory does not). */
    fun exifMatrix(bytes: ByteArray): Matrix? {
        val o = runCatching { ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
        val m = Matrix()
        when (o) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.setRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> m.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.setRotate(-90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> m.setRotate(-90f)
            else -> return null
        }
        return m
    }

    /**
     * Decodes with EXIF orientation applied. [maxSide] > 0 subsamples (power of two) and then scales so the
     * long side is at most [maxSide]; 0 keeps full resolution (OCR input must match the PC pipeline).
     */
    fun decode(bytes: ByteArray, maxSide: Int = 0): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            if (maxSide > 0) {
                var s = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (s * 2) >= maxSide) s *= 2
                inSampleSize = s
            }
        }
        var b = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) }.getOrNull() ?: return null
        if (maxSide > 0 && maxOf(b.width, b.height) > maxSide) {
            val r = maxSide.toFloat() / maxOf(b.width, b.height)
            val s = Bitmap.createScaledBitmap(b, maxOf(1, (b.width * r).toInt()), maxOf(1, (b.height * r).toInt()), true)
            if (s !== b) b.recycle()
            b = s
        }
        val m = exifMatrix(bytes) ?: return b
        val r = Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
        if (r !== b) b.recycle()
        return r
    }
}
