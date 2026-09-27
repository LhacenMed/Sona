package com.lhacenmed.sona.feature.update

import android.content.Context
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.io.File

/** A downloaded APK [ApkVerifier] has found to be an update this device can install, ready for the installer. */
data class StagedApk(val file: File, val versionCode: Long, val versionName: String, val variant: ApkVariant?)

/**
 * Whether a downloaded APK is one to hand to the installer - the one gate every staged APK passes, fresh from
 * a download or found again at launch. That it arrived whole, byte for byte what was published, is the
 * download's own check, made as it streams - see [ApkDownloader].
 */
object ApkVerifier {

    /**
     * What [apk] is, when it is an update to the running build this device can install: Sona itself, newer than
     * what is installed, with native code for one of this device's processors if it carries any. Null otherwise.
     *
     * Reads the APK's manifest and its table of contents, never the rest of it, so it takes the same moment
     * whatever the APK's size. Its signature is left to the installer, which refuses one that does not match.
     */
    fun inspect(context: Context, apk: File): StagedApk? {
        val info = context.packageManager.getPackageArchiveInfo(apk.path, 0) ?: return null
        if (info.packageName != context.packageName) return null
        val versionCode = PackageInfoCompat.getLongVersionCode(info)
        if (versionCode <= context.installedBuild().versionCode) return null
        val abis = nativeAbisOf(apk.path)
        if (abis.isNotEmpty() && abis.none { it in Build.SUPPORTED_ABIS }) return null
        return StagedApk(apk, versionCode, info.versionName.orEmpty(), ApkVariant.ofNativeAbis(abis))
    }
}
