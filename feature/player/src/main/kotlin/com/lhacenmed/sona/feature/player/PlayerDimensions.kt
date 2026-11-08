package com.lhacenmed.sona.feature.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.player.PlayerControls

// ArchiveTune's player dimensions.

internal val MiniPlayerHeight = 70.dp
internal val MiniPlayerBottomSpacing = 4.dp
internal val MiniPlayerHorizontalPadding = 12.dp

/**
 * The gap kept between the end of a screen's content and the mini player: the same as the player's gap
 * to the screen's sides, so it floats evenly clear of everything around it rather than meeting the last row.
 */
internal val MiniPlayerContentSpacing = MiniPlayerHorizontalPadding

internal val QueuePeekHeight = 64.dp
internal val QueueItemHeight = 72.dp

// The expanded player's measures - the same few the video player is built from, so the two read as one design.

/** The player's keyline: the artwork, the title, the seek bar and its times all stand this far in from the sides. */
internal val PlayerHorizontalPadding = 24.dp

/** A control of the player's bars - its touch target, and the glyph within it: every player's, see [PlayerControls]. */
internal val PlayerBarButtonSize = PlayerControls.BarButtonSize
internal val PlayerBarIconSize = PlayerControls.BarIconSize

/** The transport's height: its play button's. */
internal val PlayerPlayButtonSize = PlayerControls.PlayButtonSize

/** How quiet the player's secondary text is - the artist, the header's second line, the time left. */
internal const val PlayerSecondaryAlpha = PlayerControls.SecondaryAlpha

/** The artwork's corners, and the least it keeps from what is above and below it. */
internal val PlayerArtworkCornerRadius = 20.dp
internal val PlayerArtworkMinGap = 16.dp

/**
 * The seek bar: a thin track either side of an upright thumb, kept [PlayerSeekThumbGap] clear of it - flat, or waving
 * along the part played. [PlayerSeekBarHeight] is its touch target; the times stand [PlayerSeekTimeGap] beneath the thumb.
 */
internal val PlayerSeekBarHeight = 48.dp
internal val PlayerSeekTrackHeight = 4.dp
internal val PlayerSeekThumbSize = DpSize(4.dp, 20.dp)
internal val PlayerSeekThumbGap = 6.dp
internal val PlayerSeekWavelength = 28.dp
internal val PlayerSeekAmplitude = 3.dp
internal val PlayerSeekTimeGap = 8.dp

/** How far the thumb stands within the seek bar's touch target, above and below it: room the seek bar brings of its own. */
internal val PlayerSeekThumbInset = (PlayerSeekBarHeight - PlayerSeekThumbSize.height) / 2

/**
 * The gap between what is stacked above the seek bar - the title, the line playing or the lyrics' pill - and the thumb,
 * as seen: kept short of the seek bar by [PlayerSeekThumbInset], which it already has.
 */
internal val PlayerSectionGap = 16.dp
internal val PlayerGapAboveSeekBar = PlayerSectionGap - PlayerSeekThumbInset

/** The pill of lyrics in the player, where they open there: its height, corners and tint. */
internal val PlayerPillHeight = 48.dp
internal val PlayerPillCornerRadius = 16.dp
internal const val PlayerPillTintAlpha = 0.1f

internal val BottomSheetAnimationSpec = spring<Dp>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

internal val BottomSheetSoftAnimationSpec = spring<Dp>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessLow,
)
