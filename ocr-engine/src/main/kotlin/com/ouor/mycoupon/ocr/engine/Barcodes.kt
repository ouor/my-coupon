package com.ouor.mycoupon.ocr.engine

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.Result
import com.google.zxing.common.BitArray
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.oned.MultiFormatOneDReader
import com.ouor.mycoupon.ocr.core.Box
import com.ouor.mycoupon.ocr.core.DecodedBarcode

/** ZXing (on device, no network). Pure JVM so the app path can be tested end to end on the PC (code review O-06). */
object Barcodes {
    private val ONE_D = setOf(
        BarcodeFormat.CODABAR, BarcodeFormat.CODE_39, BarcodeFormat.CODE_93, BarcodeFormat.CODE_128, BarcodeFormat.EAN_8,
        BarcodeFormat.EAN_13, BarcodeFormat.ITF, BarcodeFormat.RSS_14, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E,
    )

    /**
     * Formats Korean mobile coupons use (O-09: fewer readers = a cheaper miss on a photo without a code). MAXICODE needs a
     * pure image and gives no result points (O-13); AZTEC, PDF_417, CODABAR, RSS_14/RSS_EXPANDED and the UPC/EAN extension
     * are not seen on coupons (all 56 .test coupons: Code128 / Code93 / EAN-13), and CODABAR is the usual false hit on texture.
     */
    private val COUPON_FORMATS = listOf(
        BarcodeFormat.CODE_128, BarcodeFormat.CODE_39, BarcodeFormat.CODE_93, BarcodeFormat.EAN_13, BarcodeFormat.EAN_8,
        BarcodeFormat.UPC_A, BarcodeFormat.UPC_E, BarcodeFormat.ITF, BarcodeFormat.QR_CODE, BarcodeFormat.DATA_MATRIX,
    )
    private val hints = mapOf(DecodeHintType.TRY_HARDER to true, DecodeHintType.POSSIBLE_FORMATS to COUPON_FORMATS)
    private val fastHints = mapOf(DecodeHintType.POSSIBLE_FORMATS to hints[DecodeHintType.POSSIBLE_FORMATS])

    fun gray(img: BgrImage): ByteArray {
        val g = ByteArray(img.width * img.height)
        val d = img.data
        for (i in g.indices) {
            val b = d[i * 3].toInt() and 0xFF
            val gg = d[i * 3 + 1].toInt() and 0xFF
            val r = d[i * 3 + 2].toInt() and 0xFF
            g[i] = ((r + 2 * gg + b) / 4).toByte() // RGBLuminanceSource formula
        }
        return g
    }

    /** All found barcodes with boxes in image pixels; tries full size, then half size (small/huge codes). */
    fun decode(img: BgrImage): List<DecodedBarcode> {
        val g = gray(img)
        decodeGray(g, img.width, img.height, 1f, hints)?.let { return listOf(it) }
        if (minOf(img.width, img.height) >= 400) {
            val w2 = img.width / 2
            val h2 = img.height / 2
            val half = ByteArray(w2 * h2)
            for (y in 0 until h2) for (x in 0 until w2) half[y * w2 + x] = g[(y * 2) * img.width + x * 2]
            decodeGray(half, w2, h2, 2f, hints)?.let { return listOf(it) }
        }
        return emptyList()
    }

    /** Cheap screening for F9: one pass without TRY_HARDER on a downscaled gray image. */
    fun quickFind(gray: ByteArray, w: Int, h: Int): Boolean = decodeGray(gray, w, h, 1f, fastHints, extend = false) != null

    private fun decodeGray(g: ByteArray, w: Int, h: Int, scale: Float, hints: Map<DecodeHintType, Any?>, extend: Boolean = true): DecodedBarcode? {
        val bitmap = BinaryBitmap(HybridBinarizer(PlanarYUVLuminanceSource(g, w, h, 0, 0, w, h, false)))
        val reader = MultiFormatReader()
        return try {
            val r = reader.decode(bitmap, hints)
            val pts = r.resultPoints.orEmpty().filterNotNull()
            var box = if (pts.isEmpty()) Box(0.0, 0.0, 0.0, 0.0) // never "the whole image": Extract would drop every line as noise (O-13)
            else Box.around(pts.map { it.x.toDouble() }, pts.map { it.y.toDouble() })
            if (extend && r.barcodeFormat in ONE_D && box.y0 == box.y1) {
                val (top, bottom) = barRows(bitmap, r, box.y0.toInt(), hints)
                box = box.copy(y0 = top.toDouble(), y1 = bottom.toDouble())
            }
            val s = scale.toDouble()
            DecodedBarcode(r.text, r.barcodeFormat.name, Box(box.x0 * s, box.y0 * s, box.x1 * s, box.y1 * s))
        } catch (_: NotFoundException) {
            null
        } catch (_: ReaderException) {
            null
        } finally {
            reader.reset()
        }
    }

    /**
     * A 1D result is one scanned row (height 0). Re-reads the rows above and below until the same value stops decoding,
     * so the box covers the bars like zxing-cpp's (O-07: code highlight, original crop, Extract's barcode noise filter).
     */
    internal fun barRows(bitmap: BinaryBitmap, r: Result, y: Int, hints: Map<DecodeHintType, Any?>): Pair<Int, Int> {
        val reader = MultiFormatOneDReader(hints)
        var row: BitArray? = null
        fun same(yy: Int): Boolean {
            if (yy < 0 || yy >= bitmap.height) return false
            row = bitmap.getBlackRow(yy, row)
            for (pass in 0..1) {
                if (pass == 1) row!!.reverse()
                try {
                    if (reader.decodeRow(yy, row, hints).text == r.text) return true
                } catch (_: ReaderException) {
                } finally {
                    reader.reset()
                }
            }
            return false
        }
        val step = maxOf(1, bitmap.height / 400)
        fun edge(dir: Int): Int {
            var last = y
            var yy = y + dir * step
            var misses = 0
            while (misses < 2) { // one noisy row inside the bars is not the edge
                if (same(yy)) { last = yy; misses = 0 } else misses++
                yy += dir * step
                if (yy < 0 || yy >= bitmap.height) break
            }
            return last
        }
        return edge(-1) to edge(1)
    }
}
