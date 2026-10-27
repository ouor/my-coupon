package com.ouor.mycoupon.ocr.engine

import com.ouor.mycoupon.ocr.core.Box
import com.ouor.mycoupon.ocr.core.CodeSource
import com.ouor.mycoupon.ocr.core.Extract
import com.ouor.mycoupon.ocr.core.Field
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/**
 * The app's own path end to end (code review O-06): Kotlin engine at the app setting (1280) + ZXing-Java [Barcodes] ->
 * Extract, scored against .test/labels.jsonl like ocr-lab/score.py. The goldens feed PC zxing-cpp barcodes instead, so
 * only this test sees ZXing-Java's formats and boxes. Each count must stay at or above the baseline. Prints counts only
 * (holdout rule: no per-image output). Skipped when .test is missing.
 */
class AppPathTest {
    private val data = File(System.getProperty("mycoupon.testData") ?: ".test")
    private val models = File(System.getProperty("mycoupon.models") ?: "app/src/main/assets/ocr")
    private val nonWord = Regex("(?U)[\\W_]+")
    private fun norm(s: String?) = s?.let { nonWord.replace(it, "").lowercase() }

    @Test
    fun scoresAtBaseline() {
        assumeTrue("missing .test or models", File(data, "labels.jsonl").exists() && File(models, "det.onnx").exists())
        val engine = PaddleOcrEngine(
            File(models, "det.onnx").path, File(models, "rec.onnx").path,
            File(models, "dict.txt").readLines(Charsets.UTF_8).filter { it.isNotEmpty() },
            PaddleOcrEngine.Options(maxSide = 1280),
        )
        val labels = File(data, "labels.jsonl").readLines(Charsets.UTF_8).filter { it.isNotBlank() }
            .map { Json.parseToJsonElement(it).jsonObject }.filter { it.str("is_coupon") == "true" }
        // PC zxing-cpp boxes (what the goldens use): the device box should cover the same bars (O-07)
        val pc = File(data, "zxing.jsonl").takeIf { it.exists() }?.readLines(Charsets.UTF_8).orEmpty().filter { it.isNotBlank() }.associate { l ->
            val o = Json.parseToJsonElement(l).jsonObject
            o.str("file")!! to o["hits"]!!.jsonArray.firstOrNull()?.jsonObject?.let { h ->
                val s = h["scale"]?.jsonPrimitive?.double ?: 1.0
                val pts = h["box"]!!.jsonArray.map { p -> p.jsonArray.map { it.jsonPrimitive.double / s } }
                Box.around(pts.map { it[0] }, pts.map { it[1] })
            }
        }
        val c = sortedMapOf<String, Int>()
        fun inc(k: String, ok: Boolean = true) { if (ok) c[k] = (c[k] ?: 0) + 1 }
        for (lab in labels) {
            val bi = ImageIO.read(File(data, lab.str("file")!!)) ?: continue
            val px = IntArray(bi.width * bi.height)
            bi.getRGB(0, 0, bi.width, bi.height, px, 0, bi.width)
            val img = BgrImage.fromArgb(px, bi.width, bi.height)
            val bars = Barcodes.decode(img)
            val x = Extract.extract(engine.recognize(img), bars, img.width, img.height)
            inc("images")
            inc("coupon", x.isCoupon)
            inc("decoded", x.codeSource == CodeSource.DECODED)
            inc("code box tall", x.fieldBoxes[Field.CODE]?.let { it.y1 - it.y0 >= 8 } == true && x.codeSource == CodeSource.DECODED)
            val pcBox = pc[lab.str("file")]
            inc("box like PC", pcBox != null && bars.isNotEmpty() && iou(bars[0].box, pcBox) >= 0.5)
            inc("expiry", x.expiryDate?.toString() == lab.str("expiry_date"))
            inc("code", x.code?.replace(Regex("[^0-9A-Za-z]"), "") == lab.str("barcode_value"))
            inc("brand", x.brand != null && norm(x.brand) == norm(lab.str("brand")))
            inc("product", x.productName != null && norm(x.productName) == norm(lab.str("product_name")))
            inc("type", x.type.name == lab.str("coupon_type")?.uppercase())
            if (lab.str("coupon_type") == "amount") { inc("face_n"); inc("face", x.faceValue == (lab["face_value"] as? JsonPrimitive)?.longOrNull) }
        }
        engine.close()
        println("app path @1280: $c")
        val low = BASELINE.filter { (k, v) -> (c[k] ?: 0) < v }
        assertTrue("below baseline: $low (got $c)", low.isEmpty())
    }

    private fun iou(a: Box, b: Box): Double {
        val w = minOf(a.x1, b.x1) - maxOf(a.x0, b.x0)
        val h = minOf(a.y1, b.y1) - maxOf(a.y0, b.y0)
        if (w <= 0 || h <= 0) return 0.0
        val i = w * h
        return i / ((a.x1 - a.x0) * (a.y1 - a.y0) + (b.x1 - b.x0) * (b.y1 - b.y0) - i)
    }

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString || it.content != "null" }?.content

    private companion object {
        /** Measured 2026-10-28 on the 56 .test coupons (extract v3: product 54 -> 56). */
        val BASELINE = mapOf(
            "images" to 56, "coupon" to 56, "decoded" to 52, "code box tall" to 52, "box like PC" to 52,
            "expiry" to 56, "code" to 56, "brand" to 56, "product" to 56, "type" to 56, "face" to 5,
        )
    }
}
