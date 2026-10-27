package com.ouor.mycoupon.ocr.engine

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Float32-exact re-implementation of OpenCV 5.0 `cv::minAreaRect` (convexHull + rotatingCalipers)
 * and `cv::boxPoints` (modules/imgproc/src/rotcalipers.cpp, convhull.cpp; Apache-2.0).
 * PaddleX truncates these float corners to integers before unclip, so a 1e-5 difference moves a
 * text box by one pixel and changes the recognized text; that is why this is not a textbook version.
 */
object CvGeometry {
    class RotatedRect(val cx: Float, val cy: Float, val width: Float, val height: Float, val angle: Float)

    /** Convex hull, counter-clockwise (y-up sense), starting at the max-x (then max-y) point like cv::convexHull(clockwise=false). */
    fun hull(xs: FloatArray, ys: FloatArray): IntArray {
        val n = xs.size
        val idx = (0 until n).sortedWith(compareBy({ xs[it] }, { ys[it] }))
        // drop exact duplicates
        val u = ArrayList<Int>()
        for (i in idx) if (u.isEmpty() || xs[u.last()] != xs[i] || ys[u.last()] != ys[i]) u.add(i)
        if (u.size < 3) return u.toIntArray().reversedArray()
        fun cross(o: Int, a: Int, b: Int) =
            (xs[a].toDouble() - xs[o]) * (ys[b].toDouble() - ys[o]) - (ys[a].toDouble() - ys[o]) * (xs[b].toDouble() - xs[o])
        val h = IntArray(2 * u.size)
        var k = 0
        for (i in u) {
            while (k >= 2 && cross(h[k - 2], h[k - 1], i) <= 0) k--
            h[k++] = i
        }
        val lower = k + 1
        for (j in u.size - 2 downTo 0) {
            val i = u[j]
            while (k >= lower && cross(h[k - 2], h[k - 1], i) <= 0) k--
            h[k++] = i
        }
        k--
        val ring = h.copyOf(k)
        // raw cv::convexHull order starts at max x (ties: max y), the last point in sort order
        val start = ring.indexOf(u.last())
        val raw = IntArray(k) { ring[(start + it) % k] }
        return cyclicShift(raw)
    }

    /** cv::convexHull: "make the hull indices an ascending or descending sequence by a cyclic shift". */
    private fun cyclicShift(hb: IntArray): IntArray {
        val nout = hb.size
        if (nout < 3) return hb
        var minIdx = 0
        var maxIdx = 0
        var lt = 0
        for (i in 1 until nout) {
            val idx = hb[i]
            if (hb[i - 1] < idx) lt++
            if (lt > 1 && lt <= i - 2) break
            if (idx < hb[minIdx]) minIdx = i
            if (idx > hb[maxIdx]) maxIdx = i
        }
        val mm = kotlin.math.abs(maxIdx - minIdx)
        if ((mm == 1 || mm == nout - 1) && (lt <= 1 || lt >= nout - 2)) {
            val asc = (maxIdx + 1) % nout == minIdx
            val i0 = if (asc) minIdx else maxIdx
            if (i0 > 0) {
                val st = IntArray(nout)
                var j = i0
                var i = 0
                while (i < nout) {
                    val cur = hb[j]
                    st[i] = cur
                    val nj = if (j + 1 < nout) j + 1 else 0
                    if (i < nout - 1 && (asc != (cur < hb[nj]))) break
                    j = nj
                    i++
                }
                if (i == nout) return st
            }
        }
        return hb
    }

    fun minAreaRect(px: FloatArray, py: FloatArray): RotatedRect = fromHull(px, py, hull(px, py))

    /**
     * minAreaRect of a cv2.findContours outer border given as unordered pixels: the hull order that
     * cv::convexHull's cyclic shift produces for a traced contour ends at the contour's first pixel
     * (min y, then min x), so rotate the counter-clockwise hull to end there.
     */
    fun minAreaRectOfContour(px: FloatArray, py: FloatArray): RotatedRect {
        val h = hull(px, py)
        if (h.size < 3) return fromHull(px, py, h)
        val p0 = h.indices.minWith(compareBy({ py[h[it]] }, { px[h[it]] }))
        return fromHull(px, py, IntArray(h.size) { h[(p0 + 1 + it) % h.size] })
    }

    private fun fromHull(px: FloatArray, py: FloatArray, hi: IntArray): RotatedRect {
        val n = hi.size
        val xs = FloatArray(n) { px[hi[it]] }
        val ys = FloatArray(n) { py[hi[it]] }
        var angle = -Math.PI / 2
        var cx = 0f
        var cy = 0f
        var w = 0f
        var h = 0f
        if (n > 2) {
            val out = rotatingCalipers(xs, ys, n)
            cx = out[0] + (out[2] + out[4]) * 0.5f
            cy = out[1] + (out[3] + out[5]) * 0.5f
            w = sqrt(out[4].toDouble() * out[4] + out[5].toDouble() * out[5]).toFloat()
            h = sqrt(out[2].toDouble() * out[2] + out[3].toDouble() * out[3]).toFloat()
            if (out[2] == 0f && out[3] > 0f) {
                val t = w; w = h; h = t
            } else {
                angle = -atan2(out[2].toDouble(), out[3].toDouble())
            }
        } else if (n == 2) {
            cx = (xs[0] + xs[1]) * 0.5f
            cy = (ys[0] + ys[1]) * 0.5f
            val dx = xs[0].toDouble() - xs[1]
            val dy = ys[0].toDouble() - ys[1]
            w = 0f
            h = sqrt(dx * dx + dy * dy).toFloat()
            if (dx == 0.0) {
                val t = w; w = h; h = t
            } else if (dy < 0) {
                angle = atan2(dy, dx); val t = w; w = h; h = t
            } else if (dy > 0) {
                angle = -atan2(dx, dy)
            }
        } else if (n == 1) {
            cx = xs[0]; cy = ys[0]
        }
        return RotatedRect(cx, cy, w, h, (angle * 180 / Math.PI).toFloat())
    }

