package com.lhacenmed.sona.feature.scanner

import android.content.Context
import android.os.Build
import android.provider.MediaStore

/**
 * Bump whenever a change makes previously-scanned rows wrong (a new column, a different id scheme,
 * a corrected parsing rule). It invalidates every stored signature, forcing one full rescan.
 */
private const val SCANNER_SCHEMA_VERSION = 6

/**
 * A cheap fingerprint of "what a scan of this device would find right now".
 *
 * Android already tracks whether its media database has changed, and asking it is essentially free:
 *  - [MediaStore.getVersion] changes when the media database is rebuilt (reboot, storage remount,
 *    a media provider upgrade).
 *  - [MediaStore.getGeneration] (API 30+) is a monotonic counter bumped on **every** insert, update
 *    or delete in a volume. If it hasn't moved, no audio file on that volume has changed.
 *
 * If the fingerprint matches the one stored after the last successful scan, and the database is not
 * empty, the scan can be skipped outright. That is what makes a second launch cost a single SQLite
 * read instead of a full MediaStore query plus (on Q+) a filesystem walk.
 *
 * Below API 30 there is no generation counter, so the fingerprint carries only the version; a scan
 * still runs on those devices, but it is now a diff (see `LibraryWriter`) and so still writes
 * nothing when nothing changed.
 */
fun scanSignatureOf(context: Context, excludedFolders: Set<String>): String {
    val version = runCatching { MediaStore.getVersion(context) }.getOrDefault("unknown")
    val generation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        runCatching {
            MediaStore.getGeneration(context, MediaStore.VOLUME_EXTERNAL_PRIMARY).toString()
        }.getOrDefault("unknown")
    } else {
        // No generation counter available - never claim the library is unchanged on these devices.
        "unsupported"
    }
    // Excluded folders are part of the result, so changing them must invalidate the signature.
    val exclusions = excludedFolders.sorted().joinToString(" ")
    return "$SCANNER_SCHEMA_VERSION|$version|$generation|${exclusions.hashCode()}"
}

/** `true` when [scanSignatureOf] produced something that may legitimately be trusted for skipping. */
fun String.isSkippableSignature(): Boolean =
    !contains("unknown") && !contains("unsupported")
