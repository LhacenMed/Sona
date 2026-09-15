package com.lhacenmed.sona.feature.library

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupScope
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.SonaCoverBackdrop
import com.lhacenmed.sona.core.designsystem.component.SonaIconButton
import com.lhacenmed.sona.core.designsystem.theme.SonaComponentStyle
import com.lhacenmed.sona.core.designsystem.theme.pressedCornerRadius
import com.lhacenmed.sona.core.designsystem.theme.rememberPressFraction
import com.lhacenmed.sona.core.navigation.LocalNavigator

private const val SHORTCUT_COUNT = 3

/** How much of the card's own colour is laid over a cover, enough for the icon and title to read on. */
private const val COVER_SCRIM_ALPHA = 0.6f

/**
 * The three collections that are always one tap away, above the browsing tabs.
 *
 * They sit outside the pager on purpose: the tabs are ways of *browsing the library*, while these
 * are destinations of their own - which is also why they keep their place while the tabs are swiped.
 *
 * No counts here. A count would have to be read before the row could be drawn, which would subscribe
 * the pager to library data it otherwise never touches; the destinations show their own counts. The
 * Favorites and Recent covers are different in kind: nothing waits for them - a card draws the same
 * with or without one - and they are read here, below the pager, so a new cover recomposes this row
 * alone.
 *
 * The cards are one button group, so holding a card widens it and its neighbours give up the width
 * it takes - the row itself never changes width, and nothing around it moves.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibraryShortcuts(
    modifier: Modifier = Modifier,
    viewModel: PlaylistsViewModel = hiltViewModel(),
) {
    val navigator = LocalNavigator.current
    val favoritesCover by viewModel.favoritesCover.collectAsStateWithLifecycle()
    val recentlyPlayedCover by viewModel.recentlyPlayedCover.collectAsStateWithLifecycle()

    // The row's width is the one width a press never changes, so each card's width at rest is read
    // from it rather than from the card, whose own width is exactly what is animating.
    var rowWidthPx by remember { mutableIntStateOf(0) }
    val spacingPx = with(LocalDensity.current) { SonaComponentStyle.ItemSpacing.roundToPx() }
    val restingCardWidthPx = (rowWidthPx - spacingPx * (SHORTCUT_COUNT - 1)) / SHORTCUT_COUNT

    ButtonGroup(
        // Never reached: three equal cards always fit the row. Required by the group all the same,
        // and a row that did run out of room should fold into a menu rather than drop cards.
        overflowIndicator = { menuState ->
            SonaIconButton(
                onClick = menuState::show,
                icon = Icons.Filled.MoreVert,
                contentDescription = "More shortcuts",
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { rowWidthPx = it.width },
        expandedRatio = SonaComponentStyle.PressedExpandedRatio,
        horizontalArrangement = Arrangement.spacedBy(SonaComponentStyle.ItemSpacing),
    ) {
        shortcutCard(
            title = "Favorites",
            icon = Icons.Filled.Favorite,
            restingWidthPx = restingCardWidthPx,
            cover = favoritesCover,
            onClick = { navigator.go(PlaylistDetailScreen(viewModel.favoritesPlaylistId)) },
        )
        shortcutCard(
            title = "Playlists",
            icon = Icons.Filled.LibraryMusic,
            restingWidthPx = restingCardWidthPx,
            onClick = { navigator.go(PlaylistsScreen) },
        )
        shortcutCard(
            title = "Recent",
            icon = Icons.Filled.History,
            restingWidthPx = restingCardWidthPx,
            cover = recentlyPlayedCover,
            onClick = { navigator.go(RecentlyPlayedScreen) },
        )
    }
}

/**
 * One card: its icon at the top start, its title at the bottom start, over a cover when it has one -
 * the default cover, when its list's first track has no artwork.
 *
 * Equal weights, so the row's shape is fixed regardless of how long a title is. The cover only fills
 * the size the icon and title already give the card, so a cover arriving, changing or going away never
 * moves anything. The press is shared between the card, which it ripples and tightens the corners of,
 * and the group, which widens the card for it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun ButtonGroupScope.shortcutCard(
    title: String,
    icon: ImageVector,
    restingWidthPx: Int,
    onClick: () -> Unit,
    cover: ShortcutCover? = null,
) = customItem(
    buttonGroupContent = {
        val interactionSource = remember { MutableInteractionSource() }
        val pressFraction by rememberPressFraction(interactionSource)
        val containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        Surface(
            modifier = Modifier
                .weight(1f)
                .animateWidth(interactionSource)
                // No indication here: a clickable Surface draws its ripple beneath its content, where
                // a cover would hide it. The ripple is drawn over everything instead, below.
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ),
            shape = RoundedCornerShape(pressedCornerRadius(pressFraction)),
            color = containerColor,
        ) {
            Box {
                // Faded as a whole - cover and scrim together - so one cover gives way to the next, and a
                // card gaining or losing its cover fades too, rather than switching in a single frame.
                Crossfade(
                    targetState = cover,
                    modifier = Modifier.matchParentSize(),
                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                    label = "shortcutCover",
                ) { shownCover ->
                    if (shownCover != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .stretchedFromWidth(restingWidthPx),
                        ) {
                            SonaCoverBackdrop(
                                coverArtUri = shownCover.coverArtUri,
                                modifier = Modifier.matchParentSize(),
                            )
                            // The card's own colour rather than black, so the icon and title keep their
                            // contrast in both themes without changing colour when a cover appears.
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(containerColor.copy(alpha = COVER_SCRIM_ALPHA)),
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .shiftedWithWidth(restingWidthPx)
                        .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .indication(interactionSource, ripple()),
                )
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
 * Lays the content out at [restingWidthPx], then stretches it across whatever width the card has now.
 *
 * For the cover: laid out again at every frame of the animation it would be re-cropped into a zoom,
 * while stretched it simply follows the card's width - wider while held, narrower while a neighbour
 * is. Until the row has been measured there is no resting width, and the content is laid out as it is.
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
 * Lays the content out at [restingWidthPx] and moves it by half of however much the card's width has
 * changed, so the icon and title travel with the card's centre - never stretched, never re-laid out.
 *
 * Every card's content moves this way, even on the card at the row's start edge, whose start never
 * moves: its content drifts toward the width it gains, the way a squeezed neighbour's drifts back. The
 * height is the one the content has at rest, so the row never changes height. Until the row has been
 * measured there is no resting width, and the content is laid out as it is.
 */
private fun Modifier.shiftedWithWidth(restingWidthPx: Int) = layout { measurable, constraints ->
    if (restingWidthPx <= 0) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    val placeable = measurable.measure(constraints.copy(minWidth = restingWidthPx, maxWidth = restingWidthPx))
    val width = constraints.maxWidth
    layout(width, placeable.height) {
        placeable.placeWithLayer(0, 0) {
            translationX = (width - restingWidthPx) / 2f
        }
    }
}
