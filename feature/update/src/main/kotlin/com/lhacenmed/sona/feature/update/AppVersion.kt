package com.lhacenmed.sona.feature.update

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat

/*
 * The running build, read from the package itself: this module has no BuildConfig of the app's, and
 * the package is the one authority on what is actually installed.
 */

/** The running build's versionCode - what every manifest and staged APK is measured against. */
fun Context.installedVersionCode(): Long =
    PackageInfoCompat.getLongVersionCode(packageManager.getPackageInfo(packageName, 0))

/** The running build's versionName, as the user sees it - "0.1.0-alpha.1". */
fun Context.installedVersionName(): String =
    packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
