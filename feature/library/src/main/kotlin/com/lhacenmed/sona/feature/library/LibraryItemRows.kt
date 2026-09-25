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
import com.lhacenmed.sona.core.designsystem.component.SonaListRow
import com.lhacenmed.sona.core.designsystem.component.SonaTrackRow
import com.lhacenmed.sona.core.designsystem.component.SonaPlaylistCover
import com.lhacenmed.sona.core.designsystem.theme.SonaComponentStyle
import com.lhacenmed.sona.core.model.Album
import com.lhacenmed.sona.core.model.Artist
import com.lhacenmed.sona.core.model.Folder
import com.lhacenmed.sona.core.model.Genre
import com.lhacenmed.sona.core.model.Playlist
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.library.selection.selectionKeyOf

/*
 * The rows of the library's lists, laid out as Auxio's: a track as its `item_song`, and an album,
 * artist, genre, playlist or folder as its `item_parent`. The two layouts are the same - cover, title
 * over a line of detail, the row's own overflow button - and differ only in their cover, so every row
 * here is a [SonaListRow] given its own.
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
    onOpenOptions: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    SonaTrackRow(
        track = track,
        isCurrent = isCurrent,
        isPlaying = isPlaying,
        selection = selection,
        selectionKey = selectionKeyOf(track),
        onClick = onClick,
        onOpenOptions = onOpenOptions,
        modifier = modifier,
        subtitle = subtitle,
    )
}

/** An album row: its cover, title over its artist - or over [subtitle], where every row shares the artist. */
@Composable
internal fun AlbumRow(
    album: Album,
    // Null where the row cannot join a selection - see [SonaListRow].
    selection: SelectionState?,
    isCurrent: () -> Boolean,
    isPlaying: () -> Boolean,
    onClick: () -> Unit,
    onOpenOptions: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String = album.artistName,
) {
    val current = isCurrent()
    SonaListRow(
        title = album.title,
        subtitle = subtitle,
        selection = selection,
        selectionKey = selectionKeyOf(album),
        onClick = onClick,
        onOpenOptions = onOpenOptions,
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
    onOpenOptions: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val current = isCurrent()
    SonaListRow(
        title = artist.name,
        subtitle = pluralCount(artist.albumCount, "album") + COUNTS_SEPARATOR +
            pluralCount(artist.trackCount, "track"),
        selection = selection,
        selectionKey = selectionKeyOf(artist),
        onClick = onClick,
        onOpenOptions = onOpenOptions,
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
    onOpenOptions: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val current = isCurrent()
    SonaListRow(
        title = genre.name,
        subtitle = pluralCount(genre.artistCount, "artist") + COUNTS_SEPARATOR +
            pluralCount(genre.trackCount, "track"),
        selection = selection,
        selectionKey = selectionKeyOf(genre),
        onClick = onClick,
        onOpenOptions = onOpenOptions,
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
    onOpenOptions: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val current = isCurrent()
    SonaListRow(
        title = playlist.name,
        subtitle = trackCountLabel(playlist.trackCount),
        selection = selection,
        selectionKey = selectionKeyOf(playlist),
        onClick = onClick,
        onOpenOptions = onOpenOptions,
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
    onOpenOptions: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val current = isCurrent()
    SonaListRow(
        title = folder.name,
        subtitle = trackCountLabel(folder.trackCount),
        selection = selection,
        selectionKey = selectionKeyOf(folder),
        onClick = onClick,
        onOpenOptions = onOpenOptions,
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
 * A row for a list of tracks with no library row of its own - Most played, pinned above the playlists.
 *
 * It sits among the playlists, so it is drawn as they are - the list keeps one rhythm, and it marks
 * itself while playing like everything else. Not being a playlist, it has no options of its own: its
 * pin stands where a playlist's options button would.
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
    SonaListRow(
        title = title,
        subtitle = trackCountLabel(trackCount),
        // Not a playlist, so it can be opened but never gathered into a selection meant for playlists.
        selection = null,
        selectionKey = null,
        onClick = onClick,
        onOpenOptions = null,
        modifier = modifier,
        isCurrent = current,
        isPinned = true,
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
 * [selection] is null where the row cannot join a selection - Recent and Most played, which are not
 * music of their own. [selectionKey] is null for a collection with no tracks, which Auxio refuses to
 * select since it stands for nothing to act on: while a selection runs, tapping it does nothing. [cover]
 * is told whether the row is selected, which its badge shows; [isCurrent] accents the title, the way
 * Auxio accents the row playback came from.
 *
 * The overflow button stays through a selection: the row keeps one shape whatever state it is in.
 */
/** "1 track", "12 tracks": Auxio's count plurals. */
internal fun pluralCount(count: Int, noun: String): String = if (count == 1) "$count $noun" else "$count ${noun}s"

/** A count of tracks, or Auxio's `def_song_count` for a list that holds none yet. */
internal fun trackCountLabel(count: Int): String = if (count == 0) "No tracks" else pluralCount(count, "track")
