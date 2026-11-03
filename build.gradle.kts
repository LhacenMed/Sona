import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.aboutlibraries) apply false
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

// Every button presses as the design system says - Material 3 Expressive's shape morph, tightening while held - and every
// dialog is the design system's. Nothing about a plain Material button or dialog fails to compile, so this is what does:
// a new screen gets the app's look or no build. See CLAUDE.md.
val checkExpressiveComponents by tasks.registering {
    group = "verification"
    description = "Fails on a Material 3 button without press shapes, or a dialog that is not SonaDialog."
    val sources = fileTree(rootDir) {
        include("**/src/main/kotlin/**/*.kt")
        exclude("**/build/**")
    }
    val stamp = layout.buildDirectory.file("checkExpressiveComponents.stamp")
    inputs.files(sources)
    outputs.file(stamp)
    doLast {
        val buttons = "TextButton|OutlinedButton|FilledTonalButton|ElevatedButton|Button|IconButton|FilledIconButton|FilledTonalIconButton|OutlinedIconButton"
        val buttonCall = Regex("""(?<![\w.])($buttons)\s*\(""")
        val problems = sources.files.flatMap { file ->
            val text = file.readText().replace("\r\n", "\n")
            fun lineOf(index: Int) = "${file.path}:${text.take(index).count { it == '\n' } + 1}"
            val rawButtons = buttonCall.findAll(text)
                .filter { "import androidx.compose.material3.${it.groupValues[1]}\n" in text }
                .filter { call ->
                    var depth = 1
                    var end = call.range.last + 1
                    while (depth > 0 && end < text.length) {
                        if (text[end] == '(') depth++ else if (text[end] == ')') depth--
                        end++
                    }
                    !Regex("""\bshapes\s*=""").containsMatchIn(text.substring(call.range.last, end))
                }
                .map { "${lineOf(it.range.first)}: ${it.groupValues[1]} needs shapes = buttonPressShapes() or iconButtonPressShapes()" }
            val rawDialog = if ("import androidx.compose.material3.AlertDialog\n" in text) {
                listOf("${file.path}: use SonaDialog with actionButton, not AlertDialog")
            } else {
                emptyList()
            }
            rawButtons.toList() + rawDialog
        }
        if (problems.isNotEmpty()) throw GradleException("Not the design system's components:\n" + problems.joinToString("\n"))
        stamp.get().asFile.apply { parentFile.mkdirs() }.writeText("")
    }
}

subprojects {
    tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(checkExpressiveComponents) }
}
