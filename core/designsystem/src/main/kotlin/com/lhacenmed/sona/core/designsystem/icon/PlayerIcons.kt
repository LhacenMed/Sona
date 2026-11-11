package com.lhacenmed.sona.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The players' own icon set - the music player's and the video player's alike, so both speak one visual language:
 * rounded strokes on a 24 unit grid, drawn once and tinted by whatever shows them. An icon pack replaces any of them - see
 * [SonaIconPack].
 */
object PlayerIcons {
    val Back by playerIcon { strokeIcon("Back", "M15 5l-7 7 7 7") }
    val ChevronDown by playerIcon { strokeIcon("ChevronDown", "M6 9l6 6 6-6") }
    val ChevronRight by playerIcon { strokeIcon("ChevronRight", "M9 6l6 6-6 6", strokeWidth = 2f) }
    val Close by playerIcon { strokeIcon("Close", "M6 6l12 12M18 6L6 18") }
    val Check by playerIcon { strokeIcon("Check", "M5 12.5l4.5 4.5L19 7.5", strokeWidth = 2f) }
    val Add by playerIcon { strokeIcon("Add", "M12 5v14M5 12h14") }
    val Headphones by playerIcon {
        strokeIcon(
            "Headphones",
            "M4 15v-3a8 8 0 0 1 16 0v3",
            "M4 15.5a2 2 0 0 1 2-2h1v6H6a2 2 0 0 1-2-2zM20 15.5a2 2 0 0 0-2-2h-1v6h1a2 2 0 0 0 2-2z",
        )
    }
    val Queue by playerIcon { strokeIcon("Queue", "M4 6.5h16M4 11.5h9M4 16.5h6", "M15.5 13.5v6l5-3z") }
    val Captions by playerIcon {
        strokeIcon(
            "Captions",
            "M6.5 5h11a3.5 3.5 0 0 1 3.5 3.5v7a3.5 3.5 0 0 1-3.5 3.5h-11A3.5 3.5 0 0 1 3 15.5v-7A3.5 3.5 0 0 1 6.5 5z",
            "M7 11.5h3.5M13 11.5h4M7 15h6",
        )
    }
    val More by playerIcon {
        filledIcon(
            "More",
            "M3.9 12a1.6 1.6 0 1 1 3.2 0a1.6 1.6 0 1 1-3.2 0zM10.4 12a1.6 1.6 0 1 1 3.2 0a1.6 1.6 0 1 1-3.2 0zM16.9 12a1.6 1.6 0 1 1 3.2 0a1.6 1.6 0 1 1-3.2 0z",
        )
    }
    val SkipPrevious by playerIcon { skipIcon("SkipPrevious", "M6.5 5.5v13", "M18.5 6.3v11.4L9.6 12z") }
    val SkipNext by playerIcon { skipIcon("SkipNext", "M17.5 5.5v13", "M5.5 6.3v11.4l8.9-5.7z") }
    val Pause by playerIcon {
        filledIcon(
            "Pause",
            "M7.7 5h1.2a1.2 1.2 0 0 1 1.2 1.2v11.6a1.2 1.2 0 0 1-1.2 1.2H7.7a1.2 1.2 0 0 1-1.2-1.2V6.2A1.2 1.2 0 0 1 7.7 5zM15.1 5h1.2a1.2 1.2 0 0 1 1.2 1.2v11.6a1.2 1.2 0 0 1-1.2 1.2h-1.2a1.2 1.2 0 0 1-1.2-1.2V6.2A1.2 1.2 0 0 1 15.1 5z",
        )
    }
    val Play by playerIcon { filledIcon("Play", "M7.7 5.6v12.8l10.4-6.4z", strokeWidth = 1.5f) }
    val Replay by playerIcon { strokeIcon("Replay", "M4.5 12a7.5 7.5 0 1 0 2.2-5.3", "M4.5 4.5v4h4", strokeWidth = 2f) }
    val Shuffle by playerIcon {
        strokeIcon(
            "Shuffle",
            "M4 7h3c2 0 3.2 1 4.3 2.7l1.4 2.6C13.8 14 15 17 18 17h2",
            "M4 17h3c1.3 0 2.2-.5 3-1.3",
            "M14.3 8.3C15.2 7.5 16.3 7 18 7h2",
            "M17.5 4.5L20 7l-2.5 2.5",
            "M17.5 14.5L20 17l-2.5 2.5",
        )
    }
    val Repeat by playerIcon { strokeIcon("Repeat", *RepeatLoop) }
    val RepeatOne by playerIcon { strokeIcon("RepeatOne", *RepeatLoop, "M11 10.5l1.5-1v5") }

