package com.lhacenmed.sona.core.datastore

/**
 * Which releases the app updates to - ArchiveTune's `UpdateChannel`. Sona's pre-releases (alpha, beta and
 * release candidates) are its builds ahead of stable, where ArchiveTune's are its nightly canaries.
 */
enum class UpdateChannel {
    /** Releases only. */
    STABLE,

    /** Pre-releases too: whichever is newest, pre-release or release. */
    BETA,
}
