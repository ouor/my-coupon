package com.ouor.mycoupon.ocr

import android.content.Context
import com.ouor.mycoupon.image.ImageCodec
import android.graphics.Bitmap
import android.os.SystemClock
import com.google.zxing.BarcodeFormat
import com.ouor.mycoupon.BuildConfig
import com.ouor.mycoupon.ocr.core.CodeSource
import com.ouor.mycoupon.ocr.core.Extract
import com.ouor.mycoupon.ocr.core.Extraction
import com.ouor.mycoupon.ocr.core.Field
import com.ouor.mycoupon.ocr.core.OcrLine
import com.ouor.mycoupon.ocr.core.ReviewReason
import com.ouor.mycoupon.ocr.engine.Barcodes
import com.ouor.mycoupon.ocr.engine.BgrImage
import com.ouor.mycoupon.ocr.engine.PaddleOcrEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** One image through F2: barcode decode -> OCR -> extract. */
data class Recognition(
    val extraction: Extraction,
    val width: Int,
    val height: Int,
    val timings: Timings,
)

/** Per-stage times; the barcode runs beside det/rec, so [totalMs] is the wall time, not the sum. */
data class Timings(val decodeMs: Long, val barcodeMs: Long, val detMs: Long, val recMs: Long, val extractMs: Long, val lines: Int, val wallMs: Long = 0) {
    val totalMs get() = wallMs
}

/**
 * On-device recognition (spec F2, §8.3 contract). The ONNX models load on the first call, not at app start
 * (spec §2.3 cold start). One image at a time; calls are serialised.
 */
@Singleton
class CouponRecognizer @Inject constructor(@ApplicationContext private val context: Context) {
    private val mutex = Mutex()
    private var engine: PaddleOcrEngine? = null

    private fun engine(): PaddleOcrEngine = engine ?: create(context, PaddleOcrEngine.Options(threads = THREADS, maxSide = MAX_SIDE)).also { engine = it }

    /** Null when the bytes are not a decodable image (S4 "읽지 못했어요"). */
    suspend fun recognize(bytes: ByteArray): Recognition? = withContext(Dispatchers.Default) {
        val ctx = coroutineContext
        // O-09: [버리기] cancels the import job; every step and rec batch checks it, so the CPU is free within one batch
        val cancel = { ctx.ensureActive() }
        mutex.withLock {
            cancel()
            val t0 = SystemClock.elapsedRealtime()
            val bmp = ImageCodec.decode(bytes, DECODE_MAX_SIDE) ?: return@withLock null
            val img = try { cancel(); toBgr(bmp) } finally { bmp.recycle() }
            val t1 = SystemClock.elapsedRealtime()
            cancel()
            // O-09: ZXing (one thread) runs beside det/rec; Extract needs both only at the end
            var barcodeMs = 0L
            val bars = async {
                val b0 = SystemClock.elapsedRealtime()
                Barcodes.decode(img).also { barcodeMs = SystemClock.elapsedRealtime() - b0 }
            }
            val e = engine()
            val lines: List<OcrLine> = e.recognize(img, cancel)
            val barcodes = bars.await()
            val et = e.lastTimings
            val t3 = SystemClock.elapsedRealtime()
            val x = withAppRules(Extract.extract(lines, barcodes, img.width, img.height))
            val t4 = SystemClock.elapsedRealtime()
            Recognition(x, img.width, img.height,
                Timings(t1 - t0, barcodeMs, et?.detMs ?: 0, et?.recMs ?: (t3 - t1), t4 - t3, lines.size, wallMs = t4 - t0))
        }
    }

    /** Frees the models (e.g. after a gallery scan); the next call reloads them. */
    suspend fun release() = mutex.withLock { engine?.close(); engine = null }

    companion object {
        /**
         * Models are compressed in the APK; they are extracted once per app version to no-backup storage and opened
         * by path, so the weights are never copied through the Java heap.
         */
        fun create(context: Context, options: PaddleOcrEngine.Options): PaddleOcrEngine {
            val dir = File(context.noBackupFilesDir, "ocr").apply { mkdirs() }
            val stamp = File(dir, "version")
            // the hashes in the stamp: a model swapped without a versionCode bump is extracted again (O-04)
            val version = "${BuildConfig.VERSION_CODE}-${BuildConfig.BUILD_TYPE}-${BuildConfig.SHA256_DET}-${BuildConfig.SHA256_REC}"
            if (!stamp.exists() || stamp.readText() != version) {
                stamp.delete()
                for ((name, hash) in listOf("det.onnx" to BuildConfig.SHA256_DET, "rec.onnx" to BuildConfig.SHA256_REC)) {
                    // one retry: a partial write or full disk must not stick until the next app update (O-04)
                    runCatching { extract(context, dir, name, hash) }.getOrElse { extract(context, dir, name, hash) }
                }
                stamp.writeText(version)
            }
            return PaddleOcrEngine(
                File(dir, "det.onnx").path, File(dir, "rec.onnx").path,
                context.assets.open("ocr/dict.txt").bufferedReader().readLines(), options,
            )
        }

        /** Copies one model out of the APK and checks the written file's SHA-256 before it replaces the old one. */
        private fun extract(context: Context, dir: File, name: String, sha256: String) {
            val tmp = File(dir, "$name.tmp")
            context.assets.open("ocr/$name").use { i -> tmp.outputStream().use { o -> i.copyTo(o); o.fd.sync() } }
            if (ImageCodec.sha256(tmp) != sha256) { tmp.delete(); throw java.io.IOException("ocr/$name hash mismatch") }
            if (!tmp.renameTo(File(dir, name))) { tmp.delete(); throw java.io.IOException("ocr/$name rename failed") }
        }

        const val THREADS = 4
        /** OCR decode cap (PaddleX max_side_limit): a 50 MP photo would otherwise need ~600 MB of bitmaps (O-02). All .test images are smaller. */
        const val DECODE_MAX_SIDE = 4000
        /** Longest side fed to the detector (PaddleX limit_type max). Decided by the POCO benchmark: docs/core/ocr-runtime.md. */
        const val MAX_SIDE = 1280

        /** PM decision (spec §9.1.1 Q10): a decoded QR value is always confirmed by the user. */
        fun withAppRules(x: Extraction): Extraction {
            if (x.codeSource != CodeSource.DECODED || x.codeFormat != BarcodeFormat.QR_CODE.name) return x
            val code = (x.needsReview[Field.CODE].orEmpty() + ReviewReason.ALWAYS_CHECK).distinct()
            return x.copy(needsReview = x.needsReview + (Field.CODE to code))
        }

        /** ARGB bitmap -> BGR bytes, row by row (no full-size IntArray copy). */
        fun toBgr(b: Bitmap): BgrImage {
            val w = b.width
            val h = b.height
            val out = ByteArray(w * h * 3)
            val row = IntArray(w)
            for (y in 0 until h) {
                b.getPixels(row, 0, w, 0, y, w, 1)
                var o = y * w * 3
                for (p in row) {
                    out[o] = p.toByte(); out[o + 1] = (p shr 8).toByte(); out[o + 2] = (p shr 16).toByte()
                    o += 3
                }
            }
            return BgrImage(w, h, out)
        }
    }
}
