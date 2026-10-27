// Pure Kotlin (no Android): OCR lines + barcodes -> coupon fields. Port of the Python reference (pp-ocr-v5/postprocess.py).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(17) }

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.serialization.json)
}

tasks.test {
    // parity fixtures live in the git-ignored .test/ folder; tests skip when it is missing
    systemProperty("mycoupon.testData", rootProject.file(".test").absolutePath)
}
