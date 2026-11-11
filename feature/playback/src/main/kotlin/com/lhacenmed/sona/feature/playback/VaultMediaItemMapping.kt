package com.lhacenmed.sona.feature.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.playback.R
import java.io.File

private const val VAULT_MEDIA_ID_PREFIX = "vault:"

/**
 * A Private Folder [track] - its id the item's, its path the file's - played straight from its file, as any
 * local file is.
 *
 * Its media id is not a number, and every record keyed by a library track - play history, the saved queue,
 * the library's resume positions - reads ids as numbers and skips what is not, so the file reaches none of
 * them. Where it was left is the vault's own to keep - see [ResumePositionRecorder].
 */
internal fun vaultMediaItem(context: Context, track: Track): MediaItem =
    MediaItem.Builder()
        .setMediaId("$VAULT_MEDIA_ID_PREFIX${track.id}")
        .setUri(Uri.fromFile(File(track.path)))
        .setMediaMetadata(
            MediaMetadata.Builder()
                // What the notification, the lock screen and the widget show for it - never its own title.
                .setTitle(context.getString(R.string.vault_display_title))
                .setDurationMs(track.durationMs)
                .setMediaType(if (track.isVideo) MediaMetadata.MEDIA_TYPE_VIDEO else MediaMetadata.MEDIA_TYPE_MUSIC)
                .build(),
        )
        .build()

internal val MediaItem.isVaultItem: Boolean get() = mediaId.startsWith(VAULT_MEDIA_ID_PREFIX)

internal val MediaItem.space: PlaybackSpace get() = if (isVaultItem) PlaybackSpace.Private else PlaybackSpace.Library

/** This item's track id in its own [space] - see [PlaybackUiState]. */
internal val MediaItem.idInSpace: Long? get() = mediaId.removePrefix(VAULT_MEDIA_ID_PREFIX).toLongOrNull()
