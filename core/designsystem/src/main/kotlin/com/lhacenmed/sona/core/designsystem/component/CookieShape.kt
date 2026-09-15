package com.lhacenmed.sona.core.designsystem.component

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PointF
import android.graphics.RectF
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/**
 * Auxio's six-sided cookie, built exactly as its `CookieShapeDrawable` builds it: its own corner
 * rounding of Material's cookie points (`ExpressiveShapes.getCookie6Sided`), laid in a centred square
 * and scaled so its farthest point from the centre just touches the square's inscribed circle.
 *
 * Material's own `MaterialShapes.Cookie6Sided` starts from the same points but rounds them differently
 * and fits its bounding box rather than that circle, so it is a subtly different shape.
 */
val CookieShape: Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(cookiePath(size.width, size.height).asComposePath())
}

/** The six-sided cookie's points and corner radii, as Material's source gives them. */
private val CookiePoints = listOf(CookiePoint(0.723f, 0.884f, 0.394f), CookiePoint(0.500f, 1.099f, 0.398f))

private const val COOKIE_REPETITIONS = 6

private data class CookiePoint(val x: Float, val y: Float, val radius: Float)

private data class CornerCut(val start: PointF, val end: PointF)

/** `CookieShapeDrawable.rebuildPath`: the cookie in a square centred in [width] by [height], fitted to its inscribed circle. */
private fun cookiePath(width: Float, height: Float): Path {
    val side = min(width, height)
    if (side <= 0f) return Path()
    val left = width / 2f - side / 2f
    val top = height / 2f - side / 2f
    val square = RectF(left, top, left + side, top + side)

    val path = polygonPath(square)
    val maxRadius = maxRadius(path, square.centerX(), square.centerY())
    if (maxRadius > 0f) {
        val scale = (side / 2f) / maxRadius
        path.transform(Matrix().apply { setScale(scale, scale, square.centerX(), square.centerY()) })
    }
    return path
}

/** `ExpressiveShapes.generatePolygonPath`: the points repeated around the centre, rounded, then fitted to [bounds]. */
private fun polygonPath(bounds: RectF): Path {
    val vertices = List(CookiePoints.size * COOKIE_REPETITIONS) { index ->
        val point = CookiePoints[index % CookiePoints.size]
        val rotationDegrees = (index / CookiePoints.size) * 360f / COOKIE_REPETITIONS
        rotated(point, rotationDegrees)
    }
    val path = roundedPath(vertices)
    val pathBounds = RectF()
    path.computeBounds(pathBounds, true)
    path.transform(Matrix().apply { setRectToRect(pathBounds, bounds, Matrix.ScaleToFit.CENTER) })
    return path
}

private fun rotated(point: CookiePoint, degrees: Float): CookiePoint {
    val radians = Math.toRadians(degrees.toDouble())
    val dx = point.x - 0.5f
    val dy = point.y - 0.5f
    return CookiePoint(
        x = (dx * cos(radians) - dy * sin(radians) + 0.5f).toFloat(),
        y = (dx * sin(radians) + dy * cos(radians) + 0.5f).toFloat(),
        radius = point.radius,
    )
}

/** `ExpressiveShapes.createRoundedPath`: each corner cut back and bridged with a quadratic through the vertex. */
private fun roundedPath(vertices: List<CookiePoint>): Path {
    val count = vertices.size
    val cuts = List(count) { i ->
        cornerCut(vertices[(i - 1 + count) % count], vertices[i], vertices[(i + 1) % count])
    }
    return Path().apply {
        moveTo(cuts[0].start.x, cuts[0].start.y)
        for (i in 0 until count) {
            val vertex = vertices[i]
            val cut = cuts[i]
            val nextCut = cuts[(i + 1) % count]
            quadTo(vertex.x, vertex.y, cut.end.x, cut.end.y)
            lineTo(nextCut.start.x, nextCut.start.y)
        }
        close()
    }
}

/** `ExpressiveShapes.calculateCorner`: how far along each edge the corner at [current] is cut back. */
private fun cornerCut(previous: CookiePoint, current: CookiePoint, next: CookiePoint): CornerCut {
    val toPreviousX = previous.x - current.x
    val toPreviousY = previous.y - current.y
    val toPreviousLength = hypot(toPreviousX, toPreviousY)
    val toNextX = next.x - current.x
    val toNextY = next.y - current.y
    val toNextLength = hypot(toNextX, toNextY)

    val dot = toPreviousX * toNextX + toPreviousY * toNextY
    val angle = acos((dot / (toPreviousLength * toNextLength)).coerceIn(-1f, 1f))
    val tanHalf = tan(angle / 2.0)
    val cutDistance = if (tanHalf != 0.0) (current.radius / tanHalf).toFloat() else 0f
    val distance = min(cutDistance, min(toPreviousLength, toNextLength) / 2f)

    return CornerCut(
        start = PointF(current.x + toPreviousX / toPreviousLength * distance, current.y + toPreviousY / toPreviousLength * distance),
        end = PointF(current.x + toNextX / toNextLength * distance, current.y + toNextY / toNextLength * distance),
    )
}

/** `CookieShapeDrawable.computeMaxRadius`: the path's farthest distance from the centre, sampled every pixel of its length. */
private fun maxRadius(path: Path, centerX: Float, centerY: Float): Float {
    val measure = PathMeasure(path, false)
    val position = FloatArray(2)
    var maxRadius = 0f
    do {
        val length = measure.length
        if (length <= 0f) continue
        val sampleCount = max(length.roundToInt(), 1)
        for (sample in 0..sampleCount) {
            if (measure.getPosTan(length * sample / sampleCount, position, null)) {
                maxRadius = max(maxRadius, hypot(position[0] - centerX, position[1] - centerY))
            }
        }
    } while (measure.nextContour())
    return maxRadius
}
