package com.lhacenmed.sona.feature.player

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One segment of a toggle row, rounding off while [active]. Ported from ArchiveTune. */
@Composable
internal fun ToggleSegmentButton(
    modifier: Modifier,
    active: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    activeContentColor: Color,
    inactiveContentColor: Color,
    activeCornerRadius: Dp,
    onClick: () -> Unit,
    @DrawableRes iconId: Int,
    contentDesc: String,
) {
    val bgColor by animateColorAsState(
        targetValue = if (active) activeColor else inactiveColor,
        animationSpec = tween(durationMillis = 250),
        label = "",
    )
    val cornerRadius by animateDpAsState(
        targetValue = if (active) activeCornerRadius else 8.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(cornerRadius))
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.padding(horizontal = 10.dp)) {
            Icon(
                painter = painterResource(iconId),
                contentDescription = contentDesc,
                tint = if (active) activeContentColor else inactiveContentColor,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
