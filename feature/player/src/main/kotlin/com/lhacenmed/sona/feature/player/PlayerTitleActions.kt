package com.lhacenmed.sona.feature.player

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.lhacenmed.sona.core.designsystem.component.copyToClipboard
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
    onGoToAlbum: ((Long) -> Unit)?,
    onGoToArtist: ((Long) -> Unit)?,
): PlayerTitleActions {
    val context = LocalContext.current
    val latestOnGoToAlbum by rememberUpdatedState(onGoToAlbum)
    val latestOnGoToArtist by rememberUpdatedState(onGoToArtist)

    return remember(track, state, context) {
        PlayerTitleActions(
            // A track with no album or artist to go to - a Private Folder item's - goes nowhere.
            onTitleClick = {
                latestOnGoToAlbum?.let { goToAlbum ->
                    state.collapseSoft()
                    goToAlbum(track.albumId)
                }
            },
            onArtistClick = {
                latestOnGoToArtist?.let { goToArtist ->
                    state.collapseSoft()
                    goToArtist(track.artistId)
                }
            },
            onCopyTitle = { context.copyToClipboard(track.title, context.getString(R.string.player_copied_title)) },
            onCopyArtists = { context.copyToClipboard(track.artist, context.getString(R.string.player_copied_artist)) },
        )
    }
}

/**
 * The artist line under a player's title, tapped to open the artist - ArchiveTune's `ClickableArtists`,
 * for Sona's one artist per track.
 *
 * Clicked as the title is, so a long press answers with the same haptic.
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
    Text(
        text = artist,
        style = style,
        color = color,
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            modifier.combinedClickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onArtistClick,
                onLongClick = onLongClick,
            ),
    )
}
