package com.ouor.mycoupon.ocr.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.ouor.mycoupon.ocr.core.OcrLine
import com.ouor.mycoupon.ocr.core.Point
import java.nio.FloatBuffer
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max

/**
 * PP-OCRv5 mobile det + korean mobile rec on ONNX Runtime, same steps as the PaddleX 3.x OCR pipeline
 * (OCR.yaml: limit_side_len 64/min, max_side_limit, DB thresh 0.3 / box 0.6 / unclip 1.5,
 * rec batch 6 sorted by aspect ratio, CTC greedy decode). Python twin: tools/ocr-bench/onnx_ocr.py.
 *
 * Not thread-safe; one call at a time.
 */
class PaddleOcrEngine private constructor(
    detSession: (OrtEnvironment, OrtSession.SessionOptions) -> OrtSession,
    recSession: (OrtEnvironment, OrtSession.SessionOptions) -> OrtSession,
    dict: List<String>,
    private val options: Options,
) : AutoCloseable {
    constructor(detModel: ByteArray, recModel: ByteArray, dict: List<String>, options: Options = Options()) :
        this({ e, o -> e.createSession(detModel, o) }, { e, o -> e.createSession(recModel, o) }, dict, options)

    /** From model files: no Java-heap copy of the weights (lower peak memory on the phone). */
    constructor(detPath: String, recPath: String, dict: List<String>, options: Options = Options()) :
        this({ e, o -> e.createSession(detPath, o) }, { e, o -> e.createSession(recPath, o) }, dict, options)

    data class Options(
        val threads: Int = 4,
        /** Longest side fed to the detector (PaddleX max_side_limit). */
        val maxSide: Int = 4000,
        val recBatch: Int = 6,
        /**
         * ORT CPU memory arena. Every image has a different input shape, so a plain arena only grows (1.3 GB peak RSS
         * on the POCO); with [shrinkArena] it is returned after each run but still peaks higher than no arena
         * (POCO, max side 1280: no arena 428 MB / 908 ms median, arena+shrink 557 MB / 813 ms). Off by default.
         */
        val arena: Boolean = false,
        val shrinkArena: Boolean = true,
    )

    /** [recMs] includes [cropMs] (perspective crops of the det boxes) */
    data class Timings(val detMs: Long, val recMs: Long, val lines: Int, val cropMs: Long = 0)

    private val env = OrtEnvironment.getEnvironment().apply { runCatching { setTelemetry(false) } } // no telemetry, ever (code review S-01)
    private val det: OrtSession
    private val rec: OrtSession
    private val chars: List<String> = listOf("blank") + dict + " "

    private val runOptions = OrtSession.RunOptions().apply {
        if (options.arena && options.shrinkArena) addRunConfigEntry("memory.enable_memory_arena_shrinkage", "cpu:0")
    }

    @Volatile
    var lastTimings: Timings? = null
        private set

    init {
        val so = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(options.threads)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setCPUArenaAllocator(options.arena)
            setMemoryPatternOptimization(false) // shapes change every call
        }
        det = detSession(env, so)
        rec = try {
            recSession(env, so)
        } catch (e: Exception) {
            det.close() // no leaked det session when rec fails (code review O-04)
            throw e
        } finally {
            so.close()
        }
        val classes = (rec.outputInfo.values.first().info as ai.onnxruntime.TensorInfo).shape.last()
        if (classes != chars.size.toLong()) {
            close()
            throw IllegalStateException("rec model has $classes classes, dict gives ${chars.size}") // O-11: wrong dict = silently wrong text
        }
        if (!rec.outputNames.containsAll(listOf(BEST, BEST_P))) {
            close()
            throw IllegalStateException("rec model lacks the best/best_p outputs (tools/rec_argmax.py)")
        }
    }

    /**
     * [cancel] is called between steps and before every rec batch (O-09): it throws to stop, e.g. the app's ensureActive(),
     * so a discarded import stops within one batch instead of finishing the image. It never changes the output.
     */
    fun recognize(img: BgrImage, cancel: () -> Unit = {}): List<OcrLine> {
        val t0 = System.nanoTime()
        val boxes = detect(img)
        val t1 = System.nanoTime()
        cancel()
        // O-09: crops are independent and deterministic, so they run on all cores; order and output stay the same
        val all = arrayOfNulls<BgrImage>(boxes.size)
        java.util.stream.IntStream.range(0, boxes.size).parallel().forEach { all[it] = crop(img, boxes[it]) }
        val crops = ArrayList<BgrImage>()
        val kept = ArrayList<IntArray>()
        boxes.forEachIndexed { i, b -> all[i]?.let { crops.add(it); kept.add(b) } }
        val tc = System.nanoTime()
        val texts = recognizeCrops(crops, cancel)
        val t2 = System.nanoTime()
        lastTimings = Timings((t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000, kept.size, (tc - t1) / 1_000_000)
        return kept.mapIndexed { i, b ->
            OcrLine(texts[i].first, texts[i].second.toDouble(), List(4) { Point(b[it * 2], b[it * 2 + 1]) })
        }
    }

    // ---- detection ----
    private fun detResize(h: Int, w: Int): Pair<Int, Int> {
        val ratio = if (minOf(h, w) < 64) 64.0 / minOf(h, w) else 1.0
        var rh = (h * ratio).toInt()
        var rw = (w * ratio).toInt()
        if (max(rh, rw) > options.maxSide) {
            val r = options.maxSide.toDouble() / max(rh, rw)
            rh = (rh * r).toInt(); rw = (rw * r).toInt()
        }
        // Python round() is half-to-even
        rh = max(Math.rint(rh / 32.0).toInt() * 32, 32)
        rw = max(Math.rint(rw / 32.0).toInt() * 32, 32)
        return rh to rw
    }

    fun detect(img: BgrImage): List<IntArray> {
        val (rh, rw) = detResize(img.height, img.width)
        val r = ImageOps.resizeLinear(img, rw, rh)
        val plane = rh * rw
        val buf = direct(3 * plane) // direct: ORT uses it in place instead of copying it (code review O-03)
        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)
        for (i in 0 until plane) for (c in 0 until 3) {
            buf.put(c * plane + i, ((r.data[i * 3 + c].toInt() and 0xFF) / 255f - mean[c]) / std[c])
        }
        val pred = OnnxTensor.createTensor(env, buf, longArrayOf(1, 3, rh.toLong(), rw.toLong())).use { x ->
            det.run(mapOf("x" to x), runOptions).use { res ->
                val fb = (res[0] as OnnxTensor).floatBuffer // already a heap copy: take its array, no second copy
                if (fb.hasArray() && fb.arrayOffset() == 0 && fb.position() == 0 && fb.remaining() == fb.array().size) fb.array()
                else FloatArray(fb.remaining()).also { fb.get(it) }
            }
        }
        return DbPostprocess.boxes(pred, rw, rh, img.width, img.height)
    }

    // ---- recognition ----
    private fun crop(img: BgrImage, b: IntArray): BgrImage? {
        val (q, _) = DbPostprocess.miniBox(b) ?: return null
        fun d(i: Int, j: Int) = hypot(q.xs[i] - q.xs[j], q.ys[i] - q.ys[j]).toFloat()
        val cw = max(d(0, 1), d(2, 3)).toInt()
        val ch = max(d(0, 3), d(1, 2)).toInt()
        if (cw <= 0 || ch <= 0) return null
        val m = ImageOps.perspective(
            DoubleArray(8) { if (it % 2 == 0) q.xs[it / 2] else q.ys[it / 2] },
            doubleArrayOf(0.0, 0.0, cw.toDouble(), 0.0, cw.toDouble(), ch.toDouble(), 0.0, ch.toDouble()),
        )
        val out = ImageOps.warpPerspectiveCubic(img, m, cw, ch)
        return if (ch.toDouble() / cw >= 1.5) ImageOps.rot90(out) else out
    }

    private fun recognizeCrops(crops: List<BgrImage>, cancel: () -> Unit): List<Pair<String, Float>> {
        val res = arrayOfNulls<Pair<String, Float>>(crops.size)
        val order = crops.indices.sortedBy { crops[it].width / crops[it].height.toDouble() }
        val chunks = order.chunked(options.recBatch)
        // O-09: the next batch's input is prepared on another thread while ORT runs this one (same bytes, same output)
        var next: java.util.concurrent.Future<Batch>? = if (chunks.isEmpty()) null else prep.submit<Batch> { batch(crops, chunks[0]) }
        try {
            for (ci in chunks.indices) {
                cancel()
                val b = try { next!!.get() } catch (e: java.util.concurrent.ExecutionException) { throw e.cause ?: e }
                next = if (ci + 1 < chunks.size) prep.submit<Batch> { batch(crops, chunks[ci + 1]) } else null
                runBatch(b, res)
            }
        } finally {
            next?.cancel(true)
        }
        return res.map { it!! }
    }

    private class Batch(val chunk: List<Int>, val maxW: Int, val buf: FloatBuffer)

    private fun batch(crops: List<BgrImage>, chunk: List<Int>): Batch {
        val widths = chunk.map { recWidth(crops[it]) } // padded width of each crop
        val maxW = widths.max()
        val plane = 48 * maxW
        val buf = direct(chunk.size * 3 * plane) // zero padded, used in place by ORT
        chunk.forEachIndexed { k, i ->
            val c = crops[i]
            val rw = if (widths[k] >= MAX_REC_W) MAX_REC_W else minOf(widths[k], ceil(48.0 * c.width / c.height).toInt())
            val r = ImageOps.resizeLinear(c, rw.coerceAtLeast(1), 48)
            val base = k * 3 * plane
            for (y in 0 until 48) for (x in 0 until r.width) for (ch in 0 until 3) {
                buf.put(base + ch * plane + y * maxW + x, ((r.data[(y * r.width + x) * 3 + ch].toInt() and 0xFF) / 255f - 0.5f) / 0.5f)
            }
        }
        return Batch(chunk, maxW, buf)
    }

    private fun runBatch(b: Batch, res: Array<Pair<String, Float>?>) {
        val chunk = b.chunk
        val maxW = b.maxW
        val buf = b.buf
        OnnxTensor.createTensor(env, buf, longArrayOf(chunk.size.toLong(), 3, 48, maxW.toLong())).use { x ->
            // P-01 (code review 2): only the per-step best class and its probability leave ORT; the [N, T, 11947]
            // probabilities (~115 MB for 6 wide lines) stay a native intermediate instead of a Java-heap buffer.
            rec.run(mapOf("x" to x), setOf(BEST, BEST_P), runOptions).use { out ->
                val best = (out[BEST].get() as OnnxTensor)
                val steps = best.info.shape[1].toInt()
                val ids = best.longBuffer
                val ps = (out[BEST_P].get() as OnnxTensor).floatBuffer
                chunk.forEachIndexed { k, i -> res[i] = ctc(ids, ps, k * steps, steps) }
            }
        }
    }

    /** O-09: prepares the next rec batch; daemon so a forgotten close() never keeps the process alive */
    private val prep = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "ocr-rec-prep").apply { isDaemon = true } }

    private fun direct(floats: Int): FloatBuffer =
        java.nio.ByteBuffer.allocateDirect(floats * 4).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer()

    private fun recWidth(c: BgrImage): Int {
        val ratio = max(320.0 / 48, c.width.toDouble() / c.height)
        // PaddleX: wider than 3200 is squeezed to exactly 3200
        return minOf((48 * ratio).toInt(), MAX_REC_W)
    }

    private companion object {
        const val MAX_REC_W = 3200
        /** outputs tools/rec_argmax.py adds: ArgMax (first maximum, int64) and ReduceMax over the classes, [N, T] */
        const val BEST = "best"
        const val BEST_P = "best_p"
    }

    /** CTC greedy decode of one line from the model's per-step best class [ids] and its probability [ps]. */
    private fun ctc(ids: java.nio.LongBuffer, ps: FloatBuffer, off: Int, steps: Int): Pair<String, Float> {
        val sb = StringBuilder()
        var sum = 0.0
        var n = 0
        var prev = -1
        for (t in 0 until steps) {
            val best = ids[off + t].toInt()
            val bp = ps[off + t]
            if (best != prev && best != 0) {
                sb.append(chars[best]); sum += bp; n++
            }
            prev = best
        }
        return sb.toString() to (if (n == 0) 0f else (sum / n).toFloat())
    }

    override fun close() {
        prep.shutdownNow()
        det.close()
        rec.close()
        runOptions.close()
    }
}
