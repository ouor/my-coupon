package com.ouor.mycoupon.ocr.core

import java.time.DateTimeException
import java.time.LocalDate

/**
 * Coupon field extraction from OCR lines + decoded barcodes: a line-for-line port of ocr-lab/extract.py
 * (VERSION "2", rules in docs/ocr/porting-spec.md). Keep it a port: change the Python first, then mirror here;
 * ExtractParityTest compares every key with the goldens.
 * Python `re` is Unicode-aware, hence (?U) on every pattern; lengths count code points like Python len().
 */
object Extract {
    const val VERSION = "2"

    private const val MIN_SCORE = 0.6
    private const val ROW_OVERLAP = 0.5
    private const val JOIN_GAP = 1.0
    private const val WRAP_GAP = 0.5
    private const val WRAP_HEIGHT = 0.8
    private const val WRAP_HEIGHT_CUE = 0.6
    private const val WRAP_WIDTH = 0.75
    private const val REVIEW_SCORE = 0.9
    private const val RIVAL_HEIGHT = 0.8
    private const val RIVAL_DIST = 6.0

    private fun re(p: String) = Regex("(?U)$p")

    private val NOTES_HEADER = re("(유의|주의|안내|참고|이용)\\s*(사항|안내|방법)|이용\\s*안내|사용\\s*방법|꼭\\s*확인")
    private val BULLET = re("^\\s*[-·•*※ㆍ]")
    private val LABEL_TAIL = re("(기간|기한|처|명|번호|일|일시|금액|수량|매장|장소|종류|가격|상품|브랜드)$")
    private val DATE_KEYWORDS = listOf("유효", "사용", "까지", "기간", "만료", "~")
    private val NOTE_DATE_KEYWORDS = listOf("유효", "사용", "만료")
    private val VALIDITY = re("유효|기한|기간|까지|만료")
    private val NOT_EXPIRY = re("발급|발행|주문|결제|구매|승인|거래|발송|등록")
    private val NOT_CODE = re("주문|승인|사업자|전화|TEL|tel|Tel|카드|가맹|고객센터|문의|계좌|운송장")
    private val BRAND_LABEL = re("^(교환처|사용처|브랜드명?|매장명?|사용\\s*매장|사용\\s*브랜드|가맹점명?)$")
    private val AMOUNT_TYPE = re("\\d\\s*원\\s*권|만\\s*원\\s*권|천\\s*원\\s*권|금액권|사용\\s*가능\\s*금액|잔액|충전\\s*금액")
    private val VALUE_LABEL = re("(사용\\s*가능\\s*)?금액|잔액|권종|액면")
    private val TAGS = re("^(EVENT|event|Event|NEW|New|HOT|BEST|이벤트|증정|특가|한정|신규|단독|기획|선물)$")
    private val COUPON_WORDS = re("교환권|금액권|상품권|쿠폰|기프티|선물하기|교환처|사용처|유효\\s*기간|사용\\s*기간|까지\\s*사용|사용\\s*가능")
    private val NUM = re("[0-9A-Za-z]?\\d[\\d\\s.\\-]{10,}\\d")
    private val FULL = re("(20\\d\\d)\\s*[./년-]\\s*(\\d{1,2})\\s*[./월-]\\s*(\\d{1,2})")
    private val SHORT = re("(?<![\\d.])\\d{2}\\s*[./-]\\s*\\d{1,2}\\s*[./-]\\s*\\d{1,2}(?!\\d)")
    private val NOYEAR = re("(?<![\\d.:/])(\\d{1,2})\\s*[/월.]\\s*(\\d{1,2})\\s*일?\\s*까지")
    private val AMOUNT = re("(\\d{1,3}(?:,\\d{3})+|\\d+)\\s*(만|천)?\\s*원(?!\\s*(이상|이하|미만|초과|할인|당))")
    private val HANGUL = re("[가-힣]")
    private val LATIN = re("[A-Za-z]")
    private val NON_WORD = re("[\\W_]+")
    private val ALNUM_KO = re("[0-9A-Za-z가-힣]")
    private val WORDS = re("[가-힣A-Za-z0-9]{2,}")

