package com.lhacenmed.sona.feature.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

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

/** A control of the player's bars - its touch target, and the glyph within it. */
internal val PlayerBarButtonSize = 44.dp
internal val PlayerBarIconSize = 22.dp

/** How far a row of controls keeps from the sides, so the glyph of each button at its ends stands on the keyline. */
internal val PlayerButtonRowPadding = PlayerHorizontalPadding - (PlayerBarButtonSize - PlayerBarIconSize) / 2

/** The transport: previous and next either side of play, as far apart as the screen allows, up to [PlayerTransportGap]. */
internal val PlayerSkipButtonSize = 56.dp
internal val PlayerSkipIconSize = 28.dp
internal val PlayerPlayButtonSize = 72.dp
internal val PlayerPlayIconSize = 32.dp
internal val PlayerTransportGap = 32.dp

/** The dot beneath a control that is on - a toggle, or the view it opens. */
internal val PlayerLitDotSize = 4.dp
internal val PlayerLitDotInset = 3.dp

/** How quiet the player's secondary text is - the artist, the header's second line, the time left. */
internal const val PlayerSecondaryAlpha = 0.72f

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
