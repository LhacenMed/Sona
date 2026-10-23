package com.lhacenmed.sona.core.common.cover

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import androidx.core.graphics.PathParser

/**
 * [DefaultCover] drawn [sizePx] square for what has no theme to draw it in - the playback notification and
 * the home screen widget - its ground and glyph the system's closest to the app's own, in light or, where
 * [isNight], dark.
 */
fun drawDefaultCover(context: Context, isNight: Boolean, sizePx: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val glyph = PathParser.createPathFromPathData(DefaultCover.GLYPH_PATH_DATA)
    val glyphSizePx = sizePx * DefaultCover.GLYPH_SIZE_FRACTION
    val glyphInsetPx = (sizePx - glyphSizePx) / 2f
    val glyphScale = glyphSizePx / DefaultCover.GLYPH_VIEWPORT_SIZE
    Canvas(bitmap).apply {
        drawColor(groundColor(context, isNight))
        translate(glyphInsetPx, glyphInsetPx)
        scale(glyphScale, glyphScale)
        drawPath(glyph, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = glyphColor(context, isNight) })
    }
    return bitmap
}

/**
 * The app's `surfaceContainer`. On Android 12 and later the app's default theme is the system's own
 * colours, so they are taken from the system - exactly on Android 14, by the nearest tone before it.
 * Older systems have none to offer, so a neutral grey of the same tone stands in.
 */
private fun groundColor(context: Context, isNight: Boolean): Int = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> context.getColor(
        if (isNight) android.R.color.system_surface_container_dark else android.R.color.system_surface_container_light,
    )
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> context.getColor(
        if (isNight) android.R.color.system_neutral1_900 else android.R.color.system_neutral1_50,
    )
    else -> if (isNight) 0xFF201F1F.toInt() else 0xFFEFEDEC.toInt()
}

/** The app's `onSurface`, taken the same way as [groundColor]. */
private fun glyphColor(context: Context, isNight: Boolean): Int = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> context.getColor(
        if (isNight) android.R.color.system_on_surface_dark else android.R.color.system_on_surface_light,
    )
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> context.getColor(
        if (isNight) android.R.color.system_neutral1_100 else android.R.color.system_neutral1_900,
    )
    else -> if (isNight) 0xFFE6E1E0.toInt() else 0xFF1C1B1B.toInt()
}
