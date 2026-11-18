package com.lhacenmed.sona.core.designsystem.icon

import androidx.compose.material.icons.Icons
// The style every icon is drawn in - these two imports and the two values below: filled, outlined, rounded or sharp. Rounded,
// as Material Symbols Rounded is: the design's own, whose outlined actions are drawn from its paths below - see symbolIcon.
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.common.cover.DefaultCover

/** Material's icons in the style the app draws them in - see the imports above. */
private val Glyphs = Icons.Rounded

/** The same style, for the icons that point one way and turn around where text runs right to left. */
private val MirroredGlyphs = Icons.AutoMirrored.Rounded

/**
 * Every icon the app draws, by what it means - the one place each is chosen, so one meaning is drawn one way everywhere, the
 * players included, and the whole app changes style by changing [Glyphs] and [MirroredGlyphs]. An icon pack replaces any of
 * them - see [SonaIconPack]. Those Android draws itself - the notifications', the widgets' - are the pack's drawables already.
 */
object SonaIcons {
    val AccountCircle: ImageVector by icon { Glyphs.AccountCircle }
    val Add: ImageVector by icon { Glyphs.Add }
    val AddPhotoAlternate: ImageVector by icon { Glyphs.AddPhotoAlternate }
    val Album: ImageVector by icon { Glyphs.Album }
    val Archive: ImageVector by icon { Glyphs.Archive }
    val ArrowBack: ImageVector by icon { MirroredGlyphs.ArrowBack }
    val ArrowForward: ImageVector by icon { MirroredGlyphs.ArrowForward }
    val ArrowDownward: ImageVector by icon { Glyphs.ArrowDownward }
    val ArrowUpward: ImageVector by icon { Glyphs.ArrowUpward }
    val Article: ImageVector by icon { MirroredGlyphs.Article }
    val Artist: ImageVector by icon { Glyphs.Person }
    val AspectClassic: ImageVector by icon { Glyphs.Crop54 }
    val AspectCrop: ImageVector by icon { Glyphs.Crop }
    val AspectExtraWide: ImageVector by icon { Glyphs.PanoramaWideAngle }
    val AspectFit: ImageVector by icon { Glyphs.FitScreen }
    val AspectOriginal: ImageVector by icon { Glyphs.CropOriginal }
    val AspectStretch: ImageVector by icon { Glyphs.ZoomOutMap }
    val AspectWide: ImageVector by icon { Glyphs.Crop169 }
    val AudioFile: ImageVector by icon { Glyphs.AudioFile }
    val AutoAwesome: ImageVector by icon { Glyphs.AutoAwesome }
    val AutoFixHigh: ImageVector by icon { Glyphs.AutoFixHigh }
    /** Turning with the device - and, held one way, [OrientationPortrait] and [OrientationLandscape]: the way it stands. */
    val AutoRotate: ImageVector by icon { Glyphs.ScreenRotation }
    val Backspace: ImageVector by icon { MirroredGlyphs.Backspace }
    val Backup: ImageVector by icon { Glyphs.Backup }
    val Bedtime: ImageVector by icon { Glyphs.Bedtime }
    val Block: ImageVector by icon { Glyphs.Block }
    val Bookmark: ImageVector by icon { Glyphs.Bookmark }
    val Brightness: ImageVector by icon { Glyphs.BrightnessMedium }
    /** The theme following the system's. */
    val BrightnessAuto: ImageVector by icon { Glyphs.BrightnessAuto }
    val BugReport: ImageVector by icon { Glyphs.BugReport }
    val CalendarMonth: ImageVector by icon { Glyphs.CalendarMonth }
    val Check: ImageVector by icon { Glyphs.Check }
    val CheckBox: ImageVector by icon { Glyphs.CheckBox }
    val CheckBoxOutlineBlank: ImageVector by icon { Glyphs.CheckBoxOutlineBlank }
    val CheckCircle: ImageVector by icon { Glyphs.CheckCircle }
    val Close: ImageVector by icon { Glyphs.Close }
    val ClosedCaption: ImageVector by icon { Glyphs.ClosedCaption }
    val Cloud: ImageVector by icon { Glyphs.Cloud }
    val CloudDone: ImageVector by icon { Glyphs.CloudDone }
    val CloudOff: ImageVector by icon { Glyphs.CloudOff }
    val CloudSync: ImageVector by icon { Glyphs.CloudSync }
    val CloudUpload: ImageVector by icon { Glyphs.CloudUpload }
    val Code: ImageVector by icon { Glyphs.Code }
    /** Closing the player down to where it was opened from. */
    val Collapse: ImageVector by icon { Glyphs.KeyboardArrowDown }
    val Comment: ImageVector by icon { MirroredGlyphs.Comment }
    val ContentCopy: ImageVector by icon { Glyphs.ContentCopy }
    val ContentCut: ImageVector by icon { Glyphs.ContentCut }
    val ContentPaste: ImageVector by icon { Glyphs.ContentPaste }
    val Cookie: ImageVector by icon { Glyphs.Cookie }
    /** The app taking on the colours of what is playing. */
    val CoverColors: ImageVector by icon { Glyphs.FormatPaint }
    val CreateNewFolder: ImageVector by icon { Glyphs.CreateNewFolder }
    val DarkMode: ImageVector by icon { Glyphs.DarkMode }
    val Delete: ImageVector by icon { Glyphs.Delete }
    val DeleteForever: ImageVector by icon { Glyphs.DeleteForever }
    val DeleteSweep: ImageVector by icon { Glyphs.DeleteSweep }
    val Description: ImageVector by icon { Glyphs.Description }
    val Deselect: ImageVector by icon { Glyphs.Deselect }
    val Details: ImageVector by icon { Glyphs.Info }
    val DoNotDisturbOn: ImageVector by icon { Glyphs.DoNotDisturbOn }
    val Download: ImageVector by icon { symbolIcon("Download", "M480-337q-8 0-15-2.5t-13-8.5L308-492q-12-12-11.5-28t11.5-28q12-12 28.5-12.5T365-549l75 75v-286q0-17 11.5-28.5T480-800q17 0 28.5 11.5T520-760v286l75-75q12-12 28.5-11.5T652-548q11 12 11.5 28T652-492L508-348q-6 6-13 8.5t-15 2.5ZM240-160q-33 0-56.5-23.5T160-240v-80q0-17 11.5-28.5T200-360q17 0 28.5 11.5T240-320v80h480v-80q0-17 11.5-28.5T760-360q17 0 28.5 11.5T800-320v80q0 33-23.5 56.5T720-160H240Z") }
    val Downloading: ImageVector by icon { Glyphs.Downloading }
    val DragHandle: ImageVector by icon { Glyphs.DragHandle }
    val DriveFileRenameOutline: ImageVector by icon { Glyphs.DriveFileRenameOutline }
    val Edit: ImageVector by icon { Glyphs.Edit }
    val EditNote: ImageVector by icon { Glyphs.EditNote }
    val Equalizer: ImageVector by icon { Glyphs.Equalizer }
    val Export: ImageVector by icon { Glyphs.FileUpload }
    val Error: ImageVector by icon { Glyphs.Error }
    val ExpandLess: ImageVector by icon { Glyphs.ExpandLess }
    val ExpandMore: ImageVector by icon { Glyphs.ExpandMore }
    val Extension: ImageVector by icon { Glyphs.Extension }
    val FastForward: ImageVector by icon { Glyphs.FastForward }
    val Favorite: ImageVector by icon { Glyphs.Favorite }
    val FavoriteBorder: ImageVector by icon { Glyphs.FavoriteBorder }
    val Feedback: ImageVector by icon { Glyphs.Feedback }
    val FileOpen: ImageVector by icon { Glyphs.FileOpen }
    val File: ImageVector by icon { Glyphs.InsertDriveFile }
    val Fingerprint: ImageVector by icon { Glyphs.Fingerprint }
    val Folder: ImageVector by icon { Glyphs.Folder }
    val FolderOpen: ImageVector by icon { Glyphs.FolderOpen }
    val FolderSpecial: ImageVector by icon { Glyphs.FolderSpecial }
    val FormatListNumbered: ImageVector by icon { Glyphs.FormatListNumbered }
    val GeneratingTokens: ImageVector by icon { Glyphs.GeneratingTokens }
    val Genre: ImageVector by icon { Glyphs.Category }
    val GoToAlbum: ImageVector by icon { Glyphs.Album }
    val GoToArtist: ImageVector by icon { Glyphs.Person }
    val GraphicEq: ImageVector by icon { Glyphs.GraphicEq }
    val Group: ImageVector by icon { Glyphs.Group }
    val Help: ImageVector by icon { MirroredGlyphs.HelpOutline }
    val HighQuality: ImageVector by icon { Glyphs.HighQuality }
    val History: ImageVector by icon { Glyphs.History }
    val HistoryToggleOff: ImageVector by icon { Glyphs.HistoryToggleOff }
    val Image: ImageVector by icon { Glyphs.Image }
    val Import: ImageVector by icon { Glyphs.FileDownload }
    val IndeterminateCheckBox: ImageVector by icon { Glyphs.IndeterminateCheckBox }
    val Info: ImageVector by icon { Glyphs.Info }
    val Input: ImageVector by icon { MirroredGlyphs.Input }
    /** Installing an app - an update of Sona's. */
    val InstallMobile: ImageVector by icon { Glyphs.InstallMobile }
    val Key: ImageVector by icon { Glyphs.Key }
    val KeyboardArrowDown: ImageVector by icon { Glyphs.KeyboardArrowDown }
    val KeyboardArrowRight: ImageVector by icon { MirroredGlyphs.KeyboardArrowRight }
    val KeyboardArrowUp: ImageVector by icon { Glyphs.KeyboardArrowUp }
    val Language: ImageVector by icon { Glyphs.Language }
    val LibraryAdd: ImageVector by icon { Glyphs.LibraryAdd }
    val LibraryMusic: ImageVector by icon { Glyphs.LibraryMusic }
    val LightMode: ImageVector by icon { Glyphs.LightMode }
    val Link: ImageVector by icon { Glyphs.Link }
    val LinkOff: ImageVector by icon { Glyphs.LinkOff }
    val Lock: ImageVector by icon { Glyphs.Lock }
    val LockOpen: ImageVector by icon { Glyphs.LockOpen }
    val Login: ImageVector by icon { MirroredGlyphs.Login }
    val Lyrics: ImageVector by icon { Glyphs.Lyrics }
    val MoneyOff: ImageVector by icon { Glyphs.MoneyOff }
    val Monitor: ImageVector by icon { Glyphs.Monitor }
    val MoreTime: ImageVector by icon { Glyphs.MoreTime }
    val MoreVert: ImageVector by icon { symbolIcon("MoreVert", "M480-160q-33 0-56.5-23.5T400-240q0-33 23.5-56.5T480-320q33 0 56.5 23.5T560-240q0 33-23.5 56.5T480-160Zm0-240q-33 0-56.5-23.5T400-480q0-33 23.5-56.5T480-560q33 0 56.5 23.5T560-480q0 33-23.5 56.5T480-400Zm0-240q-33 0-56.5-23.5T400-720q0-33 23.5-56.5T480-800q33 0 56.5 23.5T560-720q0 33-23.5 56.5T480-640Z") }
    val Muted: ImageVector by icon { MirroredGlyphs.VolumeOff }
    val NewReleases: ImageVector by icon { Glyphs.NewReleases }
    val Notes: ImageVector by icon { MirroredGlyphs.Notes }
    val Notifications: ImageVector by icon { Glyphs.Notifications }
    val Numbers: ImageVector by icon { Glyphs.Numbers }
    val OfflineBolt: ImageVector by icon { Glyphs.OfflineBolt }
    val OpenInNew: ImageVector by icon { MirroredGlyphs.OpenInNew }
    val OrientationLandscape: ImageVector by icon { Glyphs.StayCurrentLandscape }
    val OrientationPortrait: ImageVector by icon { Glyphs.StayCurrentPortrait }
    val Palette: ImageVector by icon { Glyphs.Palette }
    val Pause: ImageVector by icon { Glyphs.Pause }
    val PictureInPicture: ImageVector by icon { Glyphs.PictureInPictureAlt }
    val Play: ImageVector by icon { Glyphs.PlayArrow }
    val PlayAsAudio: ImageVector by icon { Glyphs.Headphones }
    val PlayCircle: ImageVector by icon { Glyphs.PlayCircle }
    val PlayNext: ImageVector by icon { Glyphs.QueuePlayNext }
    val Playlist: ImageVector by icon { MirroredGlyphs.QueueMusic }
    val PlaylistAdd: ImageVector by icon { MirroredGlyphs.PlaylistAdd }
    val PushPin: ImageVector by icon { Glyphs.PushPin }
    val QueueAdd: ImageVector by icon { Glyphs.AddToQueue }
    val QueueMusic: ImageVector by icon { MirroredGlyphs.QueueMusic }
    val RecordVoiceOver: ImageVector by icon { Glyphs.RecordVoiceOver }
    val Redo: ImageVector by icon { MirroredGlyphs.Redo }
    val Refresh: ImageVector by icon { Glyphs.Refresh }
    val RemoveCircleOutline: ImageVector by icon { Glyphs.RemoveCircleOutline }
    val Repeat: ImageVector by icon { Glyphs.Repeat }
    val RepeatOne: ImageVector by icon { Glyphs.RepeatOne }
    /** Playing the one track, then stopping - the boxed "1" Fossify's player draws it with. */
    val RepeatStop: ImageVector by icon { Glyphs.LooksOne }
    val Replay: ImageVector by icon { Glyphs.Replay }
    val RestartAlt: ImageVector by icon { Glyphs.RestartAlt }
    val Restore: ImageVector by icon { Glyphs.Restore }
    val Save: ImageVector by icon { Glyphs.Save }
    val Schedule: ImageVector by icon { Glyphs.Schedule }
    val Screenshot: ImageVector by icon { Glyphs.Screenshot }
    val Search: ImageVector by icon { symbolIcon("Search", "M380-320q-109 0-184.5-75.5T120-580q0-109 75.5-184.5T380-840q109 0 184.5 75.5T640-580q0 44-14 83t-38 69l224 224q11 11 11 28t-11 28q-11 11-28 11t-28-11L532-372q-30 24-69 38t-83 14Zm0-80q75 0 127.5-52.5T560-580q0-75-52.5-127.5T380-760q-75 0-127.5 52.5T200-580q0 75 52.5 127.5T380-400Z") }
    val SearchOff: ImageVector by icon { Glyphs.SearchOff }
    val Security: ImageVector by icon { Glyphs.Security }
    /** Points forward; drawn mirrored for the way back. */
    val SeekForward: ImageVector by icon { Glyphs.PlayArrow }
    val SelectAll: ImageVector by icon { Glyphs.SelectAll }
    val Sell: ImageVector by icon { Glyphs.Sell }
    val Send: ImageVector by icon { MirroredGlyphs.Send }
    val Settings: ImageVector by icon { symbolIcon("Settings", "M433-80q-27 0-46.5-18T363-142l-9-66q-13-5-24.5-12T307-235l-62 26q-25 11-50 2t-39-32l-47-82q-14-23-8-49t27-43l53-40q-1-7-1-13.5v-27q0-6.5 1-13.5l-53-40q-21-17-27-43t8-49l47-82q14-23 39-32t50 2l62 26q11-8 23-15t24-12l9-66q4-26 23.5-44t46.5-18h94q27 0 46.5 18t23.5 44l9 66q13 5 24.5 12t22.5 15l62-26q25-11 50-2t39 32l47 82q14 23 8 49t-27 43l-53 40q1 7 1 13.5v27q0 6.5-2 13.5l53 40q21 17 27 43t-8 49l-48 82q-14 23-39 32t-50-2l-60-26q-11 8-23 15t-24 12l-9 66q-4 26-23.5 44T527-80h-94Zm7-80h79l14-106q31-8 57.5-23.5T639-327l99 41 39-68-86-65q5-14 7-29.5t2-31.5q0-16-2-31.5t-7-29.5l86-65-39-68-99 42q-22-23-48.5-38.5T533-694l-13-106h-79l-14 106q-31 8-57.5 23.5T321-633l-99-41-39 68 86 64q-5 15-7 30t-2 32q0 16 2 31t7 30l-86 65 39 68 99-42q22 23 48.5 38.5T427-266l13 106Zm42-180q58 0 99-41t41-99q0-58-41-99t-99-41q-59 0-99.5 41T342-480q0 58 40.5 99t99.5 41Zm-2-140Z") }
    val SettingsApplications: ImageVector by icon { Glyphs.SettingsApplications }
    val SettingsEthernet: ImageVector by icon { Glyphs.SettingsEthernet }
    val Share: ImageVector by icon { Glyphs.Share }
    val ShortText: ImageVector by icon { Glyphs.ShortText }
    val ShowChart: ImageVector by icon { MirroredGlyphs.ShowChart }
    val Shuffle: ImageVector by icon { Glyphs.Shuffle }
    val SignalCellular: ImageVector by icon { Glyphs.SignalCellular4Bar }
    val SignalCellularOff: ImageVector by icon { Glyphs.SignalCellularConnectedNoInternet4Bar }
    val SignalWifi: ImageVector by icon { Glyphs.SignalWifi4Bar }
    val SkipNext: ImageVector by icon { Glyphs.SkipNext }
    val SkipPrevious: ImageVector by icon { Glyphs.SkipPrevious }
    val Smartphone: ImageVector by icon { Glyphs.Smartphone }
    val Song: ImageVector by icon { Glyphs.MusicNote }
    val Sort: ImageVector by icon { symbolIcon("Sort", "M360-440q-17 0-28.5-11.5T320-480v-247l-75 75q-11 11-27.5 11T189-652q-12-12-12-28.5t12-28.5l143-143q6-6 13-8.5t15-2.5q8 0 15 2.5t13 8.5l144 144q12 12 11.5 28T531-652q-12 11-28 11.5T475-652l-75-75v247q0 17-11.5 28.5T360-440ZM600-97q-8 0-15-2.5t-13-8.5L428-252q-12-12-11.5-28t12.5-28q12-11 28-11.5t28 11.5l75 75v-247q0-17 11.5-28.5T600-520q17 0 28.5 11.5T640-480v247l75-75q11-11 27.5-11t28.5 11q12 12 12 28.5T771-251L628-108q-6 6-13 8.5T600-97Z") }

