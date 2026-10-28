package com.ouor.mycoupon.ui.component

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

/** F6: redraws a decoded barcode crisply. Null when the format cannot be written (S6 then shows the original image). */
object BarcodeRenderer {
    private val writable = setOf(
        BarcodeFormat.CODE_128, BarcodeFormat.CODE_39, BarcodeFormat.CODE_93, BarcodeFormat.CODABAR, BarcodeFormat.EAN_13,
        BarcodeFormat.EAN_8, BarcodeFormat.ITF, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E, BarcodeFormat.QR_CODE,
        BarcodeFormat.DATA_MATRIX, BarcodeFormat.PDF_417, BarcodeFormat.AZTEC,
    )

    fun canRender(format: String?): Boolean = format != null && runCatching { BarcodeFormat.valueOf(format) }.getOrNull() in writable

    fun is2d(format: String?) = format in setOf("QR_CODE", "DATA_MATRIX", "AZTEC")

    /**
     * 1D: integer pixels per module, >= 10 modules quiet zone each side (design-system §6.4), fitted to [maxWidth].
     * 2D: square, integer module size.
     */
    fun render(value: String, format: String, maxWidth: Int, height1d: Int): Bitmap? {
        val f = runCatching { BarcodeFormat.valueOf(format) }.getOrNull() ?: return null
        if (f !in writable) return null
        return runCatching {
            val hints = mapOf(EncodeHintType.MARGIN to if (is2d(format)) 2 else 10)
            val m: BitMatrix = MultiFormatWriter().encode(value, f, 0, 0, hints) // minimal size: 1 px per module
            val scale = maxOf(1, maxWidth / m.width)
            val w = m.width * scale
            val h = if (is2d(format)) m.height * scale else height1d
            val px = IntArray(w * h)
            for (y in 0 until h) {
                val my = if (is2d(format)) y / scale else 0
                for (x in 0 until w) px[y * w + x] = if (m[x / scale, my]) Color.BLACK else Color.WHITE
            }
            Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        }.getOrNull()
    }

    /** "1234 5678 9012 3456" (S6, TalkBack reads groups of four). */
    fun groups(code: String) = code.chunked(4).joinToString(" ")
}
