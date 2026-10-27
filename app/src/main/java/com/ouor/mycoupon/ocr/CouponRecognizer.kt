package com.ouor.mycoupon.ocr

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.ouor.mycoupon.BuildConfig
import com.ouor.mycoupon.data.ImageStore
import com.ouor.mycoupon.ocr.core.Box
import com.ouor.mycoupon.ocr.core.CodeSource
import com.ouor.mycoupon.ocr.core.DecodedBarcode
import com.ouor.mycoupon.ocr.core.Extract
import com.ouor.mycoupon.ocr.core.Extraction
import com.ouor.mycoupon.ocr.core.Field
import com.ouor.mycoupon.ocr.core.OcrLine
import com.ouor.mycoupon.ocr.core.ReviewReason
import com.ouor.mycoupon.ocr.engine.BgrImage
import com.ouor.mycoupon.ocr.engine.PaddleOcrEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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

data class Timings(val decodeMs: Long, val barcodeMs: Long, val detMs: Long, val recMs: Long, val extractMs: Long, val lines: Int) {
    val totalMs get() = decodeMs + barcodeMs + detMs + recMs + extractMs
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
        mutex.withLock {
            val t0 = SystemClock.elapsedRealtime()
            val bmp = ImageStore.decode(bytes) ?: return@withLock null
            val img = toBgr(bmp)
            bmp.recycle()
            val t1 = SystemClock.elapsedRealtime()
            val barcodes = Barcodes.decode(img)
            val t2 = SystemClock.elapsedRealtime()
            val e = engine()
            val lines: List<OcrLine> = e.recognize(img)
            val et = e.lastTimings
            val t3 = SystemClock.elapsedRealtime()
            val x = withAppRules(Extract.extract(lines, barcodes, img.width, img.height))
            val t4 = SystemClock.elapsedRealtime()
            Recognition(x, img.width, img.height,
                Timings(t1 - t0, t2 - t1, et?.detMs ?: 0, et?.recMs ?: (t3 - t2), t4 - t3, lines.size))
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
            val version = BuildConfig.VERSION_CODE.toString() + "-" + BuildConfig.BUILD_TYPE
            if (!stamp.exists() || stamp.readText() != version) {
                for (name in listOf("det.onnx", "rec.onnx")) {
                    val tmp = File(dir, "$name.tmp")
                    context.assets.open("ocr/$name").use { i -> tmp.outputStream().use { i.copyTo(it) } }
                    tmp.renameTo(File(dir, name))
                }
                stamp.writeText(version)
            }
            return PaddleOcrEngine(
                File(dir, "det.onnx").path, File(dir, "rec.onnx").path,
                context.assets.open("ocr/dict.txt").bufferedReader().readLines(), options,
            )
        }

        const val THREADS = 4
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

/** ZXing (on device, no network). */
object Barcodes {
    private val hints = mapOf(
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.POSSIBLE_FORMATS to BarcodeFormat.entries.filter { it != BarcodeFormat.UPC_EAN_EXTENSION && it != BarcodeFormat.RSS_EXPANDED },
    )
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
    fun quickFind(gray: ByteArray, w: Int, h: Int): Boolean = decodeGray(gray, w, h, 1f, fastHints) != null

    private fun decodeGray(g: ByteArray, w: Int, h: Int, scale: Float, hints: Map<DecodeHintType, Any?>): DecodedBarcode? {
        val src = PlanarYUVLuminanceSource(g, w, h, 0, 0, w, h, false)
        val reader = MultiFormatReader()
        return try {
            val r = reader.decode(BinaryBitmap(HybridBinarizer(src)), hints)
            val pts = r.resultPoints.orEmpty().filterNotNull()
            val box = if (pts.isEmpty()) Box(0.0, 0.0, w * scale.toDouble(), h * scale.toDouble())
            else Box.around(pts.map { it.x * scale.toDouble() }, pts.map { it.y * scale.toDouble() })
            DecodedBarcode(r.text, r.barcodeFormat.name, box)
        } catch (_: NotFoundException) {
            null
        } catch (_: ReaderException) {
            null
        } finally {
            reader.reset()
        }
    }
}
