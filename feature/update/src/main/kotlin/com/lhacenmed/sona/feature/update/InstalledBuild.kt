package com.lhacenmed.sona.feature.update

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.core.content.pm.PackageInfoCompat
import com.lhacenmed.sona.feature.update.github.SemanticVersion

/**
 * The running build, read from the package itself: this module has no BuildConfig of the app's, and the
 * package is the one authority on what is actually installed.
 *
 * [variant] is read from the native code the installed APK carries - see [ApkVariant.ofNativeAbis]. Its
 * version code is no help: releases up to 1.5.0 gave the universal APK the same one as arm64's.
 */
class InstalledBuild internal constructor(
    /** As the user sees it - "1.5.0", "1.5.0-beta.1", or "1.5.0-debug". */
    val versionName: String,
    /** What a downloaded APK must be newer than. */
    val versionCode: Long,
    val variant: ApkVariant?,
    val isDebug: Boolean,
) {
    /** The version releases are compared with - a debug build's "-debug" left off, so it counts as its release. */
    internal val version: SemanticVersion? = SemanticVersion.parse(versionName.removeSuffix("-debug"))

    /**
     * Whether a release can replace this build in place. A debug build cannot: it is another app, with its
     * own ".debug" id, that a release APK would install beside rather than over.
     */
    val isUpdatable: Boolean get() = !isDebug
}

/** The running build - read once, as nothing about it changes while the process runs. */
fun Context.installedBuild(): InstalledBuild =
    cachedInstalledBuild ?: readInstalledBuild(applicationContext).also { cachedInstalledBuild = it }

@Volatile
private var cachedInstalledBuild: InstalledBuild? = null

private fun readInstalledBuild(context: Context): InstalledBuild {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val applicationInfo = context.applicationInfo
    return InstalledBuild(
        versionName = packageInfo.versionName.orEmpty(),
        versionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
        variant = ApkVariant.ofNativeAbis(nativeAbisOf(applicationInfo.sourceDir)),
        isDebug = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
    )
}