    /** cv::RotatedRect::points (OpenCV 5.0 form, as in the opencv-python 5.0 used by the Python twin) */
    fun boxPoints(r: RotatedRect): FloatArray {
        val a0 = r.angle * Math.PI / 180.0
        val b = cos(a0).toFloat() * 0.5f
        val a = sin(a0).toFloat() * 0.5f
        val ah = a * r.height
        val aw = a * r.width
        val bh = b * r.height
        val bw = b * r.width
        return floatArrayOf(
            r.cx - ah - bw, r.cy + bh - aw,
            r.cx + ah - bw, r.cy - bh - aw,
            r.cx + ah + bw, r.cy - bh + aw,
            r.cx - ah + bw, r.cy + bh + aw,
        )
    }

    private fun rotatingCalipers(px: FloatArray, py: FloatArray, n: Int): FloatArray {
        var minarea = Float.MAX_VALUE
        val invLen = FloatArray(n)
        val vx = FloatArray(n)
        val vy = FloatArray(n)
        var left = 0; var bottom = 0; var right = 0; var top = 0
        val seq = IntArray(4)
        var baseA: Float
        var baseB = 0f
        var p0x = px[0]; var p0y = py[0]
        var leftX = p0x; var rightX = p0x; var topY = p0y; var bottomY = p0y
        for (i in 0 until n) {
            if (p0x < leftX) { leftX = p0x; left = i }
            if (p0x > rightX) { rightX = p0x; right = i }
            if (p0y > topY) { topY = p0y; top = i }
            if (p0y < bottomY) { bottomY = p0y; bottom = i }
            val j = if (i + 1 < n) i + 1 else 0
            val dx = px[j].toDouble() - p0x
            val dy = py[j].toDouble() - p0y
            vx[i] = dx.toFloat(); vy[i] = dy.toFloat()
            invLen[i] = (1.0 / sqrt(dx * dx + dy * dy)).toFloat()
            p0x = px[j]; p0y = py[j]
        }
        // minAreaRect passes orientation = 1 (counter-clockwise hull)
        baseA = 1f
        seq[0] = bottom; seq[1] = right; seq[2] = top; seq[3] = left
        val rx = FloatArray(4); val ry = FloatArray(4)
        val buf = FloatArray(7)
        val bufI = IntArray(2)
        for (k in 0 until n) {
            var main = 0
            rx[0] = vx[seq[0]]; ry[0] = vy[seq[0]]
            rx[1] = vy[seq[1]]; ry[1] = -vx[seq[1]]       // rotate90CW
            rx[2] = -vx[seq[2]]; ry[2] = -vy[seq[2]]      // rotate180
            rx[3] = -vy[seq[3]]; ry[3] = vx[seq[3]]       // rotate90CCW
            for (i in 1 until 4) {
                // firstVecIsRight(rot[i], rot[main])
                val tx = ry[i]; val ty = -rx[i]
                if (tx * rx[main] + ty * ry[main] < 0) main = i
            }
            val pi = seq[main]
            val lx = vx[pi] * invLen[pi]
            val ly = vy[pi] * invLen[pi]
            when (main) {
                0 -> { baseA = lx; baseB = ly }
                1 -> { baseA = ly; baseB = -lx }
                2 -> { baseA = -lx; baseB = -ly }
                else -> { baseA = -ly; baseB = lx }
            }
            seq[main] = if (seq[main] + 1 == n) 0 else seq[main] + 1
            var dx = px[seq[1]] - px[seq[3]]
            var dy = py[seq[1]] - py[seq[3]]
            val width = dx * baseA + dy * baseB
            dx = px[seq[2]] - px[seq[0]]
            dy = py[seq[2]] - py[seq[0]]
            val height = -dx * baseB + dy * baseA
            val area = width * height
            if (area <= minarea) {
                minarea = area
                bufI[0] = seq[3]; buf[1] = baseA; buf[2] = width; buf[3] = baseB; buf[4] = height
                bufI[1] = seq[0]; buf[6] = area
            }
        }
        val a1 = buf[1]; val b1 = buf[3]
        val a2 = -buf[3]; val b2 = buf[1]
        val c1 = a1 * px[bufI[0]] + py[bufI[0]] * b1
        val c2 = a2 * px[bufI[1]] + py[bufI[1]] * b2
        val idet = 1f / (a1 * b2 - a2 * b1)
        return floatArrayOf(
            (c1 * b2 - c2 * b1) * idet, (a1 * c2 - a2 * c1) * idet,
            a1 * buf[2], b1 * buf[2],
            a2 * buf[4], b2 * buf[4],
        )
    }
}
