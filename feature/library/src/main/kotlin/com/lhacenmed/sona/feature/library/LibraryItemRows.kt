package com.lhacenmed.sona.feature.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.component.SelectionState
import com.lhacenmed.sona.core.designsystem.component.SonaAlbumCover
import com.lhacenmed.sona.core.designsystem.component.SonaArtistCover
import com.lhacenmed.sona.core.designsystem.component.SonaCoverArt
import com.lhacenmed.sona.core.designsystem.component.SonaFolderCover
import com.lhacenmed.sona.core.designsystem.component.SonaGenreCover
import com.lhacenmed.sona.core.designsystem.component.SonaIconButton
import com.lhacenmed.sona.core.designsystem.component.SonaPlaylistCover
import com.lhacenmed.sona.core.designsystem.theme.SonaComponentStyle
import com.lhacenmed.sona.core.model.Album
import com.lhacenmed.sona.core.model.Artist
import com.lhacenmed.sona.core.model.Folder
import com.lhacenmed.sona.core.model.Genre
import com.lhacenmed.sona.core.model.Playlist
import com.lhacenmed.sona.core.model.Track

/*
 * The rows of the library's lists, laid out as Auxio's: a track as its `item_song`, and an album,
 * artist, genre, playlist or folder as its `item_parent`. The two layouts are the same - cover, title
 * over a line of detail, the row's own overflow button - and differ only in their cover, so every row
 * here is a [LibraryItemRow] given its own.
 *
 * Every row also marks itself when it is the one playing: its cover shows the playing indicator and its
 * title takes the accent, which is Auxio's `sel_selectable_text_primary` on a selected row. What counts
 * as playing is [LibraryPlayback]'s to decide, not any row's.
 */

/** How strongly a selected row is tinted with the primary colour: Auxio's `sel_item_activated_bg`. */
private const val SELECTED_ROW_TINT_ALPHA = 0.12f

/** How long that tint takes to fade in and out: Auxio's `anim_fade_enter_duration` and exit duration. */
private const val SELECTED_ROW_FADE_IN_MILLIS = 200
private const val SELECTED_ROW_FADE_OUT_MILLIS = 100

/** The touch target a drag handle is centred in: Auxio's `size_touchable_small`. */
private val DragHandleTouchSize = 48.dp

/** What separates the two counts beneath an artist or a genre: Auxio's `fmt_two`. */
private const val COUNTS_SEPARATOR = " • "

/**
 * A track row: cover art, title over "artist - album".
 *
 * [isCurrent] and [isPlaying] are lambdas, not values, on purpose. Passed as `Boolean`s, every row in
 * the list recomposes whenever the playing track changes, because each row's parameters changed.
 * Read inside the row's own composition, only the row that was marked and the row that now is do any
 * work. Every row here takes them the same way, for the same reason.
 */
@Composable
internal fun TrackRow(
    track: Track,
    isCurrent: () -> Boolean,
    isPlaying: () -> Boolean,
    selection: SelectionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = isCurrent()
    LibraryItemRow(
        title = track.title,
        subtitle = "${track.artist} - ${track.album}",
        selection = selection,
        selectionKey = track.id,
        onClick = onClick,
        modifier = modifier,
        isCurrent = current,
    ) { isSelected ->
        SonaCoverArt(
            coverArtUri = track.coverArtUri,
            contentDescription = null,
            isCurrent = current,
            isPlaying = isPlaying(),
            isSelected = isSelected,
        )
    }
}

/** An album row: its cover, title over its artist. */
@Composable
internal fun AlbumRow(
    album: Album,
    // Null where the row cannot join a selection - see [LibraryItemRow].
    selection: SelectionState?,
    isCurrent: () -> Boolean,
    isPlaying: () -> Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = isCurrent()
    LibraryItemRow(
        title = album.title,
        subtitle = album.artistName,
        selection = selection,
        selectionKey = album.id,
        onClick = onClick,
        modifier = modifier,
        isCurrent = current,
    ) { isSelected ->
        SonaAlbumCover(
            coverArtUri = album.coverArtUri,
            isCurrent = current,
            isPlaying = isPlaying(),
            isSelected = isSelected,
        )
    }
}

/** An artist row: its scattered covers, name over "albums • tracks". */
@Composable
internal fun ArtistRow(
    artist: Artist,
    selection: SelectionState?,
    isCurrent: () -> Boolean,
    isPlaying: () -> Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = isCurrent()
    LibraryItemRow(
        title = artist.name,
        subtitle = pluralCount(artist.albumCount, "album") + COUNTS_SEPARATOR +
            pluralCount(artist.trackCount, "track"),
        selection = selection,
        selectionKey = artist.id,
        onClick = onClick,
        modifier = modifier,
        isCurrent = current,
    ) { isSelected ->
        SonaArtistCover(
            coverArtUris = artist.coverArtUris,
            seed = artist.id.hashCode(),
            isCurrent = current,
            isPlaying = isPlaying(),
            isSelected = isSelected,
        )
    }
}

/** A genre row: its gallery of covers, name over "artists • tracks". */
@Composable
internal fun GenreRow(
    genre: Genre,
    selection: SelectionState?,
    isCurrent: () -> Boolean,
    isPlaying: () -> Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = isCurrent()
    LibraryItemRow(
        title = genre.name,
        subtitle = pluralCount(genre.artistCount, "artist") + COUNTS_SEPARATOR +
            pluralCount(genre.trackCount, "track"),
        selection = selection,
        selectionKey = genre.id,
        onClick = onClick,
        modifier = modifier,
        isCurrent = current,
    ) { isSelected ->
        SonaGenreCover(
            coverArtUris = genre.coverArtUris,
            seed = genre.id.hashCode(),
            isCurrent = current,
            isPlaying = isPlaying(),
            isSelected = isSelected,
        )
    }
}

