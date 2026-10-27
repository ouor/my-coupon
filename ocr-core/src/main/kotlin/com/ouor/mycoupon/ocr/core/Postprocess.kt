package com.ouor.mycoupon.ocr.core

import java.time.DateTimeException
import java.time.LocalDate

/**
 * Line-for-line port of `C:\Users\ouor\Codes\pp-ocr-v5\postprocess.py` (steps 1-6, baseline version).
 *
 * The Python file is the reference: keep function names and order identical so the next port
 * (ocr-lab, docs/ocr/porting-spec.md) is a diff, not a rewrite. Every regex uses (?U) so `\d \s \W`
 * follow Python's Unicode semantics. Parity: PostprocessParityTest.
 */
object Postprocess {
    const val MIN_SCORE = 0.6
    const val ROW_OVERLAP = 0.5
    const val WRAP_GAP = 0.5
    const val WRAP_HEIGHT = 0.8
    const val WRAP_WIDTH = 0.75
    const val JOIN_GAP = 1.0
    val DATE_KEYWORDS = listOf("유효", "사용", "까지", "기간", "만료", "~")

    /** Python `box(line)` dict. [w] is only set on wrap-merged boxes (python `a.get("w", ...)`). */
    data class Box(
        val text: String, val x0: Double, val x1: Double, val y0: Double, val y1: Double, val h: Double,
        val w: Double? = null,
        /** not in Python: min OCR score of the lines merged into this box, for "확인 필요" */
        val score: Double = 1.0,
        val lines: List<OcrLine> = emptyList(),
    )

    data class Result(
        val rows: List<String>,
        /** boxes of each row, aligned with [rows] */
        val rowBoxes: List<List<Box>>,
        val dropped: List<String>,
        val boxes: List<Box>,
        val barcode: String?,
        val expiry: Expiry?,
    )

    private fun rx(p: String) = Regex("(?U)$p")

    private val NON_WORD = rx("[\\W_]+")
    fun norm(s: String) = NON_WORD.replace(s, "").lowercase()

    fun box(line: OcrLine): Box {
        val xs = line.poly.map { it.x.toDouble() }
        val ys = line.poly.map { it.y.toDouble() }
        return Box(line.text, xs.min(), xs.max(), ys.min(), ys.max(), ys.max() - ys.min(),
            score = line.score.toDouble(), lines = listOf(line))
    }

    // step 1
    private val ALNUM = rx("[0-9A-Za-z가-힣]")
    fun isNoise(line: OcrLine) = line.score < MIN_SCORE || !ALNUM.containsMatchIn(line.text)

    // step 2
    fun rowsOf(boxes: List<Box>): List<List<Box>> {
        val rows = mutableListOf<MutableList<Box>>()
        for (b in boxes.sortedBy { (it.y0 + it.y1) / 2 }) {
            if (rows.isNotEmpty()) {
                val a = rows.last()[0]
                val ov = minOf(a.y1, b.y1) - maxOf(a.y0, b.y0)
                if (ov / minOf(a.y1 - a.y0, b.y1 - b.y0) >= ROW_OVERLAP) {
                    rows.last().add(b)
                    continue
                }
            }
            rows.add(mutableListOf(b))
        }
        return rows.map { r -> joinRuns(r.sortedBy { it.x0 }) }
    }

    fun joinRuns(row: List<Box>): List<Box> {
        val out = mutableListOf(row[0])
        for (b in row.drop(1)) {
            val a = out.last()
            if (b.x0 - a.x1 < JOIN_GAP * minOf(a.h, b.h)) {
                out[out.lastIndex] = a.copy(text = "${a.text} ${b.text}", x1 = maxOf(a.x1, b.x1),
                    y0 = minOf(a.y0, b.y0), y1 = maxOf(a.y1, b.y1),
                    score = minOf(a.score, b.score), lines = a.lines + b.lines)
            } else {
                out.add(b)
            }
        }
        return out
    }

    private val LABEL = rx("[가-힣 ]+")
    fun isLabel(text: String) = text.codePointCount(0, text.length) <= 8 && LABEL.matches(text)

    fun rowText(row: List<Box>): String =
        if (row.size == 2 && isLabel(row[0].text)) "${row[0].text}: ${row[1].text}"
        else row.joinToString(" | ") { it.text }

    // step 3
    fun mergeWraps(rows: List<List<Box>>): List<List<Box>> {
        val widest = rows.flatten().maxOfOrNull { it.x1 - it.x0 } ?: 1.0
        val out = mutableListOf<List<Box>>()
        for (r in rows) {
            if (out.isNotEmpty() && r.size == 1 && out.last().size == 1) {
                val a = out.last()[0]
                val b = r[0]
                val ha = a.h
                val hb = b.h
                val wa = a.w ?: (a.x1 - a.x0)
                val wb = b.x1 - b.x0
                if (b.y0 - a.y1 <= WRAP_GAP * minOf(ha, hb) &&
                    minOf(ha, hb) / maxOf(ha, hb) >= WRAP_HEIGHT &&
                    wa >= WRAP_WIDTH * widest && wb <= wa &&
                    !b.text.startsWith("-")
                ) {
                    // shortcut: always joins with a space (same as Python); a split word stays split
                    out[out.lastIndex] = listOf(a.copy(text = "${a.text} ${b.text}", x0 = minOf(a.x0, b.x0),
                        x1 = maxOf(a.x1, b.x1), y1 = b.y1, h = hb, w = wb,
                        score = minOf(a.score, b.score), lines = a.lines + b.lines))
                    continue
                }
            }
            out.add(r)
        }
        return out
    }

