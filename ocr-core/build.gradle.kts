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
