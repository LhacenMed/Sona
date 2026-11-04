package com.lhacenmed.sona.core.data

import android.content.Context
import com.lhacenmed.sona.core.database.dao.VideoSubtitleDao
import com.lhacenmed.sona.core.database.entity.toDomain
import com.lhacenmed.sona.core.database.entity.toEntity
import com.lhacenmed.sona.core.model.VideoSubtitle
import com.lhacenmed.sona.core.model.VideoSubtitleStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** The library's videos' subtitles: what was chosen in its database, attached files in the app's own storage. */
@Singleton
class LibrarySubtitleStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: VideoSubtitleDao,
) : VideoSubtitleStore {

    override val directory: File get() = File(context.filesDir, "subtitles").apply { mkdirs() }

    override suspend fun read(videoId: Long): VideoSubtitle = dao.get(videoId)?.toDomain() ?: VideoSubtitle()

    override suspend fun write(videoId: Long, subtitle: VideoSubtitle) = dao.upsert(subtitle.toEntity(videoId))

    /** Lets go of everything kept for the videos [videoIds], which the user has deleted. */
    suspend fun forget(videoIds: Collection<Long>) {
        videoIds.chunked(MAX_BIND_ARGS).forEach { dao.deleteByIds(it) }
        videoIds.forEach(::deleteAttachedFile)
    }

    private companion object {
        const val MAX_BIND_ARGS = 900
    }
}