    // step 4 (display)
    private val COLON = rx("(?<!\\d)\\s*:\\s*(?=\\S)")
    private val BRACKET = rx("\\]\\s*(?=\\S)")
    private val HANGUL_NUM = rx("(?<=[가-힣])(?=\\d{4,})")
    private val TILDE = rx("\\s*~\\s*")
    fun tidy(s0: String): String {
        var s = COLON.replace(s0, ": ")
        s = BRACKET.replace(s, "] ")
        s = HANGUL_NUM.replace(s, " ")
        return TILDE.replace(s, " ~ ")
    }

    // step 5
    private val NUM = rx("\\d[\\d\\s.-]{10,}\\d")
    private val NON_DIGIT = rx("\\D")
    private val NUM_CHARS = rx("[\\d\\s.-]")

    data class BarcodeCandidate(val bare: Boolean, val length: Int, val digits: String, val box: Box)

    fun barcodeCandidates(boxes: List<Box>): List<BarcodeCandidate> {
        val cands = mutableListOf<BarcodeCandidate>()
        for (b in boxes) {
            for (m in NUM.findAll(b.text)) {
                val digits = NON_DIGIT.replace(m.value, "")
                if (digits.length in 12..20) {
                    val bare = NUM_CHARS.replace(b.text, "").isEmpty()
                    cands.add(BarcodeCandidate(bare, digits.length, digits, b))
                }
            }
        }
        return cands
    }

    /** Python `max(cands)` on (bare, len, digits) tuples. */
    fun bestBarcode(cands: List<BarcodeCandidate>): BarcodeCandidate? =
        cands.maxWithOrNull(compareBy<BarcodeCandidate>({ it.bare }, { it.length }, { it.digits }))

    fun barcode(boxes: List<Box>): String? = bestBarcode(barcodeCandidates(boxes))?.digits

    // step 6
    private val FULL = rx("(20\\d\\d)\\s*[./년-]\\s*(\\d{1,2})\\s*[./월-]\\s*(\\d{1,2})")
    private val NOYEAR = rx("(?<![\\d.:])(\\d{1,2})\\s*[/월]\\s*(\\d{1,2})\\s*일?\\s*까지")

    fun mkdate(y: String, m: String, d: String): LocalDate? = try {
        LocalDate.of(y.toInt(), m.toInt(), d.toInt())
    } catch (_: DateTimeException) {
        null
    } catch (_: NumberFormatException) {
        null
    }

    private fun mkdate(y: Int, m: String, d: String): LocalDate? = mkdate(y.toString(), m, d)

    /**
     * [date] equals Python `expiry(texts)`. The other fields are extra facts for "확인 필요"
     * (not in the Python baseline yet): which row won, whether its year was borrowed, all keyword candidates.
     */
    data class Expiry(
        val date: LocalDate,
        val rowIndex: Int,
        val fromKeywordRow: Boolean,
        val yearBorrowed: Boolean,
        val candidates: List<LocalDate>,
    )

    fun expiry(texts: List<String>): Expiry? {
        val full = texts.map { t -> FULL.findAll(t).mapNotNull { m -> mkdate(m.groupValues[1], m.groupValues[2], m.groupValues[3]) }.toList() }
        val years = full.flatten().map { it.year }
        data class Cand(val end: LocalDate, val row: Int, val borrowed: Boolean)
        val cands = mutableListOf<Cand>()
        val fallback = mutableListOf<Cand>()
        for ((i, t) in texts.withIndex()) {
            if ("발급" in t) continue
            var ds = full[i]
            var borrowedSet = emptySet<LocalDate>()
            if (years.isNotEmpty()) {
                val borrowed = NOYEAR.findAll(t).mapNotNull { m -> mkdate(years.max(), m.groupValues[1], m.groupValues[2]) }.toList()
                borrowedSet = (borrowed - ds.toSet()).toSet()
                ds = ds + borrowed
            }
            if (ds.isEmpty()) continue
            val end = ds.max()
            val c = Cand(end, i, end in borrowedSet)
            if (DATE_KEYWORDS.any { it in t }) cands.add(c) else fallback.add(c)
        }
        // Python min()/max() return the first extreme element; minWith/maxWith keep that order too
        if (cands.isNotEmpty()) {
            val best = cands.minWith(compareBy { it.end })
            return Expiry(best.end, best.row, true, best.borrowed, cands.map { it.end }.distinct().sorted())
        }
        if (fallback.isNotEmpty()) {
            val best = fallback.maxWith(compareBy { it.end })
            return Expiry(best.end, best.row, false, best.borrowed, fallback.map { it.end }.distinct().sorted())
        }
        return null
    }

    fun process(lines: List<OcrLine>): Result {
        val kept = lines.filterNot(::isNoise)
        val boxes = kept.map(::box)
        val rows = mergeWraps(rowsOf(boxes))
        val texts = rows.map { tidy(rowText(it)) }
        return Result(texts, rows, lines.filter(::isNoise).map { it.text }, boxes, barcode(boxes), expiry(texts))
    }
}
