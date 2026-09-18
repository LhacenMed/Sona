package com.lhacenmed.sona.core.designsystem.component.cover

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Auxio's `StackCompositionFetcher`: four covers stacked diagonally towards the upper left, each with
 * its lower-left corner rounded and a gap cut around it, giving the orderly feeling of a neat pile.
 * Used for playlists and folders.
 */
internal fun composeStack(
    bitmaps: List<Bitmap>,
    size: Int,
    random: Random,
    cornerRadiusRatio: Float,
): Bitmap {
    val sizef = size.toFloat()
    val cornerRadius = min(sizef * cornerRadiusRatio, sizef * ComposeCoverDefaults.MAX_CORNER_RATIO)
    val gapWidthPx = max(
        sizef * ComposeCoverDefaults.GAP_RATIO,
        cornerRadius * ComposeCoverDefaults.MIN_GAP_CORNER_RATIO,
    )
    val zOrder = seededZOrder(bitmaps.size, random)
    val result = createBitmap(size, size)
    val canvas = Canvas(result)
    val gapPaint = gapPaint()
    val coverPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // How much each cover is overlapped by the one above it, and so how much of it stays visible.
    val overlapSize = (sizef * ComposeCoverDefaults.COVER_SIZE_PERCENT).coerceAtMost(sizef)
    val visibleSize = sizef - overlapSize
    // Auxio's: half the visible fraction, which is what fits all four covers into the square.
    val maxStep = visibleSize / 2
    // The cover's corners shrink by the gap, so they stay concentric with it.
    val innerRadius = (cornerRadius - gapWidthPx).coerceAtLeast(0f)

    for (stackIndex in bitmaps.indices) {
        val bitmap = bitmaps[zOrder[stackIndex]]
        // The pile moves diagonally: as far down as it moves across.
        val offsetX = stackIndex * maxStep
        val offsetY = visibleSize - offsetX
        val baseRect = RectF(offsetX, offsetY - visibleSize, offsetX + sizef, offsetY - visibleSize + sizef)
        val coverRect = RectF(baseRect)

        val hasGap = stackIndex > 0 && gapWidthPx > 0f
        if (hasGap) {
            canvas.drawPath(teardropPath(baseRect, cornerRadius), gapPaint)
            coverRect.left += gapWidthPx
            coverRect.bottom -= gapWidthPx
        }

        val savedLayer = canvas.saveLayer(coverRect, null)
        canvas.drawPath(teardropPath(coverRect, if (hasGap) innerRadius else 0f), coverPaint)
        coverPaint.xfermode = SourceInXfermode
        drawBitmapCover(canvas, bitmap, coverRect, coverPaint)
        coverPaint.xfermode = null
        canvas.restoreToCount(savedLayer)
    }

    return result
}

/** Square but for the lower-left corner, the one corner of a stacked cover that shows. */
private fun teardropPath(rect: RectF, radius: Float): Path = Path().apply {
    addRoundRect(rect, floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, radius, radius), Path.Direction.CW)
}
