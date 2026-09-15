package com.lhacenmed.sona.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.maxBitmapSize
import com.lhacenmed.sona.core.common.cover.DefaultCover
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.designsystem.theme.CoverStyle
import com.lhacenmed.sona.core.designsystem.theme.LocalCoverStyle
import com.lhacenmed.sona.core.designsystem.theme.SonaComponentStyle
import coil3.size.Size as DecodeSize

/** The look every cover shares - Auxio's cover dimensions. */
object CoverArtDefaults {

    /** The size a cover takes in a list row: Auxio's `size_touchable_small`. */
    val ListSize = 48.dp

    /** The corners a list cover is cut with in round mode: the corners every component shares. */
    val ListCornerRadius = SonaComponentStyle.CornerRadius

    internal val SelectionBadgeSize = 20.dp

    /** How far the selection badge sits in from the cover's bottom end: Auxio's `spacing_tiny`. */
    internal val SelectionBadgeInset = 4.dp
}

/** The scale the selection badge shrinks to as it fades away, and grows from as it appears. */
private const val SELECTION_BADGE_HIDDEN_SCALE = 0.9f

/** The shape a cover with [cornerRadius] is cut to - square corners when round mode is off. */
fun CoverStyle.shape(cornerRadius: Dp): Shape = RoundedCornerShape(if (isRounded) cornerRadius else 0.dp)

/**
 * A cover filling whatever it is laid over, for artwork that sits behind content rather than beside it.
 *
 * Where there is no image to show - no cover, covers turned off, or one that failed to load - the
 * default cover stands in, as it does for every other cover: its ground, with its glyph. Until an image
 * arrives nothing is drawn, so the surface beneath shows through.
 */
@Composable
fun SonaCoverBackdrop(
    coverArtUri: String?,
    modifier: Modifier = Modifier,
) {
    val style = LocalCoverStyle.current
    var hasFailed by remember(coverArtUri) { mutableStateOf(false) }
    if (coverArtUri == null || !style.showsCovers || hasFailed) {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            DefaultCoverGlyph(contentDescription = null)
        }
    } else {
        AsyncImage(
            model = rememberCoverRequest(coverArtUri, style),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onError = { hasFailed = true },
            modifier = modifier,
        )
    }
}

/**
 * One album cover, drawn the way the cover settings say. Every cover in the app is one of these.
 *
 * It sits on a `surfaceContainer` ground cut to the cover's shape. With force-square covers the image
 * is cropped to fill it; otherwise the whole image is fitted inside with its own corners rounded, the
 * way Auxio rounds the bitmap itself - so a wide cover is a rounded rectangle on the ground rather than
 * a strip cut off square by the shape's edge. Where there is no image to show - no cover, covers turned
 * off, or one that failed to load - an album icon half the cover's size stands in for it.
 */
