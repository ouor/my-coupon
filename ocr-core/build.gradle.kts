import java.security.MessageDigest

// Pure Kotlin (no Android): OCR lines + barcodes -> coupon fields. Port of ocr-lab/extract.py (docs/ocr/porting-spec.md).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(17) }

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.serialization.json)
}

tasks.test {
    // parity fixtures live in the git-ignored .test/ folder; tests are skipped (not passed) when it is missing
    systemProperty("mycoupon.testData", rootProject.file(".test").absolutePath)
    systemProperty("mycoupon.requireRealGoldens", (findProperty("mycoupon.requireRealGoldens") ?: "false").toString())
    systemProperty("mycoupon.golden", rootProject.file("ocr-lab/tests/golden").absolutePath)
    testLogging { showStandardStreams = true }
}

// Code review 2 A2-03: the goldens must come from the current ocr-lab/extract.py. Without running Python, the hashes of
// extract.py and of the goldens are compared with golden-stamp.sha256; a change to either fails `check` until the goldens
// are regenerated (python ocr-lab/make_golden.py) and the stamp is rewritten with `./gradlew :ocr-core:updateGoldenStamp`.
val goldenSources = mapOf(
    "extract.py" to rootProject.file("ocr-lab/extract.py"),
    "golden-synthetic" to rootProject.file("ocr-lab/tests/golden"),
    "golden-real" to rootProject.file(".test/golden"), // git-ignored; checked only where it exists
)
val goldenStamp = file("golden-stamp.sha256")

object GoldenHash {
    fun of(f: File): String? {
        if (!f.exists()) return null
        val md = MessageDigest.getInstance("SHA-256")
        val files = if (f.isDirectory) f.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".json") }.sortedBy { it.name } else listOf(f)
        // CR dropped: the hash must not depend on git's line-ending conversion
        files.forEach { md.update(it.name.toByteArray()); md.update(it.readBytes().filter { b -> b != 13.toByte() }.toByteArray()) }
        return md.digest().joinToString("") { b -> "%02x".format(b) }
    }
}

val goldenFresh = tasks.register("goldenFresh") {
    description = "Fails when extract.py or the goldens changed since golden-stamp.sha256 (A2-03)"
    val sources = goldenSources
    val stampFile = goldenStamp
    inputs.files(sources.values.filter { it.exists() }, stampFile)
    doLast {
        val stamp = stampFile.readLines().filter { it.isNotBlank() }.associate { it.substringAfter("  ") to it.substringBefore("  ") }
        val stale = sources.mapNotNull { (k, f) -> GoldenHash.of(f)?.takeIf { it != stamp[k] }?.let { k } }
        if (stale.isNotEmpty()) throw GradleException(
            "goldens may be stale: $stale changed since ${stampFile.name}. Regenerate with python ocr-lab/make_golden.py, " +
                "run :ocr-core:test, then :ocr-core:updateGoldenStamp",
        )
    }
}
tasks.named("check") { dependsOn(goldenFresh) }

tasks.register("updateGoldenStamp") {
    description = "Records the current extract.py and golden hashes after the goldens were regenerated (A2-03)"
    val sources = goldenSources
    val stampFile = goldenStamp
    doLast {
        stampFile.writeText(sources.mapNotNull { (k, f) -> GoldenHash.of(f)?.let { "$it  $k" } }.joinToString("\n", postfix = "\n"))
    }
}
