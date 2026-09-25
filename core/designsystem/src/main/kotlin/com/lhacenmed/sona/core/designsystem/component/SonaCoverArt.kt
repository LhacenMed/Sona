package com.lhacenmed.sona.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.maxBitmapSize
import com.lhacenmed.sona.core.common.cover.DefaultCover
import com.lhacenmed.sona.core.designsystem.component.cover.CoverArrangement
import com.lhacenmed.sona.core.designsystem.component.cover.CoverComposition
import com.lhacenmed.sona.core.designsystem.component.cover.CoverCompositionFetcher
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.designsystem.theme.CoverStyle
import com.lhacenmed.sona.core.designsystem.theme.LocalCoverStyle
import com.lhacenmed.sona.core.designsystem.theme.SonaComponentStyle
import coil3.size.Size as DecodeSize

/** The look every cover shares - Auxio's cover dimensions. */
object CoverArtDefaults {

    /** The size a track's cover takes in a list row: Auxio's `size_touchable_small`. */
    val ListSize = 48.dp

    /** The size an album's, artist's, genre's, playlist's or folder's cover takes: Auxio's `size_touchable_medium`. */
    val CollectionListSize = 56.dp

    /** The size an options sheet's cover takes: Auxio's `Widget.Auxio.Image.MidFull`, larger than any list row's. */
    val OptionsHeaderSize = 72.dp

    /** The corners a list cover is cut with in round mode: the corners every component shares. */
    val ListCornerRadius = SonaComponentStyle.CornerRadius

    /** The size a detail screen's header cover takes: Auxio's `Widget.Auxio.Image.Huge`. */
    val DetailHeaderSize = 256.dp

    /** The corners a detail screen's header cover is cut with: Auxio's `Corner.ExtraLarge`. */
    val DetailHeaderCornerRadius = 28.dp

    /** The icon a collection's cover shows while it has no image: Auxio's `size_icon_medium`. */
    internal val CollectionGlyphSize = 32.dp

    internal val SelectionBadgeSize = 20.dp

    /** How far the selection badge sits in from the cover's bottom end: Auxio's `spacing_tiny`. */
    internal val SelectionBadgeInset = 4.dp
}

/** The scale the selection badge shrinks to as it fades away, and grows from as it appears. */
private const val SELECTION_BADGE_HIDDEN_SCALE = 0.9f

/** How dark the current track's cover is dimmed beneath its playing indicator: ArchiveTune's `ActiveBoxAlpha`. */
private const val ACTIVE_COVER_SCRIM_ALPHA = 0.6f

/** The shape a cover with [cornerRadius] is cut to - square corners when round mode is off. */
fun CoverStyle.shape(cornerRadius: Dp): Shape = RoundedCornerShape(if (isRounded) cornerRadius else 0.dp)

/** The shape an artist's cover is cut to: Auxio's circular shape appearance - square when round mode is off. */
private fun CoverStyle.circularShape(): Shape = if (isRounded) CircleShape else RoundedCornerShape(0.dp)

/**
 * A cover filling whatever it is laid over, for artwork that sits behind content rather than beside it.
 *
 * Like every other cover, it is the default cover - its ground, with its glyph - until an image has
 * loaded over it: while one is on its way, and wherever there is none to show (no cover, covers turned
 * off, or one that failed to load).
 */
@Composable
fun SonaCoverBackdrop(
    coverArtUri: String?,
    modifier: Modifier = Modifier,
) {
    CoverBackdrop(request = rememberCoverRequest(coverArtUri, LocalCoverStyle.current), modifier = modifier)
}

/**
 * A playlist's cover filling whatever it is laid over - [SonaCoverBackdrop] for a playlist: its covers
 * stacked exactly as its row stacks them, [seed] keeping the pile the same, composed at the size it
 * fills so it is as sharp there as a row's is.
 */
@Composable
fun SonaPlaylistCoverBackdrop(
    coverArtUris: List<String>,
    seed: Int,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        CoverBackdrop(
            request = rememberCompositionRequest(coverArtUris, CoverArrangement.Stack, seed, max(maxWidth, maxHeight)),
            modifier = Modifier.matchParentSize(),
        )
    }
}

