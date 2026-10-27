package com.ouor.mycoupon.ocr.core

import java.time.LocalDate

/**
 * OCR lines + decoded barcodes -> coupon fields (spec §8.3 contract `extract`).
 *
 * Code and expiry come from the Python baseline ([Postprocess] steps 5-6). Everything marked
 * PROVISIONAL below is a core-side stand-in so the app has data before the recognition owner's
 * rules (brand, product, type, amount, coupon/not, notes, review rules) are frozen and ported.
 * Replace those parts with the port of ocr-lab; keep this function's signature.
 */
object Extractor {
    /** PROVISIONAL: a date/code row below this OCR score is flagged LOW_CONFIDENCE. */
    const val LOW_SCORE = 0.9

    fun extract(lines: List<OcrLine>, barcodes: List<DecodedBarcode>, width: Int, height: Int): Extraction {
        val pp = Postprocess.process(lines)
        val review = linkedMapOf<Field, ReviewReason>()
        val boxes = linkedMapOf<Field, Rect>()

        // --- code: decoded barcode wins (spec F2); OCR digits only confirm it ---
        val decoded = barcodes.firstOrNull { it.value.isNotBlank() }
        val ocrCand = Postprocess.bestBarcode(Postprocess.barcodeCandidates(pp.boxes))
        val code: String?
        val format: String?
        if (decoded != null) {
            code = decoded.value
            format = decoded.format
            boxes[Field.CODE] = decoded.box
            val decodedDigits = decoded.value.filter { it.isLetterOrDigit() }
            val ocrMatches = Postprocess.barcodeCandidates(pp.boxes).any { it.digits == decodedDigits }
            if (ocrCand != null && !ocrMatches && decoded.format != "QR_CODE") review[Field.CODE] = ReviewReason.CODE_MISMATCH
        } else if (ocrCand != null) {
            code = ocrCand.digits
            format = "PIN"
            boxes[Field.CODE] = ocrCand.box.rect()
            review[Field.CODE] = ReviewReason.CODE_NOT_DECODED
        } else {
            code = null
            format = null
        }

        // --- expiry (Python step 6) + review reasons (spec F2 minimum conditions) ---
        val exp = pp.expiry
        if (exp == null) {
            review[Field.EXPIRY_DATE] = ReviewReason.NOT_FOUND
        } else {
            val rowBoxes = pp.rowBoxes[exp.rowIndex]
            boxes[Field.EXPIRY_DATE] = rowBoxes.rect()
            val reason = when {
                exp.candidates.size >= 2 -> ReviewReason.MULTIPLE_CANDIDATES
                !exp.fromKeywordRow -> ReviewReason.NO_KEYWORD
                exp.yearBorrowed -> ReviewReason.YEAR_INFERRED
                rowBoxes.minOf { it.score } < LOW_SCORE -> ReviewReason.LOW_CONFIDENCE
                else -> null
            }
            if (reason != null) review[Field.EXPIRY_DATE] = reason
        }

        // --- PROVISIONAL fields ---
        val rows = pp.rows
        val amount = rows.firstNotNullOfOrNull { AMOUNT_VOUCHER.find(it) }
        val faceValue = amount?.groupValues?.get(1)?.filter(Char::isDigit)?.toIntOrNull()
        val type = if (faceValue != null) CouponType.AMOUNT else CouponType.EXCHANGE
        val issuerIdx = rows.indexOfFirst { ISSUER.containsMatchIn(it) }
        val issuer = rows.getOrNull(issuerIdx)?.let { ISSUER.find(it)!!.value.trim() }
        val senderMatch = rows.firstNotNullOfOrNull { SENDER.find(it) }
        val sender = senderMatch?.groupValues?.get(1)
        if (sender != null) review[Field.SENDER] = ReviewReason.SENDER_DETECTED
        val notesStart = rows.indexOfFirst { NOTES_HEAD.containsMatchIn(it) }
        val notes = if (notesStart >= 0) rows.drop(notesStart).joinToString("\n") else null

        val labelBrand = rows.firstNotNullOfOrNull { BRAND_LABEL.find(it)?.groupValues?.get(1)?.trim() }
        val productIdx = productRow(pp, exp?.rowIndex, notesStart)
        val productName = productIdx?.let { rows[it] }
        if (productIdx != null) boxes[Field.PRODUCT_NAME] = pp.rowBoxes[productIdx].rect()
        val brand = labelBrand
            ?: productName?.let { BRACKET_BRAND.find(it)?.groupValues?.drop(1)?.firstOrNull { g -> g.isNotEmpty() } }
        if (brand == null) review[Field.BRAND] = ReviewReason.NOT_FOUND
        if (productName == null) review[Field.PRODUCT_NAME] = ReviewReason.NOT_FOUND

        val isCoupon = decoded != null || (code != null && exp != null)
        return Extraction(
            isCoupon = isCoupon,
            brand = brand, productName = productName, type = type, faceValue = faceValue,
            code = code, codeFormat = format, codeDecoded = decoded != null,
            expiryDate = exp?.date, expiryCandidates = exp?.candidates ?: emptyList<LocalDate>(),
            issuer = issuer, sender = sender, notes = notes,
            needsReview = review, fieldBoxes = boxes, rows = rows,
        )
    }

    /** PROVISIONAL: the tallest Hangul row above the expiry row that is not a label/date/number row. */
    private fun productRow(pp: Postprocess.Result, expiryRow: Int?, notesStart: Int): Int? {
        val end = listOfNotNull(expiryRow, notesStart.takeIf { it >= 0 }).minOrNull() ?: pp.rows.size
        return (0 until end)
            .filter { i ->
                val t = pp.rows[i]
                HANGUL2.containsMatchIn(t) && ':' !in t && '|' !in t && !ISSUER.containsMatchIn(t) &&
                    !DIGITS8.containsMatchIn(t) && !SENDER.containsMatchIn(t)
            }
            .maxByOrNull { i -> pp.rowBoxes[i].maxOf { it.h } }
    }

    private fun Postprocess.Box.rect() = Rect(x0.toInt(), y0.toInt(), x1.toInt(), y1.toInt())
    private fun List<Postprocess.Box>.rect() =
        Rect(minOf { it.x0 }.toInt(), minOf { it.y0 }.toInt(), maxOf { it.x1 }.toInt(), maxOf { it.y1 }.toInt())

    private val AMOUNT_VOUCHER = Regex("(?U)(\\d{1,3}(?:,\\d{3})+|\\d+)\\s*원\\s*(?:권|금액권|상품권)")
    private val ISSUER = Regex("(?U)\\S*\\s?선물하기")
    private val SENDER = Regex("(?U)(\\S+)\\s*님이\\s*보낸")
    private val NOTES_HEAD = Regex("(?U)유의\\s*사항|이용\\s*안내|사용\\s*안내|^\\s*-")
    private val BRAND_LABEL = Regex("(?U)(?:교환처|사용처|브랜드)\\s*:\\s*(.+)")
    private val BRACKET_BRAND = Regex("(?U)^\\s*(?:\\[([^\\]]+)]|\\(([^)]+)\\))")
    private val HANGUL2 = Regex("(?U)[가-힣].*[가-힣]")
    private val DIGITS8 = Regex("(?U)\\d{8,}")
}