/** A playlist row: its stacked covers, name over how many tracks it holds. */
@Composable
internal fun PlaylistRow(
    playlist: Playlist,
    selection: SelectionState?,
    isCurrent: () -> Boolean,
    isPlaying: () -> Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = isCurrent()
    LibraryItemRow(
        title = playlist.name,
        subtitle = trackCountLabel(playlist.trackCount),
        selection = selection,
        selectionKey = playlist.id,
        onClick = onClick,
        modifier = modifier,
        isCurrent = current,
    ) { isSelected ->
        SonaPlaylistCover(
            coverArtUris = playlist.coverArtUris,
            seed = playlist.id.hashCode(),
            isCurrent = current,
            isPlaying = isPlaying(),
            isSelected = isSelected,
        )
    }
}

/** A folder row: its stacked covers, name over how many tracks it holds. */
@Composable
internal fun FolderRow(
    folder: Folder,
    selection: SelectionState?,
    isCurrent: () -> Boolean,
    isPlaying: () -> Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = isCurrent()
    LibraryItemRow(
        title = folder.name,
        subtitle = trackCountLabel(folder.trackCount),
        selection = selection,
        selectionKey = folder.path,
        onClick = onClick,
        modifier = modifier,
        isCurrent = current,
    ) { isSelected ->
        SonaFolderCover(
            coverArtUris = folder.coverArtUris,
            seed = folder.path.hashCode(),
            isCurrent = current,
            isPlaying = isPlaying(),
            isSelected = isSelected,
        )
    }
}

/**
 * A row for a list of tracks with no library row of its own: Recent and Most played.
 *
 * They sit among the playlists, so they are drawn as playlists are - the list keeps one rhythm, and
 * they mark themselves while playing like everything else.
 */
@Composable
internal fun TrackCollectionRow(
    title: String,
    trackCount: Int,
    coverArtUris: List<String>,
    isCurrent: () -> Boolean,
    isPlaying: () -> Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = isCurrent()
    LibraryItemRow(
        title = title,
        subtitle = trackCountLabel(trackCount),
        // Not a playlist, so it can be opened but never gathered into a selection meant for playlists.
        selection = null,
        selectionKey = title,
        onClick = onClick,
        modifier = modifier,
        isCurrent = current,
    ) { isSelected ->
        SonaPlaylistCover(
            coverArtUris = coverArtUris,
            seed = title.hashCode(),
            isCurrent = current,
            isPlaying = isPlaying(),
            isSelected = isSelected,
        )
    }
}

/**
 * The shape every library row shares: [cover], [title] over [subtitle], and the row's own overflow button
 * - with a drag handle beside that button in a list that can be reordered.
 *
 * [selection] is null where the row cannot join a selection - a search's selection plays tracks, so its
 * albums, artists and genres can only be opened. [cover] is told whether the row is selected, which its
 * badge shows; [isCurrent] accents the title, the way Auxio accents the row playback came from.
 *
 * The overflow button stays through a selection: the row keeps one shape whatever state it is in.
 */
@Composable
private fun LibraryItemRow(
    title: String,
    subtitle: String,
    selection: SelectionState?,
    selectionKey: Any,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    cover: @Composable (isSelected: Boolean) -> Unit,
) {
    val isSelected = selection?.isSelected(selectionKey) == true
    // The fade is animated rather than the colour, and read only when drawing: an animated colour would
    // trail behind every theme transition, and reading it here would recompose the row every frame.
    val selectedFraction = animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isSelected) SELECTED_ROW_FADE_IN_MILLIS else SELECTED_ROW_FADE_OUT_MILLIS,
        ),
        label = "libraryRowSelection",
    )
    val selectedTint = MaterialTheme.colorScheme.primary
    val dragHandle = LocalDragHandle.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .drawBehind {
                drawRect(selectedTint.copy(alpha = SELECTED_ROW_TINT_ALPHA * selectedFraction.value))
            }
            .then(
                if (selection == null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier.selectableRow(selection, selectionKey, onClick)
                },
            )
            .padding(
                start = SonaComponentStyle.ContentHorizontalPadding,
                top = 12.dp,
                // The overflow glyph, not its 48dp touch target, ends on the keyline: the target
                // holds the glyph 12dp in from its edge.
                end = SonaComponentStyle.ContentHorizontalPadding - 12.dp,
                bottom = 12.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        cover(isSelected)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp, end = 12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (dragHandle != null) {
            Box(
                modifier = Modifier
                    .size(DragHandleTouchSize)
                    .then(dragHandle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.DragHandle,
                    contentDescription = "Reorder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SonaIconButton(
            // Opens nothing yet. It takes its place now because the row's shape is part of the
            // list's: adding it later would move the title and the cover of every row.
            onClick = {},
            icon = Icons.Filled.MoreHoriz,
            contentDescription = "More options",
        )
    }
}

/** "1 track", "12 tracks": Auxio's count plurals. */
private fun pluralCount(count: Int, noun: String): String = if (count == 1) "$count $noun" else "$count ${noun}s"

/** A count of tracks, or Auxio's `def_song_count` for a list that holds none yet. */
private fun trackCountLabel(count: Int): String = if (count == 0) "No tracks" else pluralCount(count, "track")
