package com.lhacenmed.sona.feature.update

import android.os.Build
import java.util.zip.ZipFile

/**
 * Which of a release's APKs a build is: each release publishes one per processor, and a universal one that
 * carries them all. [abi] is the processor, null for the universal APK.
 */
enum class ApkVariant(val abi: String?) {
    UNIVERSAL(null),
    ARM64_V8A("arm64-v8a"),
    ARMEABI_V7A("armeabi-v7a"),
    X86("x86"),
    X86_64("x86_64");

    /** How the release names this APK's file - "sona-1.5.0-release-arm64-v8a.apk" ends in "-arm64-v8a.apk". */
    val assetSuffix: String get() = "-${abi ?: "universal"}.apk"

    /** "Universal", or the processor as Android names it. */
    val label: String get() = abi ?: "Universal"

    /** Whether this device can run it: the universal APK anywhere, a processor's own where it is one of the device's. */
    val isSupported: Boolean get() = abi == null || abi in Build.SUPPORTED_ABIS

    /** Whether it is the one to pick: the device's own processor's - the smallest that runs - or the universal one. */
    val isRecommended: Boolean get() = abi == null || abi == Build.SUPPORTED_ABIS.firstOrNull()

    internal companion object {
        fun of(abi: String): ApkVariant? = entries.firstOrNull { it.abi == abi }

        /** The variant a release's file of this [name] is, or null for a file that is none of them. */
        fun ofAssetName(name: String): ApkVariant? = entries.firstOrNull { name.endsWith(it.assetSuffix) }

        /** The variant carrying native code for [abis]: one processor's, or every one's - null for none. */
        fun ofNativeAbis(abis: Set<String>): ApkVariant? = when (abis.size) {
            0 -> null
            1 -> of(abis.single())
            else -> UNIVERSAL
        }
    }
}

/**
 * The processors the APK at [apkPath] carries native code for, under `lib/<abi>/` - read from its table of
 * contents alone, never its files, so it costs the same however large the APK is. Empty for one that cannot
 * be read.
 */
internal fun nativeAbisOf(apkPath: String): Set<String> = runCatching {
    ZipFile(apkPath).use { apk ->
        apk.entries().asSequence()
            .mapNotNull { entry -> entry.name.takeIf { it.startsWith("lib/") }?.split('/')?.getOrNull(1) }
            .toSet()
    }
}.getOrDefault(emptySet())