@Composable
private fun CoverBackdrop(
    request: ImageRequest?,
    modifier: Modifier = Modifier,
) {
    var isImageLoaded by remember(request) { mutableStateOf(false) }
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (!isImageLoaded) DefaultCoverGlyph(contentDescription = null)
        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onSuccess = { isImageLoaded = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * One album cover, drawn the way the cover settings say. Every cover in the app is one of these.
 *
 * It sits on a `surfaceContainer` ground cut to the cover's shape. With force-square covers the image
 * is cropped to fill it; otherwise the whole image is fitted inside with its own corners rounded, the
 * way Auxio rounds the bitmap itself - so a wide cover is a rounded rectangle on the ground rather than
 * a strip cut off square by the shape's edge. Where there is no image to show - no cover, covers turned
 * off, or one that failed to load - an album icon half the cover's size stands in for it, as it does
 * while an image is still loading.
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
            request = rememberCoverRequest(coverArtUri, style),
            contentDescription = contentDescription,
            cornerRadius = cornerRadius,
            style = style,
        )
    }
}

/**
 * A track's list cover: Auxio's `CoverView` as its `item_song` sizes it.
 *
 * While its track is the current one the cover is dimmed beneath the playing indicator, the way
 * ArchiveTune's thumbnail shows it: the cover is the only thing that changes, so the list keeps its
 * rhythm and a selected row still reads as selected underneath.
 */
@Composable
fun SonaCoverArt(
    coverArtUri: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    isSelected: Boolean = false,
    size: Dp = CoverArtDefaults.ListSize,
) {
    val style = LocalCoverStyle.current
    ListCoverFrame(
        size = size,
        shape = style.shape(CoverArtDefaults.ListCornerRadius),
        isCurrent = isCurrent,
        isPlaying = isPlaying,
        isSelected = isSelected,
        modifier = modifier,
    ) {
        CoverPicture(
            request = rememberCoverRequest(coverArtUri, style),
            contentDescription = contentDescription,
            cornerRadius = CoverArtDefaults.ListCornerRadius,
            style = style,
        )
    }
}

/**
 * An album's list cover: Auxio's `CoverView` bound to an album in its `item_parent`.
 *
 * An album is one cover, so it is drawn like a track's - only larger.
 */
@Composable
fun SonaAlbumCover(
    coverArtUri: String?,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    isSelected: Boolean = false,
    size: Dp = CoverArtDefaults.CollectionListSize,
    cornerRadius: Dp = CoverArtDefaults.ListCornerRadius,
) {
    CollectionCover(
        request = rememberCoverRequest(coverArtUri, LocalCoverStyle.current),
        glyph = SonaIcons.CoverPlaceholder,
        isCircular = false,
        isCurrent = isCurrent,
        isPlaying = isPlaying,
        isSelected = isSelected,
        size = size,
        cornerRadius = cornerRadius,
        modifier = modifier,
    )
}

/**
 * An artist's list cover: Auxio's `CoverView` bound to an artist - circular, and with four or more
 * covers among the artist's tracks, those covers scattered like a messy pile of records.
 *
 * [seed] keeps the pile the same every time the cover is drawn: the artist's identity.
 */
@Composable
fun SonaArtistCover(
    coverArtUris: List<String>,
    seed: Int,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    isSelected: Boolean = false,
    size: Dp = CoverArtDefaults.CollectionListSize,
) {
    CollectionCover(
        request = rememberCompositionRequest(coverArtUris, CoverArrangement.Smattering, seed, size),
        glyph = SonaIcons.Artist,
        isCircular = true,
        isCurrent = isCurrent,
        isPlaying = isPlaying,
        isSelected = isSelected,
        size = size,
        modifier = modifier,
    )
}

/**
 * A genre's list cover: Auxio's `CoverView` bound to a genre - with four or more covers among the
 * genre's tracks, those covers framed side by side like a gallery wall.
 *
 * [seed] keeps the gallery the same every time the cover is drawn: the genre's identity.
 */
@Composable
fun SonaGenreCover(
    coverArtUris: List<String>,
    seed: Int,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    isSelected: Boolean = false,
    size: Dp = CoverArtDefaults.CollectionListSize,
    cornerRadius: Dp = CoverArtDefaults.ListCornerRadius,
) {
    CollectionCover(
        request = rememberCompositionRequest(coverArtUris, CoverArrangement.Gallery, seed, size),
        glyph = SonaIcons.Genre,
        isCircular = false,
        isCurrent = isCurrent,
        isPlaying = isPlaying,
        isSelected = isSelected,
        size = size,
        cornerRadius = cornerRadius,
        modifier = modifier,
    )
}

