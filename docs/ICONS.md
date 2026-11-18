# Icons

Every icon Sona draws is one of `SonaIcons`, by what it means, never by its look - the screens', the sheets' and the
players' alike. A screen asks for `SonaIcons.Delete` or `SonaIcons.Play`, and `SonaIcons` decides how that meaning is
drawn. Redrawing an icon, or the whole set, never touches a screen.

## One set

| Where | Drawn as | Used by |
| --- | --- | --- |
| `core/designsystem/.../icon/SonaIcons.kt` | Material's icons, filled, unless the pack draws its own | Everything the app draws: screens, sheets, rows, dialogs, top bars, the music player and the video player |

What Android draws itself - the playback notification, the widgets, the floating window, the download and backup
notifications - takes a drawable, not an `ImageVector`. Those icons are the pack's drawables in
`core/common/src/main/res/drawable`: Material's own glyphs, exported with `IconPackExport`, so they are the same drawings
as the app's. The notification's toggles that cannot be dimmed when off use Material's "on" glyphs - `icon_repeat_on.xml`,
`icon_repeat_one_on.xml`, `icon_shuffle_on.xml` - which are drawables alone. The popup menu's arrows,
`icon_arrow_back.xml` and `icon_keyboard_arrow_right.xml`, are the same for its views, in `core/designsystem`.

Outside the set, and drawn as they are: the launcher icon, the splash, the launcher shortcuts, the notification's
status bar icon (`ic_stat_sona`), the update notification's (`ic_update`), and the brand marks on About (`ic_sona_mark`,
`ic_github`).

## Redesigning the icons

The icon pack is a set of vector drawables, one per icon, named `icon_` and the `SonaIcons` name in snake case:
`SonaIcons.AddPhotoAlternate` is `icon_add_photo_alternate.xml`. A drawable of that name in any module's `res/drawable`
is drawn in the icon's place, everywhere it appears. So:

1. Export the icons as they are now, as the starting point:

   ```bash
   ICON_PACK_DIR=/path/to/pack ./gradlew :core:designsystem:testDebugUnitTest --tests "*IconPackExport*"
   ```

   It writes `xml/` and `svg/` - one file per `SonaIcons` icon, named as the pack names it.
2. Redraw them in any vector tool, on a 24 by 24 grid, in one colour: whatever shows an icon tints it.
3. Turn each SVG into a vector drawable with Android Studio's Vector Asset tool, keeping its name.
4. Put the drawables in `core/designsystem/src/main/res/drawable` - and those already in
   `core/common/src/main/res/drawable` in their place there, where Android draws them by their resource.

A pack can be partial: an icon it has no drawable for is drawn as Material draws it. An icon that points one way - back,
forward - sets `android:autoMirrored="true"`, so it turns round where text runs right to left.

## Changing the style at once

Without a pack, `SonaIcons` draws Material's filled icons. The style is two lines at the top of `SonaIcons.kt` - its
imports and its `Glyphs` and `MirroredGlyphs` - and changing them redraws every icon not in the pack: `Icons.Outlined`,
`Icons.Rounded` or `Icons.Sharp`, with the matching imports. Export the drawables in `core/common` again in the new style.

## Adding an icon

Add it to `SonaIcons` by what it means - `val Archive: ImageVector by icon { Glyphs.Archive }` - and use it by that name.
Two meanings that look alike today are still two icons, so a pack can tell them apart; one meaning is never two icons.
An icon Android draws by its resource is also exported into `core/common/src/main/res/drawable`.

## Every icon

