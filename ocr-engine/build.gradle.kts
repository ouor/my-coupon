// Pure Kotlin PP-OCRv5 engine on the ONNX Runtime Java API. The app links onnxruntime-android,
// JVM tests link the desktop onnxruntime (same ai.onnxruntime API) to check parity on the PC.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(17) }

dependencies {
    api(project(":ocr-core"))
    api(libs.zxing.core)
    compileOnly(libs.onnxruntime.jvm)
    testImplementation(libs.onnxruntime.jvm)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.serialization.json)
}

tasks.test {
    systemProperty("mycoupon.testData", rootProject.file(".test").absolutePath)
    systemProperty("mycoupon.models", rootProject.file("app/src/main/assets/ocr").absolutePath)
    maxHeapSize = "2g"
    // JDK 17's bundled msvcp140.dll is too old for onnxruntime 1.31 on Windows (native crash); run tests on 21
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)); vendor.set(JvmVendorSpec.ORACLE) })
}