/**
 * A playlist's list cover: Auxio's `CoverView` bound to a playlist - with four or more covers among
 * its tracks, those covers stacked into a neat pile.
 *
 * [seed] keeps the pile the same every time the cover is drawn: the playlist's identity.
 */
@Composable
fun SonaPlaylistCover(
    coverArtUris: List<String>,
    seed: Int,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    isSelected: Boolean = false,
    size: Dp = CoverArtDefaults.CollectionListSize,
    cornerRadius: Dp = CoverArtDefaults.ListCornerRadius,
) {
    CollectionCover(
        request = rememberCompositionRequest(coverArtUris, CoverArrangement.Stack, seed, size),
        glyph = SonaIcons.Playlist,
        isCircular = false,
        isCurrent = isCurrent,
        isPlaying = isPlaying,
        isSelected = isSelected,
        size = size,
        cornerRadius = cornerRadius,
        modifier = modifier,
    )
}

/**
 * A selection's cover: Auxio's `CoverView` bound to a list of songs - the selected tracks' covers
 * stacked as a playlist's are, over the song glyph while there is none to show.
 *
 * [seed] keeps the pile the same every time the cover is drawn: the selection's identity.
 */
@Composable
fun SonaSelectionCover(
    coverArtUris: List<String>,
    seed: Int,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    CollectionCover(
        request = rememberCompositionRequest(coverArtUris, CoverArrangement.Stack, seed, size),
        glyph = SonaIcons.Song,
        isCircular = false,
        isCurrent = false,
        isPlaying = false,
        isSelected = false,
        size = size,
        modifier = modifier,
    )
}

/**
 * A folder's list cover: the covers of its tracks stacked as a playlist's are - a folder is a pile of
 * files in an order, which is the same thing a stack says. Auxio has no folders of its own.
 *
 * [seed] keeps the pile the same every time the cover is drawn: the folder's path.
 */
@Composable
fun SonaFolderCover(
    coverArtUris: List<String>,
    seed: Int,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    isSelected: Boolean = false,
    size: Dp = CoverArtDefaults.CollectionListSize,
    cornerRadius: Dp = CoverArtDefaults.ListCornerRadius,
) {
    CollectionCover(
        request = rememberCompositionRequest(coverArtUris, CoverArrangement.Stack, seed, size),
        glyph = SonaIcons.Folder,
        isCircular = false,
        isCurrent = isCurrent,
        isPlaying = isPlaying,
        isSelected = isSelected,
        size = size,
        cornerRadius = cornerRadius,
        modifier = modifier,
    )
}

/** The list cover every album, artist, genre, playlist and folder shares: Auxio's `Widget.Auxio.Image.Medium`. */
@Composable
private fun CollectionCover(
    request: ImageRequest?,
    glyph: ImageVector,
    isCircular: Boolean,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isSelected: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = CoverArtDefaults.ListCornerRadius,
) {
    val style = LocalCoverStyle.current
    ListCoverFrame(
        size = size,
        shape = if (isCircular) style.circularShape() else style.shape(cornerRadius),
        isCurrent = isCurrent,
        isPlaying = isPlaying,
        isSelected = isSelected,
        modifier = modifier,
    ) {
        CoverPicture(
            request = request,
            contentDescription = null,
            cornerRadius = cornerRadius,
            style = style,
            isCircular = isCircular,
            glyph = glyph,
            // The glyph keeps the same share of the cover whatever size the cover is drawn at.
            glyphSize = size * (CoverArtDefaults.CollectionGlyphSize / CoverArtDefaults.CollectionListSize),
        )
    }
}

/**
 * A list cover's frame: its ground cut to [shape], the playing indicator over whatever is playing, and a
 * check badge that springs onto its corner while its row is selected.
 *
 * Every list cover in the app is one of these, so a track, an album and a genre all say "this is the one
 * playing" in exactly the same way.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ListCoverFrame(
    size: Dp,
    shape: Shape,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    cover: @Composable BoxScope.() -> Unit,
) {
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

    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            cover()
            // ArchiveTune's thumbnail: white on a black scrim in the cover's own shape.
            SonaPlayingIndicatorBox(
                isActive = isCurrent,
                isPlaying = isPlaying,
                color = Color.White,
                modifier = Modifier
                    .fillMaxSize()
                    .background(color = Color.Black.copy(alpha = ACTIVE_COVER_SCRIM_ALPHA), shape = shape),
            )
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
                imageVector = SonaIcons.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * The image of a cover, over its placeholder icon.
 *
 * The icon is drawn from the first frame and taken away only once the image has loaded, so a cover shows
 * the default cover while its image is on its way and keeps it wherever the image never comes - no
 * artwork, covers turned off, or a load that fails. A cover already in memory draws straight over it.
 * Drawing the icon only after a load had failed is what left covers blank in between: a track without
 * artwork still has an artwork address, and only the failed load says there is nothing there.
 *
 * A circular cover is always cropped to fill its circle, as Auxio square-crops every circular image.
 */
