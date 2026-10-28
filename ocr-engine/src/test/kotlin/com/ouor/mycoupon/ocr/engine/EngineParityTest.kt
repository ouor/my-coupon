package com.ouor.mycoupon.ocr.engine

import com.ouor.mycoupon.ocr.core.OcrLine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * Kotlin engine (JVM ONNX Runtime) vs the Python ONNX twin (tools/ocr-bench/onnx_ocr.py) on .test coupon images, at the
 * PaddleX default (max side 4000) and at the app setting (1280, code review O-05). Besides text it checks what Extract
 * reads: line order, boxes (±1 px) and scores (the twin dump rounds to 3 decimals). Each count must stay at or above the
 * recorded baseline. Prints counts only, never text. Writes build/parity/kotlin-jvm-<side>.jsonl for tools/ocr-bench/compare.py.
 * Skipped (reported as skipped, not passed) when .test or a twin dump is missing.
 */
class EngineParityTest {
    private val data = File(System.getProperty("mycoupon.testData") ?: ".test")
    private val models = File(System.getProperty("mycoupon.models") ?: "app/src/main/assets/ocr")
    private val out = File(data.parentFile, "tools/ocr-bench/out")

    private data class Counts(val texts: Int, val textsSame: Int, val images: Int, val imagesIdentical: Int, val lines: Int, val boxOk: Int, val scoreOk: Int)

    @Test
    fun matchesPythonOnnx4000() {
        val c = run(4000, File(out, "onnx-py.jsonl"))
        assertEquals("all texts", c.texts, c.textsSame)
        assertEquals("identical images", c.images, c.imagesIdentical)
        assertTrue("boxes $c", c.boxOk >= c.lines)
        assertTrue("scores $c", c.scoreOk >= SCORES_4000)
    }

    /** The setting the app runs with (CouponRecognizer.MAX_SIDE). Baseline measured 2026-10-28. */
    @Test
    fun matchesPythonOnnx1280() {
        val c = run(1280, File(out, "onnx-py-1280.jsonl"))
        assertTrue("texts $c", c.textsSame >= c.texts - 1)
        assertTrue("identical images $c", c.imagesIdentical >= c.images - 1)
        assertTrue("boxes $c", c.boxOk >= c.lines - 1)
        assertTrue("scores $c", c.scoreOk >= SCORES_1280)
    }

    private companion object {
        // Lines whose score matches the twin within 0.0006 on the 56 .test coupons (baseline 2026-10-28, same before and after
        // the direct-buffer change). The rest differ by up to ~0.05 from ORT Java vs Python kernels; Extract goldens cover the effect.
        const val SCORES_4000 = 557
        const val SCORES_1280 = 545
    }

    private fun run(maxSide: Int, ref: File): Counts {
        assumeTrue("missing data/ref/models: $data $ref $models", File(data, "labels.jsonl").exists() && ref.exists() && File(models, "det.onnx").exists())
        val limit = System.getProperty("mycoupon.parityLimit")?.toIntOrNull() ?: Int.MAX_VALUE
        val engine = PaddleOcrEngine(
            File(models, "det.onnx").path, File(models, "rec.onnx").path,
            File(models, "dict.txt").readLines(Charsets.UTF_8).filter { it.isNotEmpty() },
            PaddleOcrEngine.Options(maxSide = maxSide),
        )
        val refLines: Map<String, List<JsonObject>> = ref.readLines(Charsets.UTF_8).filter { it.isNotBlank() }.associate {
            val o = Json.parseToJsonElement(it).jsonObject
            o["file"]!!.jsonPrimitive.content to o["lines"]!!.jsonArray.map { l -> l.jsonObject }
        }
        val dump = File("build/parity/kotlin-jvm-$maxSide.jsonl").apply { parentFile.mkdirs() }
        var texts = 0; var textsSame = 0; var images = 0; var identical = 0; var lines = 0; var boxOk = 0; var scoreOk = 0
        dump.bufferedWriter(Charsets.UTF_8).use { w ->
            // coupons only: camera photos among non-coupons carry EXIF rotation that ImageIO ignores (cv2 and the app apply it)
            val coupons = File(data, "labels.jsonl").readLines(Charsets.UTF_8).filter { it.isNotBlank() }
                .map { Json.parseToJsonElement(it).jsonObject }
                .filter { it["is_coupon"]!!.jsonPrimitive.content == "true" }.map { it["file"]!!.jsonPrimitive.content }.toSet()
            for (name in refLines.keys.filter { it in coupons }.take(limit)) {
                val bi = ImageIO.read(File(data, name)) ?: continue
                val px = IntArray(bi.width * bi.height)
                bi.getRGB(0, 0, bi.width, bi.height, px, 0, bi.width)
                val got: List<OcrLine> = engine.recognize(BgrImage.fromArgb(px, bi.width, bi.height))
                w.write(buildJsonObject {
                    put("file", name)
                    put("lines", buildJsonArray {
                        got.forEach { l ->
                            add(buildJsonObject {
                                put("text", l.text); put("score", l.score)
                                put("poly", buildJsonArray { l.poly.forEach { p -> add(buildJsonArray { add(Json.parseToJsonElement("${p.x}")); add(Json.parseToJsonElement("${p.y}")) }) } })
                            })
                        }
                    })
                }.toString() + "\n")
                val want = refLines[name]!!
                images++
                if (want.map { it["text"]!!.jsonPrimitive.content } == got.map { it.text }) identical++
                val have = got.map { it.text }.toMutableList()
                for (l in want) {
                    texts++
                    if (have.remove(l["text"]!!.jsonPrimitive.content)) textsSame++
                }
                for ((i, l) in want.withIndex()) {
                    lines++
                    val g = got.getOrNull(i) ?: continue
                    if (g.text != l["text"]!!.jsonPrimitive.content) continue
                    val poly = l["poly"]!!.jsonArray.map { p -> p.jsonArray.let { it[0].jsonPrimitive.int to it[1].jsonPrimitive.int } }
                    if (poly.size == g.poly.size && poly.zip(g.poly).all { (a, b) -> abs(a.first - b.x) <= 1 && abs(a.second - b.y) <= 1 }) boxOk++
                    if (abs(l["score"]!!.jsonPrimitive.double - g.score) <= 0.0006) scoreOk++
                }
            }
        }
        engine.close()
        val c = Counts(texts, textsSame, images, identical, lines, boxOk, scoreOk)
        println("engine parity @$maxSide: texts $textsSame/$texts, identical images $identical/$images, boxes $boxOk/$lines, scores $scoreOk/$lines")
        return c
    }
}
