package com.ouor.mycoupon.ocr.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * PaddleX DBPostProcess.boxes_from_bitmap (box_type quad, score_mode fast) without OpenCV.
 * cv2.findContours -> 8-connected components (hole contours are ignored: they score low anyway),
 * cv2.minAreaRect -> convex hull + rotating calipers, pyclipper JT_ROUND offset -> Clipper's
 * round-join construction with integer rounding.
 */
object DbPostprocess {
    const val THRESH = 0.3f
    const val BOX_THRESH = 0.6
    const val UNCLIP_RATIO = 1.5
    const val MIN_SIZE = 3.0
    const val MAX_CANDIDATES = 1000

    /** Corners in PaddleX order: top-left, top-right, bottom-right, bottom-left. */
    class Quad(val xs: DoubleArray, val ys: DoubleArray)

    /** @return integer quads in destination (original image) pixels */
    fun boxes(pred: FloatArray, w: Int, h: Int, destW: Int, destH: Int): List<IntArray> {
        val out = mutableListOf<IntArray>()
        for (pts in components(pred, w, h).take(MAX_CANDIDATES)) {
            val (q, sside) = contourBox(pts) ?: continue
            if (sside < MIN_SIZE) continue
            if (scoreFast(pred, w, h, q) < BOX_THRESH) continue
            val (q2, sside2) = miniBox(unclip(q, UNCLIP_RATIO)) ?: continue
            if (sside2 < MIN_SIZE + 2) continue
            val sx = (destW.toDouble() / w).toFloat()
            val sy = (destH.toDouble() / h).toFloat()
            val box = IntArray(8)
            for (i in 0 until 4) {
                box[i * 2] = Math.rint((q2.xs[i].toFloat() * sx).toDouble()).toInt().coerceIn(0, destW)
                box[i * 2 + 1] = Math.rint((q2.ys[i].toFloat() * sy).toDouble()).toInt().coerceIn(0, destH)
            }
            out.add(box)
        }
        return sortBoxes(out)
    }

    /** PaddleX SortQuadBoxes. */
    fun sortBoxes(boxes: List<IntArray>): List<IntArray> {
        val b = boxes.sortedWith(compareBy({ it[1] }, { it[0] })).toMutableList()
        for (i in 0 until b.size - 1) {
            for (j in i downTo 0) {
                if (abs(b[j + 1][1] - b[j][1]) < 10 && b[j + 1][0] < b[j][0]) {
                    val t = b[j]; b[j] = b[j + 1]; b[j + 1] = t
                } else break
            }
        }
        return b
    }

    /** 8-connected components of pred > THRESH; each as hull-relevant points (row extremes, x/y pairs). */
    fun components(pred: FloatArray, w: Int, h: Int): List<IntArray> {
        val label = IntArray(w * h)
        val result = mutableListOf<IntArray>()
        val stack = IntArray(w * h)
        var next = 0
        for (start in 0 until w * h) {
            if (label[start] != 0 || pred[start] <= THRESH) continue
            next++
            var sp = 0
            stack[sp++] = start
            label[start] = next
            val minX = HashMap<Int, Int>()
            val maxX = HashMap<Int, Int>()
            while (sp > 0) {
                val p = stack[--sp]
                val x = p % w
                val y = p / w
                minX[y] = min(minX[y] ?: x, x)
                maxX[y] = max(maxX[y] ?: x, x)
                for (dy in -1..1) {
                    val ny = y + dy
                    if (ny < 0 || ny >= h) continue
                    for (dx in -1..1) {
                        val nx = x + dx
                        if (nx < 0 || nx >= w) continue
                        val q = ny * w + nx
                        if (label[q] == 0 && pred[q] > THRESH) {
                            label[q] = next
                            stack[sp++] = q
                        }
                    }
                }
            }
            val pts = IntArray(minX.size * 4)
            var k = 0
            for ((y, x0) in minX) {
                pts[k++] = x0; pts[k++] = y
                pts[k++] = maxX[y]!!; pts[k++] = y
            }
            result.add(pts)
        }
        return result
    }

    /** cv2.minAreaRect + cv2.boxPoints + PaddleX get_mini_boxes ordering. Returns quad and its shorter side. */
    fun miniBox(points: IntArray): Pair<Quad, Double>? = miniBox(DoubleArray(points.size) { points[it].toDouble() })