@Composable
fun SonaCoverImage(
    coverArtUri: String?,
    contentDescription: String?,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
) {
    val style = LocalCoverStyle.current
    Box(
        modifier = modifier
            .clip(style.shape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        CoverPicture(
            coverArtUri = coverArtUri,
            contentDescription = contentDescription,
            cornerRadius = cornerRadius,
            style = style,
        )
    }
}

/**
 * A list cover, or - while its track is the one being played - the playing indicator in its place, with
 * a check badge that springs onto its corner while its row is selected. Auxio's `CoverView`.
 *
 * The indicator replaces the artwork rather than sitting on top of it, which is what lets a row say
 * "this one is playing" without tinting the row itself: the cover is the only thing that changes,
 * so the list keeps its rhythm and a selected row still reads as selected underneath.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SonaCoverArt(
    coverArtUri: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    isSelected: Boolean = false,
) {
    val style = LocalCoverStyle.current
    val badgeAlpha = animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "coverSelectionBadgeAlpha",
    )
    val badgeScale = animateFloatAsState(
        targetValue = if (isSelected) 1f else SELECTION_BADGE_HIDDEN_SCALE,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "coverSelectionBadgeScale",
    )

    Box(modifier = modifier.size(CoverArtDefaults.ListSize)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(style.shape(CoverArtDefaults.ListCornerRadius))
                .background(
                    if (isCurrent) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Hidden rather than removed while the indicator stands in, so the cover is already there
            // the moment the track stops being the current one, instead of loading again.
            CoverPicture(
                coverArtUri = coverArtUri,
                contentDescription = contentDescription,
                cornerRadius = CoverArtDefaults.ListCornerRadius,
                style = style,
                modifier = Modifier.graphicsLayer { alpha = if (isCurrent) 0f else 1f },
            )
            if (isCurrent) {
                SonaPlayingIndicator(
                    isPlaying = isPlaying,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.fillMaxSize(0.5f),
                )
            }
        }
        // Outside the clipped cover, so the badge keeps its round shape on a square-cornered cover.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(CoverArtDefaults.SelectionBadgeInset)
                .size(CoverArtDefaults.SelectionBadgeSize)
                .graphicsLayer {
                    alpha = badgeAlpha.value
                    scaleX = badgeScale.value
                    scaleY = badgeScale.value
                }
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** The image of a cover, or its placeholder icon where there is no image to show. */
@Composable
private fun CoverPicture(
    coverArtUri: String?,
    contentDescription: String?,
    cornerRadius: Dp,
    style: CoverStyle,
    modifier: Modifier = Modifier,
) {
    var hasFailed by remember(coverArtUri) { mutableStateOf(false) }
    if (coverArtUri == null || !style.showsCovers || hasFailed) {
        DefaultCoverGlyph(contentDescription = contentDescription, modifier = modifier)
    } else {
        var imageAspectRatio by remember(coverArtUri) { mutableFloatStateOf(Float.NaN) }
        AsyncImage(
            model = rememberCoverRequest(coverArtUri, style),
            contentDescription = contentDescription,
            contentScale = if (style.isForcedSquare) ContentScale.Crop else ContentScale.Fit,
            onSuccess = { success ->
                val intrinsicSize = success.painter.intrinsicSize
                imageAspectRatio = intrinsicSize.width / intrinsicSize.height
            },
            onError = { hasFailed = true },
            modifier = modifier
                .fillMaxSize()
                .then(
                    if (style.isForcedSquare) {
                        Modifier
                    } else {
                        Modifier.clipToFittedImage(
                            aspectRatio = imageAspectRatio,
                            cornerRadius = if (style.isRounded) cornerRadius else 0.dp,
                        )
                    },
                ),
        )
    }
}

/** [DefaultCover]'s glyph in the theme's colour, sized to the cover it stands in for. */
@Composable
private fun DefaultCoverGlyph(
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    Icon(
        imageVector = SonaIcons.CoverPlaceholder,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.onSurface,
        // An icon keeps its aspect ratio inside its bounds, so on a wide backdrop the glyph spans the
        // same share of the shorter side as it does on a square cover.
        modifier = modifier.fillMaxSize(DefaultCover.GLYPH_SIZE_FRACTION),
    )
}

/** A request for [coverArtUri], decoded no larger than the cover mode allows. */
@Composable
private fun rememberCoverRequest(coverArtUri: String, style: CoverStyle): ImageRequest {
    val context = LocalPlatformContext.current
    return remember(coverArtUri, style.maxResolutionPx) {
        ImageRequest.Builder(context)
            .data(coverArtUri)
            .apply { style.maxResolutionPx?.let { maxBitmapSize(DecodeSize(it, it)) } }
            .build()
    }
}

/**
 * Rounds the corners of an image fitted inside its bounds, where the image actually lies rather than at
 * the bounds' edge. Nothing is drawn before the image is known, so there is nothing to clip until then.
 */
private fun Modifier.clipToFittedImage(aspectRatio: Float, cornerRadius: Dp) = drawWithCache {
    val fittedSize = when {
        aspectRatio.isNaN() -> size
        aspectRatio > size.width / size.height -> Size(size.width, size.width / aspectRatio)
        else -> Size(size.height * aspectRatio, size.height)
    }
    val topLeft = Offset((size.width - fittedSize.width) / 2f, (size.height - fittedSize.height) / 2f)
    val clip = Path().apply {
        addRoundRect(RoundRect(Rect(topLeft, fittedSize), CornerRadius(cornerRadius.toPx())))
    }
    onDrawWithContent {
        clipPath(clip) { this@onDrawWithContent.drawContent() }
    }
}