    // ---------- geometry / text helpers ----------

    private class Run(
        val text: String, val x0: Double, val y0: Double, val x1: Double, val y1: Double,
        val h: Double, val score: Double, val lh: Double? = null, val lw: Double? = null,
    ) {
        val box get() = Box(x0, y0, x1, y1)
    }

    private fun run(text: String, b: Box, score: Double, h: Double? = null) =
        Run(text, b.x0, b.y0, b.x1, b.y1, h ?: (b.y1 - b.y0), score)

    private fun union(bs: List<Box>) = Box(bs.minOf { it.x0 }, bs.minOf { it.y0 }, bs.maxOf { it.x1 }, bs.maxOf { it.y1 })

    private fun bbox(poly: List<Point>) =
        Box(poly.minOf { it.x }.toDouble(), poly.minOf { it.y }.toDouble(), poly.maxOf { it.x }.toDouble(), poly.maxOf { it.y }.toDouble())

    private fun len(s: String) = s.codePointCount(0, s.length)
    private fun hangul(s: String) = HANGUL.findAll(s).count()
    private fun norm(s: String) = NON_WORD.replace(s, "").lowercase()
    private fun strip(s: String) = s.trim()
    private fun String.label() = strip(strip(this).trimEnd(':'))

    // ---------- steps 1-3: noise, rows, wraps ----------

    private fun isNoise(l: OcrLine, bars: List<Box>): Boolean {
        if (l.score < MIN_SCORE || !ALNUM_KO.containsMatchIn(l.text)) return true
        val b = bbox(l.poly)
        val cx = (b.x0 + b.x1) / 2
        val cy = (b.y0 + b.y1) / 2
        return bars.any { it.x0 <= cx && cx <= it.x1 && it.y0 <= cy && cy <= it.y1 } &&
            !re("\\d{8,}").containsMatchIn(l.text.replace(" ", ""))
    }

    private fun rowsOf(runs: List<Run>): List<List<Run>> {
        val rows = ArrayList<MutableList<Run>>()
        for (b in runs.sortedBy { (it.y0 + it.y1) / 2 }) {
            if (rows.isNotEmpty()) {
                val a = rows.last()[0]
                val ov = minOf(a.y1, b.y1) - maxOf(a.y0, b.y0)
                if (ov / maxOf(1e-6, minOf(a.y1 - a.y0, b.y1 - b.y0)) >= ROW_OVERLAP) {
                    rows.last().add(b)
                    continue
                }
            }
            rows.add(mutableListOf(b))
        }
        return rows.map { r -> joinRuns(r.sortedBy { it.x0 }) }
    }

    private fun joinRuns(row: List<Run>): List<Run> {
        val out = mutableListOf(row[0])
        for (b in row.drop(1)) {
            val a = out.last()
            if (b.x0 - a.x1 < JOIN_GAP * minOf(a.h, b.h)) {
                out[out.size - 1] = run("${a.text} ${b.text}", union(listOf(a.box, b.box)), minOf(a.score, b.score), maxOf(a.h, b.h))
            } else {
                out.add(b)
            }
        }
        return out
    }

    private fun continues(a: String, b: String): Boolean {
        val bracket = a.count { it == '(' } > a.count { it == ')' } && b.count { it == ')' } > b.count { it == '(' }
        val ar = a.trimEnd()
        val bl = b.trimStart()
        return bracket || listOf("+", "&", ",", "(", "/").any { ar.endsWith(it) } || listOf("+", "&", ")").any { bl.startsWith(it) }
    }

    private fun repeats(a: String, b: String): Boolean {
        val words = WORDS.findAll(a).map { it.value }.toSet()
        return WORDS.findAll(b).any { it.value in words }
    }

