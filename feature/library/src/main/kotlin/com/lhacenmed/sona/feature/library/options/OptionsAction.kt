package com.lhacenmed.sona.feature.library.options

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.feature.library.R

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
enum class OptionsAction(val icon: ImageVector, @StringRes val labelRes: Int) {
    PLAY(SonaIcons.Play, R.string.options_play),
    SHUFFLE(SonaIcons.Shuffle, R.string.options_shuffle),
    PLAY_NEXT(SonaIcons.PlayNext, R.string.options_play_next),
    QUEUE_ADD(SonaIcons.QueueAdd, R.string.options_queue_add),
    PLAYLIST_ADD(SonaIcons.PlaylistAdd, R.string.options_playlist_add),
    ARTIST_DETAILS(SonaIcons.GoToArtist, R.string.options_artist_details),
    ALBUM_DETAILS(SonaIcons.GoToAlbum, R.string.options_album_details),
    SONG_PROPERTIES(SonaIcons.Details, R.string.options_song_properties),
    EDIT_TAGS(SonaIcons.EditNote, R.string.options_edit_tags),
    AUTO_TAG(SonaIcons.AutoFixHigh, R.string.options_auto_tag),
    STOP_COUNTING_PLAYS(SonaIcons.TrendingFlat, R.string.options_stop_counting_plays),
    COUNT_PLAYS(SonaIcons.TrendingUp, R.string.options_count_plays),
    VIEW_DETAILS(SonaIcons.Details, R.string.options_view_details),
    BROWSE_FOLDER(SonaIcons.FolderOpen, R.string.options_browse_folder),
    ADD_TRACKS(SonaIcons.PlaylistAdd, R.string.options_add_tracks),
    ADD_COLLECTIONS(SonaIcons.LibraryAdd, R.string.options_add_collections),
    EDIT(SonaIcons.Edit, R.string.options_edit),
    IMPORT(SonaIcons.FileDownload, R.string.options_import),
    EXPORT(SonaIcons.FileUpload, R.string.options_export),
    DELETE(SonaIcons.Delete, R.string.options_delete),
    REMOVE_FROM_PLAYLIST(SonaIcons.RemoveCircleOutline, R.string.options_remove_from_playlist),
    REMOVE_FROM_RECENTLY_PLAYED(SonaIcons.RemoveCircleOutline, R.string.options_remove_from_recently_played),
    REMOVE_FROM_MOST_PLAYED(SonaIcons.RemoveCircleOutline, R.string.options_remove_from_most_played),
    ADD_TO_PRIVATE(SonaIcons.Lock, R.string.options_add_to_private),
    DELETE_FROM_DEVICE(SonaIcons.Delete, R.string.options_delete_from_device),
    EXCLUDE(SonaIcons.Block, R.string.options_exclude),
    SHARE(SonaIcons.Share, R.string.options_share),
}

/** What [this] action is called, as every sheet, menu and bar names it. */
val OptionsAction.label: String
    @Composable get() = stringResource(labelRes)
