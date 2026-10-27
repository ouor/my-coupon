package com.ouor.mycoupon.ocr.engine

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CancellationException
import javax.imageio.ImageIO

/** O-09 / C2-23 AC1: once the cancel check starts throwing, recognize() returns within one rec batch (< 1 s). */
class CancelTest {
    @Test
    fun stopsWithinOneBatch() {
        val data = File(System.getProperty("mycoupon.testData") ?: ".test", "non-coupon-image")
        val models = File(System.getProperty("mycoupon.models") ?: "app/src/main/assets/ocr")
        assumeTrue("non-coupon images missing", data.isDirectory && File(models, "rec.onnx").exists())
        val engine = PaddleOcrEngine(File(models, "det.onnx").path, File(models, "rec.onnx").path,
            File(models, "dict.txt").readLines(Charsets.UTF_8).filter { it.isNotEmpty() }, PaddleOcrEngine.Options(maxSide = 1280))
        // the photo with the most text: the longest rec phase of the set
        val img = data.listFiles()!!.sortedBy { it.name }.mapNotNull { f -> ImageIO.read(f) }.maxBy { it.width * it.height }.let { bi ->
            val px = IntArray(bi.width * bi.height).also { bi.getRGB(0, 0, bi.width, bi.height, it, 0, bi.width) }
            BgrImage.fromArgb(px, bi.width, bi.height)
        }
        // [버리기] from another thread while rec runs; recognize must notice at its next check
        val cancelledAt = java.util.concurrent.atomic.AtomicLong()
        val timer = Thread { Thread.sleep(800); cancelledAt.set(System.nanoTime()) }.apply { start() }
        val stoppedAt = try {
            engine.recognize(img) { if (cancelledAt.get() != 0L) throw CancellationException("discarded") }
            0L
        } catch (_: CancellationException) {
            System.nanoTime()
        } finally {
            timer.join()
            engine.close()
        }
        assumeTrue("image finished before the cancel", stoppedAt != 0L)
        val ms = (stoppedAt - cancelledAt.get()) / 1_000_000
        println("cancel -> stop: $ms ms")
        assertTrue("stopped $ms ms after cancel", ms < 1000)
    }
}