| Icon | Pack drawable | Drawn without a pack as |
| --- | --- | --- |
| `SonaIcons.Add` | `icon_add.xml` | Material `Add` |
| `SonaIcons.AddPhotoAlternate` | `icon_add_photo_alternate.xml` | Material `AddPhotoAlternate` |
| `SonaIcons.Album` | `icon_album.xml` | Material `Album` |
| `SonaIcons.ArrowBack` | `icon_arrow_back.xml` | Material `ArrowBack`, mirrored |
| `SonaIcons.ArrowForward` | `icon_arrow_forward.xml` | Material `ArrowForward`, mirrored |
| `SonaIcons.ArrowDownward` | `icon_arrow_downward.xml` | Material `ArrowDownward` |
| `SonaIcons.ArrowUpward` | `icon_arrow_upward.xml` | Material `ArrowUpward` |
| `SonaIcons.Article` | `icon_article.xml` | Material `Article`, mirrored |
| `SonaIcons.Artist` | `icon_artist.xml` | Material `Person` |
| `SonaIcons.AspectClassic` | `icon_aspect_classic.xml` | Material `Crop54` |
| `SonaIcons.AspectCrop` | `icon_aspect_crop.xml` | Material `Crop` |
| `SonaIcons.AspectExtraWide` | `icon_aspect_extra_wide.xml` | Material `PanoramaWideAngle` |
| `SonaIcons.AspectFit` | `icon_aspect_fit.xml` | Material `FitScreen` |
| `SonaIcons.AspectOriginal` | `icon_aspect_original.xml` | Material `CropOriginal` |
| `SonaIcons.AspectStretch` | `icon_aspect_stretch.xml` | Material `ZoomOutMap` |
| `SonaIcons.AspectWide` | `icon_aspect_wide.xml` | Material `Crop169` |
| `SonaIcons.AudioFile` | `icon_audio_file.xml` | Material `AudioFile` |
| `SonaIcons.AutoAwesome` | `icon_auto_awesome.xml` | Material `AutoAwesome` |
| `SonaIcons.AutoFixHigh` | `icon_auto_fix_high.xml` | Material `AutoFixHigh` |
| `SonaIcons.AutoRotate` | `icon_auto_rotate.xml` | Material `ScreenRotation` |
| `SonaIcons.Backspace` | `icon_backspace.xml` | Material `Backspace`, mirrored |
| `SonaIcons.Backup` | `icon_backup.xml` | Material `Backup` |
| `SonaIcons.Bedtime` | `icon_bedtime.xml` | Material `Bedtime` |
| `SonaIcons.Block` | `icon_block.xml` | Material `Block` |
| `SonaIcons.Bookmark` | `icon_bookmark.xml` | Material `Bookmark` |
| `SonaIcons.Brightness` | `icon_brightness.xml` | Material `BrightnessMedium` |
| `SonaIcons.BugReport` | `icon_bug_report.xml` | Material `BugReport` |
| `SonaIcons.CalendarMonth` | `icon_calendar_month.xml` | Material `CalendarMonth` |
| `SonaIcons.Check` | `icon_check.xml` | Material `Check` |
| `SonaIcons.CheckBox` | `icon_check_box.xml` | Material `CheckBox` |
| `SonaIcons.CheckBoxOutlineBlank` | `icon_check_box_outline_blank.xml` | Material `CheckBoxOutlineBlank` |
| `SonaIcons.CheckCircle` | `icon_check_circle.xml` | Material `CheckCircle` |
| `SonaIcons.Close` | `icon_close.xml` | Material `Close` |
| `SonaIcons.Cloud` | `icon_cloud.xml` | Material `Cloud` |
| `SonaIcons.CloudDone` | `icon_cloud_done.xml` | Material `CloudDone` |
| `SonaIcons.CloudOff` | `icon_cloud_off.xml` | Material `CloudOff` |
| `SonaIcons.Code` | `icon_code.xml` | Material `Code` |
| `SonaIcons.Collapse` | `icon_collapse.xml` | Material `KeyboardArrowDown` |
| `SonaIcons.Comment` | `icon_comment.xml` | Material `Comment`, mirrored |
| `SonaIcons.ContentCopy` | `icon_content_copy.xml` | Material `ContentCopy` |
| `SonaIcons.ContentCut` | `icon_content_cut.xml` | Material `ContentCut` |
| `SonaIcons.ContentPaste` | `icon_content_paste.xml` | Material `ContentPaste` |
| `SonaIcons.CreateNewFolder` | `icon_create_new_folder.xml` | Material `CreateNewFolder` |
| `SonaIcons.Delete` | `icon_delete.xml` | Material `Delete` |
| `SonaIcons.DeleteSweep` | `icon_delete_sweep.xml` | Material `DeleteSweep` |
| `SonaIcons.Description` | `icon_description.xml` | Material `Description` |
| `SonaIcons.Deselect` | `icon_deselect.xml` | Material `Deselect` |
| `SonaIcons.Details` | `icon_details.xml` | Material `Info` |
| `SonaIcons.DoNotDisturbOn` | `icon_do_not_disturb_on.xml` | Material `DoNotDisturbOn` |
| `SonaIcons.Download` | `icon_download.xml` | Material `Download` |
| `SonaIcons.DragHandle` | `icon_drag_handle.xml` | Material `DragHandle` |
| `SonaIcons.DriveFileRenameOutline` | `icon_drive_file_rename_outline.xml` | Material `DriveFileRenameOutline` |
| `SonaIcons.Edit` | `icon_edit.xml` | Material `Edit` |
| `SonaIcons.EditNote` | `icon_edit_note.xml` | Material `EditNote` |
| `SonaIcons.Equalizer` | `icon_equalizer.xml` | Material `Equalizer` |
| `SonaIcons.Export` | `icon_export.xml` | Material `FileUpload` |
| `SonaIcons.Error` | `icon_error.xml` | Material `Error` |
| `SonaIcons.ExpandLess` | `icon_expand_less.xml` | Material `ExpandLess` |
| `SonaIcons.ExpandMore` | `icon_expand_more.xml` | Material `ExpandMore` |
| `SonaIcons.Extension` | `icon_extension.xml` | Material `Extension` |
| `SonaIcons.FastForward` | `icon_fast_forward.xml` | Material `FastForward` |
| `SonaIcons.Favorite` | `icon_favorite.xml` | Material `Favorite` |
| `SonaIcons.FavoriteBorder` | `icon_favorite_border.xml` | Material `FavoriteBorder` |
| `SonaIcons.Feedback` | `icon_feedback.xml` | Material `Feedback` |
| `SonaIcons.FileOpen` | `icon_file_open.xml` | Material `FileOpen` |
| `SonaIcons.File` | `icon_file.xml` | Material `InsertDriveFile` |
| `SonaIcons.Fingerprint` | `icon_fingerprint.xml` | Material `Fingerprint` |
| `SonaIcons.Folder` | `icon_folder.xml` | Material `Folder` |
| `SonaIcons.FolderOpen` | `icon_folder_open.xml` | Material `FolderOpen` |
| `SonaIcons.FormatListNumbered` | `icon_format_list_numbered.xml` | Material `FormatListNumbered` |
| `SonaIcons.Genre` | `icon_genre.xml` | Material `Category` |
| `SonaIcons.GoToAlbum` | `icon_go_to_album.xml` | Material `Album` |
| `SonaIcons.GoToArtist` | `icon_go_to_artist.xml` | Material `Person` |
| `SonaIcons.GraphicEq` | `icon_graphic_eq.xml` | Material `GraphicEq` |
| `SonaIcons.Group` | `icon_group.xml` | Material `Group` |
| `SonaIcons.HighQuality` | `icon_high_quality.xml` | Material `HighQuality` |
| `SonaIcons.History` | `icon_history.xml` | Material `History` |
| `SonaIcons.Image` | `icon_image.xml` | Material `Image` |
| `SonaIcons.Import` | `icon_import.xml` | Material `FileDownload` |
| `SonaIcons.IndeterminateCheckBox` | `icon_indeterminate_check_box.xml` | Material `IndeterminateCheckBox` |
| `SonaIcons.Info` | `icon_info.xml` | Material `Info` |
| `SonaIcons.Input` | `icon_input.xml` | Material `Input`, mirrored |
| `SonaIcons.KeyboardArrowDown` | `icon_keyboard_arrow_down.xml` | Material `KeyboardArrowDown` |
| `SonaIcons.KeyboardArrowRight` | `icon_keyboard_arrow_right.xml` | Material `KeyboardArrowRight`, mirrored |
| `SonaIcons.KeyboardArrowUp` | `icon_keyboard_arrow_up.xml` | Material `KeyboardArrowUp` |
| `SonaIcons.Language` | `icon_language.xml` | Material `Language` |
| `SonaIcons.LibraryAdd` | `icon_library_add.xml` | Material `LibraryAdd` |
| `SonaIcons.LibraryMusic` | `icon_library_music.xml` | Material `LibraryMusic` |
| `SonaIcons.Link` | `icon_link.xml` | Material `Link` |
| `SonaIcons.LinkOff` | `icon_link_off.xml` | Material `LinkOff` |
| `SonaIcons.Lock` | `icon_lock.xml` | Material `Lock` |
| `SonaIcons.LockOpen` | `icon_lock_open.xml` | Material `LockOpen` |
| `SonaIcons.Login` | `icon_login.xml` | Material `Login`, mirrored |
| `SonaIcons.Lyrics` | `icon_lyrics.xml` | Material `Lyrics` |
| `SonaIcons.Monitor` | `icon_monitor.xml` | Material `Monitor` |
| `SonaIcons.MoreTime` | `icon_more_time.xml` | Material `MoreTime` |
| `SonaIcons.MoreVert` | `icon_more_vert.xml` | Material `MoreVert` |
| `SonaIcons.Muted` | `icon_muted.xml` | Material `VolumeOff`, mirrored |
| `SonaIcons.NewReleases` | `icon_new_releases.xml` | Material `NewReleases` |
| `SonaIcons.Notes` | `icon_notes.xml` | Material `Notes`, mirrored |
| `SonaIcons.Numbers` | `icon_numbers.xml` | Material `Numbers` |
| `SonaIcons.OpenInNew` | `icon_open_in_new.xml` | Material `OpenInNew`, mirrored |
| `SonaIcons.OrientationLandscape` | `icon_orientation_landscape.xml` | Material `StayCurrentLandscape` |
| `SonaIcons.OrientationPortrait` | `icon_orientation_portrait.xml` | Material `StayCurrentPortrait` |
| `SonaIcons.Palette` | `icon_palette.xml` | Material `Palette` |
| `SonaIcons.Pause` | `icon_pause.xml` | Material `Pause` |
| `SonaIcons.PictureInPicture` | `icon_picture_in_picture.xml` | Material `PictureInPictureAlt` |
| `SonaIcons.Play` | `icon_play.xml` | Material `PlayArrow` |
| `SonaIcons.PlayAsAudio` | `icon_play_as_audio.xml` | Material `Headphones` |
| `SonaIcons.PlayCircle` | `icon_play_circle.xml` | Material `PlayCircle` |
| `SonaIcons.PlayNext` | `icon_play_next.xml` | Material `QueuePlayNext` |
| `SonaIcons.Playlist` | `icon_playlist.xml` | Material `QueueMusic`, mirrored |
| `SonaIcons.PlaylistAdd` | `icon_playlist_add.xml` | Material `PlaylistAdd`, mirrored |
| `SonaIcons.PushPin` | `icon_push_pin.xml` | Material `PushPin` |
| `SonaIcons.QueueAdd` | `icon_queue_add.xml` | Material `AddToQueue` |
| `SonaIcons.QueueMusic` | `icon_queue_music.xml` | Material `QueueMusic`, mirrored |
| `SonaIcons.RecordVoiceOver` | `icon_record_voice_over.xml` | Material `RecordVoiceOver` |
| `SonaIcons.Redo` | `icon_redo.xml` | Material `Redo`, mirrored |
| `SonaIcons.Refresh` | `icon_refresh.xml` | Material `Refresh` |
| `SonaIcons.RemoveCircleOutline` | `icon_remove_circle_outline.xml` | Material `RemoveCircleOutline` |
| `SonaIcons.Repeat` | `icon_repeat.xml` | Material `Repeat` |
| `SonaIcons.RepeatOne` | `icon_repeat_one.xml` | Material `RepeatOne` |
| `SonaIcons.RepeatStop` | `icon_repeat_stop.xml` | Material `LooksOne` |
| `SonaIcons.Replay` | `icon_replay.xml` | Material `Replay` |
| `SonaIcons.RestartAlt` | `icon_restart_alt.xml` | Material `RestartAlt` |
| `SonaIcons.Restore` | `icon_restore.xml` | Material `Restore` |
| `SonaIcons.Save` | `icon_save.xml` | Material `Save` |
| `SonaIcons.Schedule` | `icon_schedule.xml` | Material `Schedule` |
| `SonaIcons.Screenshot` | `icon_screenshot.xml` | Material `Screenshot` |
| `SonaIcons.Search` | `icon_search.xml` | Material `Search` |
| `SonaIcons.SearchOff` | `icon_search_off.xml` | Material `SearchOff` |
| `SonaIcons.Security` | `icon_security.xml` | Material `Security` |
| `SonaIcons.SeekForward` | `icon_seek_forward.xml` | Material `PlayArrow` |
| `SonaIcons.SelectAll` | `icon_select_all.xml` | Material `SelectAll` |
| `SonaIcons.Sell` | `icon_sell.xml` | Material `Sell` |
| `SonaIcons.Send` | `icon_send.xml` | Material `Send`, mirrored |
| `SonaIcons.Settings` | `icon_settings.xml` | Material `Settings` |
| `SonaIcons.Share` | `icon_share.xml` | Material `Share` |
| `SonaIcons.ShortText` | `icon_short_text.xml` | Material `ShortText` |
| `SonaIcons.ShowChart` | `icon_show_chart.xml` | Material `ShowChart`, mirrored |
| `SonaIcons.Shuffle` | `icon_shuffle.xml` | Material `Shuffle` |
| `SonaIcons.SkipNext` | `icon_skip_next.xml` | Material `SkipNext` |
| `SonaIcons.SkipPrevious` | `icon_skip_previous.xml` | Material `SkipPrevious` |
| `SonaIcons.Smartphone` | `icon_smartphone.xml` | Material `Smartphone` |
| `SonaIcons.Song` | `icon_song.xml` | Material `MusicNote` |
| `SonaIcons.Sort` | `icon_sort.xml` | Material `Sort`, mirrored |
| `SonaIcons.Speed` | `icon_speed.xml` | Material `Speed` |
| `SonaIcons.Storage` | `icon_storage.xml` | Material `Storage` |
| `SonaIcons.Subject` | `icon_subject.xml` | Material `Subject` |
| `SonaIcons.Subscriptions` | `icon_subscriptions.xml` | Material `Subscriptions` |
| `SonaIcons.Subtitles` | `icon_subtitles.xml` | Material `Subtitles` |
| `SonaIcons.Sync` | `icon_sync.xml` | Material `Sync` |
| `SonaIcons.SyncDisabled` | `icon_sync_disabled.xml` | Material `SyncDisabled` |
| `SonaIcons.SystemUpdate` | `icon_system_update.xml` | Material `SystemUpdate` |
| `SonaIcons.Tab` | `icon_tab.xml` | Material `Tab` |
| `SonaIcons.Terminal` | `icon_terminal.xml` | Material `Terminal` |
| `SonaIcons.ThumbUp` | `icon_thumb_up.xml` | Material `ThumbUp` |
| `SonaIcons.Timer` | `icon_timer.xml` | Material `Timer` |
| `SonaIcons.Title` | `icon_title.xml` | Material `Title` |
| `SonaIcons.TouchApp` | `icon_touch_app.xml` | Material `TouchApp` |
| `SonaIcons.TrendingFlat` | `icon_trending_flat.xml` | Material `TrendingFlat`, mirrored |
| `SonaIcons.TrendingUp` | `icon_trending_up.xml` | Material `TrendingUp`, mirrored |
| `SonaIcons.Tune` | `icon_tune.xml` | Material `Tune` |
| `SonaIcons.Undo` | `icon_undo.xml` | Material `Undo`, mirrored |
| `SonaIcons.Verified` | `icon_verified.xml` | Material `Verified` |
| `SonaIcons.Video` | `icon_video.xml` | Material `Movie` |
| `SonaIcons.VideoFile` | `icon_video_file.xml` | Material `VideoFile` |
| `SonaIcons.VideoLibrary` | `icon_video_library.xml` | Material `VideoLibrary` |
| `SonaIcons.Volume` | `icon_volume.xml` | Material `VolumeUp`, mirrored |
| `SonaIcons.Warning` | `icon_warning.xml` | Material `Warning` |
| `SonaIcons.Wifi` | `icon_wifi.xml` | Material `Wifi` |
| `SonaIcons.WifiOff` | `icon_wifi_off.xml` | Material `WifiOff` |
| `SonaIcons.CoverPlaceholder` | `icon_cover_placeholder.xml` | Sona's own |
| `SonaIcons.SelectedBadge` | `icon_selected_badge.xml` | Sona's own |
