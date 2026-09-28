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
    // Writes every dependency's license into the app, for About's licenses list.
    alias(libs.plugins.aboutlibraries)
}

sealed class Version(
    open val versionMajor: Int,
    val versionMinor: Int,
    val versionPatch: Int,
    val versionBuild: Int = 0,
) {
    abstract fun toVersionName(): String

    /**
     * A code higher than every earlier release's, whatever changed - so the in-app updater, which
     * compares codes, sees pre-release builds too. Major, minor and patch come first, then the stage
     * (alpha < beta < rc < stable, so 1.0.0 outranks 1.0.0-rc.3), then the pre-release build, which
     * must stay under 200. The last digit is left free for the per-ABI offset added to split APKs.
     */
    fun toVersionCode(): Int {
        val stage = when (this) {
            is Alpha -> 100
            is Beta -> 300
            is ReleaseCandidate -> 500
            is Stable -> 900
        }
        return (((versionMajor * 100 + versionMinor) * 100 + versionPatch) * 1000 + stage + versionBuild) * 10
    }

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

/** The last stable release - written by the release pipeline, and what a local build is versioned as. */
val lastStableVersion: Version = Version.Stable(
    versionMajor = 1,
    versionMinor = 6,
    versionPatch = 0,
)

/**
 * The version this build is: the one the release pipelines pass as `-Psona.version=1.6.0-beta.2` - worked out
 * from the repository's release tags, see scripts/lib/version.sh - or the last stable release otherwise.
 */
val currentVersion: Version = providers.gradleProperty("sona.version").orNull
    ?.let { name ->
        val match = requireNotNull(Regex("""(\d+)\.(\d+)\.(\d+)(?:-(alpha|beta|rc)\.(\d+))?""").matchEntire(name)) {
            "sona.version must look like 1.6.0 or 1.6.0-beta.2, not $name"
        }
        val (major, minor, patch, stage, build) = match.destructured
        when (stage) {
            "alpha" -> Version.Alpha(major.toInt(), minor.toInt(), patch.toInt(), build.toInt())
            "beta" -> Version.Beta(major.toInt(), minor.toInt(), patch.toInt(), build.toInt())
            "rc" -> Version.ReleaseCandidate(major.toInt(), minor.toInt(), patch.toInt(), build.toInt())
            else -> Version.Stable(major.toInt(), minor.toInt(), patch.toInt())
        }
    }
    ?: lastStableVersion

val keystorePropertiesFile: File = rootProject.file("keystore.properties")

// Release builds always emit per-ABI APKs + a universal one (store/sideload distribution);
// debug stays a single fast universal APK. Pass -Psplits to force splits for any build type.
val isReleaseBuild = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
val splitApks = project.hasProperty("splits") || isReleaseBuild

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
        versionCode = currentVersion.toVersionCode()
        versionName = currentVersion.toVersionName()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // ABI splits are opt-in (-Psplits). Without the flag every build produces a
    // single universal APK, which can be sideloaded directly.
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
        // Kuromoji ships these alongside other libraries that ship their own; none are read at runtime.
        resources.excludes += listOf("META-INF/NOTICE.md", "META-INF/CONTRIBUTORS.md", "META-INF/LICENSE.md")
        // Kuromoji's dictionary - two thirds of the app - is downloaded by those who romanize Japanese
        // instead (see JapaneseDictionary); its code stays, and reads the downloaded copy.
        resources.excludes += "com/atilika/kuromoji/ipadic/*.bin"
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
            // Per-ABI APKs get a distinct versionCode (+1..+4) so each ABI is independently updatable.
            // The universal APK - and the single APK of a build without splits - keeps the base code, the
            // lowest of a version's, rather than posing as one ABI's.
            abi?.let(abiCodes::get)?.let { code ->
                output.versionCode.set(code + (output.versionCode.get() ?: 0))
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
    implementation(project(":feature:update"))
    implementation(project(":feature:tageditor"))

    implementation(libs.androidx.core.ktx)
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
    // Coil 3 loads nothing from the network by itself: this is what fetches, and caches on disk, every
    // https image the app shows - GitHub avatars on About and Updates.
    implementation(libs.coil.network.okhttp)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
