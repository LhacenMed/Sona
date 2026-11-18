@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.library

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupScope
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.RoundedPolygon
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.SonaCoverBackdrop
import com.lhacenmed.sona.core.designsystem.component.SonaIconButton
import com.lhacenmed.sona.core.designsystem.component.SonaPlaylistCoverBackdrop
import com.lhacenmed.sona.core.designsystem.component.icon
import com.lhacenmed.sona.core.designsystem.component.shortTitle
import com.lhacenmed.sona.core.designsystem.component.title
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.designsystem.theme.SonaComponentStyle
import com.lhacenmed.sona.core.designsystem.theme.rememberPressFraction
import com.lhacenmed.sona.core.model.BuiltInList
import com.lhacenmed.sona.core.navigation.LocalNavigator

private const val SHORTCUT_COUNT = 3

/** A tile's height, and the gap between tiles. */
private val ShortcutTileHeight = 64.dp
private val TileGap = 4.dp

/** The group's own corners, and the tight ones where tiles meet. */
private val OuterCorner = 20.dp
private val InnerCorner = 6.dp

/** The badge at a tile's top start: an expressive shape with the tile's icon in it. */
private val BadgeSize = 28.dp
private val BadgeIconSize = 16.dp

/** How much of the tile's own colour is laid over a cover, enough for the badge and name to read on. */
private const val CoverScrimAlpha = 0.6f

/**
 * The three collections always one tap away, above the browsing tabs - one connected group of tiles, each with its own
 * expressive shape and its name beneath, over a preview of its list's cover.
 *
 * They sit outside the pager on purpose: the tabs are ways of browsing the library, while these are destinations of their
 * own - which is also why they keep their place while the tabs are swiped. Nothing waits for the covers - a tile draws the
 * same with or without one - and they are read here, below the pager, so a new cover recomposes this row alone. Favorites
 * and Recent preview their first track's cover, and Playlists the cover of the playlist the chosen sort puts first, stacked
 * as its row is - see [PlaylistsViewModel.playlistsCover].
 *
 * Each tile stands one step above the section it is in: [lift] is how lifted that section is, and the tiles lift with it,
 * so they never sink into its colour.
 *
 * The tiles are one button group, so holding a tile widens it and its neighbours give up the width it takes - the row
 * itself never changes width, and nothing around it moves.
 */