    /** [Sort]'s two arrows apart - up, the way an ascending order runs, and down - so the order's own can stand out. */
    val SortUp: ImageVector by icon { symbolIcon("SortUp", "M360-440q-17 0-28.5-11.5T320-480v-247l-75 75q-11 11-27.5 11T189-652q-12-12-12-28.5t12-28.5l143-143q6-6 13-8.5t15-2.5q8 0 15 2.5t13 8.5l144 144q12 12 11.5 28T531-652q-12 11-28 11.5T475-652l-75-75v247q0 17-11.5 28.5T360-440Z") }
    val SortDown: ImageVector by icon { symbolIcon("SortDown", "M600-97q-8 0-15-2.5t-13-8.5L428-252q-12-12-11.5-28t12.5-28q12-11 28-11.5t28 11.5l75 75v-247q0-17 11.5-28.5T600-520q17 0 28.5 11.5T640-480v247l75-75q11-11 27.5-11t28.5 11q12 12 12 28.5T771-251L628-108q-6 6-13 8.5T600-97Z") }
    val Speed: ImageVector by icon { Glyphs.Speed }
    val Storage: ImageVector by icon { Glyphs.Storage }
    val Subject: ImageVector by icon { Glyphs.Subject }
    val Subscriptions: ImageVector by icon { Glyphs.Subscriptions }
    val Subtitles: ImageVector by icon { Glyphs.Subtitles }
    val Sync: ImageVector by icon { Glyphs.Sync }
    val SyncAlt: ImageVector by icon { Glyphs.SyncAlt }
    val SyncDisabled: ImageVector by icon { Glyphs.SyncDisabled }
    val SystemUpdate: ImageVector by icon { Glyphs.SystemUpdate }
    val Tab: ImageVector by icon { Glyphs.Tab }
    val Terminal: ImageVector by icon { Glyphs.Terminal }
    val ThumbUp: ImageVector by icon { Glyphs.ThumbUp }
    val Timer: ImageVector by icon { Glyphs.Timer }
    val Title: ImageVector by icon { Glyphs.Title }
    val ToggleOff: ImageVector by icon { Glyphs.ToggleOff }
    val TouchApp: ImageVector by icon { Glyphs.TouchApp }
    val TrendingFlat: ImageVector by icon { MirroredGlyphs.TrendingFlat }
    val TrendingUp: ImageVector by icon { MirroredGlyphs.TrendingUp }
    val Tune: ImageVector by icon { Glyphs.Tune }
    val Undo: ImageVector by icon { MirroredGlyphs.Undo }
    val Update: ImageVector by icon { Glyphs.Update }
    val Verified: ImageVector by icon { Glyphs.Verified }
    val Video: ImageVector by icon { Glyphs.Movie }
    val VideoFile: ImageVector by icon { Glyphs.VideoFile }
    val VideoLibrary: ImageVector by icon { Glyphs.VideoLibrary }
    val Visibility: ImageVector by icon { Glyphs.Visibility }
    val VisibilityOff: ImageVector by icon { Glyphs.VisibilityOff }
    val Volume: ImageVector by icon { MirroredGlyphs.VolumeUp }
    val VpnKey: ImageVector by icon { Glyphs.VpnKey }
    val Wallpaper: ImageVector by icon { Glyphs.Wallpaper }
    val Warning: ImageVector by icon { Glyphs.Warning }
    val Wifi: ImageVector by icon { Glyphs.Wifi }
    val WifiOff: ImageVector by icon { Glyphs.WifiOff }

    /** What stands in for a missing cover: [DefaultCover]'s glyph, the one every default cover shares. */
    val CoverPlaceholder: ImageVector by icon {
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
    val SelectedBadge: ImageVector by icon {
        drawnIcon(
            name = "SelectedBadge",
            size = 20.dp,
            viewportSize = 20f,
            pathData = "M8.229,14.062 L4.708,10.521 5.75,9.479 8.229,11.938 14.25,5.938 15.292,7Z",
        )
    }
}

/** An icon of [SonaIcons], as [drawn] unless the icon pack has its own - see [SonaIconPack]. */
private fun icon(drawn: () -> ImageVector) = PackIcon(prefix = "icon_", drawn)

/**
 * A Material Symbols Rounded glyph - outlined, weight 400, 24dp - from its path as Google publishes it: in a 960-unit box
 * whose top is at -960, which the group moves down into view.
 */
private fun symbolIcon(name: String, pathData: String): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 960f, viewportHeight = 960f)
        .addGroup(translationY = 960f)
        .addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.White))
        .clearGroup()
        .build()

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
