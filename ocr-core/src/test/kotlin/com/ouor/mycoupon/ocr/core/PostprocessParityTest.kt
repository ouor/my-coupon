package com.ouor.mycoupon.ocr.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Python (pp-ocr-v5/postprocess.py) vs Kotlin [Postprocess] on the same OCR lines.
 * Fixtures: .test/core-parity/(dump).jsonl from tools/ocr-bench/make_parity.py (skipped when absent).
 * Compares rows, dropped lines, barcode and expiry for every image of every dump.
 */
class PostprocessParityTest {
    private val dir = File(System.getProperty("mycoupon.testData") ?: ".test", "core-parity")

    @Test
    fun matchesPythonReference() {
        val files = dir.listFiles { f -> f.name.endsWith(".jsonl") }?.sorted().orEmpty()
        assumeTrue("no parity fixtures in $dir", files.isNotEmpty())
        var fields = 0
        val mismatches = mutableListOf<String>()
        for (f in files) {
            f.readLines(Charsets.UTF_8).filter { it.isNotBlank() }.forEachIndexed { n, line ->
                val o = Json.parseToJsonElement(line).jsonObject
                val got = Postprocess.process(parseLines(o["lines"]!!.jsonArray))
                val exp = o["expected"]!!.jsonObject
                fun check(name: String, want: Any?, have: Any?) {
                    fields++
                    // never print values: they contain real coupon numbers
                    if (want != have) mismatches += "${f.name}#$n $name"
                }
                check("rows", exp["rows"]!!.jsonArray.map { it.jsonPrimitive.content }, got.rows)
                check("dropped", exp["dropped"]!!.jsonArray.map { it.jsonPrimitive.content }, got.dropped)
                check("barcode", exp["barcode"].str(), got.barcode)
                check("expiry", exp["expiry"].str(), got.expiry?.date?.toString())
            }
        }
        println("postprocess parity: ${fields - mismatches.size}/$fields fields match over ${files.size} dumps")
        assertEquals(mismatches.joinToString("\n"), 0, mismatches.size)
    }

    companion object {
        fun parseLines(a: JsonArray) = a.map { e ->
            val l = e.jsonObject
            OcrLine(
                l["text"]!!.jsonPrimitive.content,
                l["score"]!!.jsonPrimitive.float,
                l["poly"]!!.jsonArray.map { p -> Point(p.jsonArray[0].jsonPrimitive.int, p.jsonArray[1].jsonPrimitive.int) },
            )
        }

        private fun kotlinx.serialization.json.JsonElement?.str() =
            if (this == null || this is JsonNull) null else jsonPrimitive.content
    }
}
