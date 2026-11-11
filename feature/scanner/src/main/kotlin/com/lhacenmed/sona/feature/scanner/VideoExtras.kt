package com.lhacenmed.sona.feature.scanner

import com.lhacenmed.sona.core.database.dao.VideoExtrasDao
import com.lhacenmed.sona.core.database.entity.OnlineHeatmapEntity
import com.lhacenmed.sona.core.database.entity.VideoChapterEntity
import com.lhacenmed.sona.core.downloader.EmbeddedExtras
import java.io.File
import javax.inject.Inject

/** Bumped whenever how a video's extras are read changes, so every video is read again. */
private const val READ_VERSION = 1

/**
 * Reads into the library what each video's file carries of its site - see [EmbeddedExtras] - its Most replayed graph and its
 * chapters, which the player shows. The file is where they are kept; this is their index, for the player to find them at
 * once: each file read once, and again only once it changes. Run once the library is saved, as what is kept is kept by the
 * track each video is saved as.
 */
class VideoExtras @Inject constructor(private val dao: VideoExtrasDao) {

    suspend fun index() {
        val read = dao.reads().associate { it.trackId to it.signature }
        for (video in dao.videos()) {
            val signature = fileSignature(video.path, READ_VERSION)
            if (read[video.id] == signature) continue
            val extras = EmbeddedExtras.read(File(video.path))
            dao.keep(
                trackId = video.id,
                signature = signature,
                heatmap = extras?.heatmap?.let { OnlineHeatmapEntity(video.id, it) },
                chapters = extras?.chapters.orEmpty().map { VideoChapterEntity(video.id, it.startMs, it.title) },
            )
        }
    }
}