    /** Repeat that stops once the track playing ends - the loop around a stop. */
    val RepeatStop by playerIcon {
        ImageVector.Builder("RepeatStop", 24.dp, 24.dp, 24f, 24f)
            .apply { RepeatLoop.forEach { strokePath(it, 1.75f) } }
            .fillPath("M10.6 10h2.8a.6.6 0 0 1 .6.6v2.8a.6.6 0 0 1-.6.6h-2.8a.6.6 0 0 1-.6-.6v-2.8a.6.6 0 0 1 .6-.6z", 0f)
            .build()
    }
    val Lyrics by playerIcon {
        strokeIcon(
            "Lyrics",
            "M6 4.5h12A2.5 2.5 0 0 1 20.5 7v8a2.5 2.5 0 0 1-2.5 2.5h-6l-4.5 3v-3H6A2.5 2.5 0 0 1 3.5 15V7A2.5 2.5 0 0 1 6 4.5z",
            "M8 9.5h8M8 13h5",
        )
    }
    val SleepTimer by playerIcon { strokeIcon("SleepTimer", "M19.5 14.5A8 8 0 0 1 9.5 4.5a8 8 0 1 0 10 10z") }
    val Equalizer by playerIcon { strokeIcon("Equalizer", "M6 4v5M6 13v7M12 4v9M12 17v3M18 4v3M18 11v9", "M4 11h4M10 15h4M16 9h4") }
    val Favorite by playerIcon {
        ImageVector.Builder("Favorite", 24.dp, 24.dp, 24f, 24f).fillPath(HeartPath, 1.75f).build()
    }
    val FavoriteBorder by playerIcon { strokeIcon("FavoriteBorder", HeartPath) }
    val LockOpen by playerIcon {
        strokeIcon(
            "LockOpen",
            "M7.5 10.5h9a2.5 2.5 0 0 1 2.5 2.5v4.5a2.5 2.5 0 0 1-2.5 2.5h-9A2.5 2.5 0 0 1 5 17.5V13a2.5 2.5 0 0 1 2.5-2.5z",
            "M8.5 10.5V8a3.5 3.5 0 0 1 6.6-1.6",
        )
    }
    val Locked by playerIcon {
        strokeIcon(
            "Locked",
            "M7.5 10.5h9a2.5 2.5 0 0 1 2.5 2.5v4.5a2.5 2.5 0 0 1-2.5 2.5h-9A2.5 2.5 0 0 1 5 17.5V13a2.5 2.5 0 0 1 2.5-2.5z",
            "M8.5 10.5V8a3.5 3.5 0 0 1 7 0v2.5",
        )
    }
    val Fit by playerIcon {
        strokeIcon("Fit", "M4 9V6.5A2.5 2.5 0 0 1 6.5 4H9M15 4h2.5A2.5 2.5 0 0 1 20 6.5V9M20 15v2.5a2.5 2.5 0 0 1-2.5 2.5H15M9 20H6.5A2.5 2.5 0 0 1 4 17.5V15")
    }
    val PictureInPicture by playerIcon {
        ImageVector.Builder("PictureInPicture", 24.dp, 24.dp, 24f, 24f)
            .strokePath("M6 5h12a3 3 0 0 1 3 3v8a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3z", 1.75f)
            .fillPath("M13.7 11.5h3.1a1.2 1.2 0 0 1 1.2 1.2v2.1a1.2 1.2 0 0 1-1.2 1.2h-3.1a1.2 1.2 0 0 1-1.2-1.2v-2.1a1.2 1.2 0 0 1 1.2-1.2z", 0f)
            .build()
    }
    /** Turning with the device - and, held one way, [OrientationPortrait] and [OrientationLandscape]: the way it stands. */
    val AutoRotate by playerIcon {
        strokeIcon(
            "AutoRotate",
            "M5.5 10h9a2.5 2.5 0 0 1 2.5 2.5v5a2.5 2.5 0 0 1-2.5 2.5h-9A2.5 2.5 0 0 1 3 17.5v-5A2.5 2.5 0 0 1 5.5 10z",
            "M12 3.5a7 7 0 0 1 7.5 6",
            "M21 7l-1.5 2.5L17 8",
        )
    }
    val OrientationPortrait by playerIcon {
        strokeIcon(
            "OrientationPortrait",
            "M8.5 3h7A2.5 2.5 0 0 1 18 5.5v13a2.5 2.5 0 0 1-2.5 2.5h-7A2.5 2.5 0 0 1 6 18.5v-13A2.5 2.5 0 0 1 8.5 3z",
            "M10.5 17.5h3",
        )
    }
    val OrientationLandscape by playerIcon {
        strokeIcon(
            "OrientationLandscape",
            "M3 8.5A2.5 2.5 0 0 1 5.5 6h13A2.5 2.5 0 0 1 21 8.5v7a2.5 2.5 0 0 1-2.5 2.5h-13A2.5 2.5 0 0 1 3 15.5z",
            "M17.5 10.5v3",
        )
    }
    val Volume by playerIcon { strokeIcon("Volume", "M4 9.5h3l4.5-4v13L7 14.5H4z", "M15.5 9a4 4 0 0 1 0 6", "M18 6.5a7.5 7.5 0 0 1 0 11") }
    val Muted by playerIcon { strokeIcon("Muted", "M4 9.5h3l4.5-4v13L7 14.5H4z", "M16 9.5l5 5M21 9.5l-5 5") }
    val Brightness by playerIcon {
        strokeIcon(
            "Brightness",
            "M12 8.5a3.5 3.5 0 1 1 0 7a3.5 3.5 0 1 1 0-7z",
            "M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6L7 7M17 17l1.4 1.4M5.6 18.4L7 17M17 7l1.4-1.4",
        )
    }

