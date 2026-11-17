package com.lhacenmed.sona.core.designsystem.icon

import androidx.compose.material.icons.Icons
// The style every icon is drawn in - these two imports and the two values below: filled, outlined, rounded or sharp.
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.common.cover.DefaultCover

/** Material's icons in the style the app draws them in - see the imports above. */
private val Glyphs = Icons.Filled

/** The same style, for the icons that point one way and turn around where text runs right to left. */
private val MirroredGlyphs = Icons.AutoMirrored.Filled

/**
 * Every icon the app draws, by what it means - the one place each is chosen, so one meaning is drawn one way everywhere, the
 * players included, and the whole app changes style by changing [Glyphs] and [MirroredGlyphs]. An icon pack replaces any of
 * them - see [SonaIconPack]. Those Android draws itself - the notifications', the widgets' - are the pack's drawables already.
 */
object SonaIcons {
    val Add: ImageVector by icon { Glyphs.Add }
    val AddPhotoAlternate: ImageVector by icon { Glyphs.AddPhotoAlternate }
    val Album: ImageVector by icon { Glyphs.Album }
    val Archive: ImageVector by icon { Glyphs.Archive }
    val ArrowBack: ImageVector by icon { MirroredGlyphs.ArrowBack }
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
    val Code: ImageVector by icon { Glyphs.Code }
    /** Closing the player down to where it was opened from. */
    val Collapse: ImageVector by icon { Glyphs.KeyboardArrowDown }
    val Comment: ImageVector by icon { MirroredGlyphs.Comment }
    val ContentCopy: ImageVector by icon { Glyphs.ContentCopy }
    val ContentCut: ImageVector by icon { Glyphs.ContentCut }
    val ContentPaste: ImageVector by icon { Glyphs.ContentPaste }
    val Cookie: ImageVector by icon { Glyphs.Cookie }
    val CreateNewFolder: ImageVector by icon { Glyphs.CreateNewFolder }
    val Delete: ImageVector by icon { Glyphs.Delete }
    val DeleteForever: ImageVector by icon { Glyphs.DeleteForever }
    val DeleteSweep: ImageVector by icon { Glyphs.DeleteSweep }
    val Description: ImageVector by icon { Glyphs.Description }
    val Deselect: ImageVector by icon { Glyphs.Deselect }
    val Details: ImageVector by icon { Glyphs.Info }
    val DoNotDisturbOn: ImageVector by icon { Glyphs.DoNotDisturbOn }
    val Download: ImageVector by icon { Glyphs.Download }
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
    val Key: ImageVector by icon { Glyphs.Key }
    val KeyboardArrowDown: ImageVector by icon { Glyphs.KeyboardArrowDown }
    val KeyboardArrowRight: ImageVector by icon { MirroredGlyphs.KeyboardArrowRight }
    val KeyboardArrowUp: ImageVector by icon { Glyphs.KeyboardArrowUp }
    val Language: ImageVector by icon { Glyphs.Language }
    val LibraryAdd: ImageVector by icon { Glyphs.LibraryAdd }
    val LibraryMusic: ImageVector by icon { Glyphs.LibraryMusic }
    val Link: ImageVector by icon { Glyphs.Link }
    val LinkOff: ImageVector by icon { Glyphs.LinkOff }
    val Lock: ImageVector by icon { Glyphs.Lock }
    val LockOpen: ImageVector by icon { Glyphs.LockOpen }
    val Login: ImageVector by icon { MirroredGlyphs.Login }
    val Lyrics: ImageVector by icon { Glyphs.Lyrics }
    val MoneyOff: ImageVector by icon { Glyphs.MoneyOff }
    val Monitor: ImageVector by icon { Glyphs.Monitor }
    val MoreTime: ImageVector by icon { Glyphs.MoreTime }
    val MoreVert: ImageVector by icon { Glyphs.MoreVert }
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
    val Search: ImageVector by icon { Glyphs.Search }
    val SearchOff: ImageVector by icon { Glyphs.SearchOff }
    val Security: ImageVector by icon { Glyphs.Security }
    /** Points forward; drawn mirrored for the way back. */
    val SeekForward: ImageVector by icon { Glyphs.PlayArrow }
    val SelectAll: ImageVector by icon { Glyphs.SelectAll }
    val Sell: ImageVector by icon { Glyphs.Sell }
    val Send: ImageVector by icon { MirroredGlyphs.Send }
    val Settings: ImageVector by icon { Glyphs.Settings }
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
    val Sort: ImageVector by icon { MirroredGlyphs.Sort }
    val Speed: ImageVector by icon { Glyphs.Speed }
    val Storage: ImageVector by icon { Glyphs.Storage }
    val Subject: ImageVector by icon { Glyphs.Subject }
    val Subscriptions: ImageVector by icon { Glyphs.Subscriptions }
    val Subtitles: ImageVector by icon { Glyphs.Subtitles }
    val Sync: ImageVector by icon { Glyphs.Sync }
    val SyncAlt: ImageVector by icon { Glyphs.SyncAlt }
    val SyncDisabled: ImageVector by icon { Glyphs.SyncDisabled }
    val SystemUpdate: ImageVector by icon { Glyphs.SystemUpdate }
    val Terminal: ImageVector by icon { Glyphs.Terminal }
    val Timer: ImageVector by icon { Glyphs.Timer }
    val Title: ImageVector by icon { Glyphs.Title }
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
