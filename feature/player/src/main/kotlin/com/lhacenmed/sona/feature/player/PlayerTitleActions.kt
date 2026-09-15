package com.lhacenmed.sona.feature.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.lhacenmed.sona.core.model.Track

/**
 * What tapping and long-pressing the title and artist of any player style does. Ported from ArchiveTune.
 *
 * Opening the album or artist collapses the player first, so the screen it opens is what shows.
 */
@Immutable
internal class PlayerTitleActions(
    val onTitleClick: () -> Unit,
    val onArtistClick: () -> Unit,
    val onCopyTitle: () -> Unit,
    val onCopyArtists: () -> Unit,
)

@Composable
internal fun rememberPlayerTitleActions(
    track: Track,
    state: BottomSheetState,
    onGoToAlbum: (Long) -> Unit,
    onGoToArtist: (Long) -> Unit,
): PlayerTitleActions {
    val context = LocalContext.current
    val latestOnGoToAlbum by rememberUpdatedState(onGoToAlbum)
    val latestOnGoToArtist by rememberUpdatedState(onGoToArtist)

    return remember(track, state, context) {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        PlayerTitleActions(
            onTitleClick = {
                state.collapseSoft()
                latestOnGoToAlbum(track.albumId)
            },
            onArtistClick = {
                state.collapseSoft()
                latestOnGoToArtist(track.artistId)
            },
            onCopyTitle = {
                val label = context.getString(R.string.player_copied_title)
                clipboardManager.setPrimaryClip(ClipData.newPlainText(label, track.title))
                Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
            },
            onCopyArtists = {
                val label = context.getString(R.string.player_copied_artist)
                clipboardManager.setPrimaryClip(ClipData.newPlainText(label, track.artist))
                Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
            },
        )
    }
}

/**
 * The artist line under a player's title, tapped to open the artist - ArchiveTune's `ClickableArtists`,
 * for Sona's one artist per track.
 */
@Composable
internal fun ClickableArtist(
    artist: String,
    onArtistClick: () -> Unit,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val latestOnArtistClick by rememberUpdatedState(onArtistClick)
    val latestOnLongClick by rememberUpdatedState(onLongClick)

    Text(
        text = artist,
        style = style,
        color = color,
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            modifier.pointerInput(onLongClick != null) {
                detectTapGestures(
                    onTap = { latestOnArtistClick() },
                    onLongPress = if (onLongClick != null) {
                        { latestOnLongClick?.invoke() }
                    } else {
                        null
                    },
                )
            },
    )
}
