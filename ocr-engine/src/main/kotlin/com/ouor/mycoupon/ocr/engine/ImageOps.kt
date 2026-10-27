package com.ouor.mycoupon.ocr.engine

import kotlin.math.floor

/** 8-bit BGR image, interleaved (OpenCV layout; PaddleX feeds BGR to both models). */
class BgrImage(val width: Int, val height: Int, val data: ByteArray) {
    init {
        require(data.size == width * height * 3)
    }

    operator fun get(x: Int, y: Int, c: Int): Int = data[(y * width + x) * 3 + c].toInt() and 0xFF

    companion object {
        /** From Android/AWT ARGB_8888 pixels. */
        fun fromArgb(pixels: IntArray, width: Int, height: Int): BgrImage {
            val out = ByteArray(width * height * 3)
            for (i in pixels.indices) {
                val p = pixels[i]
                out[i * 3] = p.toByte()
                out[i * 3 + 1] = (p shr 8).toByte()
                out[i * 3 + 2] = (p shr 16).toByte()
            }
            return BgrImage(width, height, out)
        }
    }
}

/**
 * Image operations matching the OpenCV calls in PaddleX (cv2.resize INTER_LINEAR,
 * cv2.warpPerspective INTER_CUBIC + BORDER_REPLICATE, np.rot90). Float math instead of OpenCV's
 * fixed point, so pixels may differ by 1 level; parity is checked at text level (EngineParityTest).
 */
object ImageOps {
    /**
     * cv2.resize(INTER_LINEAR) for 8-bit images, bit-exact with OpenCV's fixed-point path:
     * 11-bit coefficients, horizontal pass in int, vertical pass as VResizeLinearVec_32s8u
     * (((S0>>4)*b0>>16) + ((S1>>4)*b1>>16) + 2) >> 2.
     */
    fun resizeLinear(src: BgrImage, dw: Int, dh: Int): BgrImage {
        if (dw == src.width && dh == src.height) return src
        val sw = src.width
        val sh = src.height
        val xo = IntArray(dw)
        val xa0 = IntArray(dw)
        val xa1 = IntArray(dw)
        val scaleX = 1.0 / (dw.toDouble() / sw)
        for (dx in 0 until dw) {
            var fx = ((dx + 0.5) * scaleX - 0.5).toFloat()
            var sx = floor(fx).toInt()
            fx -= sx
            if (sx < 0) { fx = 0f; sx = 0 }
            if (sx >= sw - 1) { fx = 0f; sx = sw - 1 }
            xo[dx] = sx
            xa0[dx] = Math.rint(((1f - fx) * 2048f).toDouble()).toInt()
            xa1[dx] = Math.rint((fx * 2048f).toDouble()).toInt()
        }
        val scaleY = 1.0 / (dh.toDouble() / sh)
        val out = ByteArray(dw * dh * 3)
        val s = src.data
        val row0 = IntArray(dw * 3)
        val row1 = IntArray(dw * 3)
        fun hrow(y: Int, dst: IntArray) {
            val base = y * sw * 3
            for (dx in 0 until dw) {
                val i0 = base + xo[dx] * 3
                val i1 = if (xo[dx] + 1 < sw) i0 + 3 else i0
                for (c in 0 until 3) {
                    dst[dx * 3 + c] = (s[i0 + c].toInt() and 0xFF) * xa0[dx] + (s[i1 + c].toInt() and 0xFF) * xa1[dx]
                }
            }
        }
        for (dy in 0 until dh) {
            var fy = ((dy + 0.5) * scaleY - 0.5).toFloat()
            val sy = floor(fy).toInt()
            fy -= sy
            val b0 = Math.rint(((1f - fy) * 2048f).toDouble()).toInt()
            val b1 = Math.rint((fy * 2048f).toDouble()).toInt()
            hrow(sy.coerceIn(0, sh - 1), row0)
            hrow((sy + 1).coerceIn(0, sh - 1), row1)
            val o = dy * dw * 3
            for (i in 0 until dw * 3) {
                val v = (((row0[i] shr 4) * b0 shr 16) + ((row1[i] shr 4) * b1 shr 16) + 2) shr 2
                out[o + i] = v.coerceIn(0, 255).toByte()
            }
        }
        return BgrImage(dw, dh, out)
    }

