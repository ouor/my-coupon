import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize) // code review A8: S4/S5 edit state in SavedState
    // security review S-05: dependency list + license texts generated at build time into res/raw (no runtime library, no network in the app)
    alias(libs.plugins.aboutlibraries)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.ouor.mycoupon"
    // Compose 1.12 / androidx.hilt 1.4 need compileSdk 37; targetSdk stays 36 (the test device runs API 36)
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.ouor.mycoupon"
        // 26: java.time without desugaring (code review A1, lead decision 2026-10-28)
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        // code review O-04: expected model hashes, checked at build (verifyOcrModels) and after extraction on the device
        rootProject.file("gradle/ocr-models.sha256").readLines().filter { it.isNotBlank() }.forEach { l ->
            val (hash, file) = l.trim().split(Regex("\\s+"))
            buildConfigField("String", "SHA256_" + file.substringBefore('.').uppercase(), "\"$hash\"")
        }
        ndk {
            // spec §2.3: arm64 only (APK <= 40MB)
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "src/main/keepRules/rules.keep")
            // shortcut: release is signed with the debug key until the release keystore exists (spec §8.5)
            signingConfig = signingConfigs.getByName("debug")
        }
        // non-debuggable release-like build with BenchActivity (src/bench): OCR timings on the device (docs/core/ocr-runtime.md)
        create("bench") {
            initWith(getByName("release"))
            isMinifyEnabled = false
            isShrinkResources = false
            matchingFallbacks += "release"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = false
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        // part of `check`: NewApi / ContextCastToActivity and every other error fail the build (code review A1/A12)
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    // Robolectric reads the debug variant's merged assets, not test assets: the schema JSON ships in debug builds only (A-01)
    sourceSets.getByName("debug").assets.srcDir("$projectDir/schemas")
    packaging {
        resources.excludes += setOf("META-INF/*.version", "META-INF/LICENSE*", "META-INF/NOTICE*", "DebugProbesKt.bin")
        // spec §2.3 APK <= 40MB: libonnxruntime.so is 34MB stored, 13MB deflated. Costs install-time extraction.
        jniLibs.useLegacyPackaging = true
    }
    // models stay compressed in the APK (read fully into memory once, at the first recognition)
}

kotlin { jvmToolchain(17) }

// S-05: offline only (no remote license fetch); OCR models, BouncyCastle and ORT copyright lines come from aboutlibraries/
aboutLibraries {
    collect {
        configPath = file("aboutlibraries")
        fetchRemoteLicense = false
        fetchRemoteFunding = false
    }
}

// code review O-04: the shipped models must be the ones in gradle/ocr-models.sha256 (docs/core/ocr-runtime.md)
val verifyOcrModels = tasks.register("verifyOcrModels") {
    val list = rootProject.file("gradle/ocr-models.sha256")
    val dir = file("src/main/assets/ocr")
    inputs.file(list); inputs.dir(dir)
    doLast {
        list.readLines().filter { it.isNotBlank() }.forEach { l ->
            val (hash, name) = l.trim().split(Regex("\\s+"))
            val got = MessageDigest.getInstance("SHA-256").digest(File(dir, name).readBytes()).joinToString("") { "%02x".format(it) }
            if (got != hash) throw GradleException("assets/ocr/$name SHA-256 $got != $hash in ${list.name}")
        }
    }
}
tasks.named("preBuild") { dependsOn(verifyOcrModels) }

// spec §7.1 / code review S-01: the merged manifest must never ask for the network or keep ORT's telemetry initializer
androidComponents {
    onVariants { variant ->
        val name = variant.name.replaceFirstChar { it.uppercase() }
        val manifest = variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.MERGED_MANIFEST)
        val variantName = variant.name
        val check = tasks.register("verifyNoNetwork$name") {
            inputs.file(manifest)
            doLast {
                val text = manifest.get().asFile.readText()
                val bad = listOf("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE", "ai.onnxruntime.TelemetryInitializer")
                    .filter { it in text }
                if (bad.isNotEmpty()) throw GradleException("merged manifest of $variantName contains $bad")
            }
        }
        tasks.named("check") { dependsOn(check) }
        tasks.matching { it.name == "assemble$name" }.configureEach { dependsOn(check) }
    }
}

dependencies {
    implementation(project(":ocr-engine"))
    implementation(libs.onnxruntime.android)
    implementation(libs.zxing.core)
    // Argon2id for the backup password (security review S-02; docs/core/backup-kdf.md). R8 keeps only the classes used.
    implementation(libs.bouncycastle.prov)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.navigation.compose)
    implementation(libs.coil.compose)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime.ktx)
    implementation(libs.glance.appwidget)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.hilt.viewmodel.compose)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Room on the JVM (code review A-01 migration frame, A-05 CouponActions on a real DB)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.room.testing)
}

// code review A-01: the exported schemas are committed; MigrationTestHelper reads them as test assets
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
// a new N.json is written by KSP; merge the test assets after it, or a clean build misses it
tasks.matching { it.name == "mergeDebugAssets" }.configureEach { dependsOn("kspDebugKotlin") }
