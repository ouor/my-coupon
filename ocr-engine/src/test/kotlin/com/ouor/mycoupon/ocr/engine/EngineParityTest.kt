package com.ouor.mycoupon.ocr.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/**
 * Kotlin engine (JVM ONNX Runtime) vs the Python ONNX twin (tools/ocr-bench/onnx_ocr.py) on .test images.
 * Writes build/parity/kotlin-jvm.jsonl (same shape as run_ocr.py output) for tools/ocr-bench/compare.py.
 * Skipped when .test or the Python dump (tools/ocr-bench/out/onnx-py.jsonl) is missing.
 */
class EngineParityTest {
    private val data = File(System.getProperty("mycoupon.testData") ?: ".test")
    private val models = File(System.getProperty("mycoupon.models") ?: "app/src/main/assets/ocr")
    private val ref = File(data.parentFile, "tools/ocr-bench/out/onnx-py.jsonl")

    @Test
    fun matchesPythonOnnx() {
        assumeTrue("missing data/ref/models: $data $ref $models", File(data, "labels.jsonl").exists() && ref.exists() && File(models, "det.onnx").exists())
        val limit = System.getProperty("mycoupon.parityLimit")?.toIntOrNull() ?: Int.MAX_VALUE
        val engine = PaddleOcrEngine(
            File(models, "det.onnx").readBytes(), File(models, "rec.onnx").readBytes(),
            File(models, "dict.txt").readLines(Charsets.UTF_8).filter { it.isNotEmpty() },
        )
        val refLines = ref.readLines(Charsets.UTF_8).filter { it.isNotBlank() }.associate {
            val o = Json.parseToJsonElement(it).jsonObject
            o["file"]!!.jsonPrimitive.content to o["lines"]!!.jsonArray.map { l -> l.jsonObject["text"]!!.jsonPrimitive.content }
        }
        val out = File("build/parity/kotlin-jvm.jsonl").apply { parentFile.mkdirs() }
        var total = 0
        var same = 0
        var images = 0
        var identical = 0
        out.bufferedWriter(Charsets.UTF_8).use { w ->
            // coupons only: camera photos among non-coupons carry EXIF rotation that ImageIO ignores (cv2 and the app apply it)
            val coupons = File(data, "labels.jsonl").readLines(Charsets.UTF_8).filter { it.isNotBlank() }
                .map { Json.parseToJsonElement(it).jsonObject }
                .filter { it["is_coupon"]!!.jsonPrimitive.content == "true" }.map { it["file"]!!.jsonPrimitive.content }.toSet()
            for (name in refLines.keys.filter { it in coupons }.take(limit)) {
                val bi = ImageIO.read(File(data, name)) ?: continue
                val px = IntArray(bi.width * bi.height)
                bi.getRGB(0, 0, bi.width, bi.height, px, 0, bi.width)
                val img = BgrImage.fromArgb(px, bi.width, bi.height)
                val t = System.nanoTime()
                val lines = engine.recognize(img)
                val ms = (System.nanoTime() - t) / 1e6
                w.write(buildJsonObject {
                    put("file", name)
                    put("ms", ms)
                    put("lines", buildJsonArray {
                        lines.forEach { l ->
                            add(buildJsonObject {
                                put("text", l.text); put("score", l.score)
                                put("poly", buildJsonArray { l.poly.forEach { p -> add(buildJsonArray { add(Json.parseToJsonElement("${p.x}")); add(Json.parseToJsonElement("${p.y}")) }) } })
                            })
                        }
                    })
                }.toString() + "\n")
                val want = refLines[name]!!
                val have = lines.map { it.text }.toMutableList()
                images++
                if (want == lines.map { it.text }) identical++
                for (t2 in want) {
                    total++
                    if (have.remove(t2)) same++
                }
            }
        }
        engine.close()
        val rate = same.toDouble() / total
        println("engine parity: text $same/$total = ${"%.2f".format(rate * 100)}%, identical images $identical/$images -> ${out.absolutePath}")
        assertTrue("text agreement $rate", rate >= 0.99)
    }
}
