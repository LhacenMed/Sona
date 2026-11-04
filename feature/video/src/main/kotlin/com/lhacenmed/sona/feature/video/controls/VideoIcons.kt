package com.lhacenmed.sona.feature.video.controls

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** The player's own icon set: rounded strokes on a 24 unit grid, drawn once and tinted by whatever shows them. */
internal object VideoIcons {
    val Back by lazy { strokeIcon("Back", "M15 5l-7 7 7 7") }
    val Close by lazy { strokeIcon("Close", "M6 6l12 12M18 6L6 18") }
    val Check by lazy { strokeIcon("Check", "M5 12.5l4.5 4.5L19 7.5", strokeWidth = 2f) }
    val Add by lazy { strokeIcon("Add", "M12 5v14M5 12h14") }
    val Headphones by lazy {
        strokeIcon(
            "Headphones",
            "M4 15v-3a8 8 0 0 1 16 0v3",
            "M4 15.5a2 2 0 0 1 2-2h1v6H6a2 2 0 0 1-2-2zM20 15.5a2 2 0 0 0-2-2h-1v6h1a2 2 0 0 0 2-2z",
        )
    }
    val Queue by lazy { strokeIcon("Queue", "M4 6.5h16M4 11.5h9M4 16.5h6", "M15.5 13.5v6l5-3z") }
    val Captions by lazy {
        strokeIcon(
            "Captions",
            "M6.5 5h11a3.5 3.5 0 0 1 3.5 3.5v7a3.5 3.5 0 0 1-3.5 3.5h-11A3.5 3.5 0 0 1 3 15.5v-7A3.5 3.5 0 0 1 6.5 5z",
            "M7 11.5h3.5M13 11.5h4M7 15h6",
        )
    }
    val More by lazy {
        filledIcon(
            "More",
            "M12 3.9a1.6 1.6 0 1 1 0 3.2a1.6 1.6 0 1 1 0-3.2zM12 10.4a1.6 1.6 0 1 1 0 3.2a1.6 1.6 0 1 1 0-3.2zM12 16.9a1.6 1.6 0 1 1 0 3.2a1.6 1.6 0 1 1 0-3.2z",
        )
    }
    val SkipBack by lazy { strokeIcon("SkipBack", "M4.5 12a7.5 7.5 0 1 0 2.2-5.3", "M4.5 4.5v3.6h3.6", strokeWidth = 1.6f) }
    val SkipForward by lazy { strokeIcon("SkipForward", "M19.5 12a7.5 7.5 0 1 1-2.2-5.3", "M19.5 4.5v3.6h-3.6", strokeWidth = 1.6f) }
    val Pause by lazy {
        filledIcon(
            "Pause",
            "M7.7 5h1.2a1.2 1.2 0 0 1 1.2 1.2v11.6a1.2 1.2 0 0 1-1.2 1.2H7.7a1.2 1.2 0 0 1-1.2-1.2V6.2A1.2 1.2 0 0 1 7.7 5zM15.1 5h1.2a1.2 1.2 0 0 1 1.2 1.2v11.6a1.2 1.2 0 0 1-1.2 1.2h-1.2a1.2 1.2 0 0 1-1.2-1.2V6.2A1.2 1.2 0 0 1 15.1 5z",
        )
    }
    val Play by lazy { filledIcon("Play", "M8 5.6v12.8l10.4-6.4z", strokeWidth = 1.5f) }
    val LockOpen by lazy {
        strokeIcon(
            "LockOpen",
            "M7.5 10.5h9a2.5 2.5 0 0 1 2.5 2.5v4.5a2.5 2.5 0 0 1-2.5 2.5h-9A2.5 2.5 0 0 1 5 17.5V13a2.5 2.5 0 0 1 2.5-2.5z",
            "M8.5 10.5V8a3.5 3.5 0 0 1 6.6-1.6",
        )
    }
    val Locked by lazy {
        strokeIcon(
            "Locked",
            "M7.5 10.5h9a2.5 2.5 0 0 1 2.5 2.5v4.5a2.5 2.5 0 0 1-2.5 2.5h-9A2.5 2.5 0 0 1 5 17.5V13a2.5 2.5 0 0 1 2.5-2.5z",
            "M8.5 10.5V8a3.5 3.5 0 0 1 7 0v2.5",
        )
    }
    val Fit by lazy {
        strokeIcon("Fit", "M4 9V6.5A2.5 2.5 0 0 1 6.5 4H9M15 4h2.5A2.5 2.5 0 0 1 20 6.5V9M20 15v2.5a2.5 2.5 0 0 1-2.5 2.5H15M9 20H6.5A2.5 2.5 0 0 1 4 17.5V15")
    }
    val PictureInPicture by lazy {
        ImageVector.Builder("PictureInPicture", 24.dp, 24.dp, 24f, 24f)
            .strokePath("M6 5h12a3 3 0 0 1 3 3v8a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3z", 1.75f)
            .fillPath("M13.7 11.5h3.1a1.2 1.2 0 0 1 1.2 1.2v2.1a1.2 1.2 0 0 1-1.2 1.2h-3.1a1.2 1.2 0 0 1-1.2-1.2v-2.1a1.2 1.2 0 0 1 1.2-1.2z", 0f)
            .build()
    }
    val Rotate by lazy {
        strokeIcon(
            "Rotate",
            "M5.5 10h9a2.5 2.5 0 0 1 2.5 2.5v5a2.5 2.5 0 0 1-2.5 2.5h-9A2.5 2.5 0 0 1 3 17.5v-5A2.5 2.5 0 0 1 5.5 10z",
            "M12 3.5a7 7 0 0 1 7.5 6",
            "M21 7l-1.5 2.5L17 8",
        )
    }
    val Volume by lazy { strokeIcon("Volume", "M4 9.5h3l4.5-4v13L7 14.5H4z", "M15.5 9a4 4 0 0 1 0 6", "M18 6.5a7.5 7.5 0 0 1 0 11") }
    val Muted by lazy { strokeIcon("Muted", "M4 9.5h3l4.5-4v13L7 14.5H4z", "M16 9.5l5 5M21 9.5l-5 5") }
    val Brightness by lazy {
        strokeIcon(
            "Brightness",
            "M12 8.5a3.5 3.5 0 1 1 0 7a3.5 3.5 0 1 1 0-7z",
            "M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6L7 7M17 17l1.4 1.4M5.6 18.4L7 17M17 7l1.4-1.4",
        )
    }

    /** Points back; drawn mirrored for the way forward. */
    val SeekChevron by lazy { filledIcon("SeekChevron", "M17 6v12L8 12z") }
}

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