    private fun mergeWraps(rows: List<List<Run>>): List<List<Run>> {
        val widest = rows.flatten().maxOfOrNull { it.x1 - it.x0 } ?: 1.0
        val out = ArrayList<List<Run>>()
        for (r in rows) {
            if (out.isNotEmpty() && r.size == 1 && out.last().size == 1) {
                val a = out.last()[0]
                val b = r[0]
                val ha = a.lh ?: a.h
                val hb = b.h
                val wa = a.lw ?: (a.x1 - a.x0)
                val wb = b.x1 - b.x0
                if (b.y0 - a.y1 <= WRAP_GAP * minOf(ha, hb) &&
                    minOf(ha, hb) / maxOf(ha, hb) >= (if (continues(a.text, b.text)) WRAP_HEIGHT_CUE else WRAP_HEIGHT) &&
                    !repeats(a.text, b.text) &&
                    wa >= WRAP_WIDTH * widest && wb <= wa &&
                    !BULLET.containsMatchIn(b.text) &&
                    listOf(a.text, b.text).none { FULL.containsMatchIn(it) || NUM.containsMatchIn(it) }
                ) {
                    val glue = if (b.text.startsWith("+") || listOf("+", "(", "/").any { a.text.endsWith(it) }) "" else " "
                    val m = run(a.text + glue + b.text, union(listOf(a.box, b.box)), minOf(a.score, b.score), a.h)
                    out[out.size - 1] = listOf(Run(m.text, m.x0, m.y0, m.x1, m.y1, m.h, m.score, lh = hb, lw = wb))
                    continue
                }
            }
            out.add(r)
        }
        return out
    }

    // ---------- step 4: display spacing, step 7: cells / sections ----------

    private val TIDY1 = re("(?<!\\d)\\s*:\\s*(?=\\S)")
    private val TIDY2 = re("\\]\\s*(?=\\S)")
    private val TIDY3 = re("(?<=[가-힣])(?=\\d{4,})")
    private val TIDY4 = re("\\s*~\\s*")

    internal fun tidy(s: String): String {
        var t = TIDY1.replace(s, ": ")
        t = TIDY2.replace(t, "] ")
        t = TIDY3.replace(t, " ")
        return strip(TIDY4.replace(t, " ~ "))
    }

    private val LABEL_CHARS = re("[가-힣 ]+")

    private fun isLabel(text: String): Boolean {
        val t = text.label()
        return len(t) in 2..8 && LABEL_CHARS.matches(t) && LABEL_TAIL.containsMatchIn(t)
    }

    private class Cell(
        val label: String?, val value: String, val text: String, val box: Box, val vbox: Box, val score: Double, val h: Double,
    )

    private class Row(val text: String, val cells: List<Cell>, val box: Box, val score: Double, val notes: Boolean)

    private val INLINE_LABEL = re("^\\s*([가-힣 ]{2,8}?)\\s*:\\s*(.+)$")

    private fun cellsOf(row: List<Run>): List<Cell> {
        val cells = ArrayList<Cell>()
        var i = 0
        while (i < row.size) {
            val a = row[i]
            if (i + 1 < row.size && isLabel(a.text)) {
                val b = row[i + 1]
                val lab = a.text.label()
                cells.add(Cell(lab, strip(b.text), "$lab: ${strip(b.text)}", union(listOf(a.box, b.box)), b.box, minOf(a.score, b.score), b.h))
                i += 2
                continue
            }
            val lab = INLINE_LABEL.find(a.text)
            if (lab != null && isLabel(lab.groupValues[1])) {
                cells.add(Cell(strip(lab.groupValues[1]), strip(lab.groupValues[2]), a.text, a.box, a.box, a.score, a.h))
            } else {
                cells.add(Cell(null, strip(a.text), a.text, a.box, a.box, a.score, a.h))
            }
            i += 1
        }
        return cells
    }

