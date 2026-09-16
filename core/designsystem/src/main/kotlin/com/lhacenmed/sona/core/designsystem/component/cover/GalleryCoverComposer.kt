package com.lhacenmed.sona.core.designsystem.component.cover

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Auxio's `GalleryComposeFetcher`: four covers, one to a quarter, overlapping where they meet, as if
 * framed on a wall. Only the corners that meet another cover are rounded, and each cover is cut by a gap
 * around the covers drawn over it. Used for genres.
 */
internal fun composeGallery(
    bitmaps: List<Bitmap>,
    size: Int,
    random: Random,
    cornerRadiusRatio: Float,
): Bitmap {
    val sizef = size.toFloat()
    val cornerRadiusPx = min(sizef * cornerRadiusRatio, sizef * ComposeCoverDefaults.MAX_CORNER_RATIO)
    val gapWidthPx = max(
        sizef * ComposeCoverDefaults.GAP_RATIO,
        cornerRadiusPx * ComposeCoverDefaults.MIN_GAP_CORNER_RATIO,
    )
    val zOrder = seededZOrder(bitmaps.size, random)
    val result = createBitmap(size, size)
    val canvas = Canvas(result)
    val gapPaint = gapPaint()
    val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    val coverSize = sizef * ComposeCoverDefaults.COVER_SIZE_PERCENT
    val positions = listOf(
        RectF(0f, 0f, coverSize, coverSize),
        RectF(sizef - coverSize, 0f, sizef, coverSize),
        RectF(0f, sizef - coverSize, coverSize, sizef),
        RectF(sizef - coverSize, sizef - coverSize, sizef, sizef),
    )
    // Every tile's geometry comes first, so each can be cut by the tiles drawn over it.
    val tiles = positions.mapIndexed { index, baseRect ->
        val isTop = index < 2
        val isLeft = index % 2 == 0
        val isBottom = !isTop
        val isRight = !isLeft

        // Inset where the tile meets another, so rounded corners overlap rather than leave holes.
        val innerRect = RectF(baseRect)
        innerRect.left += if (isLeft) 0f else gapWidthPx
        innerRect.top += if (isTop) 0f else gapWidthPx
        innerRect.right -= if (isRight) 0f else gapWidthPx
        innerRect.bottom -= if (isBottom) 0f else gapWidthPx

        val gapRect = RectF(innerRect).apply { inset(-gapWidthPx, -gapWidthPx) }
        val gapPath = roundedPath(
            gapRect,
            topLeft = if (!isTop && !isLeft) cornerRadiusPx else 0f,
            topRight = if (!isTop && !isRight) cornerRadiusPx else 0f,
            bottomRight = if (!isBottom && !isRight) cornerRadiusPx else 0f,
            bottomLeft = if (!isBottom && !isLeft) cornerRadiusPx else 0f,
        )

        // The cover's corners shrink by the gap, so they stay concentric with it.
        val innerRadius = (cornerRadiusPx - gapWidthPx).coerceAtLeast(0f)
        val maskPath = roundedPath(
            innerRect,
            topLeft = if (!isTop && !isLeft) innerRadius else 0f,
            topRight = if (!isTop && !isRight) innerRadius else 0f,
            bottomRight = if (!isBottom && !isRight) innerRadius else 0f,
            bottomLeft = if (!isBottom && !isLeft) innerRadius else 0f,
        )

        GalleryTile(innerRect, gapPath, maskPath)
    }

    for ((orderIndex, imageIndex) in zOrder.withIndex()) {
        val tile = tiles[imageIndex]
        // Only what the tiles drawn later leave uncovered is drawn at all.
        val visiblePath = Path(tile.gapPath)
        for (index in orderIndex + 1 until zOrder.size) {
            visiblePath.op(tiles[zOrder[index]].gapPath, Path.Op.DIFFERENCE)
            if (visiblePath.isEmpty) break
        }
        if (visiblePath.isEmpty) continue

        canvas.drawPath(visiblePath, gapPaint)
        canvas.withClip(visiblePath) {
            val savedLayer = saveLayer(tile.innerRect, null)
            drawPath(tile.maskPath, imagePaint)
            imagePaint.xfermode = SourceInXfermode
            drawBitmapCover(this, bitmaps[imageIndex], tile.innerRect, imagePaint)
            imagePaint.xfermode = null
            restoreToCount(savedLayer)
        }
    }

    return result
}

private class GalleryTile(val innerRect: RectF, val gapPath: Path, val maskPath: Path)

private fun roundedPath(
    rect: RectF,
    topLeft: Float,
    topRight: Float,
    bottomRight: Float,
    bottomLeft: Float,
) = Path().apply {
    addRoundRect(
        rect,
        floatArrayOf(topLeft, topLeft, topRight, topRight, bottomRight, bottomRight, bottomLeft, bottomLeft),
        Path.Direction.CW,
    )
}
