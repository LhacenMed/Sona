package com.lhacenmed.sona.feature.playback

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.lhacenmed.sona.core.model.Track
import java.io.File

/**
 * How a [Track] reaches the player.
 *
 * Shared by [PlaybackController] (queue built from the UI) and [PlaybackService]'s playback
 * resumption (queue rebuilt from [com.lhacenmed.sona.core.database.dao.QueueItemDao] with no UI
 * involved) - both need the exact same [MediaItem.mediaId] shape for the id to keep resolving back
 * to the same track.
 */
internal fun Track.toMediaItem(): MediaItem {
    val uri: Uri = if (isManuallyScanned) {
        Uri.fromFile(File(path))
    } else {
        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaStoreId)
    }
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .apply {
            coverArtUri?.let { setArtworkUri(Uri.parse(it)) }
        }
        .build()
    return MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(metadata)
        .build()
}