    private fun buildRows(lines: List<OcrLine>, bars: List<Box>): List<Row> {
        val runs = lines.filter { !isNoise(it, bars) }.map { run(it.text, bbox(it.poly), it.score) }
        val rows = ArrayList<Row>()
        var notes = false
        for (r in mergeWraps(rowsOf(runs))) {
            val cells = cellsOf(r)
            val text = tidy(cells.joinToString(" | ") { it.text })
            val header = len(norm(text)) <= 12 && NOTES_HEADER.containsMatchIn(text)
            notes = notes || header
            rows.add(Row(text, cells, union(cells.map { it.box }), cells.minOf { it.score }, notes || BULLET.containsMatchIn(text)))
        }
        return rows
    }

    // ---------- step 5: code ----------

    private class CodeCand(val value: String, val bare: Boolean, val box: Box, val score: Double)

    private val NON_DIGIT = re("\\D")
    private val CODE_CHARS = re("[\\d\\s.\\-]")
    private val NON_ALNUM = re("[^0-9A-Za-z]")

    private fun ocrCodes(rows: List<Row>): List<CodeCand> {
        val out = ArrayList<CodeCand>()
        for (r in rows) {
            if (NOT_CODE.containsMatchIn(r.text)) continue
            for (c in r.cells) for (m in NUM.findAll(c.value)) {
                val digits = NON_DIGIT.replace(m.value, "")
                if (len(digits) in 12..24) {
                    out.add(CodeCand(digits, CODE_CHARS.replace(c.value, "").isEmpty(), c.vbox, c.score))
                }
            }
        }
        return out
    }

    private class Code(val value: String?, val format: String?, val source: CodeSource?, val box: Box?, val review: List<ReviewReason>)

    private fun pickCode(rows: List<Row>, barcodes: List<DecodedBarcode>): Code {
        val review = ArrayList<ReviewReason>()
        val cands = ocrCodes(rows)
        val dec = barcodes.sortedBy { -(it.box.x1 - it.box.x0) * maxOf(1.0, it.box.y1 - it.box.y0) }
        if (dec.isNotEmpty()) {
            val known = cands.map { it.value }.toSet()
            val d = dec.firstOrNull { NON_ALNUM.replace(it.value, "") in known } ?: dec[0]
            val value = NON_ALNUM.replace(d.value, "")
            if (cands.isNotEmpty()) {
                val cy = (d.box.y0 + d.box.y1) / 2
                val near = cands.minBy { kotlin.math.abs((it.box.y0 + it.box.y1) / 2 - cy) }
                if (near.value != value) review.add(ReviewReason.CODE_MISMATCH)
            }
            return Code(value, d.format, CodeSource.DECODED, d.box, review)
        }
        if (cands.isEmpty()) return Code(null, null, null, null, listOf(ReviewReason.NOT_FOUND))
        val best = cands.maxWith(compareBy<CodeCand>({ it.bare }, { len(it.value) }))
        return Code(best.value, "NONE", CodeSource.OCR, best.box, listOf(ReviewReason.CODE_NOT_DECODED))
    }

    // ---------- step 6: expiry ----------

    private fun mkdate(y: String, m: String, d: String): LocalDate? =
        try { LocalDate.of(y.toInt(), m.toInt(), d.toInt()) } catch (_: DateTimeException) { null } catch (_: NumberFormatException) { null }

    private class Expiry(val value: LocalDate?, val dates: List<LocalDate>, val box: Box?, val review: List<ReviewReason>)

