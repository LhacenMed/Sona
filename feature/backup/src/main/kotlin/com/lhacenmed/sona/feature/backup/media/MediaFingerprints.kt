package com.lhacenmed.sona.feature.backup.media

import com.lhacenmed.sona.core.common.di.IoDispatcher
import com.lhacenmed.sona.core.database.dao.MediaHashDao
import com.lhacenmed.sona.core.database.entity.MediaHashEntity
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * The fingerprints of the device's files, each read once: a file is read again only after its size or its
 * last change moved, so a library is read in full on its first backup and then only for what is new.
 */
@Singleton
class MediaFingerprints @Inject constructor(
    private val mediaHashDao: MediaHashDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /** The fingerprint of the file at [path], or null where there is no file there to read. */
    suspend fun of(path: String): String? = withContext(ioDispatcher) {
        val file = File(path)
        if (!file.isFile) return@withContext null
        val sizeBytes = file.length()
        val modifiedAt = file.lastModified()
        mediaHashDao.get(path)?.takeIf { it.sizeBytes == sizeBytes && it.modifiedAt == modifiedAt }?.let { return@withContext it.fingerprint }
        val fingerprint = try {
            fingerprintOf(file)
        } catch (_: IOException) {
            return@withContext null
        }
        mediaHashDao.upsert(MediaHashEntity(path, sizeBytes, modifiedAt, fingerprint))
        fingerprint
    }

    /** Records [file] as having [fingerprint] - already known, for a file just written and checked. */
    suspend fun remember(file: File, fingerprint: String) = withContext(ioDispatcher) {
        mediaHashDao.upsert(MediaHashEntity(file.path, file.length(), file.lastModified(), fingerprint))
    }
}