@Composable
fun LibraryShortcuts(
    lift: () -> Float,
    modifier: Modifier = Modifier,
    viewModel: PlaylistsViewModel = hiltViewModel(),
) {
    val navigator = LocalNavigator.current
    val favoritesCover by viewModel.favoritesCover.collectAsStateWithLifecycle()
    val playlistsCover by viewModel.playlistsCover.collectAsStateWithLifecycle()
    val recentlyPlayedCover by viewModel.recentlyPlayedCover.collectAsStateWithLifecycle()

    // The row's width is the one width a press never changes, so each tile's width at rest is read
    // from it rather than from the tile, whose own width is exactly what is animating.
    var rowWidthPx by remember { mutableIntStateOf(0) }
    val gapPx = with(LocalDensity.current) { TileGap.roundToPx() }
    val restingTileWidthPx = (rowWidthPx - gapPx * (SHORTCUT_COUNT - 1)) / SHORTCUT_COUNT

    val favoritesTitle = BuiltInList.FAVORITES.title
    val playlistsTitle = stringResource(R.string.playlists_title)
    val recentTitle = BuiltInList.RECENTLY_PLAYED.shortTitle
    ButtonGroup(
        // Never reached: three equal tiles always fit the row. Required by the group all the same,
        // and a row that did run out of room should fold into a menu rather than drop tiles.
        overflowIndicator = { menuState ->
            SonaIconButton(
                onClick = menuState::show,
                icon = SonaIcons.MoreVert,
                contentDescription = stringResource(R.string.shortcuts_more),
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { rowWidthPx = it.width },
        expandedRatio = SonaComponentStyle.PressedExpandedRatio,
        horizontalArrangement = Arrangement.spacedBy(TileGap),
    ) {
        shortcutTile(
            title = favoritesTitle,
            icon = BuiltInList.FAVORITES.icon,
            shape = MaterialShapes.Clover4Leaf,
            cover = favoritesCover,
            lift = lift,
            position = TilePosition.START,
            restingWidthPx = restingTileWidthPx,
            onClick = { navigator.go(PlaylistDetailScreen(viewModel.favoritesPlaylistId)) },
        )
        shortcutTile(
            title = playlistsTitle,
            icon = SonaIcons.QueueMusic,
            shape = MaterialShapes.Square,
            cover = playlistsCover,
            lift = lift,
            position = TilePosition.MIDDLE,
            restingWidthPx = restingTileWidthPx,
            onClick = { navigator.go(PlaylistsScreen) },
        )
        shortcutTile(
            title = recentTitle,
            icon = BuiltInList.RECENTLY_PLAYED.icon,
            shape = MaterialShapes.Pentagon,
            cover = recentlyPlayedCover,
            lift = lift,
            position = TilePosition.END,
            restingWidthPx = restingTileWidthPx,
            onClick = { navigator.go(RecentlyPlayedScreen) },
        )
    }
}

/** Where a tile stands in the group, which decides which of its corners are the group's own. */
private enum class TilePosition { START, MIDDLE, END }

/**
 * One tile: its badge at the top start, its name at the bottom start, over its cover when it has one - the default cover,
 * when its list's first track has no artwork.
 *
 * Equal weights, so the row's shape is fixed regardless of how long a name is. The cover only fills the size the badge and
 * name already give the tile, so a cover arriving, changing or going away never moves anything. The press is shared
 * between the tile, which it ripples and tightens the group's corners of, and the group, which widens the tile for it.
 */
private fun ButtonGroupScope.shortcutTile(
    title: String,
    icon: ImageVector,
    shape: RoundedPolygon,
    cover: ShortcutCover?,
    lift: () -> Float,
    position: TilePosition,
    restingWidthPx: Int,
    onClick: () -> Unit,
) = customItem(
    buttonGroupContent = {
        val interactionSource = remember { MutableInteractionSource() }
        val pressFraction = rememberPressFraction(interactionSource)
        // One shape for the tile's life, its press read as its outline is drawn - as Material's own buttons morph: a shape
        // made anew each frame reclips the tile mid-press, and its ripple is lost.
        val tileShape = remember(position, pressFraction) { TileShape(position) { pressFraction.value } }
        val colorScheme = MaterialTheme.colorScheme
        // One step above the section: on its surface at rest, on its lifted colour as it lifts - read as drawn.
        val tileColor = { lerpColor(colorScheme.surfaceContainer, colorScheme.surfaceContainerHigh, lift()) }
        Surface(
            onClick = onClick,
            interactionSource = interactionSource,
            modifier = Modifier
                .weight(1f)
                // As tall as the row lets it be: its own height at rest, less as the shortcuts collapse.
                .fillMaxHeight()
                .animateWidth(interactionSource),
            shape = tileShape,
            color = Color.Transparent,
        ) {
          Box(modifier = Modifier.drawBehind { drawRect(tileColor()) }) {
            // Faded as a whole - cover and scrim together - so one cover gives way to the next, and a tile gaining or
            // losing its cover fades too, rather than switching in a single frame.
            Crossfade(
                targetState = cover,
                modifier = Modifier.matchParentSize(),
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "shortcutCover",
            ) { shownCover ->
                if (shownCover != null) {
                    Box(modifier = Modifier.fillMaxSize().stretchedFromWidth(restingWidthPx)) {
                        when (shownCover) {
                            is ShortcutCover.Track -> SonaCoverBackdrop(
                                coverArtUri = shownCover.coverArtUri,
                                modifier = Modifier.matchParentSize(),
                            )
                            is ShortcutCover.Playlist -> SonaPlaylistCoverBackdrop(
                                coverArtUris = shownCover.coverArtUris,
                                seed = shownCover.seed,
                                modifier = Modifier.matchParentSize(),
                            )
                        }
                        // The tile's own colour rather than black, so the badge and name keep their contrast in both
                        // themes without changing colour when a cover appears.
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .drawBehind { drawRect(tileColor().copy(alpha = CoverScrimAlpha)) },
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .shiftedWithWidth(restingWidthPx)
                    .height(ShortcutTileHeight)
                    .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 9.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    modifier = Modifier
                        .size(BadgeSize)
                        .background(MaterialTheme.colorScheme.primaryContainer, shape.toShape()),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(BadgeIconSize),
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
          }
        }
    },
    menuContent = { menuState ->
        DropdownMenuItem(
            text = { Text(title) },
            leadingIcon = { Icon(imageVector = icon, contentDescription = null) },
            onClick = {
                menuState.dismiss()
                onClick()
            },
        )
    },
)

/**
 * The group's own corners where the tile stands at its ends, tightening by [pressFraction] as it is pressed, and tight ones
 * elsewhere.
 */
private class TileShape(private val position: TilePosition, private val pressFraction: () -> Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val outer = lerp(OuterCorner, SonaComponentStyle.PressedCornerRadius, pressFraction())
        val shape = when (position) {
            TilePosition.START -> RoundedCornerShape(topStart = outer, bottomStart = outer, topEnd = InnerCorner, bottomEnd = InnerCorner)
            TilePosition.MIDDLE -> RoundedCornerShape(InnerCorner)
            TilePosition.END -> RoundedCornerShape(topStart = InnerCorner, bottomStart = InnerCorner, topEnd = outer, bottomEnd = outer)
        }
        return shape.createOutline(size, layoutDirection, density)
    }
}

/**
 * Lays the content out at [restingWidthPx], then stretches it across whatever width the tile has now.
 *
 * For the cover: laid out again at every frame of the animation it would be re-cropped into a zoom, while stretched it
 * simply follows the tile's width - wider while held, narrower while a neighbour is. Until the row has been measured there
 * is no resting width, and the content is laid out as it is.
 */
private fun Modifier.stretchedFromWidth(restingWidthPx: Int) = layout { measurable, constraints ->
    if (restingWidthPx <= 0) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    val placeable = measurable.measure(constraints.copy(minWidth = restingWidthPx, maxWidth = restingWidthPx))
    val width = constraints.constrainWidth(restingWidthPx)
    layout(width, placeable.height) {
        placeable.placeWithLayer(0, 0) {
            scaleX = width / restingWidthPx.toFloat()
            transformOrigin = TransformOrigin(0f, 0.5f)
        }
    }
}

/**
 * Lays the content out at [restingWidthPx] and moves it by half of however much the tile's width has
 * changed, so the badge and name travel with the tile's centre - never stretched, never re-laid out.
 *
 * The height is the one the content has at rest, and the tile is as tall: as the shortcuts collapse, the tile
 * shrinks over its content, cutting it off from the bottom rather than squeezing it. Until the row has been
 * measured there is no resting width, and the content is laid out as it is.
 */
private fun Modifier.shiftedWithWidth(restingWidthPx: Int) = layout { measurable, constraints ->
    if (restingWidthPx <= 0) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    val placeable = measurable.measure(
        Constraints(minWidth = restingWidthPx, maxWidth = restingWidthPx),
    )
    val width = constraints.maxWidth
    layout(width, constraints.constrainHeight(placeable.height)) {
        placeable.placeWithLayer(0, 0) {
            translationX = (width - restingWidthPx) / 2f
        }
    }
}