    /** Points back; drawn mirrored for the way forward. */
    val SeekChevron by playerIcon { filledIcon("SeekChevron", "M17 6v12L8 12z") }
}

private val RepeatLoop = arrayOf("M17 3.5l3 3-3 3", "M4 11.5v-1a4 4 0 0 1 4-4h12", "M7 20.5l-3-3 3-3", "M20 12.5v1a4 4 0 0 1-4 4H4")

private const val HeartPath = "M12 20s-7.5-4.6-7.5-10.2A4.3 4.3 0 0 1 12 7.2a4.3 4.3 0 0 1 7.5 2.6C19.5 15.4 12 20 12 20z"

/** An icon of [PlayerIcons], as [drawn] unless the icon pack has its own - see [SonaIconPack]. */
private fun playerIcon(drawn: () -> ImageVector) = PackIcon(prefix = "icon_player_", drawn)

private fun skipIcon(name: String, bar: String, triangle: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).strokePath(bar, 2f).fillPath(triangle, 1.5f).build()

private fun strokeIcon(name: String, vararg paths: String, strokeWidth: Float = 1.75f): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .apply { paths.forEach { strokePath(it, strokeWidth) } }
        .build()

private fun filledIcon(name: String, path: String, strokeWidth: Float = 0f): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).fillPath(path, strokeWidth).build()

private fun ImageVector.Builder.strokePath(path: String, strokeWidth: Float): ImageVector.Builder =
    addPath(
        pathData = addPathNodes(path),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = strokeWidth,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )

private fun ImageVector.Builder.fillPath(path: String, strokeWidth: Float): ImageVector.Builder =
    addPath(
        pathData = addPathNodes(path),
        fill = SolidColor(Color.Black),
        stroke = if (strokeWidth > 0f) SolidColor(Color.Black) else null,
        strokeLineWidth = strokeWidth,
        strokeLineJoin = StrokeJoin.Round,
    )
