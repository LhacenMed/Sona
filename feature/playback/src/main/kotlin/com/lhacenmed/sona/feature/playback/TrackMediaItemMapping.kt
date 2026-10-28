package com.lhacenmed.sona.feature.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.lhacenmed.sona.core.data.contentUri
import com.lhacenmed.sona.core.model.PlaybackParent
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.core.model.playbackParentOf
import com.lhacenmed.sona.core.model.toStorageKey

/** The extra an item carries the collection it was queued from under - see [playbackParent]. */
private const val PlaybackParentExtra = "sona.playbackParent"

/**
 * How a [Track] reaches the player.
 *
 * Shared by [PlaybackController] (queue built from the UI) and [PlaybackService]'s playback
 * resumption (queue rebuilt from [com.lhacenmed.sona.core.database.dao.QueueItemDao] with no UI
 * involved) - both need the exact same [MediaItem.mediaId] shape for the id to keep resolving back
 * to the same track.
 *
 * [parent] is the collection the track was queued from, carried by the item itself - see [playbackParent].
 */
internal fun Track.toMediaItem(parent: PlaybackParent? = null): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setDurationMs(durationMs)
        .setMediaType(if (isVideo) MediaMetadata.MEDIA_TYPE_VIDEO else MediaMetadata.MEDIA_TYPE_MUSIC)
        .apply {
            coverArtUri?.let { setArtworkUri(Uri.parse(it)) }
            parent?.let { setExtras(Bundle().apply { putString(PlaybackParentExtra, it.toStorageKey()) }) }
        }
        .build()
    return MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(contentUri)
        .setMediaMetadata(metadata)
        .build()
}

/**
 * The collection this item was queued from - played with it, rather than put in the queue on its own, as Play next
 * and Add to queue do: null for those, and for the whole library. Carried by each item, so whatever plays it - the
 * player, a watched video, a queue restored with no screen open - knows where it is played from.
 */
internal val MediaItem.playbackParent: PlaybackParent?
    get() = mediaMetadata.extras?.getString(PlaybackParentExtra)?.let(::playbackParentOf)

/** Whether this item is a video's - see [toMediaItem]. */
internal val MediaItem.isVideo: Boolean get() = mediaMetadata.mediaType == MediaMetadata.MEDIA_TYPE_VIDEO

/** Whether this shows exactly what [other] does, in every field [toMediaItem] sets. */
internal fun MediaMetadata.isShownAs(other: MediaMetadata): Boolean =
    title?.toString() == other.title?.toString() &&
        artist?.toString() == other.artist?.toString() &&
        albumTitle?.toString() == other.albumTitle?.toString() &&
        artworkUri == other.artworkUri
