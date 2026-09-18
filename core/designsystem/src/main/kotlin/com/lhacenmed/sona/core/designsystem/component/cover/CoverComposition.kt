package com.lhacenmed.sona.core.designsystem.component.cover

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.pxOrElse
import coil3.toBitmap
import kotlin.math.min
import kotlin.random.Random
import kotlin.random.nextInt

/** How the covers of many tracks are laid out as one: Auxio's composition fetchers. */
internal enum class CoverArrangement {
    /** Four covers framed edge to edge, as if hung on a wall: Auxio's genre cover. */
    Gallery,

    /** Four covers scattered at a seeded tilt, like a messy pile of records: Auxio's artist cover. */
    Smattering,

    /** Four covers stacked diagonally, like a neat pile: Auxio's playlist cover. */
    Stack,
}

/**
 * The covers of an artist or a genre, as the one image Coil is asked to load for it.
 *
 * Composed with no ground of its own: where Auxio paints its gaps in the cover's background colour, these
 * clear them, and the cover's own ground shows through. The image is the same, but it holds none of the
 * theme's colours - so a theme changing, which moves every colour through each frame of its transition,
 * never has a cover composed again.
 */
internal data class CoverComposition(
    val coverArtUris: List<String>,
    val arrangement: CoverArrangement,
    /** Keeps the arrangement the same every time the cover is drawn. */
    val seed: Int,
    /** The cover's corner as a share of its side, or 0 for square corners. */
    val cornerRadiusRatio: Float,
) {
    /** Auxio's keyer: one composed image per set of covers, size, corner and arrangement. */
    fun memoryCacheKey(sizePx: Int): String =
        "${arrangement.name}:${coverArtUris.hashCode()}.$sizePx.$cornerRadiusRatio.$seed"
}

/** Auxio's `ComposeCoverDefaults`. */
internal object ComposeCoverDefaults {
    const val COVER_SIZE_PERCENT = 0.60f
    const val GAP_RATIO = 0.04f
    const val MIN_GAP_CORNER_RATIO = 0.35f
    const val MAX_CORNER_RATIO = 0.15f
}

/** A composition takes four covers; with fewer, the first stands alone, as one cover would. */
private const val COMPOSED_COVER_COUNT = 4

/** The side a composition is drawn at when Coil gives it no size: Auxio's fallback. */
private const val FALLBACK_SIZE_PX = 512

/**
 * Auxio's `CoverCompositionFetcher`: loads the first four covers that open, in the order given, and
 * composes them.
 *
 * Each cover is loaded through the app's image loader, decoded no larger than the composition it is drawn
 * into rather than at full size - four full covers a row would be a scroll's worth of wasted memory.
 */
internal class CoverCompositionFetcher(
    private val composition: CoverComposition,
    private val options: Options,
    private val imageLoader: ImageLoader,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val size = min(
            options.size.width.pxOrElse { FALLBACK_SIZE_PX },
            options.size.height.pxOrElse { FALLBACK_SIZE_PX },
        ).coerceAtLeast(1)

        val bitmaps = ArrayList<Bitmap>(COMPOSED_COVER_COUNT)
        for (coverArtUri in composition.coverArtUris) {
            loadBitmap(coverArtUri, size)?.let { bitmaps += it }
            if (bitmaps.size == COMPOSED_COVER_COUNT) break
        }
        if (bitmaps.size < COMPOSED_COVER_COUNT) {
            val first = bitmaps.firstOrNull() ?: return null
            return ImageFetchResult(image = first.asImage(), isSampled = true, dataSource = DataSource.DISK)
        }

        val random = Random(composition.seed)
        // Auxio's: cycled a fixed number of times, then a seeded number more.
        repeat(11) { random.nextLong() }
        repeat(random.nextInt(1..30) + 1) { random.nextLong() }
        val composed = when (composition.arrangement) {
            CoverArrangement.Gallery -> composeGallery(bitmaps, size, random, composition.cornerRadiusRatio)
            CoverArrangement.Smattering -> composeSmattering(bitmaps, size, random, composition.cornerRadiusRatio)
            CoverArrangement.Stack -> composeStack(bitmaps, size, random, composition.cornerRadiusRatio)
        }
        return ImageFetchResult(image = composed.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    private suspend fun loadBitmap(coverArtUri: String, sizePx: Int): Bitmap? {
        val request = ImageRequest.Builder(options.context)
            .data(coverArtUri)
            .size(sizePx)
            // Drawn into a software canvas, which cannot read a hardware bitmap.
            .allowHardware(false)
            .build()
        return (imageLoader.execute(request) as? SuccessResult)?.image?.toBitmap()
    }

    object Factory : Fetcher.Factory<CoverComposition> {
        override fun create(data: CoverComposition, options: Options, imageLoader: ImageLoader): Fetcher =
            CoverCompositionFetcher(data, options, imageLoader)
    }
}

/** Draws only where a mask was drawn first. */
internal val SourceInXfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)

/** Clears whatever it covers: how a composition cuts the gaps Auxio paints in its background colour. */
internal fun gapPaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
    xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
}

/** Auxio's: draws [bitmap] into [dest], cropped from its centre to [dest]'s shape. */
internal fun drawBitmapCover(canvas: Canvas, bitmap: Bitmap, dest: RectF, paint: Paint) {
    val bitmapRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
    val destRatio = dest.width() / dest.height()
    val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
    if (bitmapRatio > destRatio) {
        val newWidth = (bitmap.height * destRatio).toInt()
        val xOffset = (bitmap.width - newWidth) / 2
        srcRect.left = xOffset
        srcRect.right = xOffset + newWidth
    } else {
        val newHeight = (bitmap.width / destRatio).toInt()
        val yOffset = (bitmap.height - newHeight) / 2
        srcRect.top = yOffset
        srcRect.bottom = yOffset + newHeight
    }
    canvas.drawBitmap(bitmap, srcRect, dest, paint)
}

/** Auxio's: the order the covers are drawn in, bottom first. */
internal fun seededZOrder(count: Int, random: Random): List<Int> = (0 until count).toList().shuffled(random)
