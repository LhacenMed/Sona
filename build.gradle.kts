import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
}

// coil3's Compose artifact pulls in JetBrains Compose Multiplatform, which declares a
// kotlin-stdlib version constraint newer than this project's Kotlin compiler can read
// ("incompatible metadata version"). Force the whole build onto our own Kotlin's stdlib -
// stdlib is stable/backward-compatible at this granularity, so this is safe.
subprojects {
    configurations.all {
        resolutionStrategy {
            force("org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}")
        }
    }

    // lintVital for release builds resolves a "-desktop" lint-tooling variant of
    // material-icons-extended over the network; on a flaky/offline connection that
    // aborts the whole release build even though nothing about the app itself is broken.
    // Release correctness is already covered by the normal `lint` task during CI/dev, so
    // skip the vital duplicate rather than let network hiccups block local release builds.
    plugins.withId("com.android.library") {
        extensions.configure<LibraryExtension> {
            lint {
                checkReleaseBuilds = false
            }
        }
    }
    plugins.withId("com.android.application") {
        extensions.configure<ApplicationExtension> {
            lint {
                checkReleaseBuilds = false
            }
        }
    }
}
