package com.lhacenmed.sona.core.designsystem.component.cover

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withRotation
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** How far past its quarter each cover reaches, as a share of the composition's side. */
private const val OUTSET_PERCENT = 0.08f

/**
 * Auxio's `SmatteringCompositionFetcher`: four covers, one to a quarter and reaching past it, fanned
 * apart and tilted together at a seeded angle, like a messy pile of records. Each is rounded and cut by a
 * gap around it. Used for artists.
 */
internal fun composeSmattering(
    bitmaps: List<Bitmap>,
    size: Int,
    random: Random,
    cornerRadiusRatio: Float,
): Bitmap {
    val sizef = size.toFloat()
    val cornerRadius = min(sizef * cornerRadiusRatio, sizef * ComposeCoverDefaults.MAX_CORNER_RATIO)
    val gapWidth = max(
        sizef * ComposeCoverDefaults.GAP_RATIO,
        cornerRadius * ComposeCoverDefaults.MIN_GAP_CORNER_RATIO,
    )
    val innerRadius = (cornerRadius - gapWidth).coerceAtLeast(0f)
    // 5 to 15 degrees apart, tilted together by -10 to +10.
    val fanAngle = random.nextFloat() * 10f + 5f
    val tiltAngle = random.nextFloat() * 20f - 10f
    val zOrder = seededZOrder(bitmaps.size, random)
    val result = createBitmap(size, size)
    val canvas = Canvas(result)
    val gapPaint = gapPaint()
    val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    val coverSize = sizef * ComposeCoverDefaults.COVER_SIZE_PERCENT
    val outsetSize = sizef * OUTSET_PERCENT
    val positions = listOf(
        RectF(0f, 0f, coverSize, coverSize),
        RectF(sizef - coverSize, 0f, sizef, coverSize),
        RectF(0f, sizef - coverSize, coverSize, sizef),
        RectF(sizef - coverSize, sizef - coverSize, sizef, sizef),
    )

    for (imageIndex in zOrder) {
        val innerRect = RectF(positions[imageIndex]).apply { inset(-outsetSize, -outsetSize) }
        val gapRect = RectF(innerRect).apply { inset(-gapWidth, -gapWidth) }
        val gapPath = roundedPath(gapRect, cornerRadius)
        // Opposite corners lean the same way, so the four fan out around the centre.
        val rotation = if (imageIndex == 0 || imageIndex == 3) tiltAngle - fanAngle else tiltAngle + fanAngle

        canvas.withRotation(rotation, innerRect.centerX(), innerRect.centerY()) {
            drawPath(gapPath, gapPaint)
            val savedLayer = saveLayer(innerRect, null)
            drawPath(roundedPath(innerRect, innerRadius), imagePaint)
            imagePaint.xfermode = SourceInXfermode
            drawBitmapCover(this, bitmaps[imageIndex], innerRect, imagePaint)
            imagePaint.xfermode = null
            restoreToCount(savedLayer)
        }
    }

    return result
}

private fun roundedPath(rect: RectF, radius: Float): Path =
    Path().apply { addRoundRect(rect, FloatArray(8) { radius }, Path.Direction.CW) }
