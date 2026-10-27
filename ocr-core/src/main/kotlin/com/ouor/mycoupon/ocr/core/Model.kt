package com.ouor.mycoupon.ocr.core

import java.time.LocalDate

/** Pixel point in original-image coordinates. */
data class Point(val x: Int, val y: Int)

data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    companion object {
        fun around(points: List<Point>) =
            Rect(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y })
    }
}

/** One recognized text line. Same shape as `lines[]` in pp-ocr-v5/out/(name).jsonl. */
data class OcrLine(val text: String, val score: Float, val poly: List<Point>)

/** ZXing result. [format] is the ZXing BarcodeFormat name, e.g. `CODE_128`, `EAN_13`, `QR_CODE`. */
data class DecodedBarcode(val value: String, val format: String, val box: Rect)

enum class CouponType { EXCHANGE, AMOUNT }

enum class Field { BRAND, PRODUCT_NAME, TYPE, FACE_VALUE, CODE, EXPIRY_DATE, ISSUER, SENDER, NOTES }

/**
 * Why a field needs the user's check ("확인 필요"). UI maps each code to a sentence (screens.md S5).
 */
enum class ReviewReason {
    /** Date had no year; the year was borrowed from another date on the image. "사진에 연도가 없어 추정했어요" */
    YEAR_INFERRED,
    /** Two or more different expiry candidates; the earliest was chosen. "날짜가 여러 개 있어요" (+ candidates) */
    MULTIPLE_CANDIDATES,
    /** No validity keyword row; the date was guessed from other rows. "유효기간 글자를 찾지 못해 추정했어요" */
    NO_KEYWORD,
    /** OCR confidence of the source line is below the threshold. "글자가 흐려 잘못 읽었을 수 있어요" */
    LOW_CONFIDENCE,
    /** Value not found at all. "…를 찾지 못했어요. 직접 입력해 주세요" */
    NOT_FOUND,
    /** Decoded barcode and OCR digits differ. "바코드와 숫자가 달라요" */
    CODE_MISMATCH,
    /** Barcode could not be decoded; the number comes from OCR only. "바코드를 읽지 못해 글자로 읽었어요" */
    CODE_NOT_DECODED,
    /** Sender read from "○○님이 보낸" text; always confirmed by the user (spec F2). */
    SENDER_DETECTED,
}

data class Extraction(
    val isCoupon: Boolean,
    val brand: String?,
    val productName: String?,
    val type: CouponType?,
    val faceValue: Int?,
    val code: String?,
    /** ZXing format name, `PIN` (number without a barcode) or null when there is no number. */
    val codeFormat: String?,
    val codeDecoded: Boolean,
    val expiryDate: LocalDate?,
    /** Distinct expiry candidates (sorted), for the S5 candidate chips. */
    val expiryCandidates: List<LocalDate>,
    val issuer: String?,
    val sender: String?,
    val notes: String?,
    val needsReview: Map<Field, ReviewReason>,
    val fieldBoxes: Map<Field, Rect>,
    /** Display rows after steps 1-4 (not persisted; for debugging and parity checks). */
    val rows: List<String>,
)