    /** cv2.getPerspectiveTransform: maps src[i] -> dst[i] (4 points). Returns row-major 3x3. */
    fun perspective(src: DoubleArray, dst: DoubleArray): DoubleArray {
        val a = Array(8) { DoubleArray(9) }
        for (i in 0 until 4) {
            val x = src[i * 2]
            val y = src[i * 2 + 1]
            val u = dst[i * 2]
            val v = dst[i * 2 + 1]
            a[i] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -x * u, -y * u, u)
            a[i + 4] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -x * v, -y * v, v)
        }
        val h = solve(a)
        return doubleArrayOf(h[0], h[1], h[2], h[3], h[4], h[5], h[6], h[7], 1.0)
    }

    private fun solve(m: Array<DoubleArray>): DoubleArray {
        val n = 8
        for (col in 0 until n) {
            val piv = (col until n).maxBy { kotlin.math.abs(m[it][col]) }
            val t = m[col]; m[col] = m[piv]; m[piv] = t
            val p = m[col][col]
            if (p == 0.0) continue
            for (r in 0 until n) if (r != col) {
                val f = m[r][col] / p
                if (f != 0.0) for (c in col..n) m[r][c] -= f * m[col][c]
            }
        }
        return DoubleArray(n) { if (m[it][it] == 0.0) 0.0 else m[it][n] / m[it][it] }
    }

    private fun invert(m: DoubleArray): DoubleArray {
        val a = m[0]; val b = m[1]; val c = m[2]
        val d = m[3]; val e = m[4]; val f = m[5]
        val g = m[6]; val h = m[7]; val i = m[8]
        val det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
        val r = doubleArrayOf(
            e * i - f * h, c * h - b * i, b * f - c * e,
            f * g - d * i, a * i - c * g, c * d - a * f,
            d * h - e * g, b * g - a * h, a * e - b * d,
        )
        return DoubleArray(9) { r[it] / det }
    }

    /** OpenCV initInterTab2D(INTER_CUBIC): 32x32 fractions -> 16 fixed-point weights summing to 32768. */
    private val CUBIC_TAB: Array<IntArray> by lazy {
        val t1 = Array(32) { i ->
            val x = 1f / 32 * i
            val a = -0.75f
            val c0 = ((a * (x + 1) - 5 * a) * (x + 1) + 8 * a) * (x + 1) - 4 * a
            val c1 = ((a + 2) * x - (a + 3)) * x * x + 1
            val c2 = ((a + 2) * (1 - x) - (a + 3)) * (1 - x) * (1 - x) + 1
            floatArrayOf(c0, c1, c2, 1f - c0 - c1 - c2)
        }
        Array(32 * 32) { ij ->
            val i = ij / 32
            val j = ij % 32
            val it = IntArray(16)
            var isum = 0
            for (k1 in 0 until 4) for (k2 in 0 until 4) {
                val v = t1[i][k1] * t1[j][k2]
                it[k1 * 4 + k2] = Math.rint((v * 32768f).toDouble()).toInt().coerceIn(-32768, 32767)
                isum += it[k1 * 4 + k2]
            }
            if (isum != 32768) {
                val diff = isum - 32768
                var mk1 = 2; var mk2 = 2; var bk1 = 2; var bk2 = 2
                for (k1 in 2 until 4) for (k2 in 2 until 4) {
                    if (it[k1 * 4 + k2] < it[mk1 * 4 + mk2]) { mk1 = k1; mk2 = k2 }
                    else if (it[k1 * 4 + k2] > it[bk1 * 4 + bk2]) { bk1 = k1; bk2 = k2 }
                }
                if (diff < 0) it[bk1 * 4 + bk2] -= diff else it[mk1 * 4 + mk2] -= diff
            }
            it
        }
    }

    /** cv2.warpPerspective(img, M, (w, h), flags=INTER_CUBIC, borderMode=BORDER_REPLICATE), fixed point like OpenCV. */
    fun warpPerspectiveCubic(src: BgrImage, m: DoubleArray, w: Int, h: Int): BgrImage {
        val inv = invert(m)
        val out = ByteArray(w * h * 3)
        val acc = IntArray(3)
        for (y in 0 until h) for (x in 0 until w) {
            val ww = inv[6] * x + inv[7] * y + inv[8]
            val wq = if (ww != 0.0) 32.0 / ww else 0.0
            val qx = Math.rint(((inv[0] * x + inv[1] * y + inv[2]) * wq).coerceIn(Int.MIN_VALUE.toDouble(), Int.MAX_VALUE.toDouble())).toInt()
            val qy = Math.rint(((inv[3] * x + inv[4] * y + inv[5]) * wq).coerceIn(Int.MIN_VALUE.toDouble(), Int.MAX_VALUE.toDouble())).toInt()
            val ix = qx shr 5
            val iy = qy shr 5
            val tab = CUBIC_TAB[(qy and 31) * 32 + (qx and 31)]
            acc.fill(0)
            for (j in 0 until 4) {
                val sy = (iy - 1 + j).coerceIn(0, src.height - 1)
                for (i in 0 until 4) {
                    val sx = (ix - 1 + i).coerceIn(0, src.width - 1)
                    val k = tab[j * 4 + i]
                    val o = (sy * src.width + sx) * 3
                    acc[0] += k * (src.data[o].toInt() and 0xFF)
                    acc[1] += k * (src.data[o + 1].toInt() and 0xFF)
                    acc[2] += k * (src.data[o + 2].toInt() and 0xFF)
                }
            }
            val o = (y * w + x) * 3
            for (c in 0 until 3) out[o + c] = ((acc[c] + (1 shl 14)) shr 15).coerceIn(0, 255).toByte()
        }
        return BgrImage(w, h, out)
    }

    /** np.rot90 (counter-clockwise). */
    fun rot90(src: BgrImage): BgrImage {
        val w = src.height
        val h = src.width
        val out = ByteArray(w * h * 3)
        for (i in 0 until h) for (j in 0 until w) {
            val s = (j * src.width + (src.width - 1 - i)) * 3
            val d = (i * w + j) * 3
            out[d] = src.data[s]; out[d + 1] = src.data[s + 1]; out[d + 2] = src.data[s + 2]
        }
        return BgrImage(w, h, out)
    }
}
