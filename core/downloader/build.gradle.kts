plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.lhacenmed.sona.core.downloader"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        // The version of youtubedl-android's runtime: its programs are in the app, and the rest of it - Python's and
        // FFmpeg's libraries - is downloaded from this very version, so the two always match. See DownloaderRuntime.
        buildConfigField("String", "RUNTIME_VERSION", "\"${libs.versions.youtubedlAndroid.get()}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))

    implementation(libs.androidx.core.ktx)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    // What a download carries of its site beyond its format, written into it as tags - see EmbeddedExtras.
    implementation(libs.taglib)

    // Their programs - Python's, FFmpeg's, ffprobe's and QuickJS's, a megabyte and a half - are what the app carries of
    // them: what they are run with, downloaded, is left out of it - see the app's packaging.
    implementation(libs.youtubedl.android.library)
    implementation(libs.youtubedl.android.ffmpeg)

    testImplementation(libs.junit)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
