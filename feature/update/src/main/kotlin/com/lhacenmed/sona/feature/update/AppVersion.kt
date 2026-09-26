package com.lhacenmed.sona.feature.update

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import com.lhacenmed.sona.feature.update.github.SemanticVersion

/*
 * The running build, read from the package itself: this module has no BuildConfig of the app's, and
 * the package is the one authority on what is actually installed.
 */

/** The running build's versionCode - what a staged APK is measured against. */
fun Context.installedVersionCode(): Long =
    PackageInfoCompat.getLongVersionCode(packageManager.getPackageInfo(packageName, 0))

/** The running build's versionName, as the user sees it - "1.4.0", or "1.5.0-beta.1". */
fun Context.installedVersionName(): String =
    packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()

/**
 * The running build's version, as releases are compared with it. A debug build's "-debug" suffix is left
 * off, so a debug build of 1.4.0 counts as 1.4.0 rather than as an earlier pre-release of it.
 */
internal fun Context.installedVersion(): SemanticVersion? =
    SemanticVersion.parse(installedVersionName().removeSuffix("-debug"))
