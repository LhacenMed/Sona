package com.lhacenmed.sona.feature.backup.media

import java.io.File

/**
 * Where the library's files are kept in the cloud - each once, by its fingerprint - for a restore to bring
 * back the ones a device is missing. Local backups carry no files, so without one a restore can only list them.
 */
interface CloudMediaStore {

    /** Which of [fingerprints] are kept. */
    suspend fun keeps(fingerprints: Collection<String>): Set<String>

    /** Downloads the file with [fingerprint] into [file], replacing whatever was there. */
    suspend fun download(fingerprint: String, file: File)
}
