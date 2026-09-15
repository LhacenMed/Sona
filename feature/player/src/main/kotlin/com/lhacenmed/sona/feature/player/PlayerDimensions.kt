package com.lhacenmed.sona.feature.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ArchiveTune's player dimensions.

internal val MiniPlayerHeight = 70.dp
internal val MiniPlayerBottomSpacing = 4.dp
internal val MiniPlayerHorizontalPadding = 12.dp
internal val QueuePeekHeight = 64.dp
internal val QueueItemHeight = 72.dp
internal val PlayerHorizontalPadding = 32.dp

internal val BottomSheetAnimationSpec = spring<Dp>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

internal val BottomSheetSoftAnimationSpec = spring<Dp>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessLow,
)
