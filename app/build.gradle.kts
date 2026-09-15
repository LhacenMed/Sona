import com.android.build.api.variant.FilterConfiguration
import com.android.build.api.variant.impl.VariantOutputImpl
import java.io.File
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

sealed class Version(
    open val versionMajor: Int,
    val versionMinor: Int,
    val versionPatch: Int,
    val versionBuild: Int = 0,
) {
    abstract fun toVersionName(): String

    class Alpha(
        versionMajor: Int, versionMinor: Int, versionPatch: Int, versionBuild: Int,
    ) : Version(versionMajor, versionMinor, versionPatch, versionBuild) {
        override fun toVersionName() = "$versionMajor.$versionMinor.$versionPatch-alpha.$versionBuild"
    }

    class Beta(
        versionMajor: Int, versionMinor: Int, versionPatch: Int, versionBuild: Int,
    ) : Version(versionMajor, versionMinor, versionPatch, versionBuild) {
        override fun toVersionName() = "$versionMajor.$versionMinor.$versionPatch-beta.$versionBuild"
    }

    class Stable(
        versionMajor: Int, versionMinor: Int, versionPatch: Int,
    ) : Version(versionMajor, versionMinor, versionPatch) {
        override fun toVersionName() = "$versionMajor.$versionMinor.$versionPatch"
    }

    class ReleaseCandidate(
        versionMajor: Int, versionMinor: Int, versionPatch: Int, versionBuild: Int,
    ) : Version(versionMajor, versionMinor, versionPatch, versionBuild) {
        override fun toVersionName() = "$versionMajor.$versionMinor.$versionPatch-rc.$versionBuild"
    }
}

val currentVersion: Version = Version.Alpha(
    versionMajor = 0,
    versionMinor = 1,
    versionPatch = 0,
    versionBuild = 1,
)

val keystorePropertiesFile: File = rootProject.file("keystore.properties")

// Release builds always emit per-ABI APKs + a universal one (store/sideload distribution);
// debug stays a single fast universal APK. Pass -Psplits to force splits for any build type.
val isReleaseBuild = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
val splitApks = project.hasProperty("splits") || isReleaseBuild

val abiFilterList = (properties["ABI_FILTERS"] as? String)
    ?.split(';')
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?: listOf("arm64-v8a")

android {
    namespace = "com.lhacenmed.sona"
    compileSdk = 37

    if (keystorePropertiesFile.exists()) {
        val keystoreProperties = Properties().apply {
            load(FileInputStream(keystorePropertiesFile))
        }
        signingConfigs {
            create("releaseKey") {
                keyAlias = keystoreProperties["keyAlias"].toString()
                keyPassword = keystoreProperties["keyPassword"].toString()
                storeFile = file(keystoreProperties["storeFile"]!!)
                storePassword = keystoreProperties["storePassword"].toString()
            }
        }
    }

    defaultConfig {
        applicationId = "com.lhacenmed.sona"
        minSdk = 26
        targetSdk = 36
        versionCode = currentVersion.run { versionMajor * 10000 + versionMinor * 100 + versionPatch }
        versionName = currentVersion.toVersionName()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // ABI splits are opt-in (-Psplits). Without the flag every build produces a
    // single APK filtered to abiFilterList, which can be sideloaded directly.
    if (splitApks) {
        splits {
            abi {
                isEnable = true
                reset()
                include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
                isUniversalApk = true
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            // Distinct application ID + label so a debug build installs alongside a
            // signed release build on the same device without either one overwriting
            // the other.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "Sona Debug")
            if (keystorePropertiesFile.exists()) signingConfig = signingConfigs.getByName("releaseKey")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePropertiesFile.exists()) signingConfig = signingConfigs.getByName("releaseKey")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true // required for resValue() in build types (AGP 8+)
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

androidComponents {
    onVariants { variant ->
        val abiCodes = mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2, "x86" to 3, "x86_64" to 4)
        variant.outputs.forEach { output ->
            val abi = output.filters
                .find { it.filterType == FilterConfiguration.FilterType.ABI }
                ?.identifier
            // Per-ABI APKs get a distinct versionCode so each ABI is independently updatable.
            // The single non-split APK keeps the base code (no bump).
            if (splitApks) {
                abiCodes[abi ?: abiFilterList.firstOrNull()]?.let { code ->
                    output.versionCode.set(code + (output.versionCode.get() ?: 0))
                }
            }
            // Name every artifact "sona-<version>-<abi>.apk" ("…-universal.apk" for the
            // ABI-less output) so a bare "app-release.apk"/"app-debug.apk" can never be produced.
            (output as? VariantOutputImpl)?.outputFileName
                ?.set("sona-${currentVersion.toVersionName()}-${variant.name}-${abi ?: "universal"}.apk")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:navigation"))
    implementation(project(":core:datastore"))
    implementation(project(":feature:scanner"))
    implementation(project(":feature:playback"))
    implementation(project(":feature:library"))
    implementation(project(":feature:player"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:equalizer"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.google.material)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
