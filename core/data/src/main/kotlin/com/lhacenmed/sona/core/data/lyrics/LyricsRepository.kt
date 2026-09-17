package com.lhacenmed.sona.core.data.lyrics

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.lhacenmed.sona.core.common.di.IoDispatcher
import com.lhacenmed.sona.core.database.dao.LyricsDao
import com.lhacenmed.sona.core.database.entity.LyricsEntity
import com.lhacenmed.sona.core.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Every track's lyrics, as read from its own tags or typed in by the user.
 *
 * Stands in for ArchiveTune's `LyricsHelper` with the file as the only provider: a track's tags are read
 * once, the first time its lyrics are wanted, and what they held - or that they held nothing - is kept,
 * so playing it again is a database read rather than another pass over the file.
 */
@Singleton
class LyricsRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val lyricsDao: LyricsDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private val embeddedLyricsExtractor = EmbeddedLyricsExtractor(context.contentResolver)

    fun lyrics(trackId: Long): Flow<LyricsEntity?> = lyricsDao.observeLyrics(trackId)

    /** Reads [track]'s embedded lyrics into the store, unless it already has a row there. */
    suspend fun loadLyrics(track: Track) {
        withContext(ioDispatcher) {
            if (lyricsDao.getLyrics(track.id) != null) return@withContext
            val lyrics =
                embeddedLyricsExtractor
                    .extract(contentUri = track.contentUri(), displayName = File(track.path).name, mimeType = null)
                    ?.let(LyricsUtils::lyricsOrNotFound)
                    ?: LyricsEntity.LYRICS_NOT_FOUND
            lyricsDao.insertIfAbsent(
                LyricsEntity(trackId = track.id, lyrics = lyrics, source = LyricsEntity.Source.EMBEDDED.name),
            )
        }
    }

    /** Replaces [trackId]'s lyrics with what the user typed, kept exactly as typed. */
    suspend fun updateLyrics(trackId: Long, lyrics: String) {
        withContext(ioDispatcher) {
            lyricsDao.upsert(
                LyricsEntity(trackId = trackId, lyrics = lyrics, source = LyricsEntity.Source.USER_EDIT.name),
            )
        }
    }

    /** Forgets every track's lyrics; each is read from its tags again the next time it is wanted. */
    suspend fun clearLyrics() {
        withContext(ioDispatcher) { lyricsDao.clearAll() }
    }

    // The same file the player opens.
    private fun Track.contentUri(): Uri =
        if (isManuallyScanned) {
            Uri.fromFile(File(path))
        } else {
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaStoreId)
        }
}
