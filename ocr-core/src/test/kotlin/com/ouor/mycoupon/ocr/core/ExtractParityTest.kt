package com.ouor.mycoupon.ocr.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
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

/**
 * Extract (Kotlin) vs ocr-lab/extract.py on the goldens: every key, rows included.
 * Synthetic goldens (ocr-lab/tests/golden, in git) may be printed; real ones (.test/golden) hold real coupon
 * numbers, so only their indices and differing key names are printed.
 */
class ExtractParityTest {
    @Test
    fun synthetic() {
        val dir = File(System.getProperty("mycoupon.golden"))
        val files = dir.listFiles { f -> f.name.endsWith(".json") }!!.sorted()
        assertEquals(12, files.size)
        val fails = files.filter { f -> diff(f).also { if (it.isNotEmpty()) println("FAIL ${f.name}: $it") }.isNotEmpty() }
        assertTrue("${fails.size} synthetic goldens differ", fails.isEmpty())
    }

    @Test
    fun real() {
        val dir = File(System.getProperty("mycoupon.testData"), "golden")
        val files = dir.listFiles { f -> f.name.endsWith(".json") }?.sorted().orEmpty()
        // skipped (not passed) without .test; -Pmycoupon.requireRealGoldens makes a missing set a failure (code review A-07/O-16)
        if (System.getProperty("mycoupon.requireRealGoldens") == "true") assertTrue("real goldens missing: ${dir.path}", files.isNotEmpty())
        assumeTrue("real goldens missing: ${dir.path}", files.isNotEmpty())
        var bad = 0
        files.forEachIndexed { i, f ->
            val keys = diff(f).keys
            if (keys.isNotEmpty()) { bad++; println("FAIL #$i keys=$keys") }
        }
        println("real goldens: ${files.size - bad}/${files.size} identical")
        assertEquals(0, bad)
    }

    /** key -> (expected, got) for every top-level key that differs. */
    private fun diff(f: File): Map<String, Pair<JsonElement?, JsonElement?>> {
        val g = Json.parseToJsonElement(f.readText()).jsonObject
        val inp = g["input"]!!.jsonObject
        val lines = inp["lines"]!!.jsonArray.map { l ->
            val o = l.jsonObject
            OcrLine(o["text"]!!.jsonPrimitive.content, o["score"]!!.jsonPrimitive.double,
                o["poly"]!!.jsonArray.map { p -> Point(p.jsonArray[0].jsonPrimitive.int, p.jsonArray[1].jsonPrimitive.int) })
        }
        val bars = (inp["barcodes"] as? JsonArray).orEmpty().map { b ->
            val o = b.jsonObject
            val pts = o["box"]!!.jsonArray.map { p -> p.jsonArray.map { it.jsonPrimitive.double } }
            DecodedBarcode(o["value"]!!.jsonPrimitive.content, o["format"]!!.jsonPrimitive.content,
                Box.around(pts.map { it[0] }, pts.map { it[1] }))
        }
        val got = norm(toJson(Extract.extract(lines, bars, inp["width"]!!.jsonPrimitive.int, inp["height"]!!.jsonPrimitive.int))).jsonObject
        val want = norm(g["expected"]!!).jsonObject
        return (want.keys + got.keys).filter { want[it] != got[it] }.associateWith { want[it] to got[it] }
    }

    /** numbers compared as doubles (Python mixes int and float boxes) */
    private fun norm(e: JsonElement): JsonElement = when (e) {
        is JsonObject -> JsonObject(e.mapValues { norm(it.value) })
        is JsonArray -> JsonArray(e.map { norm(it) })
        is JsonPrimitive -> if (!e.isString && e.doubleOrNull != null) JsonPrimitive(e.double) else e
        JsonNull -> e
    }

    private fun box(b: Box) = JsonArray(listOf(b.x0, b.y0, b.x1, b.y1).map { JsonPrimitive(it) })

    private fun toJson(x: Extraction): JsonElement = buildJsonObject {
        put("version", x.version)
        put("isCoupon", x.isCoupon)
        put("brand", x.brand)
        put("productName", x.productName)
        put("type", x.type.name)
        put("faceValue", x.faceValue)
        put("code", x.code)
        put("codeFormat", x.codeFormat)
        put("codeSource", x.codeSource?.name)
        put("expiryDate", x.expiryDate?.toString())
        put("expiryCandidates", JsonArray(x.expiryCandidates.map { JsonPrimitive(it.toString()) }))
        put("issuer", x.issuer)
        put("sender", x.sender)
        put("notes", x.notes)
        put("needsReview", JsonObject(x.needsReview.entries.associate { (k, v) -> k.key to JsonArray(v.map { JsonPrimitive(it.name) }) }))
        put("fieldBoxes", JsonObject(x.fieldBoxes.entries.associate { (k, v) -> k.key to box(v) }))
        put("rows", JsonArray(x.rows.map { r ->
            buildJsonObject { put("text", r.text); put("box", box(r.box)); put("notes", r.notes) }
        }))
    }
}