@Composable
private fun CoverPicture(
    request: ImageRequest?,
    contentDescription: String?,
    cornerRadius: Dp,
    style: CoverStyle,
    modifier: Modifier = Modifier,
    isCircular: Boolean = false,
    glyph: ImageVector = SonaIcons.CoverPlaceholder,
    glyphSize: Dp? = null,
) {
    var isImageLoaded by remember(request) { mutableStateOf(false) }
    if (!isImageLoaded) {
        // Described only when it is all there is to show; otherwise the image carries the description.
        DefaultCoverGlyph(
            contentDescription = if (request == null) contentDescription else null,
            glyph = glyph,
            size = glyphSize,
            modifier = modifier,
        )
    }
    if (request != null) {
        val cropsToFill = style.isForcedSquare || isCircular
        var imageAspectRatio by remember(request) { mutableFloatStateOf(Float.NaN) }
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = if (cropsToFill) ContentScale.Crop else ContentScale.Fit,
            onSuccess = { success ->
                val intrinsicSize = success.painter.intrinsicSize
                imageAspectRatio = intrinsicSize.width / intrinsicSize.height
                isImageLoaded = true
            },
            modifier = modifier
                .fillMaxSize()
                .then(
                    if (cropsToFill) {
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

/**
 * [glyph] in the theme's colour: [DefaultCover]'s glyph by default. It is [size] when given, and otherwise
 * spans [DefaultCover]'s share of the cover it stands in for.
 */
@Composable
private fun DefaultCoverGlyph(
    contentDescription: String?,
    modifier: Modifier = Modifier,
    glyph: ImageVector = SonaIcons.CoverPlaceholder,
    size: Dp? = null,
) {
    Icon(
        imageVector = glyph,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.onSurface,
        // An icon keeps its aspect ratio inside its bounds, so on a wide backdrop the glyph spans the
        // same share of the shorter side as it does on a square cover.
        modifier = if (size == null) {
            modifier.fillMaxSize(DefaultCover.GLYPH_SIZE_FRACTION)
        } else {
            modifier.size(size)
        },
    )
}

/**
 * A request for [coverArtUri], decoded no larger than the cover mode allows - or null when there is no
 * image to load: no cover, or covers turned off.
 */
@Composable
private fun rememberCoverRequest(coverArtUri: String?, style: CoverStyle): ImageRequest? {
    val context = LocalPlatformContext.current
    return remember(coverArtUri, style.showsCovers, style.maxResolutionPx) {
        if (coverArtUri == null || !style.showsCovers) return@remember null
        ImageRequest.Builder(context)
            .data(coverArtUri)
            .apply { style.maxResolutionPx?.let { maxBitmapSize(DecodeSize(it, it)) } }
            .build()
    }
}

/**
 * A request for [coverArtUris] composed as [arrangement] at a collection's list cover size - or null when
 * there is no image to load: no covers, or covers turned off.
 *
 * Its corner is Auxio's `responsiveCornerRatio`: the squarish corner as a share of the cover's side, even
 * for an artist's circular cover.
 */
@Composable
private fun rememberCompositionRequest(
    coverArtUris: List<String>,
    arrangement: CoverArrangement,
    seed: Int,
    size: Dp = CoverArtDefaults.CollectionListSize,
): ImageRequest? {
    val style = LocalCoverStyle.current
    val context = LocalPlatformContext.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    return remember(coverArtUris, arrangement, seed, style.showsCovers, style.isRounded, sizePx) {
        if (coverArtUris.isEmpty() || !style.showsCovers) return@remember null
        val composition = CoverComposition(
            coverArtUris = coverArtUris,
            arrangement = arrangement,
            seed = seed,
            cornerRadiusRatio = if (style.isRounded) CoverArtDefaults.ListCornerRadius / size else 0f,
        )
        ImageRequest.Builder(context)
            .data(composition)
            .fetcherFactory(CoverCompositionFetcher.Factory)
            .memoryCacheKey(composition.memoryCacheKey(sizePx))
            .size(sizePx)
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
