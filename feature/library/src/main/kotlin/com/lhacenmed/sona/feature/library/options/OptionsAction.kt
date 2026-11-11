package com.lhacenmed.sona.feature.library.options

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
    EDIT_TAGS(SonaIcons.EditNote, "Edit info"),
    AUTO_TAG(SonaIcons.AutoFixHigh, "Auto-tag"),
    STOP_COUNTING_PLAYS(SonaIcons.TrendingFlat, "Don't count in Most played"),
    COUNT_PLAYS(SonaIcons.TrendingUp, "Count in Most played"),
    VIEW_DETAILS(SonaIcons.Details, "View"),
    BROWSE_FOLDER(SonaIcons.FolderOpen, "Browse folder"),
    ADD_TRACKS(SonaIcons.PlaylistAdd, "Add tracks"),
    ADD_COLLECTIONS(SonaIcons.LibraryAdd, "Add from collections"),
    EDIT(SonaIcons.Edit, "Edit playlist"),
    IMPORT(SonaIcons.FileDownload, "Import"),
    EXPORT(SonaIcons.FileUpload, "Export"),
    DELETE(SonaIcons.Delete, "Delete"),
    REMOVE_FROM_PLAYLIST(SonaIcons.RemoveCircleOutline, "Remove from playlist"),
    REMOVE_FROM_RECENTLY_PLAYED(SonaIcons.RemoveCircleOutline, "Remove from Recently played"),
    REMOVE_FROM_MOST_PLAYED(SonaIcons.RemoveCircleOutline, "Remove from Most played"),
    ADD_TO_PRIVATE(SonaIcons.Lock, "Add to Private Folder"),
    DELETE_FROM_DEVICE(SonaIcons.Delete, "Delete from device"),
    EXCLUDE(SonaIcons.Block, "Exclude folder"),
    SHARE(SonaIcons.Share, "Share"),
}
