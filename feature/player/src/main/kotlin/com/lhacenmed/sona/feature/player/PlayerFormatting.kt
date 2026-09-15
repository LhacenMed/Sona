package com.lhacenmed.sona.feature.player

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.view.HapticFeedbackConstants
import android.view.View
import com.lhacenmed.sona.core.model.Track
import java.util.Locale

/** "m:ss", or "h:mm:ss" from the hour on - ArchiveTune's `makeTimeString`. */
internal fun makeTimeString(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1000
    val hours = totalSeconds / 3600
    val minutes = totalSeconds % 3600 / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}

/** The light tick ArchiveTune's player controls answer a press with. */
@Suppress("DEPRECATION")
internal fun View.performContextClick() {
    performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
}

/**
 * Offers [track]'s audio to another app.
 *
 * A manually scanned track has only a file path, which another app is not allowed to open, so its title
 * and artist are shared instead.
 */
internal fun Context.shareTrack(track: Track) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        if (track.isManuallyScanned) {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "${track.title} - ${track.artist}")
        } else {
            type = "audio/*"
            putExtra(
                Intent.EXTRA_STREAM,
                ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, track.mediaStoreId),
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    startActivity(Intent.createChooser(intent, null))
}