    fun miniBox(points: DoubleArray): Pair<Quad, Double>? {
        if (points.isEmpty()) return null
        val n = points.size / 2
        val r = CvGeometry.minAreaRect(FloatArray(n) { points[it * 2].toFloat() }, FloatArray(n) { points[it * 2 + 1].toFloat() })
        val p = CvGeometry.boxPoints(r)
        return order(DoubleArray(4) { p[it * 2].toDouble() }, DoubleArray(4) { p[it * 2 + 1].toDouble() }) to minOf(r.width, r.height).toDouble()
    }

    private fun contourBox(points: IntArray): Pair<Quad, Double>? {
        val n = points.size / 2
        val r = CvGeometry.minAreaRectOfContour(FloatArray(n) { points[it * 2].toFloat() }, FloatArray(n) { points[it * 2 + 1].toFloat() })
        val p = CvGeometry.boxPoints(r)
        return order(DoubleArray(4) { p[it * 2].toDouble() }, DoubleArray(4) { p[it * 2 + 1].toDouble() }) to minOf(r.width, r.height).toDouble()
    }

    private fun order(xs: DoubleArray, ys: DoubleArray): Quad {
        val s = (0 until 4).sortedBy { xs[it] }
        val (i1, i4) = if (ys[s[1]] > ys[s[0]]) s[0] to s[1] else s[1] to s[0]
        val (i2, i3) = if (ys[s[3]] > ys[s[2]]) s[2] to s[3] else s[3] to s[2]
        val o = intArrayOf(i1, i2, i3, i4)
        return Quad(DoubleArray(4) { xs[o[it]] }, DoubleArray(4) { ys[o[it]] })
    }

    /**
     * box_score_fast: mean of pred under cv2.fillPoly(mask, int-truncated quad). cv2.fillPoly = outline drawn
     * with 8-connected Bresenham lines + scanline spans [ceil(xl), floor(xr)] for y0 <= y < y1 (drawing.cpp).
     */
    fun scoreFast(pred: FloatArray, w: Int, h: Int, q: Quad): Double {
        val xmin = floor(q.xs.min()).toInt().coerceIn(0, w - 1)
        val xmax = ceil(q.xs.max()).toInt().coerceIn(0, w - 1)
        val ymin = floor(q.ys.min()).toInt().coerceIn(0, h - 1)
        val ymax = ceil(q.ys.max()).toInt().coerceIn(0, h - 1)
        val mw = xmax - xmin + 1
        val mh = ymax - ymin + 1
        val px = IntArray(4) { (q.xs[it] - xmin).toInt() }
        val py = IntArray(4) { (q.ys[it] - ymin).toInt() }
        val mask = BooleanArray(mw * mh)
        fun set(x: Int, y: Int) {
            if (x in 0 until mw && y in 0 until mh) mask[y * mw + x] = true
        }
        // scanline fill, 16-bit fixed point edges
        class Edge(val y0: Int, val y1: Int, val x0: Long, val dx: Long)
        val edges = ArrayList<Edge>()
        for (i in 0 until 4) {
            val j = (i + 3) % 4 // CollectPolyEdges walks v[count-1] -> v[0] -> v[1] ...
            val ax = px[j].toLong() shl 16; val ay = py[j]
            val bx = px[i].toLong() shl 16; val by = py[i]
            if (ay == by) continue
            val dx = (bx - ax) / (by - ay)
            edges += if (ay < by) Edge(ay, by, ax, dx) else Edge(by, ay, bx, dx)
        }
        if (edges.size >= 2) {
            val yTop = edges.minOf { it.y0 }
            val yBot = minOf(edges.maxOf { it.y1 }, mh)
            for (y in yTop until yBot) {
                val xs = edges.filter { y >= it.y0 && y < it.y1 }.map { it.x0 + (y - it.y0) * it.dx }.sorted()
                var k = 0
                while (k + 1 < xs.size) {
                    val x1 = ((xs[k] + 0xFFFF) shr 16).toInt().coerceAtLeast(0)
                    val x2 = (xs[k + 1] shr 16).toInt().coerceAtMost(mw - 1)
                    if (y >= 0) for (x in x1..x2) set(x, y)
                    k += 2
                }
            }
        }
        // outline: cv::Line, LINE_8, left to right
        for (i in 0 until 4) {
            val j = (i + 3) % 4
            var x0 = px[j]; var y0 = py[j]; var x1 = px[i]; var y1 = py[i]
            if (x1 < x0) { val tx = x0; x0 = x1; x1 = tx; val ty = y0; y0 = y1; y1 = ty }
            val dxa = x1 - x0
            val sy = if (y1 < y0) -1 else 1
            val dya = kotlin.math.abs(y1 - y0)
            val vert = dya > dxa
            val major = if (vert) dya else dxa
            val minor = if (vert) dxa else dya
            var err = major - 2 * minor
            var x = x0; var y = y0
            for (c in 0..major) {
                set(x, y)
                val neg = err < 0
                err += -2 * minor + (if (neg) 2 * major else 0)
                if (vert) { y += sy; if (neg) x += 1 } else { x += 1; if (neg) y += sy }
            }
        }
        var sum = 0.0
        var cnt = 0
        for (y in 0 until mh) for (x in 0 until mw) if (mask[y * mw + x]) {
            sum += pred[(y + ymin) * w + x + xmin]
            cnt++
        }
        return if (cnt == 0) 0.0 else sum / cnt
    }

