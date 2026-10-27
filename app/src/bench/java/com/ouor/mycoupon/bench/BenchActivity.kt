package com.ouor.mycoupon.bench

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import com.ouor.mycoupon.data.ImageStore
import com.ouor.mycoupon.ocr.engine.Barcodes
import com.ouor.mycoupon.ocr.CouponRecognizer
import com.ouor.mycoupon.ocr.core.Box
import com.ouor.mycoupon.ocr.core.DecodedBarcode
import com.ouor.mycoupon.ocr.core.Extract
import com.ouor.mycoupon.ocr.core.Extraction
import com.ouor.mycoupon.ocr.core.OcrLine
import com.ouor.mycoupon.ocr.core.Point
import com.ouor.mycoupon.ocr.engine.PaddleOcrEngine
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.concurrent.thread

/**
 * OCR benchmark, `bench` build type only (docs/core/ocr-runtime.md). Not in debug or release builds.
 *   adb -s d418f2d1 push <images> /sdcard/Android/data/com.ouor.mycoupon/files/bench/coupon-image/
 *   adb -s d418f2d1 shell am start -n com.ouor.mycoupon/.bench.BenchActivity --ei maxSide 4000 --ei threads 4 --ei runs 1
 * Writes files/bench/out-<maxSide>-<threads>.jsonl (lines per image, same shape as tools/ocr-bench dumps) and a
 * summary line in logcat (tag BENCH): only timings and counts, never text.
 */
class BenchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val maxSide = intent.getIntExtra("maxSide", CouponRecognizer.MAX_SIDE)
        val threads = intent.getIntExtra("threads", CouponRecognizer.THREADS)
        val runs = intent.getIntExtra("runs", 1)
        val sub = intent.getStringExtra("dir") ?: "coupon-image"
        val root = File(getExternalFilesDir(null), "bench")
        thread(name = "bench") {
            try {
                if (intent.getBooleanExtra("golden", false)) golden(root) else run(root, sub, maxSide, threads, runs)
            } catch (e: Throwable) {
                Log.e(TAG, "failed", e)
            }
            runOnUiThread { finish() }
        }
    }

    private fun run(root: File, sub: String, maxSide: Int, threads: Int, runs: Int) {
        val files = File(root, sub).listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }
        val rss0 = rss()
        val t0 = SystemClock.elapsedRealtime()
        val engine = CouponRecognizer.create(
            this,
            PaddleOcrEngine.Options(threads = threads, maxSide = maxSide, arena = intent.getBooleanExtra("arena", false), shrinkArena = intent.getBooleanExtra("shrink", true)),
        )
        val loadMs = SystemClock.elapsedRealtime() - t0
        Log.i(TAG, "rss before load=$rss0 after load=${rss()}")
        val totals = ArrayList<Long>()
        val out = File(root, "out-$maxSide-$threads.jsonl")
        out.bufferedWriter().use { w ->
            for (r in 0 until runs) for (f in files) {
                val a = SystemClock.elapsedRealtime()
                val bytes = f.readBytes()
                val bmp = ImageStore.decode(bytes) ?: continue
                val img = CouponRecognizer.toBgr(bmp)
                bmp.recycle()
                val b = SystemClock.elapsedRealtime()
                val bars = Barcodes.decode(img)
                val c = SystemClock.elapsedRealtime()
                if (intent.getBooleanExtra("stages", false) && r == 0 && totals.size < 3) {
                    Log.i(TAG, "stage img=${totals.size} after decode+barcode ${hwm()}")
                    engine.detect(img)
                    Log.i(TAG, "stage img=${totals.size} after det ${hwm()}")
                }
                val lines = engine.recognize(img)
                if (intent.getBooleanExtra("stages", false) && r == 0 && totals.size < 3) Log.i(TAG, "stage img=${totals.size} after det+rec ${hwm()}")
                val d = SystemClock.elapsedRealtime()
                val x = Extract.extract(lines, bars, img.width, img.height)
                val e = SystemClock.elapsedRealtime()
                totals += e - a
                if (r == runs - 1) {
                    val o = JSONObject()
                    o.put("file", "$sub/${f.name}")
                    o.put("ms", (d - c).toDouble())
                    o.put("t", JSONObject().put("decode", b - a).put("barcode", c - b).put("det", engine.lastTimings?.detMs).put("rec", engine.lastTimings?.recMs).put("extract", e - d).put("total", e - a))
                    o.put("lines", JSONArray(lines.map { l ->
                        JSONObject().put("text", l.text).put("score", l.score).put("poly", JSONArray(l.poly.map { JSONArray(listOf(it.x, it.y)) }))
                    }))
                    o.put("barcodes", JSONArray(bars.map { JSONObject().put("value", it.value).put("format", it.format).put("box", JSONArray(listOf(it.box.x0, it.box.y0, it.box.x1, it.box.y1))) }))
                    o.put("mem", File("/proc/self/status").readLines().filter { it.startsWith("VmHWM") || it.startsWith("VmRSS") }.joinToString(" ") { it.split(Regex("[ \t]+")).joinToString(" ") })
                    o.put("isCoupon", x.isCoupon).put("code", x.code).put("expiryDate", x.expiryDate?.toString())
                    w.write(o.toString()); w.newLine()
                }
            }
        }
        engine.close()
        val s = totals.sorted()
        val hwm = File("/proc/self/status").readLines().firstOrNull { it.startsWith("VmHWM") }?.trim()
        Log.i(TAG, "maxSide=$maxSide threads=$threads n=${s.size} load=${loadMs}ms median=${s.getOrNull(s.size / 2)}ms " +
            "p90=${s.getOrNull((s.size * 9) / 10)}ms max=${s.lastOrNull()}ms $hwm")
    }

    /** Extract on device (ICU regex) vs the goldens: writes files/bench/golden-out.jsonl for tools/ocr-bench/golden_device.py. */
    private fun golden(root: File) {
        val files = File(root, "golden").listFiles().orEmpty().filter { it.name.endsWith(".json") }.sortedBy { it.name }
        File(root, "golden-out.jsonl").bufferedWriter().use { w ->
            for (f in files) {
                val inp = JSONObject(f.readText()).getJSONObject("input")
                val la = inp.getJSONArray("lines")
                val lines = (0 until la.length()).map { i ->
                    val o = la.getJSONObject(i)
                    val p = o.getJSONArray("poly")
                    OcrLine(o.getString("text"), o.getDouble("score"), (0 until p.length()).map { Point(p.getJSONArray(it).getInt(0), p.getJSONArray(it).getInt(1)) })
                }
                val ba = inp.optJSONArray("barcodes") ?: JSONArray()
                val bars = (0 until ba.length()).map { i ->
                    val o = ba.getJSONObject(i)
                    val pts = o.getJSONArray("box")
                    val xs = (0 until pts.length()).map { pts.getJSONArray(it).getDouble(0) }
                    val ys = (0 until pts.length()).map { pts.getJSONArray(it).getDouble(1) }
                    DecodedBarcode(o.getString("value"), o.getString("format"), Box.around(xs, ys))
                }
                val x = Extract.extract(lines, bars, inp.getInt("width"), inp.getInt("height"))
                w.write(JSONObject().put("file", f.name).put("got", toJson(x)).toString()); w.newLine()
            }
        }
        Log.i(TAG, "golden n=${files.size}")
    }

    private fun box(b: Box) = JSONArray(listOf(b.x0, b.y0, b.x1, b.y1))

    private fun toJson(x: Extraction) = JSONObject()
        .put("version", x.version).put("isCoupon", x.isCoupon).put("brand", x.brand ?: JSONObject.NULL)
        .put("productName", x.productName ?: JSONObject.NULL).put("type", x.type.name).put("faceValue", x.faceValue ?: JSONObject.NULL)
        .put("code", x.code ?: JSONObject.NULL).put("codeFormat", x.codeFormat ?: JSONObject.NULL).put("codeSource", x.codeSource?.name ?: JSONObject.NULL)
        .put("expiryDate", x.expiryDate?.toString() ?: JSONObject.NULL).put("expiryCandidates", JSONArray(x.expiryCandidates.map { it.toString() }))
        .put("issuer", x.issuer ?: JSONObject.NULL).put("sender", x.sender ?: JSONObject.NULL).put("notes", x.notes ?: JSONObject.NULL)
        .put("needsReview", JSONObject().apply { x.needsReview.forEach { (k, v) -> put(k.key, JSONArray(v.map { it.name })) } })
        .put("fieldBoxes", JSONObject().apply { x.fieldBoxes.forEach { (k, v) -> put(k.key, box(v)) } })
        .put("rows", JSONArray(x.rows.map { JSONObject().put("text", it.text).put("box", box(it.box)).put("notes", it.notes) }))

    private fun hwm() = File("/proc/self/status").readLines().filter { it.startsWith("VmHWM") || it.startsWith("VmRSS") }.joinToString(" ") { it.split(Regex("[ 	]+")).joinToString(" ") }

    private fun rss() = File("/proc/self/status").readLines().firstOrNull { it.startsWith("VmRSS") }?.substringAfter(":")?.trim()

    private companion object {
        const val TAG = "BENCH"
    }
}
