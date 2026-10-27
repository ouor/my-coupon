package com.ouor.mycoupon.ocr.core

import java.time.LocalDate

/** Pixel point in original-image coordinates. */
data class Point(val x: Int, val y: Int)

/** Axis-aligned box [x0, y0, x1, y1] in original-image pixels (fieldBoxes / rows in docs/ocr/porting-spec.md). */
data class Box(val x0: Double, val y0: Double, val x1: Double, val y1: Double) {
    companion object {
        fun around(xs: List<Double>, ys: List<Double>) = Box(xs.min(), ys.min(), xs.max(), ys.max())
    }
}

/** One recognized text line (PP-OCRv5 rec_text / rec_score / rec_poly). */
data class OcrLine(val text: String, val score: Double, val poly: List<Point>)

/** ZXing result. [format] is passed through as given (ZXing format name). [box] = bounding box of its result points. */
data class DecodedBarcode(val value: String, val format: String, val box: Box)

enum class CouponType { EXCHANGE, AMOUNT }

enum class CodeSource { DECODED, OCR }

/** Field keys of needsReview / fieldBoxes; [key] is the JSON key in porting-spec.md. */
enum class Field(val key: String) {
    BRAND("brand"), PRODUCT_NAME("productName"), FACE_VALUE("faceValue"), CODE("code"),
    EXPIRY_DATE("expiryDate"), ISSUER("issuer"), SENDER("sender"),
}

/** Why a field needs the user's check ("확인 필요"). UI copy per code: docs/design/screens.md S5. */
enum class ReviewReason {
    /** Date had no year; the year was borrowed from another date on the image. */
    YEAR_INFERRED,
    /** Two or more different candidates (expiry dates, face values); the safest was chosen. */
    MULTIPLE_CANDIDATES,
    /** No validity keyword on the date's row; the date was guessed. */
    NO_KEYWORD,
    /** OCR confidence of the source line is below 0.9. */
    LOW_CONFIDENCE,
    /** Value not found (also: a two-digit-year deadline, which is never guessed). */
    NOT_FOUND,
    /** Decoded barcode and the printed number differ. */
    CODE_MISMATCH,
    /** No barcode decoded; the number comes from OCR only. */
    CODE_NOT_DECODED,
    /** Sender read from "○○님이 보낸": always confirmed by the user. */
    ALWAYS_CHECK,
    /** Another title-like line of similar size nearby: the product name pick may be wrong. */
    AMBIGUOUS,
}

/** Display row after noise / row / wrap / tidy steps. */
data class TextRow(val text: String, val box: Box, val notes: Boolean)

/** Output of [Extract.extract]; mirrors the JSON in docs/ocr/porting-spec.md (version "2"). */
data class Extraction(
    val version: String,
    val isCoupon: Boolean,
    val brand: String?,
    val productName: String?,
    val type: CouponType,
    val faceValue: Long?,
    val code: String?,
    /** ZXing format name, "NONE" for an OCR-only number, null when there is no number. */
    val codeFormat: String?,
    val codeSource: CodeSource?,
    val expiryDate: LocalDate?,
    /** Distinct expiry candidates, ascending (S5 candidate chips). */
    val expiryCandidates: List<LocalDate>,
    val issuer: String?,
    val sender: String?,
    val notes: String?,
    /** Reasons in order per field; a field without an entry needs no check. */
    val needsReview: Map<Field, List<ReviewReason>>,
    val fieldBoxes: Map<Field, Box>,
    val rows: List<TextRow>,
    /** App-only, not compared with the goldens (C2-18): the row box each expiry candidate was read from. */
    val expiryCandidateBoxes: Map<LocalDate, Box> = emptyMap(),
)
