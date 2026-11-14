package com.lhacenmed.sona.core.designsystem.icon

import android.content.Context
import android.content.res.Resources
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.common.R
import com.lhacenmed.sona.core.common.cover.DefaultCover

/**
 * Every icon the app draws, by what it means - the one place each is chosen, so one meaning is drawn one way everywhere: in
 * the screens, the sheets and the players alike. Each is the drawable of its name in core/common, `icon_` and its name in
 * snake case, which the notifications, the widgets and the floating window draw by its resource. The drawables are generated
 * from Ionicons by `core/common/icons/icons.txt` - see `docs/ICONS.md`.
 */
object SonaIcons {
    private lateinit var resources: Resources

    /** Reads the icons from [context]'s resources - as the app starts, before any icon is drawn. */
    fun install(context: Context) {
        resources = context.applicationContext.resources
    }

    /** An icon: the drawable [id], read once, when it is first drawn. */
    private fun icon(@DrawableRes id: Int) = lazy { ImageVector.vectorResource(theme = null, res = resources, resId = id) }

    val Add: ImageVector by icon(R.drawable.icon_add)
    val AddPhotoAlternate: ImageVector by icon(R.drawable.icon_add_photo_alternate)
    val Album: ImageVector by icon(R.drawable.icon_album)
    val ArrowBack: ImageVector by icon(R.drawable.icon_arrow_back)
    val ArrowDownward: ImageVector by icon(R.drawable.icon_arrow_downward)
    val ArrowUpward: ImageVector by icon(R.drawable.icon_arrow_upward)
    val Article: ImageVector by icon(R.drawable.icon_article)
    val Artist: ImageVector by icon(R.drawable.icon_artist)
    val AspectRatio: ImageVector by icon(R.drawable.icon_aspect_ratio)
    val AudioFile: ImageVector by icon(R.drawable.icon_audio_file)
    val AutoAwesome: ImageVector by icon(R.drawable.icon_auto_awesome)
    val AutoFixHigh: ImageVector by icon(R.drawable.icon_auto_fix_high)
    /** Turning with the device - and, held one way, [OrientationPortrait] and [OrientationLandscape]: the way it stands. */
    val AutoRotate: ImageVector by icon(R.drawable.icon_auto_rotate)
    val Backspace: ImageVector by icon(R.drawable.icon_backspace)
    val Backup: ImageVector by icon(R.drawable.icon_backup)
    val Bedtime: ImageVector by icon(R.drawable.icon_bedtime)
    val Block: ImageVector by icon(R.drawable.icon_block)
    val Bookmark: ImageVector by icon(R.drawable.icon_bookmark)
    val Brightness: ImageVector by icon(R.drawable.icon_brightness)
    val BugReport: ImageVector by icon(R.drawable.icon_bug_report)
    val CalendarMonth: ImageVector by icon(R.drawable.icon_calendar_month)
    val Check: ImageVector by icon(R.drawable.icon_check)
    val CheckBox: ImageVector by icon(R.drawable.icon_check_box)
    val CheckBoxOutlineBlank: ImageVector by icon(R.drawable.icon_check_box_outline_blank)
    val CheckCircle: ImageVector by icon(R.drawable.icon_check_circle)
    val Close: ImageVector by icon(R.drawable.icon_close)
    val Cloud: ImageVector by icon(R.drawable.icon_cloud)
    val CloudDone: ImageVector by icon(R.drawable.icon_cloud_done)
    val CloudOff: ImageVector by icon(R.drawable.icon_cloud_off)
    val Code: ImageVector by icon(R.drawable.icon_code)
    /** Closing the player down to where it was opened from. */
    val Collapse: ImageVector by icon(R.drawable.icon_collapse)
    val Comment: ImageVector by icon(R.drawable.icon_comment)
    val ContentCopy: ImageVector by icon(R.drawable.icon_content_copy)
    val ContentCut: ImageVector by icon(R.drawable.icon_content_cut)
    val ContentPaste: ImageVector by icon(R.drawable.icon_content_paste)
    val CreateNewFolder: ImageVector by icon(R.drawable.icon_create_new_folder)
    val Delete: ImageVector by icon(R.drawable.icon_delete)
    val DeleteSweep: ImageVector by icon(R.drawable.icon_delete_sweep)
    val Description: ImageVector by icon(R.drawable.icon_description)
    val Deselect: ImageVector by icon(R.drawable.icon_deselect)
    val Details: ImageVector by icon(R.drawable.icon_details)
    val DoNotDisturbOn: ImageVector by icon(R.drawable.icon_do_not_disturb_on)
    val Download: ImageVector by icon(R.drawable.icon_download)
    val DragHandle: ImageVector by icon(R.drawable.icon_drag_handle)
    val DriveFileRenameOutline: ImageVector by icon(R.drawable.icon_drive_file_rename_outline)
    val Edit: ImageVector by icon(R.drawable.icon_edit)
    val EditNote: ImageVector by icon(R.drawable.icon_edit_note)
    val Equalizer: ImageVector by icon(R.drawable.icon_equalizer)
    val Error: ImageVector by icon(R.drawable.icon_error)
    val ExpandLess: ImageVector by icon(R.drawable.icon_expand_less)
    val ExpandMore: ImageVector by icon(R.drawable.icon_expand_more)
    val Export: ImageVector by icon(R.drawable.icon_export)
    val Extension: ImageVector by icon(R.drawable.icon_extension)
    val FastForward: ImageVector by icon(R.drawable.icon_fast_forward)
    val Favorite: ImageVector by icon(R.drawable.icon_favorite)
    val FavoriteBorder: ImageVector by icon(R.drawable.icon_favorite_border)
    val Feedback: ImageVector by icon(R.drawable.icon_feedback)
    val FileOpen: ImageVector by icon(R.drawable.icon_file_open)
    val Fingerprint: ImageVector by icon(R.drawable.icon_fingerprint)
    val Folder: ImageVector by icon(R.drawable.icon_folder)
    val FolderOpen: ImageVector by icon(R.drawable.icon_folder_open)
    val FormatListNumbered: ImageVector by icon(R.drawable.icon_format_list_numbered)
    val Genre: ImageVector by icon(R.drawable.icon_genre)
    val GoToAlbum: ImageVector by icon(R.drawable.icon_go_to_album)
    val GoToArtist: ImageVector by icon(R.drawable.icon_go_to_artist)
    val GraphicEq: ImageVector by icon(R.drawable.icon_graphic_eq)
    val Group: ImageVector by icon(R.drawable.icon_group)
    val HighQuality: ImageVector by icon(R.drawable.icon_high_quality)
    val History: ImageVector by icon(R.drawable.icon_history)
    val Image: ImageVector by icon(R.drawable.icon_image)
    val Import: ImageVector by icon(R.drawable.icon_import)
    val IndeterminateCheckBox: ImageVector by icon(R.drawable.icon_indeterminate_check_box)
    val Info: ImageVector by icon(R.drawable.icon_info)
    val Input: ImageVector by icon(R.drawable.icon_input)
    val KeyboardArrowDown: ImageVector by icon(R.drawable.icon_keyboard_arrow_down)
    val KeyboardArrowRight: ImageVector by icon(R.drawable.icon_keyboard_arrow_right)
    val KeyboardArrowUp: ImageVector by icon(R.drawable.icon_keyboard_arrow_up)
    val Language: ImageVector by icon(R.drawable.icon_language)
    val LibraryAdd: ImageVector by icon(R.drawable.icon_library_add)
    val LibraryMusic: ImageVector by icon(R.drawable.icon_library_music)
    val Link: ImageVector by icon(R.drawable.icon_link)
    val LinkOff: ImageVector by icon(R.drawable.icon_link_off)
    val Lock: ImageVector by icon(R.drawable.icon_lock)
    val LockOpen: ImageVector by icon(R.drawable.icon_lock_open)
    val Login: ImageVector by icon(R.drawable.icon_login)
    val Lyrics: ImageVector by icon(R.drawable.icon_lyrics)
    val Monitor: ImageVector by icon(R.drawable.icon_monitor)
    val MoreTime: ImageVector by icon(R.drawable.icon_more_time)
    val MoreVert: ImageVector by icon(R.drawable.icon_more_vert)
    val Muted: ImageVector by icon(R.drawable.icon_muted)
    val NewReleases: ImageVector by icon(R.drawable.icon_new_releases)
    val Notes: ImageVector by icon(R.drawable.icon_notes)
    val Numbers: ImageVector by icon(R.drawable.icon_numbers)
    val OpenInNew: ImageVector by icon(R.drawable.icon_open_in_new)
    val OrientationLandscape: ImageVector by icon(R.drawable.icon_orientation_landscape)
    val OrientationPortrait: ImageVector by icon(R.drawable.icon_orientation_portrait)
    val Palette: ImageVector by icon(R.drawable.icon_palette)
    val Pause: ImageVector by icon(R.drawable.icon_pause)
    val PictureInPicture: ImageVector by icon(R.drawable.icon_picture_in_picture)
    val Play: ImageVector by icon(R.drawable.icon_play)
    val PlayAsAudio: ImageVector by icon(R.drawable.icon_play_as_audio)
    val PlayCircle: ImageVector by icon(R.drawable.icon_play_circle)
    val PlayNext: ImageVector by icon(R.drawable.icon_play_next)
    val Playlist: ImageVector by icon(R.drawable.icon_playlist)
    val PlaylistAdd: ImageVector by icon(R.drawable.icon_playlist_add)
    val PushPin: ImageVector by icon(R.drawable.icon_push_pin)
    val QueueAdd: ImageVector by icon(R.drawable.icon_queue_add)
    val QueueMusic: ImageVector by icon(R.drawable.icon_queue_music)
    val RecordVoiceOver: ImageVector by icon(R.drawable.icon_record_voice_over)
    val Redo: ImageVector by icon(R.drawable.icon_redo)
    val Refresh: ImageVector by icon(R.drawable.icon_refresh)
    val RemoveCircleOutline: ImageVector by icon(R.drawable.icon_remove_circle_outline)
    val Repeat: ImageVector by icon(R.drawable.icon_repeat)
    val RepeatOne: ImageVector by icon(R.drawable.icon_repeat_one)
    /** Repeat that stops once the track playing ends - the loop around a stop. */
    val RepeatStop: ImageVector by icon(R.drawable.icon_repeat_stop)
    val Replay: ImageVector by icon(R.drawable.icon_replay)
    val RestartAlt: ImageVector by icon(R.drawable.icon_restart_alt)
    val Restore: ImageVector by icon(R.drawable.icon_restore)
    val Save: ImageVector by icon(R.drawable.icon_save)
    val Schedule: ImageVector by icon(R.drawable.icon_schedule)
    val Screenshot: ImageVector by icon(R.drawable.icon_screenshot)
    val Search: ImageVector by icon(R.drawable.icon_search)
    val SearchOff: ImageVector by icon(R.drawable.icon_search_off)
    val Security: ImageVector by icon(R.drawable.icon_security)
    /** Points back; drawn mirrored for the way forward. */
    val SeekBack: ImageVector by icon(R.drawable.icon_seek_back)
    val SelectAll: ImageVector by icon(R.drawable.icon_select_all)
    val Sell: ImageVector by icon(R.drawable.icon_sell)
    val Send: ImageVector by icon(R.drawable.icon_send)
    val Settings: ImageVector by icon(R.drawable.icon_settings)
    val Share: ImageVector by icon(R.drawable.icon_share)
    val ShortText: ImageVector by icon(R.drawable.icon_short_text)
    val ShowChart: ImageVector by icon(R.drawable.icon_show_chart)
    val Shuffle: ImageVector by icon(R.drawable.icon_shuffle)
    val SkipNext: ImageVector by icon(R.drawable.icon_skip_next)
    val SkipPrevious: ImageVector by icon(R.drawable.icon_skip_previous)
    val Smartphone: ImageVector by icon(R.drawable.icon_smartphone)
    val Song: ImageVector by icon(R.drawable.icon_song)
    val Sort: ImageVector by icon(R.drawable.icon_sort)
    val Speed: ImageVector by icon(R.drawable.icon_speed)
    val Storage: ImageVector by icon(R.drawable.icon_storage)
    val Subject: ImageVector by icon(R.drawable.icon_subject)
    val Subscriptions: ImageVector by icon(R.drawable.icon_subscriptions)
    val Subtitles: ImageVector by icon(R.drawable.icon_subtitles)
    val Sync: ImageVector by icon(R.drawable.icon_sync)
    val SyncDisabled: ImageVector by icon(R.drawable.icon_sync_disabled)
    val SystemUpdate: ImageVector by icon(R.drawable.icon_system_update)
    val Terminal: ImageVector by icon(R.drawable.icon_terminal)
    val Timer: ImageVector by icon(R.drawable.icon_timer)
    val Title: ImageVector by icon(R.drawable.icon_title)
    val TouchApp: ImageVector by icon(R.drawable.icon_touch_app)
    val TrendingFlat: ImageVector by icon(R.drawable.icon_trending_flat)
    val TrendingUp: ImageVector by icon(R.drawable.icon_trending_up)
    val Tune: ImageVector by icon(R.drawable.icon_tune)
    val Undo: ImageVector by icon(R.drawable.icon_undo)
    val Verified: ImageVector by icon(R.drawable.icon_verified)
    val Video: ImageVector by icon(R.drawable.icon_video)
    val VideoFile: ImageVector by icon(R.drawable.icon_video_file)
    val VideoLibrary: ImageVector by icon(R.drawable.icon_video_library)
    val Volume: ImageVector by icon(R.drawable.icon_volume)
    val Warning: ImageVector by icon(R.drawable.icon_warning)
    val Wifi: ImageVector by icon(R.drawable.icon_wifi)
    val WifiOff: ImageVector by icon(R.drawable.icon_wifi_off)

    /** What stands in for a missing cover: [DefaultCover]'s glyph, the one every default cover shares. */
    val CoverPlaceholder: ImageVector by lazy {
        drawnIcon(
            name = "CoverPlaceholder",
            size = 24.dp,
            viewportSize = DefaultCover.GLYPH_VIEWPORT_SIZE,
            pathData = DefaultCover.GLYPH_PATH_DATA,
        )
    }

    /**
     * The tick on a selected cover. Drawn small within its 20dp square, which is the whole of the badge - that built-in room
     * around it is what sets the tick's size, so the drawable fills the badge rather than being inset inside it.
     */
    val SelectedBadge: ImageVector by lazy {
        drawnIcon(
            name = "SelectedBadge",
            size = 20.dp,
            viewportSize = 20f,
            pathData = "M8.229,14.062 L4.708,10.521 5.75,9.479 8.229,11.938 14.25,5.938 15.292,7Z",
        )
    }
}

/** A glyph drawn from its path, filled white, so whatever draws it tints it. */
private fun drawnIcon(name: String, size: Dp, viewportSize: Float, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = size,
        defaultHeight = size,
        viewportWidth = viewportSize,
        viewportHeight = viewportSize,
    )
        .addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.White))
        .build()
