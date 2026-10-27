package com.ouor.mycoupon.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import java.io.File

/** F16 / F19: hands a copy of an app file to the system share sheet (the app itself sends nothing). */
object Sharing {
    /** C2-26 (QA R2-01): broadcast the chooser sends only when the user picked a target app; S6 asks "넘겼나요?" on it. */
    const val ACTION_CHOSEN = "com.ouor.mycoupon.action.SHARE_CHOSEN"

    private fun chosenSender(context: Context): android.content.IntentSender =
        // mutable: the chooser fills in EXTRA_CHOSEN_COMPONENT; explicit by package, so no other app receives it
        android.app.PendingIntent.getBroadcast(context, 0x5A, Intent(ACTION_CHOSEN).setPackage(context.packageName),
            android.app.PendingIntent.FLAG_MUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT).intentSender

    fun intent(context: Context, source: File, name: String, mime: String, reportChoice: Boolean = false): Intent {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        dir.listFiles().orEmpty().forEach { it.delete() } // only the latest shared copy lives here
        val copy = File(dir, name)
        source.copyTo(copy, overwrite = true)
        if (mime == "image/jpeg") com.ouor.mycoupon.image.ImageCodec.stripPrivateExif(copy) // S-15
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", copy)
        val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // QA R1-15: never offer this app itself as a target (it would re-import the coupon)
        return (if (reportChoice) Intent.createChooser(send, null, chosenSender(context)) else Intent.createChooser(send, null))
            .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(android.content.ComponentName(context, com.ouor.mycoupon.ui.MainActivity::class.java)))
    }

    fun imageMime(file: File): String {
        val head = file.inputStream().use { s -> ByteArray(12).also { s.read(it) } }
        return when {
            head[0] == 0x89.toByte() && head[1] == 'P'.code.toByte() -> "image/png"
            head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte() -> "image/jpeg"
            head[0] == 'R'.code.toByte() && head[8] == 'W'.code.toByte() -> "image/webp"
            head[0] == 'G'.code.toByte() -> "image/gif"
            else -> "image/*"
        }
    }
}

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
