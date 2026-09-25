package com.lhacenmed.sona.feature.library.options

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.ui.graphics.vector.ImageVector
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons

/**
 * Every row an options sheet can offer, icon and label together - Auxio's `action_*` menu items.
 *
 * [VIEW_DETAILS] is a song's own "View properties" as much as an album's, artist's, genre's or
 * playlist's "View" - two labels over the one glyph, the way Auxio's `lbl_song_detail` and
 * `lbl_parent_detail` are. [ADD_TRACKS], [ADD_COLLECTIONS], [EXCLUDE] and [REMOVE_FROM_PLAYLIST] are
 * Sona's own, with no Auxio glyph to draw, so they draw the Material glyphs the playlist's menu and the
 * selection bar already use.
 *
 * [DELETE] deletes a playlist, and never a file; [DELETE_FROM_DEVICE] deletes tracks' files - Fossify's
 * delete - so the two are told apart by name wherever both could be read.
 * Which [OptionsAction]s a sheet shows, and in what order, is [OptionsTarget]'s
 * to decide - this only says what each one looks like once chosen.
 */
enum class OptionsAction(val icon: ImageVector, val label: String) {
    PLAY(SonaIcons.Play, "Play"),
    SHUFFLE(SonaIcons.Shuffle, "Shuffle"),
    PLAY_NEXT(SonaIcons.PlayNext, "Play next"),
    QUEUE_ADD(SonaIcons.QueueAdd, "Add to queue"),
    PLAYLIST_ADD(SonaIcons.PlaylistAdd, "Add to playlist"),
    ARTIST_DETAILS(SonaIcons.GoToArtist, "Go to artist"),
    ALBUM_DETAILS(SonaIcons.GoToAlbum, "Go to album"),
    SONG_PROPERTIES(SonaIcons.Details, "View properties"),
    VIEW_DETAILS(SonaIcons.Details, "View"),
    ADD_TRACKS(Icons.AutoMirrored.Filled.PlaylistAdd, "Add tracks"),
    ADD_COLLECTIONS(Icons.Filled.LibraryAdd, "Add from collections"),
    EDIT(SonaIcons.Edit, "Edit"),
    IMPORT(Icons.Filled.FileDownload, "Import"),
    EXPORT(Icons.Filled.FileUpload, "Export"),
    DELETE(SonaIcons.Delete, "Delete"),
    REMOVE_FROM_PLAYLIST(Icons.Filled.RemoveCircleOutline, "Remove from playlist"),
    DELETE_FROM_DEVICE(SonaIcons.Delete, "Delete from device"),
    EXCLUDE(Icons.Filled.Block, "Exclude folder"),
    SHARE(SonaIcons.Share, "Share"),
}
