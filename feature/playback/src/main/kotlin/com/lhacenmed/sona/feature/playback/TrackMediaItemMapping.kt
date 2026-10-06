package com.lhacenmed.sona.feature.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.lhacenmed.sona.core.data.contentUri
import com.lhacenmed.sona.core.model.Track

/**
 * How a [Track] reaches the player.
 *
 * Shared by [PlaybackController] (queue built from the UI) and [PlaybackService]'s playback
 * resumption (queue rebuilt from [com.lhacenmed.sona.core.database.dao.QueueItemDao] with no UI
 * involved) - both need the exact same [MediaItem.mediaId] shape for the id to keep resolving back
 * to the same track.
 */
internal fun Track.toMediaItem(): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setMediaType(if (isVideo) MediaMetadata.MEDIA_TYPE_VIDEO else MediaMetadata.MEDIA_TYPE_MUSIC)
        .apply {
            coverArtUri?.let { setArtworkUri(Uri.parse(it)) }
        }
        .build()
    return MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(contentUri)
        .setMediaMetadata(metadata)
        .build()
}

/** Whether this item is a video's - see [toMediaItem]. */
internal val MediaItem.isVideo: Boolean get() = mediaMetadata.mediaType == MediaMetadata.MEDIA_TYPE_VIDEO

/** Whether this shows exactly what [other] does, in every field [toMediaItem] sets. */
internal fun MediaMetadata.isShownAs(other: MediaMetadata): Boolean =
    title?.toString() == other.title?.toString() &&
        artist?.toString() == other.artist?.toString() &&
        albumTitle?.toString() == other.albumTitle?.toString() &&
        artworkUri == other.artworkUri