    private fun pickExpiry(rows: List<Row>): Expiry {
        val full = rows.map { r -> FULL.findAll(r.text).mapNotNull { mkdate(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }.toList() }
        val alld = full.flatten()
        val years = alld.map { it.year }
        val keyed = ArrayList<Pair<LocalDate, Row>>()
        val fallback = ArrayList<Pair<LocalDate, Row>>()
        val review = ArrayList<ReviewReason>()
        val inferred = HashSet<LocalDate>()
        for ((r, found) in rows.zip(full)) {
            if (NOT_EXPIRY.containsMatchIn(r.text)) continue
            if (r.notes && NOTE_DATE_KEYWORDS.none { it in r.text }) continue
            if (found.isEmpty() && SHORT.containsMatchIn(r.text) && DATE_KEYWORDS.any { it in r.text }) {
                return Expiry(null, emptyList(), r.box, listOf(ReviewReason.NOT_FOUND))
            }
            val ds = found.toMutableList()
            if (years.isNotEmpty()) {
                for (m in NOYEAR.findAll(r.text)) {
                    val (mo, d) = m.destructured
                    var x = mkdate(years.max().toString(), mo, d)
                    if (x != null && x < alld.min()) x = mkdate((years.max() + 1).toString(), mo, d)
                    if (x != null) {
                        ds.add(x)
                        inferred.add(x)
                    }
                }
            }
            if (ds.isEmpty()) continue
            val end = ds.max()
            if (DATE_KEYWORDS.any { it in r.text }) keyed.add(end to r)
            else if (!r.notes) fallback.add(end to r)
        }
        val dates: List<LocalDate>
        val pick: Pair<LocalDate, Row>
        if (keyed.isNotEmpty()) {
            dates = keyed.map { it.first }.toSortedSet().toList()
            pick = keyed.minBy { it.first }
            if (dates.size > 1) review.add(ReviewReason.MULTIPLE_CANDIDATES)
        } else if (fallback.isNotEmpty()) {
            dates = fallback.map { it.first }.toSortedSet().toList()
            pick = fallback.maxBy { it.first }
            review.add(ReviewReason.NO_KEYWORD)
            if (dates.size > 1) review.add(ReviewReason.MULTIPLE_CANDIDATES)
        } else {
            return Expiry(null, emptyList(), null, listOf(ReviewReason.NOT_FOUND))
        }
        val (value, src) = pick
        if (value in inferred) review.add(ReviewReason.YEAR_INFERRED)
        if (src.score < REVIEW_SCORE) review.add(ReviewReason.LOW_CONFIDENCE)
        return Expiry(value, dates, src.box, review)
    }

    // ---------- brand, product, type, amount, issuer ----------

    private val BRAND_PAREN = re("\\s*\\([^)]*\\)\\s*$")
    private val BRAND_STORE = re("\\s*(전\\s*)?(매장|지점)\\s*(,.*)?$")
    private val BRAND_GIFT = re("\\s*선물하기$")

    private fun cleanBrand(s: String): String {
        var t = BRAND_PAREN.replace(strip(s), "")
        t = BRAND_STORE.replace(t, "")
        t = BRAND_GIFT.replace(t, "")
        return t.trim { it in " :|," }
    }

    private class Found(val value: String, val box: Box, val score: Double)

    private fun brandFromLabels(rows: List<Row>): Found? {
        for (r in rows) {
            if (r.notes) continue
            for (c in r.cells) {
                if (c.label != null && BRAND_LABEL.containsMatchIn(c.label)) {
                    val b = cleanBrand(c.value)
                    if (b.isNotEmpty()) return Found(b, c.vbox, c.score)
                }
            }
        }
        return null
    }

    private fun unit(g: String?) = when (g) { "만" -> 10000L; "천" -> 1000L; else -> 1L }

    // shortcut: Long instead of Python's unbounded int; an amount past 19 digits saturates instead of matching Python
    private fun num(s: String) = s.replace(",", "").toLongOrNull() ?: Long.MAX_VALUE

    private fun amounts(s: String): List<Long> =
        AMOUNT.findAll(s).map { num(it.groupValues[1]) * unit(it.groups[2]?.value) }.filter { it > 0 }.toList()

    private val AMOUNT_ONLY = re("[\\d,\\s]*(만|천)?\\s*원")
    private val NOT_PRODUCT = re("선물하기$|님이\\s*보낸")

    private fun isProductText(t: String): Boolean {
        val v = strip(t)
        if (hangul(v) < 2 || FULL.containsMatchIn(v) || NUM.containsMatchIn(v)) return false
        if (AMOUNT_ONLY.matches(v)) return false
        if (NOT_PRODUCT.containsMatchIn(v) || NOTES_HEADER.containsMatchIn(v)) return false
        return !COUPON_WORDS.matches(norm(v))
    }

    private class Product(val cell: Cell, val index: Int, val cands: List<Cell>)

    private fun pickProduct(rows: List<Row>, limitY: Double, brand: String?): Product? {
        data class C(val h: Double, val negY: Double, val c: Cell, val i: Int)
        val cands = ArrayList<C>()
        rows.forEachIndexed { i, r ->
            if (r.notes || r.box.y0 >= limitY) return@forEachIndexed
            for (c in r.cells) {
                if (c.label != null || !isProductText(c.value)) continue
                if (!brand.isNullOrEmpty() && norm(c.value) == norm(brand)) continue
                cands.add(C(c.h, -c.box.y0, c, i))
            }
        }
        if (cands.isEmpty()) return null
        val best = cands.maxWith(compareBy<C>({ it.h }, { it.negY }))
        val c = best.c
        val i = best.i
        for (x in cands.sortedBy { it.i }) {
            val d = x.c
            val j = x.i
            if (j > i && j <= i + 2 && norm(d.value).contains(norm(c.value)) && len(norm(d.value)) > len(norm(c.value))) {
                return Product(d, j, cands.map { it.c })
            }
        }
        return Product(c, i, cands.map { it.c })
    }

    private val DIGIT = re("\\d")

    private fun ambiguous(prod: Cell, cands: List<Cell>, brand: String?): Boolean {
        for (d in cands) {
            if (d === prod || d.h < RIVAL_HEIGHT * prod.h) continue
            if (kotlin.math.abs((d.box.y0 + d.box.y1) / 2 - (prod.box.y0 + prod.box.y1) / 2) > RIVAL_DIST * prod.h) continue
            val v = d.value
            if ((!brand.isNullOrEmpty() && norm(v) == norm(brand)) || norm(prod.value).contains(norm(v)) || DIGIT.findAll(v).count() >= 6) continue
            if (repeats(prod.value, v) || repeats(v, prod.value)) continue
            return true
        }
        return false
    }

    private val BRACKETED = re("([(\\[])([^()\\[\\]]{3,})([)\\]])")

    private fun repeatedFix(text: String, rows: List<Row>): String {
        val others = LinkedHashMap<String, String>()
        for (r in rows) for (c in r.cells) if (len(norm(c.value)) >= 3) others[norm(c.value)] = strip(c.value)
        return BRACKETED.replace(text) { m ->
            val inner = m.groupValues[2]
            if (norm(inner) in others) return@replace m.value
            val cps = inner.codePoints().toArray()
            val dropLast = String(cps, 0, cps.size - 1)
            val dropFirst = String(cps, 1, cps.size - 1)
            for (cut in listOf(dropLast, dropFirst)) {
                if (len(norm(cut)) >= 3 && norm(cut) in others && strip(cut) == others[norm(cut)]) {
                    return@replace m.groupValues[1] + cut + m.groupValues[3]
                }
            }
            m.value
        }
    }

    private val BRACKET_PREFIX = re("^\\s*[\\[(]([^\\])]{1,15})[\\])]")

    private fun brandNearProduct(rows: List<Row>, pi: Int, prod: Cell): Found? {
        if (pi > 0) {
            val r = rows[pi - 1]
            val gap = prod.box.y0 - r.box.y1
            if (!r.notes && r.cells.size == 1 && r.cells[0].label == null &&
                gap <= 1.5 * prod.h && len(norm(r.text)) <= 15 &&
                !FULL.containsMatchIn(r.text) && !NUM.containsMatchIn(r.text) &&
                hangul(r.text) + LATIN.findAll(r.text).count() >= 2
            ) {
                val c = r.cells[0]
                return Found(cleanBrand(c.value), c.vbox, c.score)
            }
        }
        for (r in rows.subList(0, pi)) {
            val c = r.cells[0]
            if (!r.notes && r.cells.size == 1 && c.label == null && len(norm(c.value)) in 2..15 &&
                norm(prod.value).contains(norm(c.value)) && !NUM.containsMatchIn(c.value)
            ) {
                return Found(cleanBrand(c.value), c.vbox, c.score)
            }
        }
        val m = BRACKET_PREFIX.find(prod.value)
        if (m != null && !TAGS.containsMatchIn(strip(m.groupValues[1]))) return Found(strip(m.groupValues[1]), prod.vbox, prod.score)
        return null
    }

    private val PRODUCT_AMOUNT = re("(\\d{1,3}(?:,\\d{3})+|\\d+)\\s*(만|천)?\\s*원\\s*권")
    private val AMOUNT_CELL = re("\\s*(\\d{1,3}(?:,\\d{3})+|\\d+)\\s*(만|천)?\\s*원\\s*")

    private class TypePick(val type: CouponType, val value: Long?, val box: Box?, val review: List<ReviewReason>)

    private fun pickType(rows: List<Row>, product: String?, productBox: Box?): TypePick {
        val body = rows.filter { !it.notes }
        if (body.none { AMOUNT_TYPE.containsMatchIn(it.text) }) return TypePick(CouponType.EXCHANGE, null, null, emptyList())
        val vals = ArrayList<Long>()
        var box: Box? = null
        if (product != null) {
            val m = PRODUCT_AMOUNT.find(product)
            if (m != null) {
                vals.add(num(m.groupValues[1]) * unit(m.groups[2]?.value))
                box = productBox
            }
        }
        for (r in body) for (c in r.cells) {
            if (c.label != null && VALUE_LABEL.containsMatchIn(c.label) && amounts(c.value).isNotEmpty()) {
                vals.add(amounts(c.value)[0])
                box = box ?: c.vbox
            }
        }
        if (vals.isEmpty()) {
            for (r in body) for (c in r.cells) {
                // extract.py raises IndexError on a bare "0원" here; the app skips it instead
                if (AMOUNT_CELL.matches(c.value) && amounts(c.value).isNotEmpty()) {
                    vals.add(amounts(c.value)[0])
                    box = box ?: c.vbox
                }
            }
        }
        if (vals.isEmpty()) return TypePick(CouponType.AMOUNT, null, null, listOf(ReviewReason.NOT_FOUND))
        val review = if (vals.toSet().size > 1) listOf(ReviewReason.MULTIPLE_CANDIDATES) else emptyList()
        return TypePick(CouponType.AMOUNT, vals[0], box, review)
    }

    private val ISSUER = re("\\s*(\\S.{0,14}?)\\s*선물하기\\s*")

    private fun pickIssuer(rows: List<Row>, brand: String?, limitY: Double): Pair<String, Box>? {
        for (r in rows) {
            if (r.notes || (r.box.y1 <= limitY && !brand.isNullOrEmpty() && norm(r.text).contains(norm(brand)))) continue
            val m = ISSUER.matchEntire(r.text)
            if (m != null && !(!brand.isNullOrEmpty() && norm(m.groupValues[1]) == norm(brand))) {
                return (strip(m.groupValues[1]) + " 선물하기") to r.box
            }
        }
        return null
    }

    private val SENDER = re("([^\\s:]{1,10})\\s*님이\\s*보낸")

    // ---------- top level ----------

    fun extract(lines: List<OcrLine>, barcodes: List<DecodedBarcode>, width: Int, height: Int): Extraction {
        val bars = barcodes.map { it.box }
        val rows = buildRows(lines, bars)
        val review = LinkedHashMap<Field, List<ReviewReason>>()
        val boxes = LinkedHashMap<Field, Box>()

        val code = pickCode(rows, barcodes)
        if (code.review.isNotEmpty()) review[Field.CODE] = code.review
        code.box?.let { boxes[Field.CODE] = it }

        val exp = pickExpiry(rows)
        val expRow = if (exp.box == null) null else rows.firstOrNull { it.box == exp.box }?.text
        if (exp.review.isNotEmpty()) review[Field.EXPIRY_DATE] = exp.review
        exp.box?.let { boxes[Field.EXPIRY_DATE] = it }

        // the product title sits above the barcode; without one, above the printed code
        val limitY = bars.minOfOrNull { it.y0 } ?: code.box?.y0 ?: (height + 1).toDouble()
        val labelled = brandFromLabels(rows)
        val pp = pickProduct(rows, limitY, labelled?.value)
        var product: String? = null
        var brand: String? = null
        if (pp != null) {
            val prod = pp.cell
            product = repeatedFix(prod.value, rows)
            boxes[Field.PRODUCT_NAME] = prod.vbox
            val near = brandNearProduct(rows, pp.index, prod)
            val chosen = labelled ?: near
            var bscore = 1.0
            if (chosen != null) {
                brand = chosen.value
                boxes[Field.BRAND] = chosen.box
                bscore = chosen.score
            }
            val rv = ArrayList<ReviewReason>()
            if (ambiguous(prod, pp.cands, brand)) rv.add(ReviewReason.AMBIGUOUS)
            if (prod.score < REVIEW_SCORE) rv.add(ReviewReason.LOW_CONFIDENCE)
            if (rv.isNotEmpty()) review[Field.PRODUCT_NAME] = rv
            if (!brand.isNullOrEmpty() && bscore < REVIEW_SCORE) review[Field.BRAND] = listOf(ReviewReason.LOW_CONFIDENCE)
        } else {
            review[Field.PRODUCT_NAME] = listOf(ReviewReason.NOT_FOUND)
            if (labelled != null) {
                brand = labelled.value
                boxes[Field.BRAND] = labelled.box
            }
        }
        if (brand.isNullOrEmpty()) review[Field.BRAND] = listOf(ReviewReason.NOT_FOUND)

        val type = pickType(rows, product, boxes[Field.PRODUCT_NAME])
        if (type.review.isNotEmpty()) review[Field.FACE_VALUE] = type.review
        type.box?.let { boxes[Field.FACE_VALUE] = it }

        val issuer = pickIssuer(rows, brand, limitY)
        issuer?.let { boxes[Field.ISSUER] = it.second }
        var sender: String? = null
        for (r in rows) {
            val m = SENDER.find(r.text) ?: continue
            sender = m.groupValues[1]
            boxes[Field.SENDER] = r.box
            review[Field.SENDER] = listOf(ReviewReason.ALWAYS_CHECK)
            break
        }
        val notes = rows.filter { it.notes }.joinToString("\n") { it.text }.ifEmpty { null }

        // coupon or not: a scannable code + a validity date + coupon vocabulary
        val words = rows.any { COUPON_WORDS.containsMatchIn(it.text) }
        val keyedExpiry = exp.value != null && expRow != null && VALIDITY.containsMatchIn(expRow)
        val isCoupon = code.value != null && keyedExpiry && words

        return Extraction(
            version = VERSION,
            isCoupon = isCoupon,
            brand = brand?.ifEmpty { null },
            productName = product,
            type = type.type,
            faceValue = type.value,
            code = code.value,
            codeFormat = code.format,
            codeSource = code.source,
            expiryDate = exp.value,
            expiryCandidates = exp.dates,
            issuer = issuer?.first,
            sender = sender,
            notes = notes,
            needsReview = review,
            fieldBoxes = boxes,
            rows = rows.map { TextRow(it.text, it.box, it.notes) },
        )
    }
}
