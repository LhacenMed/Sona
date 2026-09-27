package com.lhacenmed.sona.core.datastore

/**
 * Which releases the app updates to - ArchiveTune's `UpdateChannel`. Sona's pre-releases (alpha, beta and
 * release candidates) are its builds ahead of stable, where ArchiveTune's are its nightly canaries.
 */
enum class UpdateChannel {
    /** Releases only. */
    STABLE,

    /**
     * Artifacts too - the alpha, beta and release candidate builds published ahead of each stable release:
     * whichever is newest, artifact or release.
     */
    ARTIFACT,
}