    /** pyclipper.PyclipperOffset(JT_ROUND, ET_CLOSEDPOLYGON).Execute(area * ratio / perimeter). */
    fun unclip(q: Quad, ratio: Double): DoubleArray {
        var area = 0.0
        var len = 0.0
        for (i in 0 until 4) {
            val j = (i + 1) % 4
            area += q.xs[i] * q.ys[j] - q.xs[j] * q.ys[i]
            len += hypot(q.xs[j] - q.xs[i], q.ys[j] - q.ys[i])
        }
        area = abs(area) / 2
        val delta = area * ratio / len
        // Clipper works on integers: input coordinates are truncated
        var xs = DoubleArray(4) { q.xs[it].toLong().toDouble() }
        var ys = DoubleArray(4) { q.ys[it].toLong().toDouble() }
        // outward normals need a fixed orientation
        var signed = 0.0
        for (i in 0 until 4) {
            val j = (i + 1) % 4
            signed += xs[i] * ys[j] - xs[j] * ys[i]
        }
        if (signed < 0) {
            xs = xs.reversedArray(); ys = ys.reversedArray()
        }
        val n = 4
        val nx = DoubleArray(n)
        val ny = DoubleArray(n)
        for (i in 0 until n) {
            val j = (i + 1) % n
            val dx = xs[j] - xs[i]; val dy = ys[j] - ys[i]
            val l = sqrt(dx * dx + dy * dy)
            // positive-area (CCW in math coords) polygon: outward normal is (dy, -dx)
            if (l == 0.0) { nx[i] = 0.0; ny[i] = 0.0 } else { nx[i] = dy / l; ny[i] = -dx / l }
        }
        val tol = if (0.25 > abs(delta) * 0.25) abs(delta) * 0.25 else 0.25
        var steps = PI / acos(1 - tol / abs(delta))
        if (steps > abs(delta) * PI) steps = abs(delta) * PI
        val sinS = sin(2 * PI / steps)
        val cosS = cos(2 * PI / steps)
        val stepsPerRad = steps / (2 * PI)
        val out = ArrayList<Double>()
        fun add(x: Double, y: Double) {
            out.add(round(x)); out.add(round(y))
        }
        for (j in 0 until n) {
            val k = (j - 1 + n) % n
            val sinA = nx[k] * ny[j] - nx[j] * ny[k]
            val cosA = nx[j] * nx[k] + ny[j] * ny[k]
            val a = atan2(sinA, cosA)
            val st = max((stepsPerRad * abs(a)).roundToInt(), 1)
            var x = nx[k]
            var y = ny[k]
            for (i in 0 until st) {
                add(xs[j] + x * delta, ys[j] + y * delta)
                val x2 = x
                x = x * cosS - sinS * y
                y = x2 * sinS + y * cosS
            }
            add(xs[j] + nx[j] * delta, ys[j] + ny[j] * delta)
        }
        // pyclipper's union output ends at the top-most (min y, then max x) point
        val m = out.size / 2
        val last = (0 until m).minWith(compareBy({ out[it * 2 + 1] }, { -out[it * 2] }))
        return DoubleArray(out.size) { out[((last + 1 + it / 2) % m) * 2 + it % 2] }
    }

    /** Clipper Round(): half away from zero. */
    private fun round(v: Double) = if (v < 0) -floor(-v + 0.5) else floor(v + 0.5)
}
