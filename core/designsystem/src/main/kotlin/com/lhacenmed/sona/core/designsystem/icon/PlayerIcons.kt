package com.lhacenmed.sona.core.designsystem.icon

/**
 * The players' own icon set - the music player's and the video player's alike, so both speak one visual language: rounded
 * strokes on a 24 unit grid, tinted by whatever shows them.
 *
 * Each is a vector drawable of its own, `icon_player_<name>.xml` in core/common's `res/drawable`, and the one drawing of its
 * meaning everywhere: in the players here, and by its resource in the notification, the widgets and the floating window,
 * which draw no ImageVector. Redrawing one is replacing its file - see `docs/ICONS.md`.
 */
object PlayerIcons {
    val Back by playerIcon()
    val ChevronDown by playerIcon()
    val ChevronRight by playerIcon()
    val Close by playerIcon()
    val Check by playerIcon()
    val Add by playerIcon()
    val Headphones by playerIcon()
    val Queue by playerIcon()
    val Captions by playerIcon()
    val More by playerIcon()
    val SkipPrevious by playerIcon()
    val SkipNext by playerIcon()
    val Pause by playerIcon()
    val Play by playerIcon()
    val Replay by playerIcon()
    val Shuffle by playerIcon()
    val Repeat by playerIcon()
    val RepeatOne by playerIcon()

    /** Repeat that stops once the track playing ends - the loop around a stop. */
    val RepeatStop by playerIcon()
    val Lyrics by playerIcon()
    val SleepTimer by playerIcon()
    val Equalizer by playerIcon()
    val Favorite by playerIcon()
    val FavoriteBorder by playerIcon()
    val LockOpen by playerIcon()
    val Locked by playerIcon()
    val Fit by playerIcon()
    val PictureInPicture by playerIcon()

    /** Turning with the device - and, held one way, [OrientationPortrait] and [OrientationLandscape]: the way it stands. */
    val AutoRotate by playerIcon()
    val OrientationPortrait by playerIcon()
    val OrientationLandscape by playerIcon()
    val Volume by playerIcon()
    val Muted by playerIcon()
    val Brightness by playerIcon()

    /** Points back; drawn mirrored for the way forward. */
    val SeekChevron by playerIcon()
    val Share by playerIcon()
    val Speed by playerIcon()
    val Screenshot by playerIcon()

    // Turned on, where a toggle cannot be dimmed when off - the notification: the glyph with a dot beneath. Drawn by their
    // resources alone, as the players dim a toggle instead.
}

/** An icon of [PlayerIcons]: the pack's drawable of its name - `icon_player_` and its name in snake case - see [SonaIconPack]. */
private fun playerIcon() = PackIcon(prefix = "icon_player_") { error("The player's icons are vector drawables: icon_player_*.xml is missing") }
