package com.ouor.mycoupon

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Code rule for QA R1-19 / security S-06: an exception message never carries coupon data (code, PIN, brand, product,
 * memo, authCode, givenTo). Android's pre-handler writes the original uncaught exception to the crash log buffer before CrashLog can scrub
 * it, so the only fix is not to put the data there. This test finds every string template inside an exception or
 * require/check/error message in production sources; each interpolated expression must be on the allow list below,
 * with a reason. A new one fails the build until it is reviewed here.
 */
class NoCouponDataInExceptionsTest {
    private val allowed = mapOf(
        "start[7]" to "backup header version byte",
        "counter" to "backup segment index",
        "E::class.simpleName" to "enum type name",
        "e.javaClass.simpleName" to "exception class name",
        "name" to "OCR model file name (det.onnx / rec.onnx)",
        "classes" to "rec model class count",
        "chars.size" to "dictionary size",
        "won(balance)" to "UsageInputException: S8 copy with the current balance; always caught by CouponViewModel, never a code",
    )

    @Test
    fun exceptionMessagesHoldNoCouponData() {
        val roots = listOf("src/main/java", "../ocr-core/src/main", "../ocr-engine/src/main").map(::File)
        assertTrue("source roots missing", roots.all { it.isDirectory })
        val call = Regex("""(Exception|Error)\(|\b(require|check|error|requireNotNull|checkNotNull)\(""")
        val template = Regex("""\$\{([^}]*)\}|\$([A-Za-z_][A-Za-z0-9_]*)""")
        // shortcut: one line at a time; a message string split over lines is not seen. Upgrade to a Kotlin PSI scan if that style appears.
        val bad = mutableListOf<String>()
        for (f in roots.flatMap { it.walk().filter { f -> f.extension == "kt" }.toList() }) {
            f.readLines().forEachIndexed { i, line ->
                val at = call.find(line)?.range?.first ?: return@forEachIndexed
                val tail = line.substring(at)
                val strings = Regex("\"([^\"\\\\]|\\\\.)*\"").findAll(tail).map { it.value }
                for (s in strings) for (m in template.findAll(s)) {
                    val expr = m.groupValues[1].ifEmpty { m.groupValues[2] }
                    if (expr !in allowed) bad += "${f.name}:${i + 1} \$$expr"
                }
            }
        }
        assertTrue("exception messages interpolate values not reviewed for coupon data:\n" + bad.joinToString("\n"), bad.isEmpty())
    }
}
